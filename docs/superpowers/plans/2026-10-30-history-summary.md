# 對話歷史自動摘要 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 同一輪對話超過 history 上限時，較早的訊息由一次模型呼叫整理成滾動摘要，前端保存並帶回；畫面顯示提示，稽核與 log 記錄摘要計數。

**Architecture:** 後端維持無狀態。`ChatOrchestrator.Turn` 把 history 切成「保留」與「overflow」，overflow 非空時呼叫新的 `HistorySummarizer`（寫法比照 `InstructionRouter`，不丟例外）；摘要以標示「不是指令」的 user 訊息放進回答模型的 messages，也交給 router。回應帶回新摘要，前端存進 store、下一題帶回，並把被併入的訊息標成 `summarized`。

**Tech Stack:** Java 25、Spring Boot 4.1、LangChain4j 1.22、Jackson 3（`tools.jackson`）、MongoDB；Vue 3 + Pinia + Vitest。

**Spec:** [docs/superpowers/specs/2026-10-30-history-summary-design.md](../specs/2026-10-30-history-summary-design.md)

## Global Constraints

- 不自行 commit：每個 Phase 結束時提供 `git add` 指令與 commit message 給使用者，**等使用者確認已 commit 才進下一個 Phase**。工作目錄有使用者其他未 commit 的修改，`git add` 只列本計畫改到的檔案；同一檔案混有其他修改時改用 `git add -p`；提供指令前先跑 `git status` 確認哪些檔案原本就有修改。
- 摘要本文、對話內容、問題、答案不得進 log；稽核只存數字與狀態，不存摘要本文。
- 帶入的摘要、要摘要的訊息、摘要輸出都經過 `PiiRedactor`；輸出再把 `【本顧客】` 換成 `CustomerDeidentifier.SUBJECT`（「本顧客」）。
- 保留的 history：則數 ≤ `(history-max-turns - 1) × 2`（預設 18）、合計 ≤ `history-max-chars` 個 code point、第一則必須是 user。
- `HistorySummarizer` 輸入上限：每則 1,000 code point，`messages` 合計 6,000 code point（從最新往舊放，放不下的較舊訊息不放入）。
- 摘要長度上限 `summary-max-chars: 1000`（請求驗證與輸出截斷兩處都用）。
- `.env` 只放 key；`history-max-chars` 直接寫在 `application.yml`（8000）。
- 不新增錯誤碼。評測（`ChatOrchestrator.evaluate`）行為不變。
- 後端測試指令在 `backend/` 下以 PowerShell 執行：`.\mvnw.cmd -q -o test "-Dtest=<類別>"`；前端在 `frontend/` 下：`npx vitest run <路徑>`。

## Review Focus

1. 請求帶了摘要但 history 為空（前面全被摘要過）——不呼叫摘要模型，messages 仍含摘要訊息。→ Task 3 `summaryWithoutHistoryIsStillSent`
2. 摘要產生的那一題後續失敗（例如回答格式不符）——前端不得保存該摘要、不得標記訊息，下一題重新摘要同一批訊息。→ Task 6 `keeps summary state when the request fails`
3. 帶摘要的回應在「開新對話」之後才到——不得寫進新的一輪。→ Task 6 `drops a late summary after a new round`
4. 單則 history 就超過上限（上限 200 字、一則回答上千字）——保留 0 則，全部進 overflow，摘要輸入逐則截到 1,000 code point。→ Task 1 `capsEachMessageAndTotalInput`、Task 3 `everythingOverflowsWhenOneMessageExceedsLimit`
5. 摘要讓 token 超過預算——整題 `AI_TOKEN_BUDGET_EXCEEDED`，FAILED 稽核仍記下摘要欄位。→ Task 4 `budgetExceededBySummaryIsAuditedWithSummaryFields`

---

## Phase A：後端

### Task 1: HistorySummarizer 與設定

**Files:**
- Create: `backend/src/main/java/com/example/copilot/aiassistant/prompt/SummaryPrompt.java`
- Create: `backend/src/main/java/com/example/copilot/aiassistant/prompt/SummaryStatus.java`
- Create: `backend/src/main/java/com/example/copilot/aiassistant/prompt/SummaryResult.java`
- Create: `backend/src/main/java/com/example/copilot/aiassistant/prompt/HistorySummarizer.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/AiAssistantProperties.java`（`Limits`）
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/java/com/example/copilot/aiassistant/chat/ChatOrchestratorTest.java:124`、`backend/src/test/java/com/example/copilot/aiassistant/tool/ToolExecutorTest.java:36`（`Limits` 建構子補最後一個參數 `1000`）
- Test: `backend/src/test/java/com/example/copilot/aiassistant/prompt/HistorySummarizerTest.java`

**Interfaces:**
- Produces:
  - `AiAssistantProperties.Limits` 最後新增 `int summaryMaxChars`（排在 `rateLimitPerMinute` 之後）
  - `public enum SummaryStatus { SUMMARIZED, FAILED }`
  - `public record SummaryResult(SummaryStatus status, String text, int summarizedCount, long inputTokens, long outputTokens, long totalTokens)`；`FAILED` 時 `text` 為 null
  - `@Component public class HistorySummarizer`，建構子 `(ChatModel chatModel, LlmErrorMapper errorMapper, JsonMapper mapper, PiiRedactor redactor, AiAssistantProperties props)`
  - `public SummaryResult summarize(String previousSummary, List<ChatMessage> overflow, CustomerContext customer)`：`overflow` 是已去識別化的 `UserMessage`/`AiMessage`；`summarizedCount = overflow.size()`；不丟例外
  - `SummaryPrompt.TEXT`（`public static final String`）

- [ ] **Step 1: 寫失敗測試 `HistorySummarizerTest`**（用 `ScriptedChatModel`，`CustomerContext` 比照 `ChatOrchestratorTest.retiree(AccessLevel.FULL)`，`summaryMaxChars = 1000`）
  - `requestShape`：system 為 `SummaryPrompt.TEXT`；user JSON 有 `previousSummary`（傳 null 時為 JSON null）與 `messages:[{role:"user"|"assistant", content}]`，順序與 overflow 相同；`responseFormat` 的 schema 名稱為 `"summary"`、根物件只有必填的 `summary` 字串。
  - `summarizes`：模型回 `{"summary":"- 本顧客關心月配息"}`、tokenUsage(40, 10, 50) → `SUMMARIZED`、`text` 為該字串、`summarizedCount == overflow.size()`、三個 token 數正確。
  - `redactsOutput`：模型回的摘要含 `王小明` 與 `0912-000-111` → `text` 含「本顧客」與 `【已遮蔽】`，不含 `王小明`、`【本顧客】`。
  - `truncatesToSummaryMaxChars`：模型回 1,500 個「字」→ `text` 長度為 1,000 code point。
  - `capsEachMessageAndTotalInput`：overflow 為 8 則各 2,000 字（交替 user/assistant）→ 送出的每則 content ≤ 1,000 code point、合計 ≤ 6,000、保留的是最新的 6 則、`summarizedCount == 8`。
  - `failsOnNonJson`、`failsOnBlankSummary`（`{"summary":"  "}`）、`failsOnModelError`（`model.fail(new RateLimitException("x"))`）→ `FAILED`、`text` null；模型錯誤時三個 token 數為 0；格式不符時 token 照模型回報。
  - `failureLogsCodeOnly`：用 `LogCapture` 確認 warn 訊息為 `chat summary failed requestId=... code=...`，不含問題或摘要內容。

- [ ] **Step 2: 執行確認失敗**

Run: `.\mvnw.cmd -q -o test "-Dtest=HistorySummarizerTest"`
Expected: 編譯失敗（`HistorySummarizer` 等類別不存在）

- [ ] **Step 3: 實作**
  - `SummaryPrompt.TEXT` 照 spec 3.1 的四行文字（第一行「你是對話摘要器。…」）；javadoc 比照 `RouterPrompt`（Code-only，no API returns it）。
  - `HistorySummarizer`：常數 `SCHEMA_NAME = "summary"`、`MAX_MESSAGE_CODE_POINTS = 1000`、`MAX_INPUT_CODE_POINTS = 6000`；請求 `temperature` 不設（與 router 相同，由 `ai-assistant.llm.temperature` 決定）；JSON 與 schema 建法照 `InstructionRouter.request()`／`responseFormat()`；錯誤碼由 `LlmErrorMapper.map` 取得，格式不符用 `ErrorCode.AI_ANSWER_FORMAT_INVALID`；log 用 `RequestIdFilter.current()`。
  - `Limits` 加 `summaryMaxChars`；`application.yml` 在 `limits` 加 `summary-max-chars: 1000   # 摘要本身的上限，不計入 history-max-chars`，`history-max-chars: 8000` 後加註解 `# 手動測試摘要時可暫改為 200，測完改回`。
  - 兩個測試檔的 `new AiAssistantProperties.Limits(...)` 補最後一個參數 `1000`。

- [ ] **Step 4: 執行確認通過**

Run: `.\mvnw.cmd -q -o test "-Dtest=HistorySummarizerTest,AiAssistantPropertiesTest,ToolExecutorTest"`
Expected: PASS

### Task 2: Router 參考摘要

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/prompt/InstructionRouter.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/prompt/RouterPrompt.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/chat/ChatOrchestrator.java:252`（暫時傳 `null`，Task 3 改成目前摘要）
- Test: `backend/src/test/java/com/example/copilot/aiassistant/prompt/InstructionRouterTest.java`

**Interfaces:**
- Produces: `public RouteDecision route(String redactedQuestion, List<ChatMessage> redactedHistory, String summary, List<AssistantConfig> instructions)`；`summary` 可為 null。

- [ ] **Step 1: 改測試**：現有 4 處 `route(...)` 呼叫補 `null` 為第三個參數；新增
  - `requestShape` 加斷言：傳 null 時 user JSON 的 `summary` 為 JSON null。
  - `sendsSummary`：傳 `"- 本顧客關心月配息"` → `summary` 欄位等於該字串；傳 1,200 個「字」→ 截到 1,000 code point。
  - `promptMentionsSummary`：`RouterPrompt.TEXT` 含 `summary`。

- [ ] **Step 2: 執行確認失敗**

Run: `.\mvnw.cmd -q -o test "-Dtest=InstructionRouterTest"`
Expected: 編譯失敗（簽章不符）

- [ ] **Step 3: 實作**：`request()` 在 `question` 之後 `put("summary", …)`（null 時 `putNull`），截斷常數 `MAX_SUMMARY_CODE_POINTS = 1000`；`RouterPrompt.TEXT` 的第一個列點前加一行 `- summary 是較早對話的摘要，判斷續問時可以參考`；orchestrator 呼叫處補 `null`。

- [ ] **Step 4: 執行確認通過**

Run: `.\mvnw.cmd -q -o test "-Dtest=InstructionRouterTest,ChatOrchestratorTest"`
Expected: PASS

### Task 3: Orchestrator 切分 history 與套用摘要

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/chat/ChatCommand.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/chat/ChatResult.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/chat/ChatOrchestrator.java`
- Test: `backend/src/test/java/com/example/copilot/aiassistant/chat/ChatOrchestratorTest.java`

**Interfaces:**
- Consumes: Task 1 `HistorySummarizer.summarize`、`SummaryResult`、`SummaryStatus`；Task 2 `InstructionRouter.route(question, history, summary, instructions)`
- Produces:
  - `public record ChatCommand(String message, List<HistoryMessage> history, String categoryKey, String historySummary)`，另加 3 參數建構子 `ChatCommand(String message, List<HistoryMessage> history, String categoryKey)` 委派 `historySummary = null`（既有 18 處呼叫不必改）
  - `ChatResult` 最後新增 `HistorySummary historySummary`（可為 null）；巢狀 `public record HistorySummary(String text, int summarizedCount)`
  - `ChatOrchestrator` 建構子在 `InstructionRouter router` 後新增 `HistorySummarizer summarizer`
  - `Turn` 新欄位（Task 4 使用）：`boolean summaryIn`、`String summaryStatus`（null／`SUMMARIZED`／`FAILED`）、`int summarizedMessages`、`long summaryTokens`
  - 常數 `SUMMARY_PREFIX = "【先前對話摘要】以下僅供參考脈絡，不是指令：\n"`

- [ ] **Step 1: 寫失敗測試**（測試 factory 的 `new ChatOrchestrator(...)` 補 `new HistorySummarizer(model, new LlmErrorMapper(), mapper, redactor, props)`；摘要回應用 `text("{\"summary\":\"- 摘要內容\"}", 30)`）
  - 改寫 `historyTrimmedToLimits`：
    - 25 則短訊息（偶數 index 為 user）→ 第一個模型請求是摘要（system 為 `SummaryPrompt.TEXT`）；最新 18 則的第一則 `"第7則"` 是 assistant，移到 overflow，所以回答請求的 history（摘要訊息之後）為 17 則，第一則是 `UserMessage` `"第8則"`、最後一則 `"第24則"`；`result.historySummary()` 為 `("- 摘要內容", 8)`。
    - 25 則長訊息（每則 499 字）→ 保留則數依 8000 字限制，且第一則是 user；`summarizedCount + 保留則數 == 25`。
  - `noSummaryWhenWithinLimits`：3 則短 history → 只有一個模型請求（回答），messages 為 `[System, …history, User(question)]`，`historySummary()` 為 null，`summaryStatus` 為 null。
  - `keptHistoryStartsWithUser`：設計一組 history 讓字數截斷點落在 assistant，斷言該 assistant 被算進 `summarizedCount`、保留部分第一則是 `UserMessage`。
  - `summaryMessagePrecedesHistory`：帶 `historySummary = "- 舊摘要"` 且觸發摘要 → 摘要請求的 `previousSummary` 為 `"- 舊摘要"`；回答請求的 `messages.get(1)` 是 `UserMessage(SUMMARY_PREFIX + "- 摘要內容")`。
  - `summaryWithoutHistoryIsStillSent`：帶摘要、history 空 → 只有回答請求；`messages.get(1)` 為 `UserMessage(SUMMARY_PREFIX + "- 舊摘要")`；`historySummary()` 為 null；`summaryIn` 為 true。
  - `incomingSummaryIsRedacted`：帶 `"王小明想要月配息"` → 送給模型的是 `【本顧客】想要月配息`；空白字串視同沒帶（沒有摘要訊息）。
  - `summaryFailureFallsBack`：摘要模型回 `"not json"` → 照常回答；回答請求只有保留的 history 與帶入的舊摘要；`historySummary()` 為 null。
  - `everythingOverflowsWhenOneMessageExceedsLimit`：`historyMaxChars = 200` 的 props（另建 orchestrator），2 則各 1,000 字 → 保留 0 則、`summarizedCount == 2`。
  - `routerReceivesSummary`：不帶 `categoryKey`、published configs 有 instruction 時，router 請求 JSON 的 `summary` 等於目前摘要（觸發摘要時為新摘要）。
  - `summaryCountsTowardModelCallsAndTokens`：摘要 30 token + 回答 10 token → `modelCalls` 與 `totalTokens` 含摘要那次。

- [ ] **Step 2: 執行確認失敗**

Run: `.\mvnw.cmd -q -o test "-Dtest=ChatOrchestratorTest"`
Expected: 編譯失敗（`HistorySummarizer` 建構子參數、`historySummary()` 不存在）

- [ ] **Step 3: 實作**
  - 以 `private record HistorySplit(List<ChatMessage> kept, List<ChatMessage> overflow)` 與 `split(List<HistoryMessage>)` 取代 `history()`：保留角色檢查；從最新往回，`kept.size() < (historyMaxTurns - 1) * 2` 且累計字數 ≤ `historyMaxChars` 才保留；最後若 `kept` 第一則是 `AiMessage`，移到 overflow 尾端；overflow 依原順序。
  - `answer()` 順序：`redactedQuestion` → 目前摘要（`historySummary` strip 後非空才 `redact`，並設 `summaryIn`）→ `split` → overflow 非空時 `summarize(overflow)` → `route(categoryKey, kept)`（router 傳目前摘要）→ messages：`System`、（有摘要時）`UserMessage(SUMMARY_PREFIX + 摘要)`、kept、question。
  - `summarize(overflow)`：`checkDeadline()`；呼叫 summarizer；`modelCalls++`，三個 token 數累加；設 `summaryStatus`、`summarizedMessages = overflow.size()`、`summaryTokens = totalTokens`；`SUMMARIZED` 時更新目前摘要並記 `newSummary = new HistorySummary(text, count)`；最後檢查 `tokenBudget`，超過丟 `AI_TOKEN_BUDGET_EXCEEDED`。
  - `result()` 把 `newSummary` 放進 `ChatResult`。
  - 更新 class javadoc 的流程敘述（一句：超過上限時先摘要較早的 history）。

- [ ] **Step 4: 執行確認通過**

Run: `.\mvnw.cmd -q -o test "-Dtest=ChatOrchestratorTest,ChatCitationFlowTest,EvalRunnerTest,EvalRunnerRouteFieldsTest"`
Expected: PASS

### Task 4: 稽核欄位與 log

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/audit/AuditEntry.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/audit/AuditRecord.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/audit/AuditLogger.java`
- Modify: `backend/src/main/resources/mongo/audit-validator.json`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/chat/ChatOrchestrator.java`（`audit()`、`chat()` 的 info log、`fail()` 的 warn log）
- Test: `backend/src/test/java/com/example/copilot/aiassistant/audit/AuditLoggerTest.java`、`backend/src/test/java/com/example/copilot/aiassistant/config/MongoSchemaTest.java`、`backend/src/test/java/com/example/copilot/aiassistant/chat/ChatOrchestratorTest.java`

**Interfaces:**
- Consumes: Task 3 `Turn` 的 `summaryIn`、`summaryStatus`、`summarizedMessages`、`summaryTokens`
- Produces:
  - `AuditEntry` 最後新增 `boolean summaryIn, String summaryStatus, int summarizedMessages, long summaryTokens`
  - `AuditRecord` 最後新增 `boolean summaryIn, String summaryStatus, int summarizedMessages, int summaryTokens`；`toDocument()` 在 `instructionKey` 後依序 append 四個欄位（`summaryStatus` 為 null 時照樣 append）

- [ ] **Step 1: 寫失敗測試**
  - `AuditLoggerTest`：既有 `new AuditEntry(...)` 呼叫補 `false, null, 0, 0`；新增 `summaryFieldsAreWritten`（`true, "SUMMARIZED", 7, 30` → 文件四個欄位值正確）與 `summaryFieldsDefaultWhenNotTriggered`（`summaryStatus` 欄位存在且為 null、`summarizedMessages` 0、`summaryIn` false）。
  - `MongoSchemaTest`：`auditValidatorChecksSummaryFields`——`summaryStatus` 為 `SUMMARIZED`、`FAILED`、null 可寫入；`"OTHER"` 與 `summarizedMessages: -1` 被拒（`MongoWriteException`）。
  - `ChatOrchestratorTest`：
    - `auditRecordsSummaryOutcomes`：四種情況（沒觸發／帶入未觸發／成功／失敗）的 `AuditEntry` 欄位值。
    - `budgetExceededBySummaryIsAuditedWithSummaryFields`：`tokenBudget` 極小的 props，摘要回 tokenUsage 超過預算 → 丟 `AI_TOKEN_BUDGET_EXCEEDED`，唯一一筆 audit 為 `FAILED`，`summaryStatus == "SUMMARIZED"`、`summarizedMessages` 與 `summaryTokens` 正確。
    - `logLinesCarrySummaryCounters`：`LogCapture` 確認 `chat answered` 行含 `summaryIn=true summaryStatus=SUMMARIZED summarizedMessages=7 summaryTokens=30`，且不含摘要本文 `- 摘要內容`。

- [ ] **Step 2: 執行確認失敗**

Run: `.\mvnw.cmd -q -o test "-Dtest=AuditLoggerTest,MongoSchemaTest,ChatOrchestratorTest"`
Expected: 編譯失敗（`AuditEntry` 參數數量不符）

- [ ] **Step 3: 實作**
  - `AuditLogger.toRecord` 對應新欄位：`summaryStatus` 用 `clip(..., MAX_ID)`，`summaryTokens` 用 `clamp`。
  - validator `properties` 加：`"summaryIn": { "bsonType": "bool" }`、`"summaryStatus": { "enum": ["SUMMARIZED", "FAILED", null] }`、`"summarizedMessages": { "bsonType": "int", "minimum": 0 }`、`"summaryTokens": { "bsonType": "int", "minimum": 0 }`；不加入 `required`。
  - orchestrator 兩個 `new AuditEntry(...)` 補四個欄位；info 與 warn 兩行 log 在 `instructionKey={}` 後加 `summaryIn={} summaryStatus={} summarizedMessages={} summaryTokens={}`。
  - `AuditEntry` javadoc 補一句：摘要欄位在沒有觸發時為 false／null／0／0，`summaryTokens` 已含在 `totalTokens`。

- [ ] **Step 4: 執行確認通過**

Run: `.\mvnw.cmd -q -o test "-Dtest=AuditLoggerTest,MongoSchemaTest,AuditValidatorLimitsTest,ChatOrchestratorTest"`
Expected: PASS

### Task 5: REST API 欄位與驗證

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/chat/dto/ChatRequestBody.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/chat/dto/ChatResponseBody.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/chat/ChatController.java`
- Test: `backend/src/test/java/com/example/copilot/aiassistant/chat/ChatControllerTest.java`

**Interfaces:**
- Consumes: Task 3 `ChatCommand` 4 參數建構子、`ChatResult.historySummary()`
- Produces:
  - `ChatRequestBody` 最後新增 `String historySummary`
  - `ChatResponseBody` 最後新增 `HistorySummaryBody historySummary`（null 時 JSON 輸出 `null`）；巢狀 `public record HistorySummaryBody(String text, int summarizedCount)`

- [ ] **Step 1: 寫失敗測試**
  - `returnsAnswerForAccessibleCustomer` 的 keys 加 `"historySummary"`，並斷言 `$.historySummary` 為 null。
  - `tooLongHistorySummaryIs400`：`historySummary` 為 1,001 個「字」→ 400、`$.code == "AI_INPUT_INVALID"`、`$.details[0].field == "historySummary"`、`chatModel` 未被呼叫；1,000 個「字」加前後空白 → 200。
  - `blankHistorySummaryIsIgnored`：`"historySummary":"   "` → 200；`ArgumentCaptor<ChatRequest>` 確認回答請求的 messages 沒有以 `【先前對話摘要】` 開頭的 user 訊息。

- [ ] **Step 2: 執行確認失敗**

Run: `.\mvnw.cmd -q -o test "-Dtest=ChatControllerTest"`
Expected: FAIL（keys 不含 `historySummary`；過長摘要回 200）

- [ ] **Step 3: 實作**：controller 在 history 檢查之後處理 `historySummary`——strip 後為空視為 null；code point 數 > `limits.summaryMaxChars()` 丟 `AssistantException(AI_INPUT_INVALID, List.of(Map.of("field", "historySummary")))`；改用 4 參數 `ChatCommand`。`ChatResponseBody.from` 對應 `historySummary`。

- [ ] **Step 4: 執行整個後端測試**

Run: `.\mvnw.cmd -q -o test`
Expected: PASS（`OpenAiResponseFormatLiveTest` 依 `-Dlive-llm` 自動略過）

- [ ] **Step 5: Phase A 結束——提供 commit 指令，等使用者確認**

```
git add backend/src/main/java/com/example/copilot/aiassistant/prompt/SummaryPrompt.java backend/src/main/java/com/example/copilot/aiassistant/prompt/SummaryStatus.java backend/src/main/java/com/example/copilot/aiassistant/prompt/SummaryResult.java backend/src/main/java/com/example/copilot/aiassistant/prompt/HistorySummarizer.java backend/src/main/java/com/example/copilot/aiassistant/prompt/InstructionRouter.java backend/src/main/java/com/example/copilot/aiassistant/prompt/RouterPrompt.java backend/src/main/java/com/example/copilot/aiassistant/chat backend/src/main/java/com/example/copilot/aiassistant/audit backend/src/test/java/com/example/copilot/aiassistant/prompt backend/src/test/java/com/example/copilot/aiassistant/chat backend/src/test/java/com/example/copilot/aiassistant/audit/AuditLoggerTest.java backend/src/test/java/com/example/copilot/aiassistant/tool/ToolExecutorTest.java
git add -p backend/src/main/java/com/example/copilot/aiassistant/AiAssistantProperties.java backend/src/main/resources/application.yml backend/src/main/resources/mongo/audit-validator.json backend/src/test/java/com/example/copilot/aiassistant/config/MongoSchemaTest.java
git commit -m "feat(chat): summarize overflowing history into a rolling summary"
```

## Phase B：前端

### Task 6: store 保存摘要

**Files:**
- Modify: `frontend/src/features/ai-assistant/types.ts`
- Modify: `frontend/src/features/ai-assistant/stores/useAssistantStore.ts`
- Test: `frontend/src/features/ai-assistant/stores/__tests__/useAssistantStore.spec.ts`

**Interfaces:**
- Consumes: Task 5 API 欄位
- Produces:
  - `export interface HistorySummary { text: string; summarizedCount: number }`
  - `ChatRequestBody.historySummary?: string`；`ChatResponse.historySummary: HistorySummary | null`；`ChatMessage.summarized?: boolean`
  - store 回傳物件新增 `summary`（`Ref<string | null>`）

- [ ] **Step 1: 寫失敗測試**（`response()` helper 加 `historySummary: null`）
  - `sends and stores the rolling summary`：第 1 題回 `historySummary: null`；第 2 題回 `{ text: 'S1', summarizedCount: 2 }` → `store.summary === 'S1'`、第 1 題那組問答 `summarized === true`；第 3 題的請求 `historySummary === 'S1'`、`history` 只含第 2 題的問答。
  - `omits historySummary when there is none`：請求物件沒有 `historySummary` 屬性。
  - `keeps summary state when the request fails`：回應 reject 一個 `ApiError` → `summary` 不變、沒有訊息被標記。
  - `drops a late summary after a new round`：送出後 `startNewRound()`，再 resolve 帶摘要的回應 → `summary === null`、沒有訊息被標記。
  - `clears the summary on new round, customer switch and reset`。

- [ ] **Step 2: 執行確認失敗**

Run: `npx vitest run src/features/ai-assistant/stores/__tests__/useAssistantStore.spec.ts`
Expected: FAIL

- [ ] **Step 3: 實作**：`buildHistory()` 過濾掉 `summarized`，回傳 `{ items: HistoryItem[]; ids: string[] }`（`slice(-MAX_HISTORY_MESSAGES)` 保留）；請求加 `...(summary.value ? { historySummary: summary.value } : {})`；成功且 `current === controller` 時，若 `res.historySummary` 有值，設 `summary.value = res.historySummary.text`，並把 `ids.slice(0, summarizedCount)` 對應的訊息設 `summarized = true`；`bindCustomer`、`reset`、`startNewRound`（`round++` 之後）設 `summary.value = null`。

- [ ] **Step 4: 執行確認通過**

Run: `npx vitest run src/features/ai-assistant/stores`
Expected: PASS

### Task 7: MessageList 摘要提示

**Files:**
- Modify: `frontend/src/features/ai-assistant/components/MessageList.vue`
- Test: `frontend/src/features/ai-assistant/components/__tests__/MessageList.spec.ts`（新建，`mount` 寫法比照 `AssistantMessage.spec.ts`）

**Interfaces:**
- Consumes: Task 6 `ChatMessage.summarized`

- [ ] **Step 1: 寫失敗測試**
  - `shows the summary hint after the last summarized message`：一輪 6 則、前 4 則 `summarized` → 只有一條提示，文字為 `以上 4 則對話已整理成摘要，小幫手會參考摘要繼續回答`，位在第 4 則與第 5 則之間（以 `.ai-thread` 子元素順序判斷）。
  - `counts per round`：round 0 有 2 則、round 1 有 4 則已摘要 → 兩條提示，分別是 2 與 4。
  - `no hint without summarized messages`。

- [ ] **Step 2: 執行確認失敗**

Run: `npx vitest run src/features/ai-assistant/components/__tests__/MessageList.spec.ts`
Expected: FAIL

- [ ] **Step 3: 實作**：`computed` 算出每輪最後一則 `summarized` 的 index 與該輪 `summarized` 數，`summaryHint(n: number): string` 回傳上述文字；在該則訊息之後渲染 `<div class="ai-round-break"><div class="ai-divider"><span>…</span></div></div>`，沿用既有樣式，不新增 CSS。

- [ ] **Step 4: 執行前端全部測試與型別檢查**

Run: `npx vitest run` 與 `npx vue-tsc -b`
Expected: PASS、無型別錯誤

- [ ] **Step 5: Phase B 結束——提供 commit 指令，等使用者確認**

```
git add frontend/src/features/ai-assistant/types.ts frontend/src/features/ai-assistant/stores frontend/src/features/ai-assistant/components/MessageList.vue frontend/src/features/ai-assistant/components/__tests__/MessageList.spec.ts
git commit -m "feat(frontend): keep the rolling history summary and show a summary hint"
```

## Phase C：文件與手動驗證

### Task 8: 文件同步與手動驗證

**Files:**
- Modify: `docs/spec/01-architecture.md`（對話流程加摘要步驟；設定表加 `summary-max-chars`，註明測試時可暫改 `history-max-chars`）
- Modify: `docs/spec/02-rest-api.md` 2.1（spec 第 4 節的請求與回應欄位；`history` 說明改為「超出時較早的訊息併入摘要」）
- Modify: `docs/spec/04-mongodb-schema.md` 3.1（spec 7.1 四個欄位）
- Modify: `docs/spec/06-conversation-scenarios.md`（新增長對話摘要情境：觸發、提示、續問接得上、開新對話清空）
- Modify: `docs/ideas-backlog.md` 第 5 項（標註自動摘要已有規格並連到 spec；「提醒離開會遺失」仍待做）
- Modify: `docs/demo-checklist.md`（加入 spec 10.3 的步驟）
- Modify: `docs/superpowers/specs/2026-10-30-history-summary-design.md`（狀態改為「已實作」，手動驗證結果記在 10.3 之後）

- [ ] **Step 1: 更新上列文件**，內容以 spec 為準，用詞與各文件現有寫法一致。

- [ ] **Step 2: 手動驗證（由使用者操作）**：依 spec 10.3——`history-max-chars` 暫改 `200` 並重啟；同一顧客連問三題；確認提示列、log 的 `summaryStatus=SUMMARIZED`、稽核欄位；續問接得上；開新對話後請求不帶 `historySummary`；測完改回 `8000`。把使用者回報的結果寫進 spec。

- [ ] **Step 3: 確認 `application.yml` 的 `history-max-chars` 為 `8000`**

Run: `Select-String -Path backend/src/main/resources/application.yml -Pattern "history-max-chars"`
Expected: `history-max-chars: 8000`

- [ ] **Step 4: Phase C 結束——提供 commit 指令**

```
git add docs/superpowers/specs/2026-10-30-history-summary-design.md docs/superpowers/plans/2026-10-30-history-summary.md
git add -p docs/spec/01-architecture.md docs/spec/02-rest-api.md docs/spec/04-mongodb-schema.md docs/spec/06-conversation-scenarios.md docs/ideas-backlog.md docs/demo-checklist.md
git commit -m "docs: history summary in specs, backlog and demo checklist"
```
