# 追蹤碼統一為 traceId Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 全系統只留平台的 `traceId` 當追蹤碼，移除 `requestId`，並讓工具執行緒、評測、排程的 log 都帶得到它。

**Architecture:** 註冊平台的 `TraceIdFilter` 取代自製的 `RequestIdFilter`；`RequestContext` 改讀 MDC 的 `traceId`。對外欄位、稽核欄位與索引、前端型別一律改名為 `traceId`。`ToolExecutor` 複製呼叫端 MDC 到工具執行緒，`EvalRunner` 與 `ScheduledPublishJob` 在進入點自行設定。

**Tech Stack:** Java 25、Spring Boot 4.1.0、SLF4J MDC（Log4j2）、MongoDB、JUnit 5 + AssertJ + Testcontainers；Vue 3.5 + TypeScript 6 + Vitest。

**Spec:** [docs/superpowers/specs/2026-11-10-trace-id-unification-design.md](../specs/2026-11-10-trace-id-unification-design.md)（v0.2）。執行者必須先讀規格。

## Global Constraints

- 不修改平台檔案：`backend/src/main/java/com/example/` 下除了 `copilot/` 以外的所有目錄（`filter`、`aspect`、`logging`、`shared`、`client`、`persistence`、`platform`、`constant`）。
- 不新增或修改 `log4j2.xml`（`backend/src/main/resources/dev/log4j2.xml` 是使用者自己在處理的檔案，不要碰）。
- 不引入 `InboundLogAspect`，不調整 `AssistantExceptionHandler` 記 log 的方式，只改它取追蹤碼的來源與文字。
- 不寫 Mongo 資料搬移程式。
- 不更新 `docs/superpowers/` 下既有的設計與計畫。
- 追蹤碼格式：`TraceIdFilter` 產生的是 32 碼小寫十六進位；回饋 API 接受 `^[0-9a-f]{16,32}$`。
- MDC 鍵名：`traceId`、`spanId`（與 `TraceIdFilter.MDC_TRACE_ID`、`MDC_SPAN_ID` 相同）。
- log 不得包含例外訊息、提問、回答或顧客資料（沿用現有做法，只記類別名稱與代碼）。
- `ArchitectureRulesTest` 必須全綠。
- **不要執行 `git commit`**。每個階段結束時由主控者把 `git add` 與 commit 訊息交給使用者，並等使用者確認已提交後才開始下一階段。子代理略過所有 commit 步驟。
- 驗證指令：後端單一測試 `cd backend && mvn -q test -Dtest=<Class>`；後端全部 `cd backend && mvn -q test`（需 Docker 啟動中，Testcontainers 會拉 `mongo:8`）；前端 `cd frontend && npm test`、型別檢查 `cd frontend && npx vue-tsc -b`。

## Review Focus

規格有暗示、但規格的測試清單沒有涵蓋的輸入。每一項的測試已加進負責的 Task。

1. **呼叫端帶了不合格式的 `X-B3-TraceId`**（例如大寫、含連字號、超過 64 字）：聊天仍應正常回答並把該值原樣回傳；稽核寫入時截到 64 字不失敗；之後用它評分得到 400 而不是 500。→ Task 3、Task 4
2. **兩個請求同時執行工具**：各自的工具執行緒必須看到自己的 `traceId`，不能互相覆蓋。→ Task 5
3. **工具逾時或丟例外**：呼叫端執行緒的 MDC 必須保持不變。→ Task 5
4. **評測途中失敗**（作答丟出未預期例外）：`runner.run` 返回後 MDC 仍要還原成進入前的值。→ Task 6
5. **未登入的請求**：401 回應也要有 `X-Trace-Id`，且 body 的 `traceId` 與它相同。→ Task 1、Task 3

另有一項無法以測試涵蓋、改由文件處理：舊的稽核集合若沒有刪除，`IX_ICRM_AI_AUDIT_4` 同名不同鍵會讓啟動失敗。→ Task 9 的 demo-checklist。

---

## 階段 A：後端追蹤碼來源與 log

### Task 1: 註冊平台的 TraceIdFilter

**Files:**
- Create: `backend/src/main/java/com/example/copilot/mockplatform/PlatformFilterConfiguration.java`
- Create: `backend/src/test/java/com/example/copilot/mockplatform/PlatformFilterConfigurationTest.java`

**Interfaces:**
- Produces: 每個 HTTP 請求執行期間 MDC 有 `traceId`、`spanId`；每個回應有 `X-Trace-Id` header。

- [ ] **Step 1: 寫失敗的測試**（繼承 `com.example.copilot.IntegrationTestBase`，用它的 `mockMvc`）

```java
@Test
void everyResponseCarriesAGeneratedTraceId() throws Exception {
    String id = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getHeader("X-Trace-Id");
    assertThat(id).matches("[0-9a-f]{32}");
}

@Test
void inboundB3TraceIdIsEchoed() throws Exception {
    mockMvc.perform(get("/actuator/health").header("X-B3-TraceId", "463ac35c9f6413ad48485a3953bb6124"))
            .andExpect(header().string("X-Trace-Id", "463ac35c9f6413ad48485a3953bb6124"));
}

@Test
void unauthenticatedResponseCarriesTraceId() throws Exception {
    mockMvc.perform(post("/api/ai-assistant/chat").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists("X-Trace-Id"));
}
```

第三個測試的請求寫法照 `AssistantExceptionHandlerTest.unauthenticatedResponseStillCarriesRequestId` 現有的未登入請求（含它對 CSRF 的處理）。若 `/actuator/health` 在測試環境需要登入，改用 `AssistantExceptionHandlerTest` 用的 `/test-support/conflict`（`post`，加 `.with(user("tester")).with(csrf())`）。

- [ ] **Step 2: 執行，確認失敗**

Run: `cd backend && mvn -q test -Dtest=PlatformFilterConfigurationTest`
Expected: FAIL，`X-Trace-Id` 為 null。

- [ ] **Step 3: 新增 `PlatformFilterConfiguration`**

```java
/** Registers the platform filters this app does not component-scan; delete after moving onto the platform. */
@Configuration
@Import(TraceIdFilter.class)
public class PlatformFilterConfiguration {
}
```

- [ ] **Step 4: 執行，確認通過**

Run: `cd backend && mvn -q test -Dtest=PlatformFilterConfigurationTest+ArchitectureRulesTest`
Expected: PASS。

### Task 2: RequestContext 改讀 traceId，刪除 RequestIdFilter，log 文字改名

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/application/support/RequestContext.java`
- Delete: `backend/src/main/java/com/example/copilot/aiassistant/interfaces/rest/RequestIdFilter.java`
- Modify（取值與 log 文字）: `interfaces/rest/AssistantExceptionHandler.java`、`interfaces/rest/ApiError.java`、`application/chat/ChatOrchestrator.java`、`application/chat/InstructionRouter.java`、`application/chat/HistorySummarizer.java`、`application/chat/StepArgumentExtractor.java`、`application/chat/WorkflowRunner.java`、`application/audit/AuditLogger.java`（只改兩行 log 文字）、`application/feedback/FeedbackService.java`（只改 log 文字）、`domain/tool/ToolExecutor.java`、`infrastructure/llm/OutboundLlmLogListener.java`（以上皆在 `backend/src/main/java/com/example/copilot/aiassistant/` 下）
- Create: `backend/src/test/java/com/example/copilot/aiassistant/application/support/RequestContextTest.java`
- Modify（測試）: `AssistantExceptionHandlerTest`、`ChatOrchestratorTest`、`HistorySummarizerTest`、`InstructionRouterTest`、`StepArgumentExtractorTest`、`EvalRunnerTest`、`FeedbackControllerTest`、`OutboundLlmLogListenerTest`

**Interfaces:**
- Consumes: Task 1 的 `TraceIdFilter` 註冊。
- Produces:

```java
public final class RequestContext {
    public static final String MDC_KEY = "traceId";
    public static final String SPAN_KEY = "spanId";
    public static String currentTraceId();   // MDC.get(MDC_KEY)
    public static String newId();            // 32 碼小寫十六進位：UUID.randomUUID() 去掉連字號
}
```

`currentRequestId()` 不保留。這個 Task 結束時，`ApiError`、`ChatResult`、稽核的**欄位名稱仍是 `requestId`**（值已是 `traceId`），改名在 Task 3、4。

- [ ] **Step 1: 寫失敗的測試** `RequestContextTest`（純單元測試）

```java
@Test
void keysMatchThePlatformFilter() {
    assertThat(RequestContext.MDC_KEY).isEqualTo(TraceIdFilter.MDC_TRACE_ID);
    assertThat(RequestContext.SPAN_KEY).isEqualTo(TraceIdFilter.MDC_SPAN_ID);
}

@Test
void newIdIs32LowerHexAndUnique() {
    assertThat(RequestContext.newId()).matches("[0-9a-f]{32}").isNotEqualTo(RequestContext.newId());
}

@Test
void currentTraceIdReadsTheMdc() {
    MDC.put("traceId", "abc");
    try {
        assertThat(RequestContext.currentTraceId()).isEqualTo("abc");
    } finally {
        MDC.remove("traceId");
    }
}
```

- [ ] **Step 2: 改既有測試的斷言（先改測試）**

| 測試 | 變更 |
| --- | --- |
| `AssistantExceptionHandlerTest.mapsAssistantExceptionToApiError` | header 改讀 `X-Trace-Id`，格式改 `[0-9a-f]{32}`；body 仍斷言 `"requestId":"<headerId>"`（Task 3 再改名）。另加 `assertThat(result.getResponse().getHeader("X-Request-Id")).isNull()` |
| `AssistantExceptionHandlerTest.unauthenticatedResponseStillCarriesRequestId` | 改名 `unauthenticatedResponseStillCarriesTraceId`，斷言 `X-Trace-Id` 存在 |
| `ChatOrchestratorTest` | `RequestIdFilter.MDC_KEY` → `RequestContext.MDC_KEY`；常數 `REQUEST_ID` 改名 `TRACE_ID`；三處 log 斷言 `"requestId=" +` → `"traceId=" +` |
| `HistorySummarizerTest`、`InstructionRouterTest`、`StepArgumentExtractorTest` | log 斷言的 `requestId=` → `traceId=` |
| `EvalRunnerTest` | `RequestIdFilter.MDC_KEY` → `RequestContext.MDC_KEY`（行為斷言留到 Task 6） |
| `FeedbackControllerTest.eachChangeIsLoggedWithTheAnsweredRequestId` | 改名 `eachChangeIsLoggedWithTheAnsweredTraceId`；header 改讀 `"X-Trace-Id"`；期望文字改為 `"feedback set traceId=" + httpTraceId + " answerTraceId=a1b2c3e1 userId=ICRM00028 rating=UP"`，第二個斷言同樣把 `answerRequestId` 改為 `answerTraceId` |
| `OutboundLlmLogListenerTest` | `MDC.put/remove("requestId"...)` → `"traceId"`；`/llm/request_id` → `/llm/trace_id`；`missingRequestIdIsBlank` 改名 `missingTraceIdIsBlank`；成功案例加 `assertThat(json.at("/llm").has("request_id")).isFalse()` |

- [ ] **Step 3: 執行，確認失敗**

Run: `cd backend && mvn -q test -Dtest=RequestContextTest`
Expected: 編譯失敗（`SPAN_KEY`、`newId`、`currentTraceId` 不存在）。

- [ ] **Step 4: 實作**

- `RequestContext` 照 Interfaces。
- 刪除 `RequestIdFilter.java`；`AssistantExceptionHandler` 與 `ApiError` 的 `RequestIdFilter.current()` 改為 `RequestContext.currentTraceId()`。
- 所有 `RequestContext.currentRequestId()` 改為 `currentTraceId()`。
- 所有 log 文字的 `requestId={}` 改為 `traceId={}`。`FeedbackService` 的那一行改為 `"feedback set traceId={} answerTraceId={} userId={} rating={}"`。
- `ChatOrchestrator` 內 `Turn` 的欄位 `requestId` 改名 `traceId`（傳給 `ChatResult`、`AuditEntry` 的建構子參數位置不變）。
- `ToolExecutor`：`REQUEST_ID_KEY = "requestId"` 改為 `TRACE_ID_KEY = "traceId"`，log 文字 `requestId={}` → `traceId={}`。
- `OutboundLlmLogListener.llm(...)`：`llm.put("request_id", ...)` 改為 `llm.put("trace_id", ...)`，區域變數改名。

- [ ] **Step 5: 執行，確認通過**

Run: `cd backend && mvn -q test`
Expected: PASS（全部）。

- [ ] **Step 6: 階段 A 完成，交給使用者提交**

```bash
git add backend/src/main/java/com/example/filter backend/src/main/java/com/example/copilot backend/src/test/java/com/example/copilot
git commit -m "refactor(trace): use the platform traceId as the only tracking id in MDC and logs"
```

`backend/src/main/java/com/example/filter`（平台的 `TraceIdFilter`）目前未進版控；從這個階段起程式依賴它，所以要一起提交。其他未進版控的平台目錄與 `resources/dev/log4j2.xml` 不在這次提交內。停下來等使用者確認。

---

## 階段 B：後端對外介面與稽核

### Task 3: 回應欄位與回饋 API 改名

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/domain/chat/ChatResult.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/interfaces/rest/chat/dto/ChatResponseBody.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/interfaces/rest/ApiError.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/interfaces/rest/feedback/FeedbackController.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/application/feedback/FeedbackService.java`（參數改名）
- Modify（測試）: `ChatControllerTest`、`ChatCitationFlowTest`、`ChatOrchestratorTest`、`AssistantExceptionHandlerTest`、`MockAuthControllerTest`、`FeedbackControllerTest`

**Interfaces:**
- Produces:
  - `ChatResult(String traceId, ...)`、`ChatResponseBody(String traceId, ...)`、`ApiError(String code, String message, String traceId, List<Object> details)`：只改第一個（或第三個）元件的名稱，其餘元件不變。JSON 欄位因此變成 `traceId`。
  - `PUT /api/ai-assistant/chat/{traceId}/feedback`，`@PathVariable @Pattern(regexp = "^[0-9a-f]{16,32}$") String traceId`。
  - `FeedbackService.rate(String traceId, String userId, FeedbackRating rating)`。

- [ ] **Step 1: 改測試**

| 測試 | 變更 |
| --- | --- |
| `ChatControllerTest` | `$.requestId` → `$.traceId`；鍵清單的 `"requestId"` → `"traceId"`；`unknownCustomerIs404WithSameBodyApartFromRequestId` 改名 `...ApartFromTraceId`，`withoutRequestId` 改名 `withoutTraceId`，正規式改 `"\"traceId\":\"[^\"]*\""` |
| `ChatCitationFlowTest` | 鍵清單與 `JsonPath.read(body, "$.traceId")`（查稽核的欄位名稱留到 Task 4） |
| `ChatOrchestratorTest` | `result.requestId()` → `result.traceId()` |
| `AssistantExceptionHandlerTest` | body 斷言改為 `"\"traceId\":\"" + headerId + "\""` |
| `MockAuthControllerTest` | `$.requestId` → `$.traceId` |
| `FeedbackControllerTest` | 所有 8 碼的回答 id（`a1b2c3d5`、`a1b2c3e1`、`a1b2c3d6`、`a1b2c3db` 等）改為 32 碼：在原值後面補 `"0".repeat(24)`，以一個 helper `private static String id(String s) { return s + "0".repeat(24); }` 統一；log 斷言的 `answerTraceId=` 跟著用完整 32 碼 |

- [ ] **Step 2: 新增測試**

`ChatControllerTest`（沿用該檔既有的成功提問寫法，以下只列斷言）：

```java
@Test
void traceIdInBodyEqualsTheResponseHeader() throws Exception {
    MvcResult r = /* 既有的成功提問 */;
    String header = r.getResponse().getHeader("X-Trace-Id");
    assertThat(header).matches("[0-9a-f]{32}");
    assertThat(JsonPath.<String>read(r.getResponse().getContentAsString(), "$.traceId")).isEqualTo(header);
    assertThat(r.getResponse().getHeader("X-Request-Id")).isNull();
}

@Test
void malformedInboundTraceIdStillAnswersAndIsEchoed() throws Exception {
    // Review Focus 1：加 .header("X-B3-TraceId", "ABC-not-hex")
    // 斷言 status 200，$.traceId == "ABC-not-hex"
}
```

`AssistantExceptionHandlerTest`：

```java
@Test
void unauthenticatedBodyCarriesTheHeaderTraceId() throws Exception {
    // Review Focus 5：未登入呼叫 /api/ai-assistant/chat
    // 斷言 401、body 的 $.traceId 等於 X-Trace-Id header
}
```

`FeedbackControllerTest`：

```java
@ParameterizedTest
@ValueSource(strings = {"463ac35c9f6413ad", "463ac35c9f6413ad48485a3953bb6124"})
void acceptsSixteenAndThirtyTwoHex(String traceId) throws Exception {
    // 先插入該 traceId、該使用者、outcome ANSWERED 的稽核文件（此時欄位名稱仍是 requestId，Task 4 改）
    // rate(...) 斷言 204
}

@ParameterizedTest
@ValueSource(strings = {"a1b2c3d4", "463ac35c9f6413ad48485a3953bb61240", "463AC35C9F6413AD",
        "463ac35c9f6413ag", "ABC-not-hex"})
void rejectsAnyOtherFormat(String traceId) throws Exception {
    // rate(...) 斷言 400，$.code == "AI_INPUT_INVALID"
}
```

- [ ] **Step 3: 執行，確認失敗**

Run: `cd backend && mvn -q test -Dtest=FeedbackControllerTest+ChatControllerTest+AssistantExceptionHandlerTest`
Expected: 編譯失敗或斷言失敗（`traceId` 欄位不存在、32 碼被 `{8}` 的格式拒絕）。

- [ ] **Step 4: 實作**（照 Interfaces 改名；`ChatResponseBody.from` 改用 `r.traceId()`）

- [ ] **Step 5: 執行，確認通過**

Run: `cd backend && mvn -q test -Dtest=FeedbackControllerTest+ChatControllerTest+ChatCitationFlowTest+ChatOrchestratorTest+AssistantExceptionHandlerTest+MockAuthControllerTest+ArchitectureRulesTest`
Expected: PASS。

### Task 4: 稽核欄位、validator 與索引改名

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/domain/audit/AuditEntry.java`、`domain/audit/AuditRecord.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/application/audit/AuditLogger.java`（`e.requestId()` → `e.traceId()`）
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/domain/repository/AuditRepository.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/infrastructure/mongo/AuditRecordMapper.java`、`AuditRepositoryImpl.java`、`MongoSchemaInitializer.java`
- Modify: `backend/src/main/resources/mongo/audit-validator.json`
- Modify（測試）: `AuditLoggerTest`、`MongoSchemaTest`、`FeedbackStatsQueryRepositoryImplTest`、`FeedbackControllerTest`、`ChatCitationFlowTest`、`ChatOrchestratorTest`

**Interfaces:**
- Produces:
  - `AuditEntry(String traceId, ...)`、`AuditRecord(String id, String traceId, ...)`：只改該元件名稱。
  - `AuditRepository.setFeedback(String traceId, String userId, FeedbackRating rating, Instant at)`。
  - Mongo 文件欄位 `traceId`（string，maxLength 64，必填）；索引 `IX_ICRM_AI_AUDIT_4` 的鍵為 `{ traceId: 1 }`，名稱不變。

- [ ] **Step 1: 改測試**

所有測試裡的 `new Document("requestId", ...)`、`Criteria.where("requestId")`、`DOC_KEYS` 的 `"requestId"`、`audit.requestId()` 一律改為 `traceId`；helper 的參數名稱跟著改。`MongoSchemaTest` 第 228 行的索引斷言改為 `containsExactly("traceId")`。

- [ ] **Step 2: 新增測試**

`AuditLoggerTest`：

```java
@Test
void documentHasTraceIdAndNoRequestId() {
    logger.record(entry("0af7651916cd43dd8448eb211c80319c", "q", "a", List.of()));
    Document doc = find("0af7651916cd43dd8448eb211c80319c");
    assertThat(doc.getString("traceId")).isEqualTo("0af7651916cd43dd8448eb211c80319c");
    assertThat(doc.containsKey("requestId")).isFalse();
}

@Test
void overlongTraceIdIsClippedTo64AndStillWritten() {
    // Review Focus 1
    logger.record(entry("t".repeat(200), "q", "a", List.of()));
    assertThat(find("t".repeat(64))).isNotNull();
}
```

`MongoSchemaTest`：

```java
@Test
void auditWithoutTraceIdIsNotAccepted() {
    // 沿用該檔既有「缺必填欄位」案例的寫法與期望（稽核 validator 的 validationAction 以該檔現有斷言為準）
    // 文件：auditDoc(...) 移除 traceId、改放 requestId
}
```

若該檔沒有「缺必填欄位」的既有案例可沿用，改為斷言 `collMod` 後 `listCollections` 取得的 validator `$jsonSchema.required` 含 `traceId`、不含 `requestId`。

- [ ] **Step 3: 執行，確認失敗**

Run: `cd backend && mvn -q test -Dtest=AuditLoggerTest+MongoSchemaTest`
Expected: 編譯失敗（`traceId()` 不存在）。

- [ ] **Step 4: 實作**

照 Interfaces 改名。`audit-validator.json` 的 `required` 與 `properties` 兩處 `requestId` → `traceId`。`MongoSchemaInitializer.createAuditIndexes` 的 `.on("requestId", ...)` → `.on("traceId", ...)`，不加任何搬移或刪舊索引的程式。

- [ ] **Step 5: 執行，確認通過**

Run: `cd backend && mvn -q test`
Expected: PASS（全部）。測試用的 Mongo 是每次新起的容器，不會遇到舊索引。

- [ ] **Step 6: 階段 B 完成，交給使用者提交**

```bash
git add backend/src/main/java/com/example/copilot backend/src/main/resources/mongo backend/src/test
git commit -m "refactor(trace)!: rename requestId to traceId in the API, feedback path and audit documents

BREAKING: the audit collection must be dropped before starting this version."
```

提醒使用者：本機啟動前先刪除集合 `COLL_ICRM_AI_ASSISTANT_AUDIT`（`db.COLL_ICRM_AI_ASSISTANT_AUDIT.drop()`）。停下來等使用者確認。

---

## 階段 C：MDC 傳遞

### Task 5: 工具執行緒帶著呼叫端的 MDC

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/domain/tool/ToolExecutor.java`（`run` 方法，約第 72–90 行）
- Test: `backend/src/test/java/com/example/copilot/aiassistant/domain/tool/ToolExecutorTest.java`

**Interfaces:**
- Produces: 工具的 `execute` 執行期間，`MDC.getCopyOfContextMap()` 等於呼叫 `ToolExecutor.execute` 當下呼叫端執行緒的內容。公開簽章不變。

- [ ] **Step 1: 寫失敗的測試**（用該檔既有的 `run(Supplier<ToolResult>)` 等 helper）

```java
@AfterEach
void clearMdc() {
    MDC.clear();
}

@Test
void toolThreadSeesTheCallersMdc() {
    MDC.put("traceId", "trace-1");
    MDC.put("spanId", "span-1");
    AtomicReference<Map<String, String>> seen = new AtomicReference<>();
    run(() -> {
        seen.set(MDC.getCopyOfContextMap());
        return ToolResult.success("ok", null);
    });
    assertThat(seen.get()).containsEntry("traceId", "trace-1").containsEntry("spanId", "span-1");
}

@Test
void callerWithoutMdcDoesNotFail() {
    AtomicReference<String> seen = new AtomicReference<>("unset");
    ToolOutcome outcome = run(() -> {
        seen.set(MDC.get("traceId"));
        return ToolResult.success("ok", null);
    });
    assertThat(outcome.result().status()).isEqualTo(ToolStatus.SUCCESS);
    assertThat(seen.get()).isNull();
}

@Test
void callersMdcIsUnchangedAfterTimeoutAndFailure() {
    // Review Focus 3
    MDC.put("traceId", "trace-1");
    run(Duration.ofMillis(100), Duration.ofSeconds(5), ToolExecutorTest::sleepThenSucceed);
    run(() -> { throw new RuntimeException("boom"); });
    assertThat(MDC.getCopyOfContextMap()).containsOnly(Map.entry("traceId", "trace-1"));
}

@Test
void concurrentCallsEachSeeTheirOwnTraceId() throws Exception {
    // Review Focus 2：兩個呼叫端執行緒各自 MDC.put("traceId", "a" / "b") 後同時呼叫同一個 ToolExecutor；
    // 工具內以 CountDownLatch 等兩邊都進入後才讀 MDC.get("traceId") 並回傳。
    // 斷言：呼叫端 a 的工具讀到 "a"，呼叫端 b 的讀到 "b"。
}
```

- [ ] **Step 2: 執行，確認失敗**

Run: `cd backend && mvn -q test -Dtest=ToolExecutorTest`
Expected: `toolThreadSeesTheCallersMdc` FAIL（`seen` 為 null 或空）。

- [ ] **Step 3: 實作**

在 `run` 內、`executor.submit` 之前於呼叫端執行緒取 `Map<String, String> mdc = MDC.getCopyOfContextMap();`。提交的任務：`mdc != null` 時先 `MDC.setContextMap(mdc)`，`try` 執行 `tool.execute(args, context)`，`finally` 呼叫 `MDC.clear()`。

- [ ] **Step 4: 執行，確認通過**

Run: `cd backend && mvn -q test -Dtest=ToolExecutorTest+ArchitectureRulesTest`
Expected: PASS。

### Task 6: 評測以 run id 當 traceId

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/application/evaluation/EvalRunner.java`（`run` 約第 80–106 行；`side` 約第 172–185 行；`restore` 約第 220–226 行）
- Test: `backend/src/test/java/com/example/copilot/aiassistant/application/evaluation/EvalRunnerTest.java`（`logsCountersOnlyAndSetsRequestId` 約第 383 行）

**Interfaces:**
- Consumes: `RequestContext.MDC_KEY`、`SPAN_KEY`、`newId()`（Task 2）。
- Produces: `EvalRunner.run(run, snapshot)` 整段執行期間 MDC `traceId == run.id()`、`spanId` 為 32 碼十六進位；返回（含丟例外）後兩個鍵都還原為進入前的值，進入前沒有值就移除。

- [ ] **Step 1: 改測試**

`logsCountersOnlyAndSetsRequestId` 改名 `logsCountersOnlyAndRunsUnderTheRunIdAsTraceId`：

- `during` 的期望由 `"e" + run.id().substring(...)` 改為 `run.id()`；`duringJudge` 仍等於 `during`。
- 作答時另擷取 `MDC.get(RequestContext.SPAN_KEY)`，斷言 `matches("[0-9a-f]{32}")`。
- 呼叫前除了 `traceId = "outer"` 再 `MDC.put(RequestContext.SPAN_KEY, "outer-span")`；`finally` 斷言兩者都還原，然後移除。

- [ ] **Step 2: 新增測試**

```java
@Test
void summaryLogLinesAreWrittenUnderTheRunTraceId() {
    // 以 LogCapture.of(EvalRunner.class, Level.INFO) 擷取，
    // 斷言 "eval run finished" 那個 LogEvent 的 getContextData().getValue("traceId") 等於 run.id()
}

@Test
void mdcIsRestoredWhenTheRunFailsMidway() {
    // Review Focus 4：scripted.reply(request -> { throw new IllegalStateException("boom"); })
    // 呼叫前 MDC 沒有 traceId／spanId；runner.run(...) 返回後斷言兩個鍵都是 null
}
```

- [ ] **Step 3: 執行，確認失敗**

Run: `cd backend && mvn -q test -Dtest=EvalRunnerTest`
Expected: FAIL（`during` 是 `e` 開頭的 8 碼）。

- [ ] **Step 4: 實作**

- `run(...)`：方法開頭記下兩個鍵原本的值，`MDC.put(MDC_KEY, run.id())`、`MDC.put(SPAN_KEY, RequestContext.newId())`；把既有的 `try/catch/finally` 整段包在外層 `try { ... } finally { 還原兩個鍵 }` 裡，讓 `eval run finished` 那行在還原之前寫出。
- `side(...)`：移除原本對 MDC 的 `put` 與 `restore(previous)`，只留作答與評分。
- `restore` 改為 `private static void restore(String key, String previous)`（null 則 `MDC.remove(key)`）。

- [ ] **Step 5: 執行，確認通過**

Run: `cd backend && mvn -q test -Dtest="EvalRunner*Test"`
Expected: PASS。

### Task 7: 排程每次執行產生 traceId

**Files:**
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/infrastructure/scheduler/ScheduledPublishJob.java`
- Test: `backend/src/test/java/com/example/copilot/aiassistant/infrastructure/scheduler/ScheduledPublishJobTest.java`

**Interfaces:**
- Consumes: `RequestContext.MDC_KEY`、`SPAN_KEY`、`newId()`。
- Produces: `ScheduledPublishJob.run()` 期間 MDC 有 32 碼的 `traceId` 與 `spanId`；返回後兩個鍵都不存在。

- [ ] **Step 1: 寫失敗的測試**（比照該檔的 `runSwallowsFailures`，用 Mockito mock 的 `ConfigCommandService`）

```java
@Test
void runsUnderAFreshTraceIdAndClearsItAfterwards() {
    ConfigCommandService spy = mock(ConfigCommandService.class);
    List<String> seen = new ArrayList<>();
    doAnswer(inv -> {
        seen.add(MDC.get("traceId"));
        seen.add(MDC.get("spanId"));
        return 0;
    }).when(spy).publishDue();
    ScheduledPublishJob job = new ScheduledPublishJob(spy);

    job.run();
    job.run();

    assertThat(seen).hasSize(4).allSatisfy(id -> assertThat(id).matches("[0-9a-f]{32}"));
    assertThat(seen.get(0)).isNotEqualTo(seen.get(2));
    assertThat(MDC.get("traceId")).isNull();
    assertThat(MDC.get("spanId")).isNull();
}

@Test
void traceIdIsClearedWhenPublishDueThrows() {
    ConfigCommandService broken = mock(ConfigCommandService.class);
    doThrow(new IllegalStateException("secret")).when(broken).publishDue();
    new ScheduledPublishJob(broken).run();
    assertThat(MDC.get("traceId")).isNull();
    assertThat(MDC.get("spanId")).isNull();
}
```

- [ ] **Step 2: 執行，確認失敗**

Run: `cd backend && mvn -q test -Dtest=ScheduledPublishJobTest`
Expected: `runsUnderAFreshTraceIdAndClearsItAfterwards` FAIL（`seen` 內為 null）。

- [ ] **Step 3: 實作**

`run()` 開頭 `MDC.put` 兩個鍵（各呼叫一次 `RequestContext.newId()`），既有的 `try/catch` 加 `finally` 以 `MDC.remove` 移除這兩個鍵（不用 `MDC.clear()`）。

- [ ] **Step 4: 執行，確認通過**

Run: `cd backend && mvn -q test`
Expected: PASS（全部）。

- [ ] **Step 5: 階段 C 完成，交給使用者提交**

```bash
git add backend/src/main/java/com/example/copilot backend/src/test
git commit -m "feat(trace): carry the traceId into tool threads, evaluation runs and the scheduled job"
```

停下來等使用者確認。

---

## 階段 D：前端

### Task 8: 前端欄位改名

**Files:**
- Modify: `frontend/src/features/ai-assistant/types.ts`（第 39、74、95 行的 `requestId`）
- Modify: `frontend/src/features/ai-assistant/stores/useAssistantStore.ts`（第 100、122、169、183 行）
- Modify: `frontend/src/features/ai-assistant/api/aiAssistantApi.ts`（`putFeedback` 參數）
- Modify: `frontend/src/features/ai-assistant/components/AssistantMessage.vue`（第 87 行）、`ErrorMessage.vue`（第 11 行）
- Modify: `frontend/src/platform-mock/apiClient.ts`（第 19、22、27、42、71 行）
- Test: `frontend/src/platform-mock/__tests__/apiClient.spec.ts`、`features/ai-assistant/stores/__tests__/useAssistantStore.spec.ts`、`features/ai-assistant/components/__tests__/AssistantMessage.spec.ts`、`AssistantPanel.spec.ts`

**Interfaces:**
- Consumes: 後端回應的 `traceId` 欄位（Task 3）、`PUT /ai-assistant/chat/{traceId}/feedback`。
- Produces: 型別欄位 `traceId`（聊天回應為必填 `string`，訊息與錯誤物件為選填）；`ApiError.traceId`；`aiAssistantApi.putFeedback(traceId: string, rating: FeedbackRating | null)`。顯示文字維持 `` `${e.code} · 追蹤碼 ${e.traceId}` ``。

- [ ] **Step 1: 改測試**

四個 spec 檔的 `requestId` 一律改為 `traceId`（屬性、測試名稱、fixture）。fixture 的值改用 32 碼：`'r1'` → `'0af7651916cd43dd8448eb211c80319c'`，`'a1b2c3d4'` → `'463ac35c9f6413ad48485a3953bb6124'`。`apiClient.spec.ts` 另加：

```ts
it('ignores a legacy requestId field', async () => {
  // 回應 body 為 { code: 'AI_X', message: 'm', requestId: 'old' }
  // 斷言拋出的 ApiError.traceId 為 undefined
})
```

若既有測試沒有涵蓋 `ErrorMessage.vue` 的追蹤碼文字，在 `AssistantMessage.spec.ts` 加一個案例：錯誤訊息帶 `traceId` 時畫面含 `追蹤碼 463ac35c9f6413ad48485a3953bb6124`。

- [ ] **Step 2: 執行，確認失敗**

Run: `cd frontend && npm test`
Expected: FAIL（`traceId` 為 undefined）。

- [ ] **Step 3: 實作**（照 Files 逐一改名；`apiClient.ts` 第 71 行改讀 `data.traceId`）

- [ ] **Step 4: 執行，確認通過**

Run: `cd frontend && npm test && npx vue-tsc -b`
Expected: 測試全過，型別檢查沒有錯誤。

- [ ] **Step 5: 階段 D 完成，交給使用者提交**

```bash
git add frontend/src
git commit -m "refactor(trace)!: frontend reads traceId instead of requestId"
```

停下來等使用者確認。

---

## 階段 E：文件與殘留檢查

### Task 9: 更新現行規格並確認沒有殘留

**Files:**
- Modify: `docs/spec/01-architecture.md`（第 470、481 行附近）
- Modify: `docs/spec/02-rest-api.md`（第 14、47、188、225、343–370、768、1123 行附近）
- Modify: `docs/spec/04-mongodb-schema.md`（第 267、293、301、315、323、369、379–381、446 行附近）
- Modify: `docs/spec/05-error-codes.md`（第 11、16、95、156、160、162 行附近）
- Modify: `docs/spec/06-conversation-scenarios.md`（第 528、532 行）
- Modify: `docs/demo-checklist.md`、`docs/questions-for-manager.md`（第 188 行）、`docs/platform-setup-requests.md`（第 80 行）、`docs/ideas-backlog.md`（第 10 行）
- Modify: `docs/superpowers/specs/2026-11-10-llm-observability-design.md`（只加註，不改歷史內容）

- [ ] **Step 1: 改 `docs/spec/`**

`requestId` 一律改為 `traceId`，範例值 `a1b2c3d4` 改為 `463ac35c9f6413ad48485a3953bb6124`。除了改名，以下句子的**意思**也要改：

| 位置 | 改成 |
| --- | --- |
| 01 第 470 行 | `RequestContext（MDC traceId）` |
| 01 第 481 行 | 移除 `RequestIdFilter`；在 mockplatform 的清單加 `PlatformFilterConfiguration（註冊平台 TraceIdFilter）` |
| 02 第 47 行 | 每個回應都有 `X-Trace-Id` header（平台 `TraceIdFilter`）；錯誤回應的 body 也帶 `traceId`。呼叫端帶 `X-B3-TraceId` 時沿用該值 |
| 02 第 348 行 | 格式 `^[0-9a-f]{16,32}$` |
| 02 第 362 行、04 第 315 行 | 刪掉「只有 8 碼十六進位，所以」的理由，改為：`traceId` 可能由呼叫端帶入而重複，所以一定同時比對 `userId`；同一人有兩筆相同 `traceId` 時更新 `createdAt` 最新的一筆 |
| 02 第 367 行附近 | 補一句：呼叫端帶入的 `X-B3-TraceId` 不符合格式時，該則回答無法評分 |
| 04 第 267 行 | `traceId`：平台追蹤碼，對應一次提問；最長 64 字，超過截斷 |
| 05 第 160 行 | `feedback set traceId={這次 HTTP 請求} answerTraceId={被評分的回答} ...` |
| 05 第 162 行 | 評測執行期間 MDC 的 `traceId` 為評測紀錄 id（24 碼），可由長度與一般請求（32 碼）區分 |

- [ ] **Step 2: 改其他文件**

- `demo-checklist.md`：在啟動後端的步驟之前加一步「升級到 traceId 版本後第一次啟動前，刪除稽核集合：`db.COLL_ICRM_AI_ASSISTANT_AUDIT.drop()`。只刪文件不夠，舊索引 `IX_ICRM_AI_AUDIT_4` 還在時啟動會失敗」。把手動驗證 Outbound log 的步驟（原步驟 28）提到的 `request_id` 改為 `trace_id`，並加一項：回應的 `X-Trace-Id`、畫面上的追蹤碼、稽核文件的 `traceId` 三者相同。
- `questions-for-manager.md`、`platform-setup-requests.md`、`ideas-backlog.md`：句中的 `requestId` 改為 `traceId`。
- `2026-11-10-llm-observability-design.md`：標頭加一行 `- v0.4（2026-11-10）：追蹤碼已統一為 traceId，見 [trace-id-unification-design](2026-11-10-trace-id-unification-design.md)；本文的 requestId、llm.request_id 即現在的 traceId、llm.trace_id，第 7 節「TraceId」已處理。` 其餘內容不動。
- `2026-11-10-trace-id-unification-design.md`：狀態改為「已實作」。

- [ ] **Step 3: 殘留檢查**

Run（在 repo 根目錄）:

```bash
grep -rnE "requestId|RequestId|request_id|X-Request-Id" backend/src frontend/src docs/spec docs/demo-checklist.md docs/questions-for-manager.md docs/platform-setup-requests.md docs/ideas-backlog.md
```

Expected: 沒有輸出，只允許一個例外：`frontend/src/platform-mock/__tests__/apiClient.spec.ts` 中 Task 8 加的 `ignores a legacy requestId field` 案例。

- [ ] **Step 4: 全部測試**

Run: `cd backend && mvn -q test` 與 `cd frontend && npm test && npx vue-tsc -b`
Expected: 全部通過。

- [ ] **Step 5: 階段 E 完成，交給使用者提交**

```bash
git add docs
git commit -m "docs: describe traceId as the single tracking id"
```

提醒使用者照 demo-checklist 做一次手動驗證。
