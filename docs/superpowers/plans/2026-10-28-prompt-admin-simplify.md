# Prompt 管理頁精簡實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **本專案規則：執行者不得自己執行 `git add` 或 `git commit`。** 各任務沒有 Commit 步驟。每個 Phase 結束時，主控者把該 Phase 的 `git add` 指令與 commit message 交給使用者，**等使用者確認已 commit 後才開始下一個 Phase**。

**Goal:** 把 Prompt 管理頁改成「左側設定清單＋右側內容／版本／評測三個分頁」，並依規格精簡每個分頁的資訊；功能全部保留。同時放寬發布前驗證規則（只留程式依賴與防外洩的規則、網址改白名單）、發布前檢查改為自動執行、沒有範例問題的分類不顯示在快捷問題區。

**Architecture:** `PromptManagementPage` 以 `tab` 狀態取代原本的 `view`（main／evaluation／version／diff），新的 `ConfigHeader` 顯示名稱、狀態、meta 與分頁。Phase A 先把現有元件原樣搬進三個分頁（功能不變、移除所有「返回」）；Phase B 改後端 `ConfigValidator` 規則、`allowed-url-hosts` 設定與快捷問題過濾，並同步前端的規則用詞；之後各 Phase 分別精簡內容（含自動檢查）、版本、評測分頁。`usePromptEvaluation` 提升到頁面層，讓分頁標題顯示評測結論。API 端點與回應格式不動。

**Tech Stack:** Vue 3.5、TypeScript、Pinia、Vitest、@vue/test-utils；Spring Boot、JUnit 5、AssertJ。

**Spec:** [docs/superpowers/specs/2026-10-28-prompt-admin-simplify-design.md](../specs/2026-10-28-prompt-admin-simplify-design.md)（v0.2，下稱「規格」）。版面參考 `prototype/prompt-management-b.html`（不進版控）；兩者不同時以規格為準。

## Global Constraints

- 前端只改 `frontend/src/features/ai-assistant/`；後端只改 Task 2、3 列出的檔案；另改 `docs/`。不改 API 端點、DTO、錯誤碼、`types.ts` 的 API 型別。
- 後端測試指令（在 `backend/`）：`.\mvnw test -Dtest=<類別>`；任務結束跑 `.\mvnw test` 全部（Testcontainers 需要 Docker）。
- 不新增 npm 依賴；不用 `v-html`；樣式只用既有 CSS 變數（`tokens.css`），深色模式不另寫規則。
- 畫面不得出現內部版本號（既有測試 `VERSION_NUMBER = /91\d\d/` 持續適用）。
- 畫面用詞：分頁「內容」「版本」「評測」；設定類型頁籤「System Prompt」「問題分類」；版本狀態在列表與 meta 用「草稿／已排程／目前生效／已封存／已捨棄」。
- 時間一律用 `taipeiTime.ts` 的既有函式（列表、meta、標籤用 `formatTaipeiShort`）。
- 測試指令（在 `frontend/`）：`npm test -- <檔名關鍵字>`；每個任務結束跑 `npm test` 全部與 `npx vue-tsc -b`，兩者都要通過。
- 現有測試中斷言已移除畫面（「返回編輯」「存成草稿」「重新檢查」「尚未檢查」「越權指令」「工具 ✓」「勾選任兩個版本後比較」「正在檢視」等）的部分，由改動該畫面的任務一併更新，不得 `skip`。

## Review Focus

1. **換設定時在「放棄尚未儲存的修改？」按取消**：應停在原設定、原分頁，不得先跳回內容分頁。→ Task 1 測試 `cancelling the discard dialog keeps the config and the tab`。
2. **在版本分頁發布或建立草稿後，版本清單更新**：預設選取要重算（例如發布後沒有草稿 → 右側改顯示目前生效版本），不得殘留舊的差異。→ Task 8 測試 `recomputes the default pane when the versions change`。
3. **他人正在評測別的設定**：本設定的評測分頁標題不顯示「…」；本設定評測執行中切到其他分頁再回來，進度仍在。→ Task 10 測試 `verdictMark ignores another key's running evaluation` 與 `keeps the progress across tab switches`。
4. **「儲存並評測」時儲存失敗（422 驗證錯誤或 409 衝突）**：不開始評測，結論卡維持「有未儲存的修改」。→ Task 10 測試 `save and run does not start when the save fails`。
5. **已排程時改了日期卻按「立即發布」**：送 `IMMEDIATE`，不帶日期；草稿按「預約…」再按「取消」回到「立即發布」。→ Task 4 測試 `scheduled 立即發布 publishes immediately` 與 `預約… then 取消 restores 立即發布`。
6. **發布流程中的儲存不得觸發自動檢查**：發布成功後已沒有編輯中版本，遲到的檢查回 409 會誤顯示衝突橫幅。→ Task 4 測試 `publish does not auto-check after its save step`。
7. **網址白名單比對**：host 須完全相等（不分大小寫）；`docs.example.com.evil.com`、`evil.com/docs.example.com`、`docs.example.com@evil.com` 都不得通過；IP 不因列入白名單而通過。→ Task 2 測試 `urlAllowlistMatchesExactHostOnly` 與 `ipIsForbiddenEvenWhenAllowlisted`。

---

## 檔案結構

```
frontend/src/features/ai-assistant/
├─ admin/PromptManagementPage.vue   （改）tab 狀態、ConfigHeader、三個分頁、評測狀態提升、套用確認框文案
├─ admin/ConfigHeader.vue           （新）名稱、狀態標籤、meta、分頁（含評測結論符號）
├─ admin/ConfigList.vue             （改）只留名稱與狀態標籤；頁籤「問題分類」
├─ admin/ConfigEditor.vue           （改）移除標題列與橫幅；提示列；動作列左側放發布前檢查
├─ admin/PromptAssemblyGuide.vue    （刪）
├─ admin/MarkdownEditor.vue         （改）只顯示缺少的必要章節；預覽沒有範例問題時的提示
├─ admin/ValidationPanel.vue        （改）動作列內的一行；自動檢查，沒有按鈕
├─ composables/usePromptAdmin.ts    （改）載入與儲存後自動檢查
├─ components/QuickQuestions.vue    （改）不顯示 0 題的分類
├─ admin/PublishBar.vue             （改）新按鈕組；左側 slot
├─ admin/VersionViewer.vue          （改）meta 一行、單一動作
├─ admin/VersionDiff.vue            （改）只剩差異本體；名稱相同時一行；行內標示
├─ admin/VersionHistory.vue → VersionList.vue （改名＋改）只負責列表
├─ admin/VersionsTab.vue            （新）列表＋右側內容＋預設選取
├─ admin/EvaluationView.vue         （改）收 evaluation 物件；結論卡；收合指標；底部連結
├─ admin/EvaluationVerdict.vue      （新）結論卡外觀
├─ admin/EvaluationSummary.vue      （改）變化欄文字化
├─ admin/EvaluationCaseList.vue     （改）題目優先、只列未通過原因
├─ admin/EvaluationCasesDialog.vue  （改）欄寬
├─ admin/evaluationFormat.ts        （改）metricRows、tokenRow、overallVerdict、verdictSubtitle、verdictMark
├─ admin/promptLabels.ts            （改）LIST_STATUS；REQUIRED_SECTIONS 與通過清單文字
├─ admin/diffRows.ts、markdown/lineDiff.ts （改）行內片段
└─ admin/prompt-admin.css           （改）對應樣式

backend/src/main/
├─ java/.../AiAssistantProperties.java        （改）Config.allowedUrlHosts
├─ java/.../config/ConfigValidator.java        （改）必要章節、範例問題、禁止內容規則
├─ java/.../prompt/SuggestionService.java      （改）略過 0 題的分類
└─ resources/application.yml                   （改）ai-assistant.config.allowed-url-hosts
```

---

## Phase A：版面骨架

### Task 1：三個分頁與標題區，現有元件搬進分頁

**Files:**
- Create: `admin/ConfigHeader.vue`
- Modify: `admin/PromptManagementPage.vue`、`admin/ConfigList.vue`、`admin/ConfigEditor.vue`（只移除標題列：名稱、狀態標籤、meta、「評測」按鈕與 `evaluate` emit）、`admin/VersionViewer.vue`、`admin/VersionDiff.vue`、`admin/EvaluationView.vue`（移除 `backLabel` prop 與所有返回按鈕）、`admin/prompt-admin.css`
- Test: `admin/__tests__/PromptManagementPage.spec.ts`、`admin/__tests__/ConfigEditor.spec.ts`、`admin/__tests__/EvaluationView.spec.ts`

**Interfaces:**
- Produces:
  - `type PageTab = 'content' | 'versions' | 'evaluation'`（`ConfigHeader.vue` export）
  - `ConfigHeader` props `{ name: string; configKey: string; workingCopy: ConfigVersion | null; published: ConfigVersion | null; tab: PageTab; verdictMark: string }`，emit `'update:tab': [tab: PageTab]`。本任務頁面傳 `verdictMark=""`（Task 10 接上）。
  - 頁面中原本的 `tab: Ref<ConfigType>` 改名為 `configType`；新 `tab: Ref<PageTab>`；版本分頁右側暫用 `versionPane: Ref<null | { kind: 'version'; version: ConfigVersion } | { kind: 'diff'; before: VersionHistoryItem; after: VersionHistoryItem; nameDiff: DiffLine[]; contentDiff: DiffLine[] }>`（Task 8 改由 `VersionsTab` 自己管理）。
  - `ConfigList` 不再有 `history` slot。

規則：
- 標題區：名稱取 `workingCopy?.name ?? published?.name`；狀態標籤：`SCHEDULED` →「預約 {formatTaipeiShort(scheduledAt)} 發布」、`DRAFT` →「草稿」、沒有編輯中版本 →「目前生效」；meta：`{configKey}・目前生效：{formatTaipeiShort(published.updatedAt)}（{published.updatedBy} 發布）`，未發布為 `{configKey}・尚未發布`。
- 分頁按鈕 `role="tab"`、`aria-selected`，文字「內容」「版本」「評測」，評測後接 `verdictMark`（空字串不顯示）。
- 內容分頁：有編輯中版本 → `ConfigEditor`；否則 → `VersionViewer`（目前生效版本）；都沒有 → 既有空狀態文字。
- 版本分頁（暫時）：左 `VersionHistory`（原元件，從側欄移入），右 `versionPane`；點 `DRAFT`／`SCHEDULED` 列 → `tab = 'content'`；其他列 → 載入後顯示 `VersionViewer`；比較 → `VersionDiff`。
- 評測分頁：`EvaluationView`（不再有返回按鈕）。
- `watch(selectedKey)`：`tab = 'content'`、`versionPane = null`。換設定的「放棄修改？」流程不變；**按取消時 tab 不變**。
- 套用（`onApply`）成功或確認後 → `tab = 'content'`。
- 移除：`backLabel`、`showMain`、`view`、「正在檢視…」橫幅（`VersionViewer` 的 `backLabel` prop 一併移除）、`VersionDiff` 的返回按鈕與底部說明、`ConfigList` 的 key／作者／時間列。
- 頁首說明改為「管理小幫手的 System Prompt 與問題分類，發布後立即套用於所有對話。」；「＋ 新增問題分類」不變。
- `ConfigList` 每列：名稱＋標籤（`SCHEDULED` 且有 `scheduledAt` →「已排程 {formatTaipeiShort}」、有 `workingCopy` →「草稿」、否則無）；頁籤文字「System Prompt」「問題分類」；搜尋框不變。

- [ ] **Step 1：寫失敗測試**（`PromptManagementPage.spec.ts`，沿用既有 fixture；`system.main` 已發布版為 `T.v1`、`it.admin`）
  - `shows the header meta and the three tabs`：標題區含 `system.main・目前生效：10/19 08:00（it.admin 發布）`；`role=tab` 的文字依序為「內容」「版本」「評測」，「內容」`aria-selected="true"`。
  - `lists configs by name and status only`：側欄文字不含 `system.main`、`instruction.contact-prep`、`已發布`；「聯繫準備」列顯示「已排程 10/24 09:00」；頁籤文字含「問題分類」不含「System Instructions」。
  - `switching config returns to the 內容 tab`：在「版本」分頁時選另一個設定 → 「內容」為 selected。
  - `switching tabs keeps unsaved edits without asking`：改名稱 → 切「版本」→ 切回「內容」→ 名稱仍是修改後的值，沒有出現確認框。
  - `cancelling the discard dialog keeps the config and the tab`（Review Focus 1）：在「版本」分頁、表單有修改時選另一個設定 → 確認框按「取消」→ 仍是原設定、「版本」仍為 selected。
  - `clicking the draft row in 版本 opens 內容`。
  - `applying a version ends on 內容 with the new draft`：改寫既有的套用測試，確認後「內容」為 selected、編輯器內容為套用版本。
  - 既有測試改用分頁進入（評測、版本），並刪除對「返回編輯」「返回目前生效的版本」的斷言；新增斷言整頁文字不含「返回」與「正在檢視」。
  - `ConfigEditor.spec.ts`：移除對標題列與「評測」按鈕的斷言；`EvaluationView.spec.ts`：移除 `backLabel`。
- [ ] **Step 2：執行確認失敗** — `npm test -- PromptManagementPage`，預期新測試失敗。
- [ ] **Step 3：實作**（依上方規則）。
- [ ] **Step 4：執行確認通過** — `npm test` 全部、`npx vue-tsc -b`。

**Phase A 結束：** 主控者交付 commit 指令（`feat(frontend): prompt admin tabs for content, versions and evaluation`），等使用者確認。

---

## Phase B：發布前驗證規則與快捷問題

### Task 2：後端驗證規則與網址白名單

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/AiAssistantProperties.java`、`backend/src/main/resources/application.yml`、`backend/src/main/java/com/example/copilot/aiassistant/config/ConfigValidator.java`
- Test: `backend/src/test/java/com/example/copilot/aiassistant/config/ConfigValidatorTest.java`、`backend/src/test/java/com/example/copilot/aiassistant/AiAssistantPropertiesTest.java`

**Interfaces:**
- Produces:
  - `AiAssistantProperties.Config(Duration scheduleCheckInterval, Duration scheduleMinLead, Duration scheduleMaxLead, List<String> allowedUrlHosts)`；`application.yml` 在 `ai-assistant.config` 下加 `allowed-url-hosts: []`（註解：prompt 內允許出現的網址 host；空白＝不允許任何網址）。
  - `ConfigValidator` 建構子：`@Autowired public ConfigValidator(ExampleQuestionParser, AiAssistantProperties)` 取 `config().allowedUrlHosts()`；`ConfigValidator(ExampleQuestionParser, Collection<String> allowedUrlHosts)`（package-private，測試用）。null 視為空；存成小寫的不可變 `Set`。
  - `validate(...)` 兩個多載的簽章不變；`API_KEY_REGEXES`、`PASSWORD_REGEX` 不變（稽核的 `CredentialMasker` 共用）。

規則（規格 6 節）：
- 刪除 `PROMPT_SECTIONS`；`SYSTEM_PROMPT` 不檢查任何章節。`SYSTEM_INSTRUCTION` 只檢查 `## 適用問題`：
  - 沒有該標題（沿用 `hasHeading`，行尾空白忽略）→ `REQUIRED_SECTION`「缺少必要章節「## 適用問題」」。
  - 有標題，但到下一個以 `## ` 開頭的行（或檔尾）之間沒有任何非空白行 → `REQUIRED_SECTION`「「## 適用問題」不可空白」。
- `EXAMPLE_QUESTIONS`：`SYSTEM_INSTRUCTION` 一律解析（沒有章節時 `ExampleQuestionParser` 回傳空清單）；超過 5 題 →「「範例問題」最多 5 題，目前有 {n} 題」；每題最多 40 字（code point）的規則與訊息不變。移除 `MIN_EXAMPLES` 與 `hasExampleSection`。
- 禁止內容：`Forbidden` 改為 `record Forbidden(String label, Predicate<String> matches)`，清單改為實例欄位（網址規則需要白名單），`checkForbidden` 改為實例方法；「每類只報第一行」與訊息格式 `{第 n 行|名稱}疑似包含{label}，請移除` 不變。順序：
  1. `API key 或 token`、`密碼`（不變）
  2. `未允許的網址`：`(?i)https?://([^\s/:?#]+)` 逐一取 host，轉小寫後任一個不在白名單 → 命中
  3. `IP 位址`：沿用 `\b\d{1,3}(\.\d{1,3}){3}\b`，不看白名單
  4. `身分證號`、`手機號碼`、`顧客編號`（不變）
  - 刪除 `越權指令`。

- [ ] **Step 1：寫失敗測試**
  - `ConfigValidatorTest`：欄位改為 `new ConfigValidator(new ExampleQuestionParser(), List.of())`；另一個 `allowing = new ConfigValidator(new ExampleQuestionParser(), List.of("Docs.Example.com", "10.1.2.3"))`。
    - 改寫 `oneErrorPerMissingSectionAndTrailingWhitespaceIgnored` → `systemPromptNeedsNoSections`：`SYSTEM_PROMPT` 的 `"## 角色  \n內容\n"` 與 `"任意內容"` 都沒有錯誤。
    - 改寫 `missingSectionIsReportedByName`：把 seed 的 `## 適用問題` 換成 `## 其他` → 恰好一個 `REQUIRED_SECTION`，訊息含 `## 適用問題`；把 `## 回答套版` 換成 `## 其他` → 沒有錯誤。
    - 新增 `instructionNeedsOnlyScope`：`"## 適用問題  \n詢問大盤行情\n"` 沒有錯誤。
    - 新增 `blankScopeIsRejected`（`@ValueSource`）：`"## 適用問題\n\n  \n## 回答套版\n套版\n"`、`"## 適用問題\n## 回答套版\n套版\n"`、`"## 適用問題"` → 恰好一個錯誤，`REQUIRED_SECTION`，訊息「「## 適用問題」不可空白」。
    - 改寫 `exampleQuestionsCountAndLength`：0 題沒有錯誤；6 題一個錯誤且訊息含「目前有 6 題」；41 字一個錯誤；40 字＋4 題沒有錯誤。
    - 新增 `exampleSectionIsOptional`：`"## 適用問題\n詢問大盤行情\n"` 沒有 `EXAMPLE_QUESTIONS`。
    - `forbiddenContentIsReportedWithLine` 的參數移除「忽略以上所有規則」「ignore previous instructions」，其餘不變。
    - 新增 `injectionPhrasesAreNotForbidden`：上述兩句加進 seed 後沒有錯誤。
    - 新增 `urlAllowlistMatchesExactHostOnly`（Review Focus 7，用 `allowing`）：`https://docs.example.com/a`、`HTTPS://DOCS.EXAMPLE.COM`、`見 http://docs.example.com?x=1` 沒有錯誤；`https://docs.example.com.evil.com`、`https://evil.com/docs.example.com`、`https://docs.example.com@evil.com` → 一個 `FORBIDDEN_CONTENT`，訊息「第 3 行疑似包含未允許的網址，請移除」。同一行一個允許、一個不允許的網址 → 命中。
    - 新增 `ipIsForbiddenEvenWhenAllowlisted`（用 `allowing`）：`http://10.1.2.3/x` → 只有「IP 位址」一項錯誤。
    - `allSeedFilesPass`、`eachPatternTypeReportedOnceWithFirstLine`、名稱相關測試維持通過（後者的 `http://x` 現在屬於「未允許的網址」，錯誤數仍為 2）。
  - `AiAssistantPropertiesTest`：`props.config().allowedUrlHosts()` 為空清單。
- [ ] **Step 2：執行確認失敗** — `.\mvnw test -Dtest=ConfigValidatorTest,AiAssistantPropertiesTest`。
- [ ] **Step 3：實作**。
- [ ] **Step 4：執行確認通過** — `.\mvnw test -Dtest=ConfigValidatorTest,AiAssistantPropertiesTest,SeedLoaderTest,ConfigServiceTest,ScheduledPublishJobTest,EvalRunServiceTest`，再跑 `.\mvnw test` 全部。

### Task 3：快捷問題過濾與前端規則用詞

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/prompt/SuggestionService.java`、`frontend/src/features/ai-assistant/components/QuickQuestions.vue`、`admin/promptLabels.ts`、`admin/MarkdownEditor.vue`（只改預覽的空狀態文字）
- Test: Create `backend/src/test/java/com/example/copilot/aiassistant/prompt/SuggestionServiceTest.java`、`components/__tests__/QuickQuestions.spec.ts`；Modify `admin/__tests__/MarkdownEditor.spec.ts`、`admin/__tests__/ConfigEditor.spec.ts`

**Interfaces:**
- Consumes: Task 2 的規則（前端文字需與其一致）。
- Produces: `REQUIRED_SECTIONS = { SYSTEM_PROMPT: [], SYSTEM_INSTRUCTION: ['## 適用問題'] }`（Task 5 的 `missingSections` 使用）；`passedRuleDescriptions` 簽章不變。

規則（規格 7 節）：
- `SuggestionService.suggestions()`：map 之後 `.filter(c -> !c.questions().isEmpty())`；方法註解補一句「沒有範例問題的分類不列出」。
- `QuickQuestions.vue`：`const visible = computed(() => props.categories.filter((c) => c.questions.length > 0))`；模板改用 `visible`（含外層 `v-if` 與色塊序號）。
- `passedRuleDescriptions`：
  - `SYSTEM_INSTRUCTION`：「「適用問題」已填寫」、`範例問題 {n} 題（最多 5 題，每題 40 字內）`
  - 兩種設定：「未偵測到 API key、密碼、未允許的網址、IP、身分證號、手機或顧客編號」、`內容大小 {size}（上限 64 KB）`
  - 移除「必要章節：…」與「未偵測到越權指令…」兩列。
- `MarkdownEditor` 預覽：問題分類沒有範例問題時顯示「沒有範例問題，對話視窗不會顯示這個分類」（取代「「## 範例問題」下尚無條列的問題」）。System Prompt 的「必要章節」標籤列因 `REQUIRED_SECTIONS.SYSTEM_PROMPT` 為空而不再出現；其餘由 Task 5 處理。

- [ ] **Step 1：寫失敗測試**
  - `SuggestionServiceTest`：mock `PublishedConfigCache.current()` 回傳 `new PublishedConfigs(null, List.of(有 2 題的分類, 沒有 ## 範例問題 的分類, 有章節但 0 題的分類))` → 結果只有第一個，`questions` 為那 2 題。
  - `QuickQuestions.spec.ts`：`[A(2 題), B(0 題)]` → 只有一個 `.ai-quick-group`，文字含 A 不含 B，按鈕 2 顆；`[B(0 題)]` → 沒有 `.ai-quick-groups`。
  - `MarkdownEditor.spec.ts`：System Prompt 不含「必要章節」；問題分類內容沒有範例問題 → 預覽含「沒有範例問題，對話視窗不會顯示這個分類」。移除對 System Prompt 必要章節標籤的舊斷言。
  - `ConfigEditor.spec.ts`：`shows the passing rule list after a successful check` 改斷言含「「適用問題」已填寫」「最多 5 題」，不含「必要章節：」「越權指令」；`lists 422 details in the validation panel` 的錯誤訊息範例改為「缺少必要章節「## 適用問題」」。
- [ ] **Step 2：執行確認失敗** — `.\mvnw test -Dtest=SuggestionServiceTest`；`npm test -- QuickQuestions MarkdownEditor ConfigEditor`。
- [ ] **Step 3：實作**。
- [ ] **Step 4：執行確認通過** — `.\mvnw test -Dtest=SuggestionServiceTest,SuggestionControllerTest`；`npm test` 全部、`npx vue-tsc -b`。

**Phase B 結束：** 交付 commit 指令（`feat: relax prompt validation rules, url allowlist and hide empty quick-question groups`），等使用者確認。

---

## Phase C：內容分頁

### Task 4：發布列與自動發布前檢查

**Files:**
- Modify: `admin/PublishBar.vue`、`admin/ValidationPanel.vue`、`admin/ConfigEditor.vue`（把 `ValidationPanel` 放進 `PublishBar` 的預設 slot；移除 `@check`）、`composables/usePromptAdmin.ts`、`admin/prompt-admin.css`
- Test: Create `admin/__tests__/PublishBar.spec.ts`、`admin/__tests__/ValidationPanel.spec.ts`；Modify `admin/__tests__/ConfigEditor.spec.ts`、`composables/__tests__/usePromptAdmin.spec.ts`、`admin/__tests__/PromptManagementPage.spec.ts`

**Interfaces:**
- Consumes: 既有 `PublishBar` props（`status`、`scheduledAt`、`canPublish`、`busy`、`scheduleError`）與 emits（`save: [mode: SaveMode]`、`publish: [mode: PublishMode, date: string, time: string]`、`discard: []`），**簽章不變**。
- Produces:
  - `PublishBar` 新增預設 slot（動作列左側）。
  - `ValidationState = 'idle' | 'checking' | 'passed' | 'failed'`。
  - `ValidationPanel` props `{ validation: ValidationState; errors: ValidationIssue[]; configType: ConfigType; checkedContent: string; dirty: boolean }`，沒有 emits（移除 `canCheck`、`busy`、`check`）。
  - `usePromptAdmin` 不再匯出 `validate()`。

自動檢查（`usePromptAdmin`，規格 3.1）：
- 內部 `check(key)`：沒有編輯中版本或已不在該 key 時不呼叫；`const seq = ++checkSeq`；`state.validation = 'checking'`；呼叫既有 `aiAssistantApi.validateDraft(key)`；回應抵達時 `seq !== checkSeq` 或 `!onKey(key)` 就丟棄；否則寫入 `errors`、`validation`、`checkedContent = state.workingCopy.content`。失敗時（同樣先檢查 seq）走 `handleWriteError`（409 → 衝突橫幅；非 `ApiError` → `GENERIC_ERROR`），之後若仍為 `checking` 則設回 `idle`。不經過 `run()`，不改 `busy`。
- 觸發：`loadSelected` 成功且有 `workingCopy` 時 `void check(key)`（不讓載入等它）；`doSave` 成功且 `refresh === true` 時 `void check(key)`（一般儲存、取消排程、儲存並評測）。**發布流程的儲存步驟（`refresh: false`）不觸發**（Review Focus 6）。
- `resetChecks()` 另外 `checkSeq++`，讓換設定前的檢查作廢。
- 移除 `doSave` 裡「已排程 `KEEP_STATUS` 儲存後直接設為 passed」的特例（改由自動檢查取得結果）。
- 刪除 `validate()`（原本的先儲存再檢查）與其回傳。

按鈕（規格 3.1 表格）：
- `DRAFT`：`捨棄草稿`（ghost）→ `discard`；`儲存` → `save('KEEP_STATUS')`；`預約…`（`canPublish` 時）→ 本地 `scheduling = true`；`立即發布`（主要，`canPublish` 時）→ `publish('IMMEDIATE', date, time)`。
- `DRAFT` 且 `scheduling`：`捨棄草稿`、`儲存`、日期、時間、`取消`（文字連結，回到前一列）、`預約發布`（主要）→ `publish('SCHEDULED', date, time)`。
- `SCHEDULED`：`取消排程`（ghost）→ `save('DRAFT')`；日期、時間；`儲存` → `save('KEEP_STATUS')`；`立即發布`（次要）→ `publish('IMMEDIATE', …)`；`更新預約`（主要）→ `publish('SCHEDULED', …)`。不顯示「捨棄草稿」。
- 日期時間預設值沿用現有 watch（已排程帶入預約時間，否則下一個整點）。
- 時間範圍提示只在 `scheduleError` 有值時顯示該錯誤文字；移除常駐的「至少 1 分鐘後、最多 90 天內」與「發布方式」單選鈕。

`ValidationPanel`（一行，沒有任何按鈕）：
- `idle`：不渲染內容。
- `checking`：「檢查中…」（`aria-live="polite"`）。
- `passed`：`<details>` 收合，summary「✓ 發布前檢查通過」；展開後為通過規則清單。
- `failed`：`<details open>`，summary「✕ {n} 項未通過」；清單 `role="alert"`。
- `dirty` 且為 `passed`／`failed`：summary 後加小字「・儲存後重新檢查」。
- 沒有編輯權限時 `ConfigEditor` 不渲染 `PublishBar`（同現況），發布前檢查一併不顯示（規格 3.3）。

- [ ] **Step 1：寫失敗測試**
  - `PublishBar.spec.ts`：
    - `draft shows 捨棄草稿 儲存 預約… 立即發布`，且沒有「存成草稿」「發布方式」。
    - `draft 儲存 saves KEEP_STATUS`、`立即發布 publishes IMMEDIATE`。
    - `預約… then 取消 restores 立即發布`（Review Focus 5）：按「預約…」→ 出現日期時間與「預約發布」；按「取消」→ 日期時間消失、「立即發布」回來。
    - `預約發布 publishes SCHEDULED with the date and time`。
    - `scheduled shows 取消排程 儲存 立即發布 更新預約 and no 捨棄草稿`；`取消排程 saves DRAFT`；`更新預約 publishes SCHEDULED`。
    - `scheduled 立即發布 publishes immediately`（Review Focus 5）：改日期後按「立即發布」→ emit 第一個參數為 `IMMEDIATE`。
    - `without canPublish there is no 預約… 立即發布 更新預約`。
    - `shows the range hint only with a scheduleError`：無錯誤時不含「90 天」；`scheduleError='預約時間需在 1 分鐘後、90 天內'` 時顯示該文字。
  - `ValidationPanel.spec.ts`：idle 不含「發布前檢查」「尚未檢查」；checking 顯示「檢查中…」；passed 的 `details` 沒有 `open`、summary 為「✓ 發布前檢查通過」；failed 的 `details` 有 `open`、summary 為「✕ 2 項未通過」且列出兩個錯誤訊息；`dirty=true` 且 passed → 含「儲存後重新檢查」；任何狀態都沒有 `button`。
  - `usePromptAdmin.spec.ts`（`beforeEach` 設 `api.validateDraft.mockResolvedValue({ valid: true, errors: [] })`）：
    - `auto-checks after loading a config with a draft`：載入後 `validateDraft` 以該 key 呼叫一次，`validation` 為 `passed`。
    - `does not check without a working copy`：只有已發布版本 → 不呼叫。
    - `auto-checks after a successful save`：`save('KEEP_STATUS')` 後再呼叫一次；儲存失敗則不呼叫。
    - `publish does not auto-check after its save step`（Review Focus 6）：`publish('IMMEDIATE')` 過程中 `validateDraft` 呼叫次數不增加。
    - `drops a check that resolves after switching config`：`validateDraft` 回傳未 resolve 的 promise → 換 key → resolve `{ valid: false, … }` → 新 key 的 `validation` 不是 `failed`。
    - `checking does not set busy`：檢查進行中 `state.busy` 為 false。
    - `a 409 from the check shows the conflict banner`：`validateDraft` 以 409 `AI_CONFIG_CONFLICT` 拒絕 → `state.conflict` 為 true，`validation` 為 `idle`。
    - 刪除舊的「先儲存再檢查」測試（`admin.validate()`）。
  - `ConfigEditor.spec.ts`、`PromptManagementPage.spec.ts`：`beforeEach` 加上 `api.validateDraft` 的預設回傳（同上）；發布前檢查出現在動作列（`.pm-actionbar` 內）；掛載有草稿的設定後不按任何按鈕即顯示「✓ 發布前檢查通過」；沒有「檢查」「重新檢查」按鈕；移除對「重新檢查」「存成草稿」「尚未檢查」的舊斷言。
- [ ] **Step 2：執行確認失敗** — `npm test -- PublishBar ValidationPanel ConfigEditor usePromptAdmin PromptManagementPage`。
- [ ] **Step 3：實作**。
- [ ] **Step 4：執行確認通過** — `npm test` 全部、`npx vue-tsc -b`。

### Task 5：編輯器精簡與唯讀檢視

**Files:**
- Modify: `admin/ConfigEditor.vue`、`admin/MarkdownEditor.vue`、`admin/VersionViewer.vue`、`admin/promptLabels.ts`、`admin/PromptManagementPage.vue`（`VersionViewer` 新 props）、`admin/prompt-admin.css`
- Delete: `admin/PromptAssemblyGuide.vue`
- Test: `admin/__tests__/ConfigEditor.spec.ts`、`admin/__tests__/MarkdownEditor.spec.ts`；Create `admin/__tests__/VersionViewer.spec.ts`

**Interfaces:**
- Produces:
  - `promptLabels.ts`：`export const LIST_STATUS: Record<ConfigStatus, string> = { DRAFT: '草稿', SCHEDULED: '已排程', PUBLISHED: '目前生效', ARCHIVED: '已封存', DISCARDED: '已捨棄' }`
  - `promptLabels.ts`：`export function missingSections(configType: ConfigType, content: string): string[]`（回傳缺少的標題，順序同 `REQUIRED_SECTIONS`；依 Task 3 只可能是問題分類的 `'## 適用問題'`）
  - `VersionViewer` props `{ version: ConfigVersion; versions: VersionHistoryItem[]; canEdit: boolean; busy: boolean; conflict: boolean; action: 'edit' | 'apply' }`，emits `{ edit: []; apply: [version: number]; reload: [] }`（衝突橫幅與「重新載入」沿用現有）
  - `export function versionMeta(v: ConfigVersion | VersionHistoryItem, versions: VersionHistoryItem[]): string`（`promptLabels.ts`）
- Consumes: Task 1 的頁面結構（內容分頁沒有草稿時用 `action="edit"`，emit `edit` → `admin.createDraft()`；版本分頁用 `action="apply"`）。

規則：
- `ConfigEditor`：移除 System Prompt 藍色橫幅、已排程黃色橫幅、`PromptAssemblyGuide`；System Prompt 在最上方加一行提示：「這份會和 {publishedInstructionCount} 份已發布的問題分類一起送給模型，影響所有對話。」＋按鈕「查看問題分類 →」（emit 既有 `go-instructions`）；問題分類沒有提示列。修改說明標籤只寫「修改說明」，placeholder「選填，例如：補充無權限時的回覆方式」，`maxlength` 不變。衝突橫幅保留。
- `MarkdownEditor`：移除「必要章節」標籤列與其樣式；編輯器標題列在有缺少時顯示 `缺少「## 適用問題」`（`missingSections` 結果各自加「」、以「、」連接，日後規則增加時照樣適用）；齊全時不顯示。章節存在但內容空白由伺服器檢查回報，編輯器不另判斷。
- `versionMeta`：以「・」連接，沒有值的項目省略：
  1. `{LIST_STATUS[status]}`
  2. `{formatTaipeiShort(updatedAt)} {updatedBy} {STATUS_VERB[status]}`
  3. `changeNote`
  4. `套用自 {formatTaipeiShort(source.updatedAt)} 的版本`（`basedOnVersion` 找得到時；找不到為「套用自先前的版本」）
  5. `原預約 {formatTaipeiShort(scheduledAt)}`（只有 `DISCARDED`）
  6. `已被 {formatTaipeiShort(next.updatedAt)} 的版本取代`（只有 `ARCHIVED`，next 為版本號較大的第一個已發布／已封存版本）
- `VersionViewer`：移除唯讀橫幅與五格資訊卡；上方一行 `versionMeta`；內容 `SafeMarkdown`；動作列：
  - `action='edit'`：提示「編輯會建立一份草稿，發布前不影響線上。」＋`複製 Markdown`＋`編輯`（主要，`canEdit` 時）。
  - `action='apply'`：`複製 Markdown`＋`以此版本建立草稿`（主要；`canEdit` 且狀態在 `APPLICABLE` 時）。
  - 兩種動作列都與編輯時的 `.pm-actionbar` 相同（sticky，固定在畫面下方）：移除 `.pm-actionbar--viewer` 的 `position: static`；預覽不另設 `max-height` 捲動框，隨頁面捲動（規格 3.2、4.3）。

- [ ] **Step 1：寫失敗測試**
  - `ConfigEditor.spec.ts`：System Prompt 顯示「這份會和 5 份已發布的問題分類一起送給模型，影響所有對話。」，點「查看問題分類 →」emit `go-instructions`；問題分類沒有這行；整個元件不含「只有一份」「送給模型的系統訊息」「（選填，最多 200 字）」；修改說明 input 的 placeholder 為「選填，例如：補充無權限時的回覆方式」。
  - `MarkdownEditor.spec.ts`：問題分類缺「## 適用問題」→ 顯示「缺少「## 適用問題」」；問題分類只有 `## 適用問題` → 不含「缺少」；System Prompt 任何內容（含空白）都不含「缺少」；不再有「必要章節」文字。
  - `VersionViewer.spec.ts`：
    - 已封存、有 `changeNote`、被較新的已發布版本取代 → meta 為「已封存・10/19 08:00 system 發布・初始版本・已被 10/19 09:30 的版本取代」。
    - 已捨棄、有 `scheduledAt` → meta 含「原預約 10/24 09:00」。
    - 有 `basedOnVersion` → meta 含「套用自 10/19 08:00 的版本」。
    - `action='edit'` → 有「編輯」與提示文字，按下 emit `edit`；`action='apply'` → 有「以此版本建立草稿」，按下 emit `apply` 帶版本號；`canEdit=false` → 兩者都沒有。
    - 不含「正在檢視」「原作者」「預約時間」「套用來源」。
  - `PromptManagementPage.spec.ts`：沒有草稿時內容分頁的「編輯」呼叫 `createDraft` 並顯示編輯器（改寫既有「建立草稿」測試）。
- [ ] **Step 2：執行確認失敗** — `npm test -- ConfigEditor MarkdownEditor VersionViewer PromptManagementPage`。
- [ ] **Step 3：實作**；刪除 `PromptAssemblyGuide.vue` 與其引用。
- [ ] **Step 4：執行確認通過** — `npm test` 全部、`npx vue-tsc -b`。

**Phase C 結束：** 交付 commit 指令（`feat(frontend): simplify the content tab of prompt admin`），等使用者確認。

---

## Phase D：設定選單與版本分頁

### Task 6：設定選單取代左側清單（規格 v0.3）

**Files:**
- Create: `admin/ConfigPicker.vue`；Test: Create `admin/__tests__/ConfigPicker.spec.ts`
- Delete: `admin/ConfigList.vue`（與其測試，若有）
- Modify: `admin/ConfigHeader.vue`（名稱改為 `ConfigPicker`）、`admin/PromptManagementPage.vue`、`admin/prompt-admin.css`
- Test: Modify `admin/__tests__/PromptManagementPage.spec.ts`、`admin/__tests__/ConfigEditor.spec.ts`（若斷言 `go-instructions` 行為）

**Interfaces:**
- Produces:
  - `ConfigPicker` props `{ configs: ConfigSummary[]; selectedKey: string; loading: boolean }`，emits `{ select: [configKey: string] }`；`defineExpose({ open })`（展開面板並聚焦搜尋框）。
  - `ConfigHeader` 新增 props `configs: ConfigSummary[]`、`loading: boolean`，新增 emit `select: [configKey: string]`，`defineExpose({ openPicker })`（轉呼叫 `ConfigPicker.open()`）；原 `name` prop 保留給選單按鈕文字。
  - 頁面：`selectedKey: Ref<string>`（單一選取，取代 `configType`＋`selected` 兩個狀態）；`configType` 改為 `computed(() => selectedSummary.value?.configType ?? 'SYSTEM_PROMPT')`。

規則（規格 2、2.1、2.2、2.4、3.1 第 1 點）：
- 版面：刪除 `ConfigList` 與 `.pm-layout` 兩欄格線；主卡片全寬。1100px 以下的頁首上下排與版本分頁上下排規則保留。
- 初始選取：設定清單載入後，若目前沒有選取或選取的 key 已不存在，選第一個 `SYSTEM_PROMPT`；沒有則選第一個問題分類；都沒有則空字串（沿用既有空狀態文字）。
- `ConfigPicker`：
  - 收合：`<button type="button" aria-haspopup="listbox" :aria-expanded>`，文字為目前設定名稱（套用既有 `.pm-editor__title` 字級）＋「▾」；清單未載入時文字「載入中…」，沒有選取時「請選擇設定」。
  - 展開面板：搜尋 `<input role="combobox" aria-autocomplete="list" :aria-controls :aria-activedescendant>`，placeholder「搜尋名稱或 key」，展開時自動聚焦；`<ul role="listbox">` 內兩組（`role="group"`＋`aria-labelledby` 組名）：「System Prompt」「問題分類 {N}」（N 為問題分類總數，不受搜尋影響）；每項 `role="option"`、`aria-selected`（目前設定），文字為名稱＋狀態標籤（`SCHEDULED` 且有 `scheduledAt` →「已排程 {formatTaipeiShort}」、有 `workingCopy` →「草稿」、否則無；沿用原 `ConfigList` 的 badge class）；目前設定以 `box-shadow: inset 3px 0 0 var(--color-primary)` 與 `var(--color-primary-subtle)` 底色標示（同原清單）。
  - 搜尋：名稱或 key 包含輸入（trim、不分大小寫）；沒有符合項目的組整組隱藏；兩組都空 → 「沒有符合的設定」（清單本身為空時「尚無設定」）。
  - 鍵盤：按鈕 Enter／Space／↓ 展開；搜尋框 ↓／↑ 在可見選項間移動（到底不循環），初始為目前設定（若可見）否則第一項；Enter 選取目前項目；Esc 關閉並聚焦按鈕；Tab 關閉。
  - 點擊：點選項 → emit `select` 並關閉；點面板與按鈕以外的地方 → 關閉（`pointerdown` 監聽掛在 `document`，卸載時移除）。
  - 選取目前已選的設定 → 只關閉，不 emit。
  - 面板：`position: absolute`，`min-width: 320px`、`max-height: 60vh`、`overflow-y: auto`，背景 `var(--color-surface)`、邊框與陰影用既有變數；1100px 以下 `left: 0; right: 0` 與卡片同寬。
- 頁面：
  - `ConfigHeader` 的 `select` → 既有換設定流程：`if (key !== selectedKey.value) guarded(() => (selectedKey.value = key))`。按「放棄修改？」的取消 → 選取不變、分頁不變（Review Focus 1 照舊）。
  - `watch(selectedKey, showContent)` 保留。
  - 「＋ 新增問題分類」：移除 `configType === 'SYSTEM_INSTRUCTION'` 條件，只保留 `canEdit`；建立成功 → `selectedKey.value = created.configKey`。
  - `ConfigEditor` 的 `go-instructions` → 呼叫 `ConfigHeader` 的 `openPicker()`（提示列「查看問題分類 →」展開設定選單）。
  - 頁面沒有設定可顯示時（`state.workingCopy` 與 `state.published` 皆無），維持既有空狀態卡片，但頁首仍顯示（`ConfigHeader` 不顯示時，空狀態卡片上方另外放一個 `ConfigPicker`，讓使用者仍可切換）。
  - 移除 `selectConfigType`、`selected` reactive 與「keep a valid selection per tab」watch（改為上述初始選取規則）。

- [ ] **Step 1：寫失敗測試**
  - `ConfigPicker.spec.ts`（`attachTo: document.body`）：
    - `shows the current name and opens on click`：按鈕文字含目前名稱；點擊後出現搜尋框且為 `document.activeElement`，`aria-expanded="true"`。
    - `groups configs with status badges`：兩組標題「System Prompt」「問題分類 2」；已排程項目含「已排程 10/24 09:00」、草稿項目含「草稿」；目前設定的 option `aria-selected="true"`。
    - `filters by name or key and hides empty groups`：輸入 `contact` → 只剩「聯繫準備」，System Prompt 組不存在；輸入 `zzz` → 「沒有符合的設定」。
    - `keyboard: arrow keys and Enter select`：按鈕上 `ArrowDown` 展開；搜尋框 `ArrowDown` 後 `aria-activedescendant` 指向下一項；`Enter` → emit `select` 帶該 key，面板關閉。
    - `Escape closes and returns focus to the button`。
    - `pointerdown outside closes`。
    - `selecting the current config only closes`：不 emit。
  - `PromptManagementPage.spec.ts`：
    - 改寫 `lists configs by name and status only` → `the picker lists configs and selecting one switches config`：頁面沒有 `.pm-sidebar`；展開選單點「聯繫準備」→ 標題為「聯繫準備」、「內容」分頁 selected。
    - 改寫 Review Focus 1 測試：在「版本」分頁、表單有修改時用選單選另一個設定 → 確認框按「取消」→ 仍是原設定、「版本」仍 selected。
    - `new instruction button is always shown and selects the new config`：System Prompt 選取時也有「＋ 新增問題分類」；建立後標題為新名稱。
    - `查看問題分類 opens the picker`：System Prompt 內容分頁點「查看問題分類 →」→ 選單面板展開。
    - 其餘依賴 `ConfigList`、`.pm-tabs`（設定類型頁籤）的既有斷言改用選單操作。
- [ ] **Step 2：執行確認失敗** — `npm test -- ConfigPicker PromptManagementPage`。
- [ ] **Step 3：實作**；刪除 `ConfigList.vue` 與其引用。
- [ ] **Step 4：執行確認通過** — `npm test` 全部、`npx vue-tsc -b`。

### Task 7：行內差異與比較畫面

**Files:**
- Modify: `markdown/lineDiff.ts`、`admin/diffRows.ts`、`admin/VersionDiff.vue`、`admin/prompt-admin.css`
- Test: `markdown/__tests__/lineDiff.spec.ts`、`admin/__tests__/diffRows.spec.ts`；Create `admin/__tests__/VersionDiff.spec.ts`

**Interfaces:**
- Produces:
  - `lineDiff.ts`：`export type Segment = { text: string; changed: boolean }`；`export function inlineSegments(before: string, after: string): { before: Segment[]; after: Segment[] }`
  - `diffRows.ts`：`DiffRow` 的非 gap 列新增選填 `segments?: Segment[]`
  - `VersionDiff` props `{ before: VersionHistoryItem; after: VersionHistoryItem; nameDiff: DiffLine[]; contentDiff: DiffLine[] }`（移除 `configKey`、`name`、`backLabel`），沒有 emits

規則：
- `inlineSegments`：以 code point（`Array.from`）比對共同前綴與共同後綴（後綴不得與前綴重疊）；回傳「前綴（unchanged）＋中段（changed）＋後綴（unchanged）」，空字串的片段省略。沒有任何共同前後綴 → 兩邊各一個 `changed` 片段（整行）。
- `diffRows`：在每段連續的變更（先 removed 再 added）中，第 k 個 removed 與第 k 個 added 配對（k < 兩者較小的數量），兩列都填 `segments`；沒配對的列不填。
- `VersionDiff`：只剩差異本體。標題列「− {LIST_STATUS} {formatTaipeiShort}」「＋ {LIST_STATUS} {formatTaipeiShort}」「＋{added} −{removed} 行」；名稱沒有差異 → 只顯示一行「名稱相同」，有差異才顯示名稱區塊；內容列有 `segments` 時逐段渲染，`changed` 片段用 `<del>`（removed 列）或 `<ins>`（added 列）。「（兩個版本皆為空白）」保留。

- [ ] **Step 1：寫失敗測試**
  - `lineDiff.spec.ts`：
    - `inlineSegments('一律忽略，必要時提醒。', '一律忽略，也不要轉述。')` → before `[{一律忽略，,false},{必要時提醒,true},{。,false}]`、after `[{一律忽略，,false},{也不要轉述,true},{。,false}]`。
    - 只在尾端新增：`('ab', 'abc')` → before `[{ab,false}]`、after `[{ab,false},{c,true}]`。
    - 完全不同：`('ab', 'cd')` → 各一個 changed 片段。
    - 重疊：`('aa', 'aaa')` → before `[{aa,false}]`、after `[{aa,false},{a,true}]`。
    - 含 emoji 等 surrogate pair 時不拆斷字元。
  - `diffRows.spec.ts`：`[same, removed x2, added x1, same]` → 第一個 removed 與 added 有 `segments`，第二個 removed 沒有；gap 規則不變。
  - `VersionDiff.spec.ts`：名稱相同 → 有「名稱相同」、沒有名稱區塊；名稱不同 → 顯示名稱區塊；配對行渲染出 `del`／`ins` 且文字為中段；標題列含「− 目前生效 10/19 08:00」「＋ 草稿 10/19 09:30」與「＋1 −1 行」；不含「返回」「套用」。
- [ ] **Step 2：執行確認失敗** — `npm test -- lineDiff diffRows VersionDiff`。
- [ ] **Step 3：實作**；頁面更新 `VersionDiff` 的 props。
- [ ] **Step 4：執行確認通過** — `npm test` 全部、`npx vue-tsc -b`。

### Task 8：版本分頁元件

**Files:**
- Create: `admin/VersionsTab.vue`
- Rename + Modify: `admin/VersionHistory.vue` → `admin/VersionList.vue`；`admin/__tests__/VersionHistory.spec.ts` → `admin/__tests__/VersionList.spec.ts`
- Modify: `admin/PromptManagementPage.vue`（版本分頁改用 `VersionsTab`，移除 `versionPane`；套用確認框文案）、`admin/prompt-admin.css`
- Test: Create `admin/__tests__/VersionsTab.spec.ts`；Modify `VersionList.spec.ts`、`PromptManagementPage.spec.ts`

**Interfaces:**
- Consumes: Task 5 `VersionViewer`（`action='apply'`）、`LIST_STATUS`；Task 7 `VersionDiff`；`PromptAdmin`（`state.versions`、`state.feedback`、`state.workingCopy`、`state.published`、`state.busy`、`loadVersion(version)`、`compare(a, b)`）。
- Produces:
  - `VersionList` props `{ versions: VersionHistoryItem[]; selected: number[]; checked: number[]; feedback: Record<number, VersionFeedback>; configType: ConfigType }`，emits `{ view: [version: number]; 'update:checked': [versions: number[]]; compare: [] }`
  - `VersionsTab` props `{ admin: PromptAdmin; configType: ConfigType; canEdit: boolean }`，emits `{ 'open-content': []; apply: [version: number] }`

規則：
- `VersionList`：每列兩行——`LIST_STATUS`＋時間（同分鐘多列時加秒，沿用現有 `crowdedMinutes`）；`{updatedBy}・{changeNote}`（無說明時只有操作者）。已發布／已封存列的讚／倒讚與底部 180 天小字沿用現有格式與文案。已捨棄預設隱藏，底部「顯示已捨棄（N）」。勾選框 `aria-label` 沿用現有寫法；`checked.length === 2` 時才顯示「比較勾選的兩版」（emit `compare`）。移除列上的「套用」、「原作者」「原預約」「套用自」、「勾選任兩個版本後比較」、`<details>` 外框。`selected` 的列標 `aria-current="true"`。
- `VersionsTab` 版面：左 280px `VersionList`，右側為下列之一：
  - 預設（規格 4.2）：有編輯中版本且有已發布版本 → 自動 `compare(published.version, workingCopy.version)` 並勾選這兩列；只有已發布 → 顯示目前生效版本（`VersionViewer action='apply'`）；只有編輯中版本 → 提示「尚未發布過，草稿請在內容分頁編輯」。
  - **`admin.state.versions` 變動（發布、建立草稿、捨棄後重新載入）時重算預設**。
  - 點 `DRAFT`／`SCHEDULED` 列 → emit `open-content`；點其他列 → `loadVersion` 後顯示 `VersionViewer`（`action='apply'`，emit `apply` 往上傳）。
  - 比較 → `compare(較小版本號, 較大版本號)` → `VersionDiff`。
  - 預設差異或比較失敗：`DiffTooLargeError` 以其訊息 toast、其他非 `ApiError` 以 `GENERIC_ERROR` toast（同頁面現有 `surface()`）；右側改顯示目前生效版本，沒有則顯示提示。
  - 載入中右側顯示「載入中…」；以序號丟棄過時的回應（同頁面現有 `viewSeq` 作法）。
- 頁面：`VersionsTab` 的 `open-content` → `tab = 'content'`；`apply` → 既有 `onApply`。套用確認框（規格 4.4）：
  - title：`以 {formatTaipeiShort(source.updatedAt)} 的版本建立草稿？`
  - 內文（草稿）：`目前 {formatTaipeiShort(detail.updatedAt)} 儲存的草稿會改為已捨棄，可在版本列表找回。`
  - 內文（已排程）：`目前預約於 {formatTaipeiShort(detail.scheduledAt)} 發布的版本會取消排程並改為已捨棄，可在版本列表找回。`
  - `confirm-label="建立草稿"`；移除 `dialog__summary` 摘要表。

- [ ] **Step 1：寫失敗測試**
  - `VersionList.spec.ts`（由舊 spec 改寫）：列文字為「草稿」「目前生效」「已封存」；沒有「套用」按鈕；勾一列時沒有「比較勾選的兩版」，勾兩列時有、按下 emit `compare`；已捨棄預設隱藏、勾「顯示已捨棄（1）」後出現；回饋格式與 180 天小字維持既有斷言；不含版本號。
  - `VersionsTab.spec.ts`（mock 一個最小 `PromptAdmin`）：
    - `defaults to the published vs draft diff`：有草稿與已發布 → 呼叫 `compare(9101, 9102)`、顯示 `VersionDiff`、兩列勾選。
    - `defaults to the published version without a draft`：顯示 `VersionViewer` 與「以此版本建立草稿」。
    - `shows a hint when never published`：顯示「尚未發布過，草稿請在內容分頁編輯」。
    - `recomputes the default pane when the versions change`（Review Focus 2）：先為差異，改 `admin.state.versions`／`workingCopy=null` 模擬發布 → 右側改為目前生效版本。
    - `clicking a draft row emits open-content`；`clicking an archived row shows it read-only and 以此版本建立草稿 emits apply`。
    - `falls back to the published version when the default diff is too large`：`compare` 丟 `DiffTooLargeError` → toast 該訊息、右側為目前生效版本。
  - `PromptManagementPage.spec.ts`：改寫套用測試的確認框斷言為規格 4.4 的 title、兩種內文與「建立草稿」按鈕；確認框不含「套用後」。
- [ ] **Step 2：執行確認失敗** — `npm test -- VersionList VersionsTab PromptManagementPage`。
- [ ] **Step 3：實作**。
- [ ] **Step 4：執行確認通過** — `npm test` 全部、`npx vue-tsc -b`。

**Phase D 結束：** 交付 commit 指令（`feat(frontend): versions tab with default diff and inline changes`），等使用者確認。

---

## Phase E：評測分頁

### Task 9：指標列與結論規則

**Files:**
- Modify: `admin/evaluationFormat.ts`、`admin/EvaluationSummary.vue`
- Test: `admin/__tests__/evaluationFormat.spec.ts`、`admin/__tests__/EvaluationSummary.spec.ts`

**Interfaces:**
- Produces（`evaluationFormat.ts`）:
  - `export interface MetricRow { label: string; base: number; cand: number; d: number; direction: Direction; lowerIsBetter: boolean }`
  - `export function metricRows(s: EvalSummary): MetricRow[]`：五項品質指標，順序與 label 同現有 `EvaluationSummary`（正確率、工具選擇正確率、分類正確率、拒答率（應拒答題）、拒答率（一般題））；`direction` 用既有 `rateDirection`。
  - `export function tokenRow(s: EvalSummary): { base: number; cand: number; direction: Direction; percent: number | null }`（`percent` 為四捨五入整數、base 為 0 時 null；`direction` 用既有 `tokenDirection`）
  - `export function overallVerdict(s: EvalSummary): Direction`：任一 `metricRows` 為 `worse` → `worse`；否則任一 `better` → `better`；否則 `same`。不看 Token。
  - `export function changeText(r: MetricRow): string`：`d === 0` →「—」；`same` →「持平」；否則「變好」或「變差」＋`（{+|−}{|cand−base|} 題）`（正負號依 `cand − base`）。
  - `export function verdictSubtitle(s: EvalSummary): string`：有變化的品質指標各寫 `{label} {formatRate(base,d)} → {formatRate(cand,d)}`，以「；」連接；若還有持平的指標加「其他指標持平」；全部持平寫「各項指標持平」；Token `direction !== 'same'` 時再加「平均 Token 多 {percent}%」或「平均 Token 少 {|percent|}%」。

`EvaluationSummary`：表頭「指標」「目前生效」「草稿」「變化」可見；比率欄沿用「65%（11/17）」格式；變化欄用 `changeText`，`better` 綠、`worse` 紅；Token 列變化為「多 {n}%」（紅）／「少 {n}%」（綠）／「持平」；「拒答率（一般題）」與「平均 Token」名稱後加小字「越低越好」；表下說明改為「差 1 題以內標為持平，可能只是模型隨機性。」；移除 ▲▼ 符號。

- [ ] **Step 1：寫失敗測試**
  - `evaluationFormat.spec.ts`（`evalFixtures.ts` 的 summary 可直接改數字）：
    - `overallVerdict`：正確率 11→14、其他相同 → `better`；正確率 11→14 但工具 16→13 → `worse`；只有差 1 題 → `same`；只有 Token 增加 13% → `same`；一般題拒答 0→3 → `worse`。
    - `changeText`：11→14 →「變好（+3 題）」；一般題拒答 0→2 →「變差（+2 題）」；14→11 →「變差（−3 題）」；差 1 →「持平」；d=0 →「—」。
    - `verdictSubtitle`：65%→82% 且 Token 3470→3927 →「正確率 65% → 82%；其他指標持平；平均 Token 多 13%」；全持平 →「各項指標持平」。
  - `EvaluationSummary.spec.ts`：表頭文字依序「指標」「目前生效」「草稿」「變化」；Token 列含「多 13%」且不含「▼」「▲」；「越低越好」出現兩次；說明文字為新文案。
- [ ] **Step 2：執行確認失敗** — `npm test -- evaluationFormat EvaluationSummary`。
- [ ] **Step 3：實作**（`EvaluationSummary` 改用 `metricRows`／`tokenRow`，移除自身重複的列計算）。
- [ ] **Step 4：執行確認通過** — `npm test` 全部、`npx vue-tsc -b`。

### Task 10：評測狀態提升、結論卡與分頁標示

**Files:**
- Create: `admin/EvaluationVerdict.vue`
- Modify: `admin/EvaluationView.vue`、`admin/PromptManagementPage.vue`、`admin/evaluationFormat.ts`、`admin/prompt-admin.css`
- Test: `admin/__tests__/EvaluationView.spec.ts`、`admin/__tests__/evaluationFormat.spec.ts`、`admin/__tests__/PromptManagementPage.spec.ts`

**Interfaces:**
- Consumes: Task 9 `overallVerdict`、`verdictSubtitle`；既有 `usePromptEvaluation`（`state.current`、`state.history`、`state.run`、`refresh()`、`start(docVersion)`、`open(runId)`、`loadCases()`）、`versionState`；Task 1 `ConfigHeader` 的 `verdictMark`。
- Produces:
  - `export type PromptEvaluation = ReturnType<typeof usePromptEvaluation>`（`usePromptEvaluation.ts`）
  - `export function verdictMark(history: EvalRunSummary[], current: CurrentEvaluation | null, configKey: string, wc: ConfigVersion | null): string`（`evaluationFormat.ts`）：`current?.configKey === configKey` →「…」；否則最新 `COMPLETED` 且 `versionState === 'evaluated'` 且有 `summary` → `better`「▲」／`worse`「▼」／`same`「≈」；其他 → 空字串。
  - `EvaluationVerdict` props `{ tone: 'better' | 'worse' | 'same' | 'neutral'; title: string; subtitle: string; progress?: { completed: number; total: number } | null }`，預設 slot 放按鈕。
  - `EvaluationView` props `{ evaluation: PromptEvaluation; configKey: string; name: string; workingCopy: ConfigVersion | null; dirty: boolean; canEdit: boolean; busy: boolean; nameOf?: (configKey: string) => string }`，emits `{ 'save-and-run': [] }`。不再自己呼叫 `usePromptEvaluation` 或 `onMounted(refresh)`。

規則：
- 頁面：`const evaluation = usePromptEvaluation(selectedKey)`；掛載時呼叫一次 `evaluation.refresh()`（之後由 composable 的 key watch 處理）；`verdictMark` 傳給 `ConfigHeader`。`save-and-run` → `const saved = await admin.save('KEEP_STATUS'); if (saved) await evaluation.start(saved.docVersion)`（錯誤吞掉的方式同現有 `onRun`）。
- `EvaluationView` 結論卡（規格 5.1，依序判斷，第一個符合的生效）：
  1. 本設定執行中 → tone `neutral`、title「評測中 {completed}/{total}」、`progress`，無按鈕。
  2. 他人評測中 → title「{triggeredBy} 正在評測「{名稱}」（{completed}/{total}），完成後才能執行」（`triggeredBy` 為空時省略前綴），無按鈕。
  3. 沒有編輯中版本 → title 為最近結果的結論或「還沒有評測紀錄」；subtitle「評測會比較草稿和目前生效的版本，請先在內容分頁按「編輯」」，無按鈕。
  4. `dirty` → title「有未儲存的修改，評測只會用已儲存的內容」；按鈕「儲存並評測」（`canEdit` 時，`busy` 時停用）。
  5. 正在看的 run 為 `FAILED` → title「這次評測沒有完成，已完成的題目仍列在下方」；按鈕「重新評測」。
  6. `versionState === 'not-evaluated'` → title「這份草稿還沒評測」，subtitle「會用 {N} 題分別問目前生效的版本和草稿，比較兩者的回答，約需 2–3 分鐘。」（N 取 `caseCount`，未知時省略「{N} 題」改為「固定題目」）；按鈕「執行評測」（主要）。
  7. `modified` → title「評測後內容已修改，結果可能不準」，tone `neutral`；按鈕「重新評測」。
  8. `evaluated` → tone 與 title 依 `overallVerdict`：「草稿比目前生效的版本好」／「草稿有指標變差」／「草稿和目前生效的版本差不多」；subtitle `verdictSubtitle`；按鈕「重新評測」。
  - 沒有 `canEdit` 時一律不顯示按鈕。
- 結論卡下方：`<details>`「全部指標」（預設收合，內含 `EvaluationSummary`）；題目清單（Task 11 調整內容）；底部兩個連結按鈕：「評測歷程（{history.length}）」切換顯示 `EvaluationHistory`、「題庫（{N} 題）」開啟 `EvaluationCasesDialog`。正在看的 run 不是最新一筆時，結論卡上方顯示「正在看 {formatTaipeiShort(run.startedAt)} 的結果・」＋按鈕「回到最新」（`open(history[0].runId)`）。
- 移除：標題列、「編輯中版本（…）：已評測」那一行、「最近一次結果 …」標題、原本停用按鈕旁的說明文字。

- [ ] **Step 1：寫失敗測試**
  - `evaluationFormat.spec.ts`：`verdictMark` — 本 key 執行中 →「…」；`verdictMark ignores another key's running evaluation`（Review Focus 3）：別的 key 執行中、本 key 已評測且 better →「▲」；`modified` → 空字串；無歷程 → 空字串。
  - `EvaluationView.spec.ts`（在測試用 wrapper 元件的 `setup` 中呼叫 `usePromptEvaluation(ref('system.main'))` 並先 `await refresh()`，再把它傳給 `EvaluationView`；api 沿用既有 mock 方式）：結論卡 8 種狀態各一個測試，斷言 title 文字與按鈕有無；`儲存並評測` emit `save-and-run`；「全部指標」的 `details` 預設沒有 `open`；「評測歷程（1）」點開後出現歷程列；看舊 run 時出現「回到最新」且按下呼叫 `getEvaluation(最新 runId)`。
  - `PromptManagementPage.spec.ts`：
    - `shows the verdict mark on the 評測 tab`：最新完成 run 的 `candidateVersion`／`candidateDocVersion` 對上草稿且正確率變好 → 分頁文字為「評測 ▲」。
    - `save and run saves first then starts`：改內容 → 評測分頁按「儲存並評測」→ `saveDraft` 先於 `startEvaluation` 被呼叫，`startEvaluation` 收到儲存回傳的 `docVersion`。
    - `save and run does not start when the save fails`（Review Focus 4）：`saveDraft` 以 422 `AI_CONFIG_VALIDATION_FAILED` 拒絕 → 不呼叫 `startEvaluation`，結論卡仍為「有未儲存的修改…」。
    - `keeps the progress across tab switches`（Review Focus 3）：開始評測 → 切「內容」再切回「評測」→ 仍顯示「評測中 0/17」，`startEvaluation` 只被呼叫一次。
- [ ] **Step 2：執行確認失敗** — `npm test -- evaluationFormat EvaluationView PromptManagementPage`。
- [ ] **Step 3：實作**。
- [ ] **Step 4：執行確認通過** — `npm test` 全部、`npx vue-tsc -b`。

### Task 11：題目清單與題庫對話框

**Files:**
- Modify: `admin/EvaluationCaseList.vue`、`admin/EvaluationCasesDialog.vue`
- Test: `admin/__tests__/EvaluationCaseList.spec.ts`、`admin/__tests__/EvaluationCasesDialog.spec.ts`

**Interfaces:**
- Consumes: 既有 props（`cases`、`nameOf`）不變。

規則（規格 5.4）：
- 標題「結果不同的題目 {n} 題」（只看不同時）或「全部題目 {N} 題」；下方連結按鈕在兩者間切換：「顯示全部 {N} 題」／「只看結果不同的」。移除勾選框。只看不同且沒有差異時顯示「兩個版本的結果沒有差異」。
- 列：題目內容（`question`）＋小字 `group`＋狀態標籤（沿用 `status()`：執行中／變好／變差）。
- 展開：小字 `caseId`；兩欄標題「目前生效」「草稿」。每欄：
  - 回答或作答失敗訊息（不變）。
  - 原因清單：`routeOk === false` 時第一條為「分類不符：預期 {預期名稱}，實際 {routeText(r)}」；其後為既有 `failures()` 其他項目（不再含分類不符那條）；評審有 `error` →「評審失敗」；評審有 `comment` 且（有未達成要點或拒答不符）→「評審：{comment}」；未達成要點各一條「評分要點未達成：{text}」。
  - 原因清單為空且評審沒有失敗 →「全部通過」。
  - 移除「工具 ✓ 字詞 ✓ 數字 ✓」、路由那一行（分類正確時）、「評審：無」。
- 題庫對話框：表頭 `th` 不換行（`white-space: nowrap`），「題號」「應拒答」「預期分類」欄不被擠成直排；內容不變。

- [ ] **Step 1：寫失敗測試**（`EvaluationCaseList.spec.ts`，沿用 `evalFixtures.ts`）
  - 列文字以題目內容開頭、不含 `caseId`；展開後才看到 `caseId`。
  - 預設標題「結果不同的題目 2 題」；按「顯示全部 3 題」→ 標題「全部題目 3 題」，連結變「只看結果不同的」。
  - 兩欄標題為「目前生效」「草稿」，不含「已發布版本」「編輯中版本」。
  - 分類不符 →「分類不符：預期 聯繫準備，實際 無」為第一條；分類正確時不含「路由」。
  - 全部通過且評審無評語 → 顯示「全部通過」，不含「評審：無」與「✓」「✗」。
  - 工具不符且評審未達成一項 → 列出「工具選擇不符」「評分要點未達成：…」。
  - 移除舊測試中對勾選框與「工具 ✓」的斷言。
  - `EvaluationCasesDialog.spec.ts`：表頭 `th` 帶 nowrap 的 class（實作時定名，例如 `ev-dialog__th`）。
- [ ] **Step 2：執行確認失敗** — `npm test -- EvaluationCaseList EvaluationCasesDialog`。
- [ ] **Step 3：實作**。
- [ ] **Step 4：執行確認通過** — `npm test` 全部、`npx vue-tsc -b`。

**Phase E 結束：** 交付 commit 指令（`feat(frontend): evaluation tab with verdict card and simpler case list`），等使用者確認。

---

## Phase F：文件與實測

### Task 12：文件同步

**Files:** 規格第 11 節所列文件；`docs/superpowers/specs/2026-10-28-prompt-admin-simplify-design.md`

- [ ] **Step 1** — 依規格第 11 節更新：
  - `docs/spec/02-rest-api.md` 2.2：快捷問題不含沒有範例問題的分類。3.0：畫面示意改為分頁版面；按鈕對照表改為「儲存／取消排程（`DRAFT`）／立即發布／預約發布、更新預約／捨棄草稿（已排程時不顯示）」；發布前檢查改為載入與儲存後自動執行；評測入口改為「評測」分頁；版面參考改為 `prototype/prompt-management-b.html`。3.0.1 改寫為「版本分頁」（預設差異、點草稿切內容、以此版本建立草稿、行內標示）。3.1「System Instructions」分頁名稱改為「問題分類」。3.8 錯誤範例改為新規則的訊息（例如「「## 適用問題」不可空白」「「範例問題」最多 5 題，目前有 6 題」「第 12 行疑似包含未允許的網址，請移除」）。
  - `docs/spec/04-mongodb-schema.md` 2.5：規則表與禁止內容表改為規格 6 節（`REQUIRED_SECTION` 只剩問題分類的 `## 適用問題` 且不可空白；`EXAMPLE_QUESTIONS` 0–5 題；網址改白名單 `ai-assistant.config.allowed-url-hosts`；IP 獨立一列；刪除越權指令列）。
  - `docs/spec/05-error-codes.md`：驗證錯誤範例的「缺少必要章節：## 安全規則」改為「## 適用問題」。
  - `docs/spec/07-prompt-examples.md`：開頭「所有範例都必須能通過發布前驗證」一句與驗證規則表（必要章節、範例問題、禁止內容）改為新規則；範本中除 `## 適用問題` 外的章節註明為建議章節。
  - `docs/spec/01-architecture.md`：設定範例的 `config` 區塊加 `allowed-url-hosts: []` 與註解。
  - 評測規格 8.3：結論卡、結論規則（規格 5.2）、全部指標收合、題目清單、底部連結。
  - POC 設計決策表「編輯頁按鈕」列改為新按鈕組（註明 2026-10-28 精簡）；版本紀錄加一筆。
  - `06-conversation-scenarios.md` M4、M8、M9、M11、M15、M17、M20、M21：操作路徑改為分頁用語（例如「版本分頁 → 點舊版本 → 以此版本建立草稿」「取消排程」「評測分頁」「儲存並評測」）。
  - `demo-checklist.md` 步驟 12、15、18、19、20 同步操作路徑。
  - 本規格狀態改為「已實作」、版本 v0.3。
- [ ] **Step 2** — `grep -rn "返回編輯\|存成草稿\|重新檢查\|尚未檢查\|越權指令\|1–5 題\|1 至 5\|版本歷程對\|標題列的「評測」" docs` 只剩歷史紀錄性質的段落（舊計畫、版本紀錄）。

### Task 13：實際操作驗證（主控者與使用者一起做）

- [ ] **Step 1** — 使用者啟動前後端；主控者以 Playwright（本機 Edge，`backoffice.admin`／`it.admin`，密碼 `poc1234`）截圖：內容分頁（草稿、已排程、沒有草稿、檢查未通過、有未儲存修改時的「儲存後重新檢查」）、版本分頁三種預設、比較（行內標示）、評測分頁（已評測、評測後已修改、有未儲存的修改）、深色模式、視窗寬 1000px 的單欄版面。不按發布與執行評測，除非使用者同意。
- [ ] **Step 2** — 與使用者一起跑 demo 檢查清單步驟 12、15、18、19、20（19、20 會呼叫 GPT，需使用者同意）。另驗證規格 10.3：把一個問題分類的範例問題全部刪除後發布，對話視窗不顯示該分類，直接輸入相關問題仍分到該分類（會呼叫 GPT，需使用者同意；驗證後以「以此版本建立草稿」還原）。
- [ ] **Step 3** — 交付 Phase F commit 指令（`docs: prompt admin simplify — specs, scenarios and demo checklist`）。
