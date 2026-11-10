# LLM 可觀測性 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 每一次模型呼叫（含評測）各寫一行平台格式的 `Outbound` log，帶用途、耗時、token 與錯誤分類，失敗時附去掉訊息的 StackTrace。

**Architecture:** 在 `OpenAiChatModel` 註冊自訂 `ChatModelListener`（`OutboundLlmLogListener`），由 Jackson 產生 Table D 格式的單行 JSON。5 個呼叫點改用 `chat(request, LlmCallPurpose.X.options())` 帶入用途；`ArchitectureRulesTest` 確保以後的呼叫點不會漏帶。兩個階段：A 建 listener 並註冊；B 改呼叫點、移除重複的 ERROR、同步文件。

**Tech Stack:** Java 25、Spring Boot 4.1、LangChain4j 1.22.0（`ChatModelListener`、`ChatRequestOptions`）、Jackson 3（`tools.jackson`）、Log4j2、JUnit 5、AssertJ。

**Spec:** [docs/superpowers/specs/2026-11-10-llm-observability-design.md](../specs/2026-11-10-llm-observability-design.md)

## Global Constraints

- logger 名稱固定 `Outbound`；成功 INFO、失敗 ERROR（spec §1、§4）。
- 平台欄位名稱與型別照 spec §4：`event`、`result`、`request.target_system`（`"OpenAI"`）、`request.path`（`"OpenAiChatModel.chat"`）、`response.response_type`、`response.status_code`、`response.result_code`、`execution_time`、`duration_ms`、`error_type`、`root_cause_type`；**所有值都是字串**。
- 新增欄位只放在 `llm` 物件：`request_id`、`purpose`、`model`、`input_tokens`、`output_tokens`、`total_tokens`、`error_code`。
- `event` 成功為 `external_call_completed`、失敗為 `external_call_failed`；`result` 與 `result_code` 為 `SUCCESS`／`FAIL`。
- 不得輸出 `error_message`、`root_cause_message`，log 與 StackTrace 中不得出現任何例外訊息、prompt、回答或工具參數。
- MDC key：讀 `requestId`（`RequestContext.MDC_KEY`）；寫 `logMessage`，寫完還原原值。
- 不修改 `com.example.copilot` 以外的檔案（平台檔案）；不動任何既有的 `log.info`。
- 維持 `ArchitectureRulesTest` 全綠：application 不得 import infrastructure。
- logger 慣例：類別自己的 logger 用 Lombok `@Slf4j`（專案現行寫法）；`Outbound` 這個具名 logger 用 `LoggerFactory.getLogger("Outbound")`。
- 使用者不要自動 commit：每個 Phase 結束時提供 `git add` 指令與 commit message，**停下來等使用者確認已 commit** 再開始下一個 Phase；各 task 不 commit。
- 驗證指令：單一測試 `cd backend && mvn -q test -Dtest=<Class>`；全部 `cd backend && mvn -q test`（需 Docker 啟動中，Testcontainers 會拉 `mongo:8`）。

## Review Focus

1. **`RateLimitException` 沒有 `HttpException` cause**：例外鏈裡找不到狀態碼時，`status_code` 應為 `"500"`，`llm.error_code` 仍是 `AI_LLM_RATE_LIMITED`，不可拋例外。→ Task 2 `rateLimitWithoutHttpCauseReports500`。
2. **MDC 沒有 `requestId`**（排程、啟動時或測試直接呼叫）：`llm.request_id` 應為 `""`，不可是 `"null"`。→ Task 2 `missingRequestIdIsBlank`。
3. **外層已設定 MDC `logMessage`**：寫完 Outbound log 後必須還原成原值，原本沒有就移除，否則後續 log 會帶錯摘要。→ Task 2 `logMessageMdcIsRestored`。
4. **例外訊息從 `Caused by:` 漏出**：`RateLimitException(Throwable)` 的訊息就是 cause 的 `toString()`，含供應商回應內容；每一層都必須清空。→ Task 1 `keepsTypesAndFramesDropsMessages`、Task 2 `failureWritesErrorWithStatusCodeAndNoMessages`。
5. **改用 `doChat()` 後測試替身行為改變**：`ChatModel.chat` 會把 `defaultRequestParameters()` 與請求參數合併後再呼叫 `doChat`；若合併後遺失 `toolSpecifications` 或 `responseFormat`，既有斷言會失敗。2026-11-10 已實測合併後保留參數型別與 `responseFormat`。→ Task 4 Step 3 跑全部既有測試。

---

## Phase A：listener 與註冊

### Task 1: `MessageFreeThrowable`

**Files:**
- Create: `backend/src/main/java/com/example/copilot/aiassistant/infrastructure/llm/MessageFreeThrowable.java`
- Test: `backend/src/test/java/com/example/copilot/aiassistant/infrastructure/llm/MessageFreeThrowableTest.java`

**Interfaces:**
- Produces: `final class MessageFreeThrowable extends RuntimeException`（package-private）；`static Throwable of(Throwable source)`（`source` 為 null 回傳 null）。

- [ ] **Step 1: Write the failing tests**

```java
@Test
void keepsTypesAndFramesDropsMessages() {
    RateLimitException source = new RateLimitException(new HttpException(429, "SECRET-BODY"));
    Throwable copy = MessageFreeThrowable.of(source);

    StringWriter out = new StringWriter();
    copy.printStackTrace(new PrintWriter(out));
    assertThat(out.toString())
            .doesNotContain("SECRET-BODY")
            .startsWith("dev.langchain4j.exception.RateLimitException")
            .contains("Caused by: dev.langchain4j.exception.HttpException");
    assertThat(copy.getMessage()).isNull();
    assertThat(copy.getCause().getMessage()).isNull();
    assertThat(copy.getStackTrace()).isEqualTo(source.getStackTrace());
    assertThat(copy.getCause().getStackTrace()).isEqualTo(source.getCause().getStackTrace());
}

@Test
void cyclicCauseChainTerminates() {
    IllegalStateException a = new IllegalStateException("a");
    IllegalArgumentException b = new IllegalArgumentException("b", a);
    a.initCause(b);

    Throwable copy = MessageFreeThrowable.of(a);

    assertThat(copy.toString()).isEqualTo("java.lang.IllegalStateException");
    assertThat(copy.getCause().toString()).isEqualTo("java.lang.IllegalArgumentException");
    assertThat(copy.getCause().getCause()).isNull();
}

@Test
void nullGivesNull() {
    assertThat(MessageFreeThrowable.of(null)).isNull();
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && mvn -q test -Dtest=MessageFreeThrowableTest`
Expected: 編譯失敗，`MessageFreeThrowable` 不存在。

- [ ] **Step 3: Implement `MessageFreeThrowable`**

建構子 `private MessageFreeThrowable(String typeName, StackTraceElement[] frames, Throwable cause)` 呼叫 `super(null, cause, false, true)` 後 `setStackTrace(frames)`；`toString()` 回傳 `typeName`（原例外的 `getClass().getName()`），`getMessage()` 因此為 null。`of` 先以 identity set 沿 `getCause()` 收集鏈，遇到重複即停，再由最深一層往外逐層建立。

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && mvn -q test -Dtest=MessageFreeThrowableTest`
Expected: 3 tests PASS。

### Task 2: `LlmCallPurpose` 與 `OutboundLlmLogListener`

**Files:**
- Create: `backend/src/main/java/com/example/copilot/aiassistant/application/llm/LlmCallPurpose.java`
- Create: `backend/src/main/java/com/example/copilot/aiassistant/infrastructure/llm/OutboundLlmLogListener.java`
- Modify: `backend/src/test/java/com/example/copilot/testsupport/LogCapture.java`（加 `of(String)`）
- Test: `backend/src/test/java/com/example/copilot/aiassistant/infrastructure/llm/OutboundLlmLogListenerTest.java`

**Interfaces:**
- Consumes: `MessageFreeThrowable.of(Throwable)`（Task 1）；既有 `LlmErrorMapper.map(Throwable): ErrorCode`；`RequestContext.MDC_KEY`。
- Produces:
  - `public enum LlmCallPurpose { ROUTE, SUMMARY, STEP_ARGUMENTS, ANSWER, JUDGE }`，含 `public ChatRequestOptions options()`（以 `LlmCallPurpose.class` 為 key、enum 本身為值，用 `ChatRequestOptions.builder().addListenerAttribute(...)`）與 `public static Optional<LlmCallPurpose> from(Map<Object, Object> attributes)`。
  - `@Component public class OutboundLlmLogListener implements ChatModelListener`，建構子 `(LlmErrorMapper errorMapper, JsonMapper mapper)`（不讀設定檔：模型名稱取自 context 的 `chatRequest().modelName()`）；常數 `static final String LOGGER_NAME = "Outbound"`、`static final String LOG_MESSAGE_MDC_KEY = "logMessage"`。
  - `LogCapture.of(String loggerName)`。

- [ ] **Step 1: 擴充 `LogCapture`**

新增 `public static LogCapture of(String loggerName)`；私有建構子改收 logger 名稱，`of(Class)` 與 `of(Class, Level)` 改為傳 `type.getName()`。

- [ ] **Step 2: Write the failing tests**

測試共用：`listener = new OutboundLlmLogListener(new LlmErrorMapper(), JsonMapper.builder().build())`；`request` 為 `ChatRequest.builder().messages(UserMessage.from("q")).modelName("test-model").build()`。`attrs` 為 `new HashMap<>(Map.of(LlmCallPurpose.class, LlmCallPurpose.ROUTE))`，`onRequest`／`onResponse`／`onError` 共用同一份；context 以 `new ChatModelRequestContext(request, ModelProvider.OPEN_AI, attrs)` 等方式直接建立。`@BeforeEach` 設 `MDC.put("requestId", "r-1")`，`@AfterEach` `MDC.clear()`。以 `LogCapture.of("Outbound")` 擷取，`json(event)` 為 `mapper.readTree(event.getMessage().getFormattedMessage())`。

| 測試 | 斷言 |
| --- | --- |
| `successWritesOneInfoLineInTableDFormat` | 回應 `modelName("gpt-x")`、`TokenUsage(100, 20)`。恰一個事件、等級 INFO、`getThrown()` 為 null；`event`=`external_call_completed`、`result`=`SUCCESS`、`request.target_system`=`OpenAI`、`request.path`=`OpenAiChatModel.chat`、`response.response_type`=`ChatResponse`、`response.status_code`=`"200"`、`response.result_code`=`SUCCESS`；`duration_ms` 為字串且符合 `\d+`，等於 `execution_time`；`llm.request_id`=`r-1`、`llm.purpose`=`ROUTE`、`llm.model`=`gpt-x`、`llm.input_tokens`=`"100"`、`output_tokens`=`"20"`、`total_tokens`=`"120"`；所有葉節點 `isString()` |
| `failureWritesErrorWithStatusCodeAndNoMessages` | 錯誤 `new RateLimitException(new HttpException(429, "SECRET-BODY"))`。等級 ERROR；`event`=`external_call_failed`、`result`=`FAIL`、`status_code`=`"429"`、`response_type`=`""`、`error_type`=`RateLimitException`、`root_cause_type`=`HttpException`、`llm.error_code`=`AI_LLM_RATE_LIMITED`、`llm.model`=`test-model`；JSON 沒有 `error_message`、`root_cause_message`；`LogCapture.render(event)` 不含 `SECRET-BODY`、含 `Caused by: dev.langchain4j.exception.HttpException` |
| `timeoutWithoutHttpStatusReports500` | 錯誤 `new TimeoutException(new HttpTimeoutException("SECRET"))`：`status_code`=`"500"`、`llm.error_code`=`AI_LLM_TIMEOUT`；render 不含 `SECRET` |
| `rateLimitWithoutHttpCauseReports500` | 錯誤 `new RateLimitException("SECRET")`：`status_code`=`"500"`、`llm.error_code`=`AI_LLM_RATE_LIMITED`；render 不含 `SECRET` |
| `missingPurposeIsUnknown` | `attrs` 為空 map：`llm.purpose`=`UNKNOWN` |
| `missingRequestIdIsBlank` | 先 `MDC.remove("requestId")`：`llm.request_id`=`""` |
| `nullTokenUsageAndModelNameFallBack` | 回應無 `tokenUsage`、無 `modelName`：三個 token 欄位皆 `""`、`llm.model`=`test-model` |
| `missingStartTimeLeavesDurationBlank` | 不呼叫 `onRequest` 直接 `onResponse`：`duration_ms`=`""`、`execution_time`=`""` |
| `logMessageMdcIsRestored` | 事件的 `getContextData().getValue("logMessage")` 含 `event="external_call_completed"`、`operation="ROUTE"`、`status_code="200"`；之後 `MDC.get("logMessage")`：原本設為 `"outer"` 時仍是 `"outer"`，原本沒有時為 null |
| `internalFailureIsSwallowed` | `errorMapper` 用覆寫 `map` 丟 `new IllegalStateException("SECRET")` 的子類別：`onError` 不丟例外；`LogCapture.of(OutboundLlmLogListener.class)` 有一個 WARN，render 含 `IllegalStateException`、不含 `SECRET` |

- [ ] **Step 3: Run to verify they fail**

Run: `cd backend && mvn -q test -Dtest=OutboundLlmLogListenerTest`
Expected: 編譯失敗，`LlmCallPurpose`、`OutboundLlmLogListener` 不存在。

- [ ] **Step 4: Implement `LlmCallPurpose` 與 `OutboundLlmLogListener`**

- `onRequest`：在 attributes 放私有 key `START_NANOS` = `System.nanoTime()`。
- `onResponse`／`onError`：各自整段包 `try { ... } catch (RuntimeException e) { log.warn("outbound log failed error={}", e.getClass().getSimpleName()); }`，`log` 來自類別上的 `@Slf4j`。
- 內層 JSON 以 `ObjectNode` 組成後 `mapper.writeValueAsString`；欄位與取值照 spec §4 表格，數值一律 `String.valueOf`，null 一律 `""`。
- `llm.model`：成功取 `chatResponse().modelName()`，null 時取 `chatRequest().modelName()`；失敗取 `chatRequest().modelName()`。
- `status_code`：沿 cause 鏈（identity set 防循環）找第一個 `HttpException`，取 `statusCode()`，找不到為 `"500"`。
- `error_type`／`root_cause_type`：例外與最深 cause 的 `getClass().getSimpleName()`。
- `logMessage`：`key="value"` 以空白分隔，欄位依序 `event`、`result`、`target_system`、`operation`（=purpose）、`status_code`、`duration_ms`，失敗再加 `error_type`、`root_cause_type`；值中的 `\`、`"` 跳脫。先存舊值，`finally` 還原（舊值 null 則 `MDC.remove`）。
- 寫入：成功 `OUTBOUND.info(json)`；失敗 `OUTBOUND.error(json, MessageFreeThrowable.of(error))`。

- [ ] **Step 5: Run to verify they pass**

Run: `cd backend && mvn -q test -Dtest=OutboundLlmLogListenerTest+MessageFreeThrowableTest`
Expected: 全部 PASS。

### Task 3: 在 `LlmConfiguration` 註冊 listener

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/infrastructure/llm/LlmConfiguration.java:15-31`
- Modify: `backend/src/test/java/com/example/copilot/aiassistant/infrastructure/llm/OpenAiResponseFormatLiveTest.java:59`（另一個直接呼叫 `chatModel(props)` 的地方，補上 listener 參數）
- Test: `backend/src/test/java/com/example/copilot/aiassistant/infrastructure/llm/LlmConfigurationTest.java`

**Interfaces:**
- Consumes: `OutboundLlmLogListener`（Task 2）。
- Produces: `ChatModel chatModel(AiAssistantProperties props, OutboundLlmLogListener listener)`。

- [ ] **Step 1: Write the failing test**

把 `model(...)` 內建立 `AiAssistantProperties` 的部分抽成 `private static AiAssistantProperties props(Double temperature, String reasoningEffort, String baseUrl)`，另加 `private static OutboundLlmLogListener listener()`（`new OutboundLlmLogListener(new LlmErrorMapper(), JsonMapper.builder().build())`）；`model(...)` 改呼叫 `chatModel(props(...), listener())`。新增：

```java
@Test
void registersTheOutboundLogListener() {
    OutboundLlmLogListener listener = listener();
    ChatModel model = new LlmConfiguration().chatModel(props(null, null, null), listener);
    assertThat(model.listeners()).containsExactly(listener);
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -q test -Dtest=LlmConfigurationTest`
Expected: 編譯失敗，`chatModel` 只有一個參數。

- [ ] **Step 3: 加參數並 `.listeners(List.of(listener))`；`OpenAiResponseFormatLiveTest:59` 同樣傳入一個 listener**

- [ ] **Step 4: Run to verify it passes**

Run: `cd backend && mvn -q test -Dtest=LlmConfigurationTest+OutboundLlmLogListenerTest+MessageFreeThrowableTest+ArchitectureRulesTest`
Expected: 全部 PASS。

- [ ] **Step 5: Phase A 結束，交給使用者 commit**

先跑 `cd backend && mvn -q test`，全綠後提供：

```bash
git add backend/src/main/java/com/example/copilot/aiassistant/application/llm/LlmCallPurpose.java backend/src/main/java/com/example/copilot/aiassistant/infrastructure/llm backend/src/test/java/com/example/copilot/aiassistant/infrastructure/llm backend/src/test/java/com/example/copilot/testsupport/LogCapture.java docs/superpowers/specs/2026-11-10-llm-observability-design.md docs/superpowers/plans/2026-11-10-llm-observability.md
```

commit message：`feat(llm): log every model call as a platform Outbound log`（內文：OutboundLlmLogListener 以 Table D 格式寫入 Outbound logger；失敗附去掉訊息的 StackTrace 與 HTTP 狀態碼；LlmCallPurpose；LogCapture 可依名稱擷取）。**停下來等使用者確認已 commit。**

---

## Phase B：呼叫點、重複的 ERROR、文件

### Task 4: `ScriptedChatModel` 改走 `doChat()` 並記錄用途

**Files:**
- Modify: `backend/src/test/java/com/example/copilot/aiassistant/application/chat/ScriptedChatModel.java:47-56`

**Interfaces:**
- Consumes: `LlmCallPurpose.from(Map<Object, Object>)`（Task 2）。
- Produces: `public List<LlmCallPurpose> purposes()`：依呼叫順序記錄每次呼叫的用途，沒帶用途記 null。

- [ ] **Step 1: 把 `chat(ChatRequest)` 覆寫改為 `doChat(ChatRequest)`，內容不變**

- [ ] **Step 2: 覆寫 `listeners()`，回傳一個在 `onRequest` 把 `LlmCallPurpose.from(ctx.attributes()).orElse(null)` 加入 `purposes` 的 listener；加 `purposes()`**

- [ ] **Step 3: 跑全部既有測試**

Run: `cd backend && mvn -q test`
Expected: 全部 PASS，沒有修改任何既有斷言（Review Focus 5）。

### Task 5: 5 個呼叫點帶用途，並以規則鎖住

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/application/chat/InstructionRouter.java:73`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/application/chat/HistorySummarizer.java:67`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/application/chat/StepArgumentExtractor.java:78`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/application/chat/ChatOrchestrator.java:449`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/application/evaluation/EvalJudge.java:68`
- Test: `InstructionRouterTest`、`HistorySummarizerTest`、`StepArgumentExtractorTest`、`ChatOrchestratorTest`、`EvalJudgeTest`（皆在 `backend/src/test/java/com/example/copilot/aiassistant/application/**`）、`backend/src/test/java/com/example/copilot/aiassistant/ArchitectureRulesTest.java`

**Interfaces:**
- Consumes: `LlmCallPurpose.X.options()`（Task 2）、`ScriptedChatModel.purposes()`（Task 4）。

- [ ] **Step 1: Write the failing tests**

各測試類別新增一個測試，以該類別既有的成功情境呼叫一次，然後：

| 測試類別 | 新測試 | 斷言 |
| --- | --- | --- |
| `InstructionRouterTest` | `routeCallCarriesRoutePurpose` | `model.purposes()` `containsExactly(ROUTE)` |
| `HistorySummarizerTest` | `summaryCallCarriesSummaryPurpose` | `containsExactly(SUMMARY)` |
| `StepArgumentExtractorTest` | `extractionCallCarriesStepArgumentsPurpose` | `containsExactly(STEP_ARGUMENTS)` |
| `EvalJudgeTest` | `judgeCallCarriesJudgePurpose` | `containsExactly(JUDGE)` |
| `ChatOrchestratorTest` | `answerRoundsCarryAnswerPurpose` | `contains(ANSWER)` 且 `doesNotContainNull()` |

`ArchitectureRulesTest` 新增 `applicationModelCallsCarryAPurpose`：讀 `application` 下每個 `.java`，對每個 `chatModel.chat(` 出現位置取到其後第一個 `;` 為止的片段，收集不含 `LlmCallPurpose.` 的片段（前綴相對路徑）；斷言找到的呼叫數 `isPositive()`（防止規則因改名而失效），違規清單 `isEmpty()`。

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && mvn -q test -Dtest=InstructionRouterTest+HistorySummarizerTest+StepArgumentExtractorTest+EvalJudgeTest+ChatOrchestratorTest+ArchitectureRulesTest`
Expected: 5 個新測試 FAIL（用途為 null），`applicationModelCallsCarryAPurpose` 列出 5 個違規。

- [ ] **Step 3: 5 個呼叫點改為 `chatModel.chat(<原請求>, LlmCallPurpose.<用途>.options())`**

用途對照：InstructionRouter `ROUTE`、HistorySummarizer `SUMMARY`、StepArgumentExtractor `STEP_ARGUMENTS`、ChatOrchestrator `ANSWER`、EvalJudge `JUDGE`。

- [ ] **Step 4: Run to verify they pass**

Run: 同 Step 2。
Expected: 全部 PASS。

### Task 6: 移除重複的 `LLM call failed` ERROR

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/application/chat/ChatOrchestrator.java:450-455`
- Test: `backend/src/test/java/com/example/copilot/aiassistant/application/chat/ChatOrchestratorTest.java`

- [ ] **Step 1: Write the failing test**

`modelFailureLeavesTheErrorLogToTheListener`：`ScriptedChatModel` 第一次回答輪 `fail(new RateLimitException("x"))`，在 `LogCapture.of(ChatOrchestrator.class)` 內呼叫 `chat`，預期丟 `AssistantException`（code `AI_LLM_RATE_LIMITED`，行為不變）；擷取的事件中沒有 `Level.ERROR`。

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && mvn -q test -Dtest=ChatOrchestratorTest#modelFailureLeavesTheErrorLogToTheListener`
Expected: FAIL，有一個 `LLM call failed` ERROR。

- [ ] **Step 3: 刪除 `log.error("LLM call failed ...")` 與其上方註解，保留 `errorMapper.map(e)` 與 `throw new AssistantException(code)`**

- [ ] **Step 4: Run to verify it passes**

Run: `cd backend && mvn -q test -Dtest=ChatOrchestratorTest`
Expected: 全部 PASS。

### Task 7: 文件同步與手動驗證

**Files:**
- Modify: `docs/spec/01-architecture.md`（約 469、488、509、530 行）
- Modify: `docs/demo-checklist.md`
- Modify: `docs/superpowers/specs/2026-11-10-llm-observability-design.md`（版本與狀態）

- [ ] **Step 1: 01-architecture.md**

5.1 目錄結構：`application/llm/` 加 `LlmCallPurpose`；`infrastructure/llm/` 加 `OutboundLlmLogListener`、`MessageFreeThrowable`。分層圖與 Infrastructure 列的範例加 `OutboundLlmLogListener`。1.2 元件明細加一列：`OutboundLlmLogListener`：每次模型呼叫寫一行 `Outbound` log（設計見本 spec）。

- [ ] **Step 2: demo-checklist.md 加手動驗證步驟**

以「後端：偵錯（Java）」啟動，問一題後在 Debug Console 過濾 `external_call`：每次模型呼叫各一行，`llm.purpose`、`duration_ms`、token 有值；把 `OPENAI_API_KEY` 改成無效值再問一次：出現 ERROR、`status_code` 為 `"401"`、StackTrace 只有類型與呼叫位置。

- [ ] **Step 3: 手動驗證**

照 Step 2 實際操作一次，把結果（是否符合、遇到的差異）回報給使用者；差異若來自實作則修正，若來自 spec 則先問使用者。

- [ ] **Step 4: spec 改為 v0.2、狀態「已實作」，版本紀錄寫一行實作差異（沒有就寫「與設計一致」）**

- [ ] **Step 5: Phase B 結束，交給使用者 commit**

先跑 `cd backend && mvn -q test`，全綠後提供：

```bash
git add backend/src/main/java/com/example/copilot/aiassistant/application backend/src/test/java/com/example/copilot/aiassistant docs/spec/01-architecture.md docs/demo-checklist.md docs/superpowers/specs/2026-11-10-llm-observability-design.md
```

commit message：`feat(llm): tag every model call with its purpose`（內文：5 個呼叫點帶 LlmCallPurpose；ArchitectureRulesTest 鎖住未帶用途的呼叫；移除與 Outbound log 重複的 LLM call failed ERROR；ScriptedChatModel 改走 doChat；文件同步）。**停下來等使用者確認已 commit。**
