# 問題分類按需載入（路由）實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **本專案規則：執行者不得自己執行 `git add` 或 `git commit`。** 本計畫各任務沒有 Commit 步驟。使用者會把上一輪（demo 後修正）與本計畫的全部變更**最後一次 commit**；Task 1–7 連續執行，完成後交付 commit 指令，再與使用者一起做 Task 8。

**Goal:** 每次提問先用一次模型呼叫選出一個問題分類（或無），回答時只載入該分類；回饋統計與評測都能對應到實際使用的分類；評測結果頁預設只看結果不同的題目。

**Architecture:** 新元件 `InstructionRouter` 以 strict JSON enum 選出分類；`ChatOrchestrator.Turn` 先去識別化、再決定分類（快捷按鈕的 `categoryKey` 優先、路由失敗退回全部），然後以 `PromptAssembler.assemble(configs, selection)` 組裝。`evaluate()` 改收 `PublishedConfigs` 快照，走同一流程。稽核記 `routeSource`、`instructionKey`，`promptVersions` 只列實際載入的設定；回饋統計對問題分類只計入 `ROUTER`／`CHIP`。評測資料集新增 `expect.category`、規則 `routeOk` 與「分類正確率」。

**Tech Stack:** Java 25、Spring Boot 4.1、LangChain4j 1.22.0、Jackson 3、MongoDB 8、JUnit 5、Testcontainers；Vue 3.5、TypeScript、Pinia、Vitest。

**Spec:** [docs/superpowers/specs/2026-10-27-instruction-routing-design.md](../specs/2026-10-27-instruction-routing-design.md)（v0.1，下稱「路由規格」）；評測相關沿用 [Prompt 評測集設計](../specs/2026-10-24-prompt-evaluation-design.md)（v0.3，下稱「評測規格」）。

## Global Constraints

- 測試指令：後端在 `backend/` 執行 `.\mvnw test -Dtest=<類別>`（Testcontainers 需要 Docker）；前端在 `frontend/` 執行 `npm test -- <關鍵字>`、`npx vue-tsc -b`。
- 不新增 Maven 或 npm 依賴。
- 路由與回答使用同一個 `ChatModel` bean；路由 `temperature` 0、不帶工具、strict JSON Schema。
- `RouterPrompt.TEXT` 只存在程式碼，任何 API 都不回傳；文字照路由規格 2.1 原文。
- 送給路由的提問與歷史是送給回答模型的同一份去識別化內容；歷史只取最後 4 則、每則最多 500 code point。
- `categoryKey` 格式 `^instruction\.[a-z0-9-]{1,50}$`；`expect.category` 格式 `^instruction\.[a-z0-9-]{1,50}$` 或 `none`。
- `RouteSource`：`ROUTER`、`CHIP`、`FALLBACK`。
- log 只有計數、代碼與設定鍵；不得出現提問、回答、路由輸入輸出。
- 對話 API 不新增錯誤碼；路由失敗不影響回答。
- 寫入 MongoDB 一律用明確的 `org.bson.Document`；validator 新欄位選填，舊紀錄仍合法。
- 前端不得用 `v-html`；只用既有 CSS 變數；元件不直接用 axios。

## Review Focus

1. **快捷按鈕開頭的對話之後的追問**（「那第二點呢？」）：追問不帶 `categoryKey`，必須走路由，且路由輸入含前面的對話。→ Task 2 測試 `followUpWithoutCategoryKeyIsRoutedWithHistory`。
2. **分類在頁面載入後被捨棄或下架，使用者才點快捷按鈕**：失效的 `categoryKey` 改走路由，不回錯誤。→ Task 2 測試 `staleCategoryKeyIsRouted`。
3. **模型無視 strict enum 回了不存在的 key**：視為格式錯誤 → `FALLBACK`，不得載入不存在的分類。→ Task 1 測試 `unknownKeyIsFallback`。
4. **長對話歷史讓路由就用掉大部分 token 預算**：路由 token 計入 16,000 上限，超過時照既有規則回 `AI_TOKEN_BUDGET_EXCEEDED`，不是退回。→ Task 2 測試 `routerTokensCountTowardBudget`。
5. **重試以快捷按鈕送出、但失敗的訊息**：重試要沿用原本的 `categoryKey`。→ Task 5 測試 `retry keeps the categoryKey of a chip question`。

---

## 檔案結構

```
backend/src/main/java/com/example/copilot/aiassistant/
├─ prompt/RouteSource.java、InstructionSelection.java、RouteDecision.java、RouterPrompt.java、InstructionRouter.java （新）
├─ prompt/PromptAssembler.java                     （改）snapshot()、assemble(configs, selection)
├─ chat/ChatOrchestrator.java、ChatCommand.java、EvaluationReport.java、dto/ChatRequestBody.java、ChatController.java （改）
├─ audit/AuditEntry.java、AuditRecord.java、AuditLogger.java （改）
├─ feedback/FeedbackService.java                   （改）
├─ evaluation/EvalRunService.java、EvalRunner.java、EvalRunRepository.java、EvalCase.java、EvalDatasetLoader.java、
│  CaseChecker.java、RuleChecks.java、CaseResult.java、EvalMetrics.java （改）
backend/src/main/resources/mongo/audit-validator.json、eval-run-validator.json、eval/eval-cases.yml （改）

frontend/src/features/ai-assistant/
├─ types.ts、api/aiAssistantApi.ts、stores/useAssistantStore.ts、composables/useAssistantChat.ts （改）
├─ components/QuickQuestions.vue 與往上傳遞 ask 事件的元件 （改）
├─ admin/EvaluationSummary.vue、EvaluationCaseList.vue、EvaluationCasesDialog.vue、EvaluationView.vue、
│  VersionHistory.vue、PromptManagementPage.vue、evaluationFormat.ts （改）
```

---

## Phase A：後端

### Task 1：InstructionRouter 與組裝選項

**Files:**
- Create: `prompt/RouteSource.java`、`prompt/InstructionSelection.java`、`prompt/RouteDecision.java`、`prompt/RouterPrompt.java`、`prompt/InstructionRouter.java`
- Modify: `prompt/PromptAssembler.java`
- Test: `prompt/InstructionRouterTest.java`（新，使用 `ScriptedChatModel`）、`prompt/PromptAssemblerTest.java`

**Interfaces:**
- Produces:
  - `enum RouteSource { ROUTER, CHIP, FALLBACK }`
  - `record InstructionSelection(Kind kind, String configKey)`，`enum Kind { ALL, NONE, ONLY }`；`static all()`、`none()`、`only(String configKey)`
  - `record RouteDecision(String instructionKey, RouteSource source, boolean modelCalled, long inputTokens, long outputTokens, long totalTokens)`；`InstructionSelection selection()`：`FALLBACK` → `all()`，`instructionKey == null` → `none()`，否則 `only(key)`
  - `RouterPrompt.TEXT`（路由規格 2.1 的 system message 原文）
  - `InstructionRouter.route(String redactedQuestion, List<ChatMessage> redactedHistory, List<AssistantConfig> instructions): RouteDecision`（`@Component`；不丟例外；`AssistantException` 例外：見 Step 3）
  - `PromptAssembler.snapshot(): PublishedConfigs`（= 快取的 `current()`）；`PromptAssembler.assemble(PublishedConfigs configs, InstructionSelection selection): AssembledPrompt`；移除 `current()` 與單參數 `assemble(configs)`（所有呼叫端改用 `assemble(configs, InstructionSelection.all())` 或依路由結果）

- [ ] **Step 1：寫失敗測試 `InstructionRouterTest`**
  - `requestShape`：system message 等於 `RouterPrompt.TEXT`；user message 為 JSON `{question, history, categories}`；`history` 只有最後 4 則、每則截到 500 code point、role 為 `user`／`assistant`；`categories[i]` 為 `{key, name, scope}`，`scope` 取自內容的 `## 適用問題` 章節（去除前後空白，到下一個 `## ` 為止），沒有該章節時等於 `name`；`temperature == 0.0`；沒有 tool specifications；`responseFormat` 為 strict schema，`category` 的 enum 為全部 key 加 `none`。
  - `picksCategory`：回 `{"category":"instruction.contact-prep"}` → `RouteDecision("instruction.contact-prep", ROUTER, true, …tokens)`。
  - `noneMeansNoInstruction`：回 `{"category":"none"}` → `instructionKey == null`、`ROUTER`、`selection().kind() == NONE`。
  - `unknownKeyIsFallback`：回 `{"category":"instruction.other"}` → `FALLBACK`、`selection().kind() == ALL`。
  - `badOutputIsFallback`：非 JSON、缺 `category`、模型丟 `RuntimeException` → `FALLBACK`；後兩者 `modelCalled == true`，tokens 照回應（例外時 0）。
  - `noInstructionsSkipsModel`：instructions 為空 → `ROUTER`、null、`modelCalled == false`、沒有模型呼叫。
  - `failureLogHasCodeOnly`：`LogCapture` 抓到 WARN `chat route failed requestId= code=`，不含提問文字。
- [ ] **Step 2：寫失敗測試 `PromptAssemblerTest`** — `only(key)` 只出現該分類的 `## 名稱` 與內容、`versions` 為 `system.main` 與該分類；`none()` 在「# 問題分類」之下為「（本題沒有適用的問題分類）」、`versions` 只有 `system.main`；`all()` 與現行相同；`snapshot()` 回傳快取內容。既有測試改用新方法。
- [ ] **Step 3：執行確認失敗，再實作** — 路由的 `ChatRequest` 照 `EvalJudge` 的作法建立（`JsonEnumSchema` 的 enum 依本次 instructions 建立）；模型例外以 `LlmErrorMapper` 轉成錯誤碼寫進 WARN log，但不往外丟；`maxOutputTokens` 沿用設定。log 的 requestId 取 `RequestIdFilter.current()`。
- [ ] **Step 4：維持可編譯** — 把現有呼叫端（`ChatOrchestrator` 的 `current()`、`EvalRunService` 的單參數 `assemble`、測試中的覆寫）暫時改成 `assemble(snapshot(), InstructionSelection.all())` 的等價寫法，行為不變；路由由 Task 2 接上。
- [ ] **Step 5：執行確認通過** — `.\mvnw test -Dtest=InstructionRouterTest,PromptAssemblerTest`，再跑 `.\mvnw test` 全部。

### Task 2：對話流程接上路由、快捷按鈕、稽核與評測快照

**Files:**
- Modify: `chat/ChatOrchestrator.java`、`chat/ChatCommand.java`、`chat/EvaluationReport.java`、`chat/dto/ChatRequestBody.java`、`chat/ChatController.java`、`audit/AuditEntry.java`、`audit/AuditRecord.java`、`audit/AuditLogger.java`、`resources/mongo/audit-validator.json`、`evaluation/EvalRunService.java`、`evaluation/EvalRunner.java`
- Test: `chat/ChatOrchestratorTest.java`、`chat/ChatControllerTest.java`、`audit/AuditLoggerTest.java`、`config/MongoSchemaTest.java`、`evaluation/EvalRunServiceTest.java`、`evaluation/EvalRunnerTest.java`（及其他因簽名改變需調整者）

**Interfaces:**
- Consumes: Task 1 全部
- Produces:
  - `ChatCommand(String message, List<HistoryMessage> history, String categoryKey)`（`categoryKey` 可為 null）
  - `ChatRequestBody` 新增 `@Pattern(regexp = "^instruction\\.[a-z0-9-]{1,50}$") String categoryKey`（選填）
  - `ChatOrchestrator.evaluate(String question, UserContext user, CustomerContext customer, PublishedConfigs configs): EvaluationReport`（取代 `AssembledPrompt` 參數）
  - `EvaluationReport` 新增元件 `String routeSource`、`String instructionKey`（`failed(code)` 時兩者 null）
  - `AuditEntry` 新增元件 `String routeSource`、`String instructionKey`；`AuditRecord.toDocument()` 寫入兩欄（明確 null）
  - `EvalRunService.EvalSnapshot(PublishedConfigs baseline, PublishedConfigs candidate)`；run 的 `baselineVersions` 取 `assembler.assemble(baseline, all()).versions()`

- [ ] **Step 1：寫失敗測試 `ChatOrchestratorTest`**（`ScriptedChatModel` 第一個回應是路由）
  - `routedInstructionIsTheOnlyOneLoaded`：快照有 3 個分類，路由回其中一個 → 回答請求的 system message 只含該分類；稽核 `routeSource == "ROUTER"`、`instructionKey` 為該 key、`promptVersions` 只有 `system.main` 與該分類；`modelCalls` 含路由那一次；token 分項含路由。
  - `noneLoadsNoInstruction`：路由回 `none` → system message 含「（本題沒有適用的問題分類）」、`instructionKey == null`、`promptVersions` 只有 `system.main`。
  - `validCategoryKeySkipsRouter`：`categoryKey` 為已發布分類 → 沒有路由請求（第一個模型請求就是回答）、`routeSource == "CHIP"`。
  - `staleCategoryKeyIsRouted`：`categoryKey` 不在快照中 → 走路由、`ROUTER`，不丟例外。
  - `followUpWithoutCategoryKeyIsRoutedWithHistory`：帶 2 則歷史、無 `categoryKey` → 路由請求的 `history` 含那 2 則（去識別化後）。
  - `routerFailureFallsBackAndAnswers`：路由回非 JSON → 回答請求含全部分類、`routeSource == "FALLBACK"`、`instructionKey == null`、回答 ANSWERED。
  - `routerTokensCountTowardBudget`：路由回應的 token 為 15,990、回答回應 20 → `AI_TOKEN_BUDGET_EXCEEDED`，稽核 FAILED 且 `routeSource == "ROUTER"`。
  - `routeFieldsInLogLine`：「chat answered」行含 `routeSource=ROUTER instructionKey=instruction.…`，不含提問文字。
  - `evaluateRoutesWithGivenSnapshot`：`evaluate(..., configs)` 用傳入快照的分類路由；`EvaluationReport.routeSource`、`instructionKey` 正確；不寫稽核。
- [ ] **Step 2：寫失敗測試**
  - `ChatControllerTest.categoryKeyFormatIsValidated`：`categoryKey: "system.main"` → 400 `AI_INPUT_INVALID`；`"instruction.contact-prep"` → 正常。
  - `AuditLoggerTest`：新欄位寫入；未知時為明確 null。`MongoSchemaTest`：validator 接受 `routeSource: "CHIP"`、拒絕 `"OTHER"`。
  - `EvalRunServiceTest`／`EvalRunnerTest`：快照改為 `PublishedConfigs` 後既有斷言照舊；候選快照含編輯中版本時，路由請求的 `categories` 含編輯中版本的 `scope`（`candidateScopeReachesRouter`）。
- [ ] **Step 3：執行確認失敗，再實作**
  - `Turn` 改為持有 `PublishedConfigs configs`（`chat()` 在 try 內呼叫 `promptAssembler.snapshot()`，載入失敗照舊以 `AI_INTERNAL_ERROR` 稽核）。
  - `answer(command)`：先去識別化提問與歷史 → 決定分類（`categoryKey` 有效 → `CHIP`；否則 `checkDeadline()` 後呼叫 `InstructionRouter.route`）→ 路由的 `modelCalled` 計入 `modelCalls`、token 加總後檢查預算（超過丟 `AI_TOKEN_BUDGET_EXCEEDED`）→ `assemble(configs, decision.selection())` → 既有迴圈。
  - `history()` 回傳的去識別化訊息同時用於路由與回答（同一份）。
  - log 兩行加 `routeSource={} instructionKey={}`。
  - validator：`routeSource` enum `["ROUTER","CHIP","FALLBACK",null]`、`instructionKey` string|null maxLength 64，皆選填。
- [ ] **Step 4：執行確認通過** — 本任務相關測試類別，再跑 `.\mvnw test` 全部。

### Task 3：回饋統計只計入實際使用的分類

**Files:**
- Modify: `feedback/FeedbackService.java`
- Test: `feedback/FeedbackControllerTest.java`

**Interfaces:**
- Consumes: Task 2 的稽核欄位

- [ ] **Step 1：寫失敗測試**
  - `instructionStatsCountOnlyRoutedAndChip`：同一個分類 v1 各有回饋紀錄 `ROUTER`（UP）、`CHIP`（DOWN）、`FALLBACK`（UP）、沒有 `routeSource` 的舊紀錄（UP）→ 統計為 `up 1, down 1`。
  - `systemMainStatsCountEverything`：上述 4 筆的 `promptVersions` 都含 `system.main` v1 → `system.main` 統計 `up 3, down 1`。
- [ ] **Step 2：執行確認失敗，再實作** — 依 key 的 `configType`（既有的存在檢查已取到該設定）決定：`SYSTEM_INSTRUCTION` 在第一個 `$match` 加 `routeSource: { $in: ["ROUTER","CHIP"] }`；`SYSTEM_PROMPT` 不變。
- [ ] **Step 3：執行確認通過** — `.\mvnw test -Dtest=FeedbackControllerTest`。

### Task 4：評測的分類檢查與分類正確率

**Files:**
- Modify: `evaluation/EvalCase.java`、`EvalDatasetLoader.java`、`CaseChecker.java`、`RuleChecks.java`、`CaseResult.java`、`EvalMetrics.java`、`EvalRunRepository.java`（Document 對應）、`resources/eval/eval-cases.yml`、`resources/mongo/eval-run-validator.json`
- Test: `EvalDatasetLoaderTest`、`CaseCheckerTest`、`EvalMetricsTest`、`EvalRunServiceTest`（往返）、`MongoSchemaTest`、`EvalControllerTest`

**Interfaces:**
- Consumes: Task 2 的 `EvaluationReport.routeSource`、`instructionKey`
- Produces:
  - `EvalCase.Expect` 新增元件 `String category`（null = 不檢查）
  - `RuleChecks` 新增 `Boolean routeOk`（null = 不檢查）
  - `CaseResult` 新增 `Boolean routeOk`、`String routeSource`、`String instructionKey`；`passed` 另需 `routeOk != Boolean.FALSE`
  - `EvalCaseRecord` 新增 `String expectCategory`（寫入當下的題目預期，與既有 `expectRefusal` 相同做法；null = 不檢查）
  - `EvalMetrics` 新增 `int routeOk`、`int routeCases`

- [ ] **Step 1：寫失敗測試**
  - `EvalDatasetLoaderTest`：`category: instruction.contact-prep` 與 `none` 合法；`category: system.main`、`Contact` → 錯誤 `<id>.expect.category: …`；`expect` 白名單含 `category`；bundled 資料集 13 題有 `category`，值照路由規格 4.1 表格。
  - `CaseCheckerTest.routeCheck`：預期 key 且相符 → true；不符 → false；預期 `none` 且 `ROUTER`+null → true；預期 `none` 但 `FALLBACK` → false；`FAILED` 且有預期 → false；沒有預期 → null。
  - `EvalMetricsTest`：`routeOk`／`routeCases` 計數；`routeOk == false` 使 `passed` 為 false；`routeOk == null` 不影響。
  - `expectedCategoryMissingFromSnapshotFailsRouteCheck`：預期 key 不在候選快照（分類被移除）→ `routeOk == false`，不丟例外。
  - `MongoSchemaTest`：validator 接受新欄位（`routeOk` bool|null、`routeSource` enum|null、`instructionKey` string|null ≤64、`cases[].expectCategory` string|null ≤64、Metrics 的 `routeOk`、`routeCases` int ≥0）；`EvalRunServiceTest` 的往返測試含 `expectCategory`。
- [ ] **Step 2：執行確認失敗，再實作**；`eval-cases.yml` 依路由規格 4.1 表格為 13 題加上 `expect.category`（Q5-opening、D4-identity、D5-other-customer、X5-advice-refuse 不加）。
- [ ] **Step 3：執行確認通過** — 上列測試類別，再跑 `.\mvnw test` 全部。

---

## Phase B：前端

### Task 5：快捷按鈕送出 categoryKey

**Files:**
- Modify: `types.ts`、`api/aiAssistantApi.ts`、`stores/useAssistantStore.ts`、`composables/useAssistantChat.ts`、`components/QuickQuestions.vue` 與把 `ask` 事件往上傳的元件（同既有 `ask` 的傳遞鏈）
- Test: `stores/__tests__/useAssistantStore.spec.ts`、`components/__tests__/AssistantPanel.spec.ts`（或 QuickQuestions 所在元件的 spec）

**Interfaces:**
- Produces: `ChatRequestBody.categoryKey?: string`；`ChatMessage.categoryKey?: string`（只用在使用者訊息）；store `send(text: string, categoryKey?: string)`；`useAssistantChat().ask(question: string, categoryKey?: string)`；`QuickQuestions` emit `ask: [question: string, categoryKey: string]`

- [ ] **Step 1：寫失敗測試**
  - store：`send('q', 'instruction.contact-prep')` → api body 含 `categoryKey`；`send('q')` → body 沒有 `categoryKey` 欄位；`retry keeps the categoryKey of a chip question`：失敗後重試，api 第二次收到同一個 `categoryKey`。
  - 元件：點快捷按鈕 → `ask` 收到題目與該分類的 `configKey`；在輸入框送出 → 不帶 `categoryKey`。
- [ ] **Step 2：執行確認失敗，再實作；Step 3：執行確認通過** — `npm test -- useAssistantStore AssistantPanel`（依實際 spec 名稱），再 `npx vue-tsc -b`。

### Task 6：評測畫面與版本歷程

**Files:**
- Modify: `types.ts`、`admin/EvaluationSummary.vue`、`admin/EvaluationCaseList.vue`、`admin/EvaluationCasesDialog.vue`、`admin/EvaluationView.vue`、`admin/VersionHistory.vue`、`admin/PromptManagementPage.vue`、`admin/evaluationFormat.ts`
- Test: 對應的 `admin/__tests__/*.spec.ts`

**Interfaces:**
- Consumes: Task 4 的 JSON 欄位（`CaseResult.routeOk`、`routeSource`、`instructionKey`；`EvalCaseRecord.expectCategory`；`EvalMetrics.routeOk`、`routeCases`；`EvalCase.expect.category`）
- Produces: `EvaluationCaseList` 與 `EvaluationCasesDialog` 新增 prop `nameOf: (configKey: string) => string`（由 `EvaluationView` 傳入既有的 `nameOf`）；`VersionHistory` 新增 prop `configType: ConfigType`

- [ ] **Step 1：寫失敗測試**
  - `EvaluationSummary`：新增「分類正確率」列，格式「85%（11/13）」，分母 0 顯示「—」，方向規則同其他比率（越高越好、差 1 題以內不標色）。
  - `EvaluationCaseList`：
    - 「只看結果不同的題目」預設勾選；取消後顯示全部。
    - 列標題只有一個標籤：任一邊 null →「執行中」；`passed` 不同 →「變好」／「變差」；相同 → 沒有標籤；不再出現「已發布：」「編輯中：」。
    - 展開：兩邊各顯示路由結果——`instructionKey` 的名稱（`nameOf`）、`ROUTER` 且 null →「無」、`FALLBACK` →「路由失敗，載入全部」；`routeOk === false` 時未通過的檢查含「分類不符：預期 {名稱或「無」}」，預期值取自該題的 `expectCategory`（`none` →「無」）。
  - `EvaluationCasesDialog`：新增「預期分類」欄（名稱、`none` →「無」、未填 →「—」）。
  - `VersionHistory`：`configType === 'SYSTEM_INSTRUCTION'` 時小字為「回饋統計涵蓋 180 天內、且確定使用此分類的回答」，`SYSTEM_PROMPT` 維持「回饋統計涵蓋 180 天內的回答」。
- [ ] **Step 2：執行確認失敗，再實作；Step 3：執行確認通過** — `npm test`（全部）與 `npx vue-tsc -b`。

---

## Phase C：文件與實測

### Task 7：文件同步

- [ ] **Step 1** — 依路由規格第 9 節更新各文件；路由規格狀態改為「已實作（真實模型驗證見 8.3）」、v0.2；評測規格改為 v0.4，同步 3.3、4.1、4.6、8.3。`ideas-backlog.md` 記錄「全部載入（10/19，commit `1e2717d`）→ 先路由（10/27）」的決策與原因（回饋歸屬、token 隨分類數成長）。
- [ ] **Step 2** — 評測規格 11.4 的兩處措辭：發現 2 註明「第二輪已完成」；「題目與程式為最終版本」改為「題目與程式為當時（第一輪修正後）的版本」。
- [ ] **Step 3** — `grep` 確認文件中沒有「已發布：通過」等已移除的標籤文字（`plans/` 除外）。

### Task 8：真實模型驗證（主控者與使用者一起做）

- [ ] **Step 1** — 使用者重新啟動後端（載入上一輪與本計畫的程式），前端不需重啟以外的動作。
- [ ] **Step 2** — 以 API 建立 `system.main` 草稿（內容 = `backend/src/main/resources/seed/system.main.md`），執行評測：記錄五項指標、Q5／D4／D5／X2／X3／K1 的結果與評審判定、每題平均 token（與評測規格 11.4 的 4,570 比較）、路由 token、整次耗時。
- [ ] **Step 3** — 使用者手動：一般提問、快捷按鈕、追問各一次並按 👍／👎；主控者查稽核 `routeSource`、`instructionKey`、`promptVersions`，與版本歷程統計只計入使用的分類。
- [ ] **Step 4** — 結果寫入路由規格 8.3（並在評測規格 11.4 加一句指向）；是否發布 `system.main` 草稿由使用者決定；未發布則捨棄草稿。
- [ ] **Step 5** — 交付一次涵蓋上一輪與本計畫全部變更的 commit 指令。
