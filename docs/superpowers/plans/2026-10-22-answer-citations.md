# 回答引用（加分挑戰 1）實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **本專案規則：執行者不得自己執行 `git add` 或 `git commit`。** 各任務沒有 Commit 步驟；每個階段（A：Task 1–6、B：Task 7–8、C：Task 9–10）全部完成並通過 review 後，由主控者把該階段的 `git add` 指令和 commit message 交給使用者執行，**等使用者確認已 commit 再開始下一階段**。

**Goal:** 回答的每個段落／條列項目標出所依據的工具呼叫，點開可看資料來源、工具名稱、資料時間與摘錄；稽核紀錄保存標註對照、結果雜湊與數字核對結果。

**Architecture:** `ToolExecutor` 為每次成功的工具呼叫配發 `S1`、`S2`…並寫進回送模型的結果。每一回合的 `ChatRequest` 都帶 strict JSON Schema（`AnswerSchema`），模型最後回 `{ blocks: [{type, text, sources}] }`。新元件 `AnswerAssembler` 驗證、去識別化、重新編號、數字核對（`NumberChecker`），並產生回應用與稽核用的 Markdown。前端以 `AnswerBlocks` 取代 `SafeMarkdown` 顯示回答、以 `CitationList` 取代 `SourceList`。

**Tech Stack:** Java 25、Spring Boot 4.1、LangChain4j 1.22.0（`ResponseFormat`、`JsonSchema`、`OpenAiChatModel.strictJsonSchema`）、Jackson 3（`tools.jackson`）、MongoDB 8、JUnit 5、Testcontainers；Vue 3.5、TypeScript、Vitest、@vue/test-utils。

**Spec:** [docs/superpowers/specs/2026-10-22-answer-citations-design.md](../specs/2026-10-22-answer-citations-design.md)（v0.1）。計畫中寫「spec 2.3」指這份規格的 2.3 節；「03 §1.5」指 `docs/spec/03-tool-catalog.md` 第 1.5 節，以此類推。

## Global Constraints

- 測試指令：後端在 `backend/` 執行 `.\mvnw test -Dtest=<類別>`（Testcontainers 需要 Docker）；前端在 `frontend/` 執行 `npm test -- <檔名關鍵字>`，型別檢查 `npx vue-tsc -b`。
- 送給模型、回給前端、寫進稽核的每一段文字都要先經過 `PiiRedactor`；最後才把 `【本顧客】` 換成「本顧客」（`PiiRedactor.SELF` → `CustomerDeidentifier.SUBJECT`）。
- 模型原始輸出（JSON 字串）、工具結果、問題、回答都不得出現在 application log；log 只能有計數。
- 引用編號：`S1`、`S2`… 只配發給 **SUCCESS** 的呼叫；失敗、DENIED、TIMEOUT、超過上限都不配發。
- 區塊型別只有 `heading`、`paragraph`、`bullet`、`numbered`；上限 40 個區塊、`text` 合計 8000 字（code point）。
- 數字核對：略過 1～10 的整數；`unverifiedNumbers` 去重、最多 20 個。
- 新錯誤碼 `AI_ANSWER_FORMAT_INVALID`：HTTP 502，訊息「回答格式異常，請重試」。不自動重試。
- 新設定 `ai-assistant.audit.store-tool-results`，預設 `false`。
- `ai-assistant.llm.max-output-tokens` 改為 `1500`（最終值由 Task 10 決定）。
- 前端不得使用 `v-html`、`innerHTML`；外部摘錄一律純文字；連結只允許 http(s)（沿用 `SafeMarkdown.ts` 的 `safeHref`）。
- 回答格式規則只存在程式碼（`AnswerFormatRules`），不得出現在任何 API 回應或畫面。

## Review Focus

1. **模型把標註直接寫進文字**（`台積電收盤 2420 元 [S1]`、`【S1】`、`（S1）`）：使用者不應看到 `S1`；標記要從文字移除並併入該區塊的 `sources`。→ Task 4 測試 `inlineMarkersMoveIntoSources`。
2. **模型在條列文字前重複加符號**（bullet 的 text 是 `- 台積電…`、heading 的 text 是 `### 小節`）：畫面與複製內容不應出現 `- - 台積電`。→ Task 4 測試 `leadingMarkdownMarkersAreStripped`。
3. **工具宣告的引用資訊含個資**（外部公告主旨裡有手機號碼、本顧客姓名）：引用清單的 `target`、`excerpt`、`description` 也要去識別化。→ Task 6 測試 `citationFieldsAreRedacted`。
4. **同一個工具查兩次**（先查 2330 再查 2317）：要變成兩個引用，各自有不同的 `target`，不能合併成一個「臺灣證券交易所 OpenAPI」。→ Task 6 測試 `sameToolTwiceGivesTwoCitations`。
5. **區塊引用的編號在 `citations` 裡找不到**（資料不一致）：前端不顯示那個上標按鈕，也不能報錯。→ Task 7 測試 `skips citation ids that are not in the list`。

---

## 檔案結構

```
backend/src/main/java/com/example/copilot/aiassistant/
├─ answer/                         （新）回答組裝
│  ├─ AnswerSchema.java            每回合的 ResponseFormat（動態 enum）
│  ├─ BlockType.java               heading / paragraph / bullet / numbered
│  ├─ AnswerBlock.java             回應用區塊：type、text、citations（int）
│  ├─ AssembledAnswer.java         AnswerAssembler 的輸出
│  ├─ AnswerAssembler.java         spec 2.3 全部步驟
│  └─ NumberChecker.java           spec 2.4
├─ tool/
│  ├─ CitationDetail.java          （新）工具宣告的引用資訊
│  ├─ CitationIds.java             （新）每個問題一份的 S 編號配發器
│  ├─ ToolResult.java、ToolOutcome.java、ToolExecutor.java   （改）
│  ├─ customer/*.java、market/*.java                          （改）7 個工具提供 CitationDetail
├─ prompt/AnswerFormatRules.java   （新）、PromptAssembler.java（改）
├─ chat/ChatOrchestrator.java、ChatResult.java、dto/ChatResponseBody.java （改）
├─ audit/AuditEntry.java、AuditLogger.java、AuditRecord.java             （改）
├─ common/ErrorCode.java、llm/LlmErrorMapper.java、llm/LlmConfiguration.java、AiAssistantProperties.java （改）
backend/src/main/resources/application.yml、mongo/audit-validator.json （改）

frontend/src/features/ai-assistant/
├─ types.ts、stores/useAssistantStore.ts、markdown/parser.ts、markdown/SafeMarkdown.ts （改）
├─ components/AnswerBlocks.ts      （新）render function 元件
├─ components/CitationList.vue     （新）
├─ components/AssistantMessage.vue （改）；components/SourceList.vue （刪除）
```

---

## Phase A：後端

### Task 1：AnswerSchema 與 strict JSON Schema 實測

**Files:**
- Create: `backend/src/main/java/com/example/copilot/aiassistant/answer/AnswerSchema.java`
- Modify: `backend/src/main/java/com/example/copilot/aiassistant/llm/LlmConfiguration.java`
- Test: `backend/src/test/java/com/example/copilot/aiassistant/answer/AnswerSchemaTest.java`
- Test: `backend/src/test/java/com/example/copilot/aiassistant/llm/OpenAiResponseFormatLiveTest.java`

**Interfaces:**
- Produces: `AnswerSchema.responseFormat(List<String> citationIds): ResponseFormat`（`public static`）；`AnswerSchema.NAME = "answer"`。

- [ ] **Step 1：寫失敗測試 `AnswerSchemaTest`**

```java
@Test void enumListsIssuedIds() {
    ResponseFormat f = AnswerSchema.responseFormat(List.of("S1", "S2"));
    assertThat(f.type()).isEqualTo(ResponseFormatType.JSON);
    assertThat(f.jsonSchema().name()).isEqualTo("answer");
    JsonObjectSchema block = blockSchema(f);   // root.properties["blocks"] -> JsonArraySchema.items()
    assertThat(block.required()).containsExactlyInAnyOrder("type", "text", "sources");
    assertThat(block.additionalProperties()).isFalse();
    assertThat(((JsonEnumSchema) block.properties().get("type")).enumValues())
            .containsExactly("heading", "paragraph", "bullet", "numbered");
    JsonArraySchema sources = (JsonArraySchema) block.properties().get("sources");
    assertThat(((JsonEnumSchema) sources.items()).enumValues()).containsExactly("S1", "S2");
}
@Test void noIdsMeansPlainStringItems() {
    JsonArraySchema sources = (JsonArraySchema) blockSchema(AnswerSchema.responseFormat(List.of()))
            .properties().get("sources");
    assertThat(sources.items()).isInstanceOf(JsonStringSchema.class);
}
@Test void rootRequiresBlocksOnly() { /* root.required() == ["blocks"], root.additionalProperties() == false */ }
```

- [ ] **Step 2：執行確認失敗** — `.\mvnw test -Dtest=AnswerSchemaTest`，預期編譯失敗（`AnswerSchema` 不存在）。

- [ ] **Step 3：實作 `AnswerSchema`**（`final class`，私有建構子），schema 內容照 spec 2.2。用 `JsonObjectSchema.builder()…additionalProperties(false).required(...)`、`JsonArraySchema`、`JsonEnumSchema`、`JsonStringSchema`。

- [ ] **Step 4：`LlmConfiguration` 開啟 strict schema** — builder 加上 `.strictJsonSchema(true)` 與 `.supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)`。

- [ ] **Step 5：執行確認通過** — `.\mvnw test -Dtest=AnswerSchemaTest,ApplicationSmokeTest`，預期 PASS。

- [ ] **Step 6：寫實測類別 `OpenAiResponseFormatLiveTest`**（spec 2.2「實作第一步」）
  - 類別加 `@EnabledIfSystemProperty(named = "live-llm", matches = "true")`，平常的 `mvnw test` 會略過。
  - 用 `java.util.Properties` 讀 `backend/.env`（工作目錄就是 `backend/`）取得 `OPENAI_API_KEY`、`OPENAI_MODEL`；**不得印出或斷言 key 的值**。用 `new LlmConfiguration().chatModel(props)` 建 model，props 照 application.yml（timeout 30s、max-output-tokens 1500）。
  - 三個測試，工具只帶 `new GetStockQuoteTool(null).specification()`：
    - `toolCallStillHappensWithSchema`：問「台積電(2330)最近收盤價？」，帶 `AnswerSchema.responseFormat(List.of())` → `aiMessage().hasToolExecutionRequests()` 為 true。
    - `answersWithEnumRestrictedSources`：延續上一則，補上 `ToolExecutionResultMessage`，內容 `{"untrustedData":true,"status":"SUCCESS","citationId":"S1","data":{"stockCode":"2330","name":"台積電","dataDate":"2026-10-16","close":2420}}`，帶 `responseFormat(List.of("S1"))` → 回覆文字可被 `JsonMapper` 解析、有 `blocks`、所有 `sources` ⊆ `["S1"]`、至少一個區塊的 sources 含 `S1`。
    - `schemaWithoutEnumIsAccepted`：一般問題「什麼是殖利率？」帶 `responseFormat(List.of())` → 不丟例外、回覆為合法 JSON。
- [ ] **Step 7：主控者執行實測**（實作者不跑，回報後由主控者跑）— `.\mvnw test -Dtest=OpenAiResponseFormatLiveTest -Dlive-llm=true`。
  - 全部通過：照計畫繼續。
  - `answersWithEnumRestrictedSources` 或 `schemaWithoutEnumIsAccepted` 因 schema 被拒而失敗：把 `AnswerSchema` 改成**永遠**用 `JsonStringSchema`（不帶 enum），更新 `AnswerSchemaTest` 並在 Task 9 的 spec 異動中記錄。
  - `toolCallStillHappensWithSchema` 失敗：停下來回報使用者（需要改成「工具回合不帶 schema、最後再補一回合」的設計，超出本計畫）。

---

### Task 2：工具提供 CitationDetail，ToolExecutor 配發 S 編號

**Files:**
- Create: `backend/src/main/java/com/example/copilot/aiassistant/tool/CitationDetail.java`、`tool/CitationIds.java`
- Modify: `tool/ToolResult.java`、`tool/ToolOutcome.java`、`tool/ToolExecutor.java`、`tool/customer/*.java`（4 個工具）、`tool/market/AbstractMarketTool.java`、`tool/market/GetStockQuoteTool.java`、`GetMarketIndexTool.java`、`GetCompanyAnnouncementsTool.java`、`chat/ChatOrchestrator.java`（只改呼叫端）
- Test: `ToolExecutorTest.java`、`customer/CustomerToolsTest.java`、`market/MarketToolsTest.java`、`audit/AuditLoggerTest.java`（只修編譯）

**Interfaces:**
- Produces:
  - `record CitationDetail(String title, String target, String excerpt, String description, Integer resultCount)`
  - `ToolResult` 新增欄位 `CitationDetail citation`（最後一個參數）；`ToolResult.success(Object data, List<Source> sources, CitationDetail citation)`；原本的 `success(data, sources)` 保留，citation 為 `null`（測試用假工具用）；`failure(...)` 的 citation 為 `null`。
  - `final class CitationIds`：`String next()` 依序回傳 `S1`、`S2`…；`List<String> issued()` 回傳已配發的編號（依配發順序，不可變副本）。每個問題 new 一個，不需執行緒安全。
  - `record ToolOutcome(String toolName, String purpose, ToolResult result, String payloadForModel, long durationMs, String citationId)`；`citationId` 只有 SUCCESS 才非 null。
  - `ToolExecutor.execute(ToolExecutionRequest request, ToolContext context, Set<String> offeredToolNames, CitationIds ids): ToolOutcome`

- [ ] **Step 1：寫失敗測試（ToolExecutorTest）**

```java
@Test void successGetsCitationIdInPayloadAndOutcome() {
    CitationIds ids = new CitationIds();
    ToolOutcome first = run(ok, ids); ToolOutcome second = run(ok, ids);
    assertThat(first.citationId()).isEqualTo("S1");
    assertThat(second.citationId()).isEqualTo("S2");
    assertThat(first.payloadForModel()).startsWith("{\"untrustedData\":true,\"status\":\"SUCCESS\",\"citationId\":\"S1\",\"data\":");
    assertThat(ids.issued()).containsExactly("S1", "S2");
}
@Test void failuresGetNoCitationId() { /* ERROR、TIMEOUT、guard 拒絕、limitReached：citationId 為 null、payload 不含 "citationId"、ids.issued() 為空 */ }
@Test void oversizedResultDoesNotConsumeAnId() {
    // 先跑 "x".repeat(5000)（payload 建不出來 → ERROR），再跑一次成功的
    assertThat(after.citationId()).isEqualTo("S1");
}
```

- [ ] **Step 2：寫失敗測試（工具的 CitationDetail，值照 spec 2.5）**

`CustomerToolsTest`（C001，FULL）：
```java
assertThat(profile.citation()).isEqualTo(new CitationDetail("顧客概況", null, null, "8 個欄位", 8));
// 欄位數 = profile map 的項目數，不含 "subject" 與 "accessNote"；LIMITED 時為 "3 個欄位", 3
assertThat(holdings.citation()).isEqualTo(new CitationDetail("顧客持有商品", null, null, n + " 筆", n));
assertThat(interactions.citation().description()).matches("\\d+ 筆，最近一筆 \\d{2}/\\d{2}");
// 0 筆時 description 為 "0 筆"
assertThat(campaigns.citation()).isEqualTo(new CitationDetail("行銷活動反應", null, null, n + " 筆", n));
```
`MarketToolsTest`（fixture）：
```java
// 2330
assertThat(quote.citation()).isEqualTo(new CitationDetail("上市個股行情", "台積電 2330",
        "收盤 2420、漲跌 -20、本益比 28.05、殖利率 0.91%", null, 1));
// 0050（沒有估值）：excerpt 為 "收盤 190.8"，缺值的項目整個省略
// 大盤：title "大盤指數"、target null、resultCount 5、excerpt 以 "加權指數 47111.26（漲跌 +141.39）、" 開頭，剛好 3 項（以 "、" 分隔）
// 公告 2072：title "上市公司重大訊息"、target "世紀風電 2072"、excerpt 為主旨前 60 個字、resultCount 1
// 公告 2330（無資料）：target "2330"、excerpt "查無重大訊息"、resultCount 0、sources[0].dataTime 為 null
```

- [ ] **Step 3：執行確認失敗** — `.\mvnw test -Dtest=ToolExecutorTest,CustomerToolsTest,MarketToolsTest`。

- [ ] **Step 4：實作**
  - `ToolExecutor.payload` 在 SUCCESS 時依序寫 `untrustedData`、`status`、`citationId`、`data`（`truncated` 照舊放最後）。只有 payload 成功建出來之後才真正消耗編號（例如先用「下一個會發的編號」建 payload，成功再 `next()`）。`limitReached` 的 outcome `citationId` 為 null。
  - 數字格式（excerpt 用）：`BigDecimal.stripTrailingZeros().toPlainString()`；漲跌正數加 `+`、負數保留 `-`、0 為 `0`。放在 `AbstractMarketTool` 的 `static String plain(BigDecimal)`、`static String signed(BigDecimal)`。
  - 大盤 excerpt：取前 3 項，`發行量加權股價指數` 顯示為 `加權指數`，其餘照原名，格式 `名稱 收盤（漲跌 ±X）`，以 `、` 連接。
  - 公告 excerpt：最多 3 則主旨，每則用 `shorten(subject, 60)`，以 `；` 連接；無資料為 `查無重大訊息`。`target`：有公司名稱時 `名稱 代號`，否則只有代號。
  - 個股 `target`：`名稱 代號`（名稱 null 時只有代號）。
  - 互動紀錄「最近一筆」取第一筆（已由新到舊）的日期，格式 `MM/dd`。
  - `ChatOrchestrator.Turn` 新增欄位 `private final CitationIds citationIds = new CitationIds();`，呼叫 `executor.execute(safe, toolContext, offered, citationIds)`。
  - 其他建構 `ToolOutcome` 的測試（`AuditLoggerTest.tool(...)`）補上最後一個參數（SUCCESS 給 `"S1"`，其餘 `null`）。

- [ ] **Step 5：執行確認通過** — `.\mvnw test`，預期全部 PASS（`ChatOrchestratorTest` 的既有斷言 `contains("\"untrustedData\":true", "\"status\":\"SUCCESS\"")` 仍成立）。

---

### Task 3：NumberChecker

**Files:**
- Create: `backend/src/main/java/com/example/copilot/aiassistant/answer/NumberChecker.java`
- Test: `backend/src/test/java/com/example/copilot/aiassistant/answer/NumberCheckerTest.java`

**Interfaces:**
- Produces: `NumberChecker.unverified(List<String> texts, List<JsonNode> toolData): List<String>`（`public static`，`tools.jackson.databind.JsonNode`）。回傳在 `toolData` 找不到依據的數字，**以回答中出現的原樣**（例如 `2,420`、`10/21`）表示，依首次出現順序、以正規化值去重、最多 20 個。

- [ ] **Step 1：寫失敗測試**（`data(json)` 是把字串 parse 成 `JsonNode` 的 helper）

```java
@Test void thousandsSeparatorAndTrailingZeros() {
    assertThat(unverified(List.of("收盤 2,420.00 元，本益比 28.050"), List.of(data("{\"close\":2420,\"pe\":\"28.05\"}")))).isEmpty();
}
@Test void percentComparesValueOnly() {
    assertThat(unverified(List.of("殖利率 0.91%"), List.of(data("{\"dividendYield\":0.91}")))).isEmpty();
}
@Test void datesCompareMonthAndDay() {
    List<JsonNode> d = List.of(data("{\"dataDate\":\"2026-10-21\"}"));
    assertThat(unverified(List.of("資料日期 2026-10-21、10/21、2026/10/21"), d)).isEmpty();
    assertThat(unverified(List.of("10/22 收盤"), d)).containsExactly("10/22");
}
@Test void dateFieldsSplitIntoYearMonthDay() {
    assertThat(unverified(List.of("2026 年"), List.of(data("{\"dataDate\":\"2026-10-21\"}")))).isEmpty();
}
@Test void numbersInsideToolStringsCount() {
    assertThat(unverified(List.of("占比 20%-30%"), List.of(data("{\"weightRange\":\"20%-30%\"}")))).isEmpty();
}
@Test void smallIntegersAreSkipped() {
    assertThat(unverified(List.of("共 3 筆，第 10 名，11 檔"), List.of())).containsExactly("11");
}
@Test void signsAreIgnored() {
    assertThat(unverified(List.of("下跌 -20 元"), List.of(data("{\"change\":-20}")))).isEmpty();
}
@Test void dedupesAndCapsAtTwenty() {
    String text = IntStream.rangeClosed(11, 40).mapToObj(String::valueOf).collect(joining("、")) + "、11";
    assertThat(unverified(List.of(text), List.of())).hasSize(20).startsWith("11", "12").doesNotHaveDuplicates();
}
```

- [ ] **Step 2：執行確認失敗** — `.\mvnw test -Dtest=NumberCheckerTest`。

- [ ] **Step 3：實作 `NumberChecker`**（spec 2.4）
  - 擷取順序：先日期 `\d{4}[-/]\d{1,2}[-/]\d{1,2}`、`(?<![\d/])\d{1,2}/\d{1,2}(?![\d/])`，再數字 `\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?`（後面可接 `%`）；已被日期吃掉的範圍不再當數字。
  - 正規化：數字 → 去千分位、`new BigDecimal(..).stripTrailingZeros().toPlainString()`；日期 → `"D:" + 月 + "-" + 日`（月日去前導 0）。
  - 工具端：走訪整棵 `JsonNode`，數值節點取正規化值；字串節點用同一套擷取（日期另外拆出年、月、日三個數字）。
  - 正規化後是 1～10 的整數就略過。

- [ ] **Step 4：執行確認通過** — `.\mvnw test -Dtest=NumberCheckerTest`。

---

### Task 4：AnswerAssembler 與 AI_ANSWER_FORMAT_INVALID

**Files:**
- Create: `answer/BlockType.java`、`answer/AnswerBlock.java`、`answer/AssembledAnswer.java`、`answer/AnswerAssembler.java`
- Modify: `common/ErrorCode.java`、`llm/LlmErrorMapper.java`
- Test: `answer/AnswerAssemblerTest.java`、`llm/LlmErrorMapperTest.java`

**Interfaces:**
- Consumes: `ToolOutcome.citationId()`、`ToolOutcome.payloadForModel()`（Task 2）；`NumberChecker.unverified`（Task 3）；`PiiRedactor.redact(String, CustomerContext)`。
- Produces:
  - `enum BlockType { HEADING, PARAGRAPH, BULLET, NUMBERED }`，JSON 值為小寫（`@JsonValue String json()`），`static Optional<BlockType> fromJson(String)`。
  - `record AnswerBlock(BlockType type, String text, List<Integer> citations)`
  - `record AssembledAnswer(List<AnswerBlock> blocks, String answer, String annotatedAnswer, Map<String, Integer> citationNumbers, Set<String> cited, boolean truncated, List<String> unverifiedNumbers)` — `citationNumbers` 是 S 編號 → 引用編號（`LinkedHashMap`，依編號排序，含未引用的）；`cited` 是有被區塊引用的 S 編號。
  - `@Component AnswerAssembler(PiiRedactor redactor, JsonMapper mapper)`；`AssembledAnswer assemble(String modelText, List<ToolOutcome> successful, CustomerContext customer, boolean dropDisclaimer)` — `successful` 是本次 citationId 非 null 的呼叫，依呼叫順序。
  - `AnswerAssembler.MARKET_DISCLAIMER = "資訊僅供內部經營參考，不構成投資建議"`（由 `ChatOrchestrator` 搬過來）。
  - `ErrorCode.AI_ANSWER_FORMAT_INVALID(HttpStatus.BAD_GATEWAY, "回答格式異常，請重試")`，放在 2.2 chat 區。

- [ ] **Step 1：寫失敗測試 `AnswerAssemblerTest`**（`json(...)` helper 組出模型 JSON；`outcome("S1", "{\"untrustedData\":true,\"status\":\"SUCCESS\",\"citationId\":\"S1\",\"data\":{\"close\":2420}}")` 建 ToolOutcome；customer 用 C001，selfReferences 含「王小明」）

```java
// 解析
@Test void invalidOutputIsFormatInvalid() {
    for (String bad : List.of("", "不是 JSON", "{}", "{\"blocks\":\"x\"}",
            "{\"blocks\":[{\"type\":\"quote\",\"text\":\"a\",\"sources\":[]}]}",
            "{\"blocks\":[{\"type\":\"paragraph\",\"sources\":[]}]}",
            "{\"blocks\":[{\"type\":\"paragraph\",\"text\":\"a\",\"sources\":[1]}]}",
            "{\"blocks\":[{\"type\":\"paragraph\",\"text\":\"   \",\"sources\":[]}]}")) {
        assertThatThrownBy(() -> assemble(bad)).isInstanceOfSatisfying(AssistantException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_ANSWER_FORMAT_INVALID));
    }
}
@Test void blankBlocksAreDropped() { /* 兩個區塊其中一個空白 → 只剩一個 */ }
// 去識別化
@Test void eachBlockIsRedactedAndSelfTokenReplaced() {
    // text "王小明近期無新活動" → "本顧客近期無新活動"；annotatedAnswer 也不含王小明、【本顧客】
}
// 來源
@Test void unknownAndDuplicateSourcesAreCleaned() {
    // successful = S1；block sources ["S1","S9","S1"] → citations [1]
}
@Test void headingSourcesAreCleared() { /* heading sources ["S1"] → citations []，且 S1 不算 cited */ }
@Test void inlineMarkersMoveIntoSources() {
    // text "收盤 2420 元 [S1]【S2】（S3）"，sources []，successful = S1,S2 → text "收盤 2420 元"，citations [1, 2]；S3 只被刪除
}
// 重新編號
@Test void numbersFollowFirstUseThenUncitedInCallOrder() {
    // successful = S1,S2,S3；區塊依序引用 S2、S1 → citationNumbers {S2=1, S1=2, S3=3}，cited = {S2, S1}
}
// 警語
@Test void trailingDisclaimerBlockIsDroppedWhenAsked() {
    // 最後一個區塊 "※ 資訊僅供內部經營參考，不構成投資建議。" → dropDisclaimer=true 時移除；false 時保留
    // 只有這一個區塊時保留；警語前一個區塊是 "---" 時一併移除
}
// 上限
@Test void moreThanFortyBlocksAreTruncated() { /* 41 個 → 40 個，truncated = true */ }
@Test void textOverEightThousandCharsIsTruncated() {
    // 3 個區塊各 3000 字 → 前兩個完整、第三個剩 2000 字，truncated = true
}
// 文字整理
@Test void leadingMarkdownMarkersAreStripped() {
    // bullet "- 台積電" → "台積電"；numbered "2. 鴻海" → "鴻海"；heading "### 小節" → "小節"
}
@Test void lineBreaksCollapseOutsideParagraphs() { /* bullet "a\n b" → "a b"；paragraph 保留換行 */ }
// 扁平化
@Test void flattensToMarkdown() {
    // heading「持有標的」、bullet A [S1]、bullet B、numbered C、numbered D、paragraph E [S1]
    assertThat(r.answer()).isEqualTo("### 持有標的\n\n- A\n- B\n\n1. C\n2. D\n\nE");
    assertThat(r.annotatedAnswer()).isEqualTo("### 持有標的\n\n- A [1]\n- B\n\n1. C\n2. D\n\nE [1]");
}
// 數字核對
@Test void unverifiedNumbersComeFromNonHeadingBlocks() {
    // heading "2025 年回顧"、paragraph "收盤 2420 元，目標 3000" ；data close 2420 → ["3000"]
}
```

`LlmErrorMapperTest`：
```java
@Test void refusalIsFormatInvalid() {
    assertThat(mapper.map(new ContentFilteredException("refused"))).isEqualTo(ErrorCode.AI_ANSWER_FORMAT_INVALID);
}
```

- [ ] **Step 2：執行確認失敗** — `.\mvnw test -Dtest=AnswerAssemblerTest,LlmErrorMapperTest`。

- [ ] **Step 3：實作**，處理順序：
  1. 用 `JsonMapper.readTree` 解析；根節點必須是物件且 `blocks` 是陣列；每個元素要有字串 `type`（可轉成 `BlockType`）、字串 `text`、元素全為字串的 `sources` 陣列。任何不符 → `new AssistantException(ErrorCode.AI_ANSWER_FORMAT_INVALID)`。不得把模型原文放進例外訊息或 log。
  2. 把文字中的 `[S\d+]`、`【S\d+】`、`(S\d+)`、`（S\d+）` 移除並併入 sources；去除前後空白；heading 去掉開頭的 `#+\s*`，bullet 去掉 `[-*+•]\s+`，numbered 去掉 `\d+[.)、]\s*`；非 paragraph 把 `\s*\n\s*` 換成一個空格。
  3. 移除空白區塊；全空 → FORMAT_INVALID。
  4. 上限：40 個區塊、合計 8000 code point（超過的那個區塊截到剩餘字數，剩餘為 0 則捨棄），設 `truncated`。
  5. 每個區塊 `redactor.redact(text, customer)`，再 `replace(PiiRedactor.SELF, CustomerDeidentifier.SUBJECT)`。
  6. sources 只留 `successful` 裡有的編號、去重；heading 清空。
  7. `dropDisclaimer` 且區塊數 > 1 且最後區塊符合 `DISCLAIMER_ONLY`（`DECORATION` 正規式由 `ChatOrchestrator` 搬來）→ 移除；新的最後區塊是水平線（`HORIZONTAL_RULE`）也移除。
  8. 重新編號、算 `cited`。
  9. `NumberChecker.unverified(非 heading 區塊的 text, 每個 successful 的 payloadForModel 解析後的 "data" 節點)`。
  10. 扁平化：區塊之間空一行，連續同型別的 bullet／numbered 之間只換一行；numbered 在同一段連續區塊內編號 1、2、3…；annotated 版在區塊文字後加 ` [n]`（多個為 ` [1][2]`）。
  - `LlmErrorMapper`：cause chain 中遇到 `dev.langchain4j.exception.ContentFilteredException` → `AI_ANSWER_FORMAT_INVALID`（OpenAI 的 refusal 由 LangChain4j 轉成這個例外）。

- [ ] **Step 4：執行確認通過** — `.\mvnw test -Dtest=AnswerAssemblerTest,LlmErrorMapperTest,AssistantExceptionHandlerTest`。

---

### Task 5：稽核新欄位與 store-tool-results 開關

**Files:**
- Modify: `AiAssistantProperties.java`、`application.yml`、`audit/AuditEntry.java`、`audit/AuditRecord.java`、`audit/AuditLogger.java`、`mongo/audit-validator.json`、`chat/ChatOrchestrator.java`（只改呼叫端）
- Test: `audit/AuditLoggerTest.java`、`config/MongoSchemaTest.java`、`AiAssistantPropertiesTest.java`；所有 `new AiAssistantProperties(...)` 的測試（`ChatOrchestratorTest`、`CustomerToolsTest`、`ToolExecutorTest`）補參數

**Interfaces:**
- Consumes: `ToolOutcome`（Task 2）、`CitationDetail`。
- Produces:
  - `AiAssistantProperties` 新增最後一個元件 `Audit audit`，`record Audit(boolean storeToolResults)`；yml `ai-assistant.audit.store-tool-results: false`。`audit` 為 null 時視同 false。
  - `record AuditEntry(String requestId, UserContext user, CustomerContext customer, String redactedQuestion, String answer, boolean answerTruncated, String outcome, String errorCode, List<ToolOutcome> toolCalls, Map<String, Integer> citationNumbers, List<String> unverifiedNumbers, long durationMs)` — `answer` 是含 ` [n]` 的版本。
  - `AuditLogger(MongoTemplate, Clock, CredentialMasker, AiAssistantProperties)`。
  - `AuditRecord.ToolCall(String toolName, String status, String errorCode, int durationMs, Integer citationId, String target, String dataTime, Integer resultCount, String resultHash, Document result)`；`AuditRecord` 新增 `List<String> unverifiedNumbers`。

- [ ] **Step 1：寫失敗測試（AuditLoggerTest）**

```java
@Test void successfulCallsCarryCitationFields() {
    // tool: SUCCESS, citationId "S1", payload = "{\"untrustedData\":true,\"status\":\"SUCCESS\",\"citationId\":\"S1\",\"data\":{\"close\":2420}}",
    //       citation = new CitationDetail("上市個股行情", "台積電 2330", "收盤 2420", null, 1), source dataTime "2026-10-16"
    // entry citationNumbers {S1=1}, unverifiedNumbers ["3000"]
    Document call = find(...).getList("toolCalls", Document.class).get(0);
    assertThat(call.getInteger("citationId")).isEqualTo(1);
    assertThat(call.getString("target")).isEqualTo("台積電 2330");
    assertThat(call.getString("dataTime")).isEqualTo("2026-10-16");
    assertThat(call.getInteger("resultCount")).isEqualTo(1);
    assertThat(call.getString("resultHash")).isEqualTo(sha256Hex(payload)).hasSize(64);
    assertThat(call.containsKey("result")).isFalse();
    assertThat(doc.getList("unverifiedNumbers", String.class)).containsExactly("3000");
}
@Test void failedCallsHaveNullCitationFields() { /* ERROR：citationId/target/dataTime/resultCount/resultHash 都是 null */ }
@Test void toolResultStoredOnlyWhenEnabled() {
    // new AuditLogger(mongo, clock, masker, propsWith(true)) → call.get("result", Document.class).getInteger("close") == 2420
    // 預設 bean（false）→ 沒有 "result" 鍵
}
@Test void answerTruncatedFromAssemblerIsKept() { /* entry answerTruncated=true、answer 很短 → 文件 answerTruncated 為 true */ }
@Test void unverifiedNumbersCappedAtTwenty() { /* 傳 25 個 → 存 20 個 */ }
```
並更新：`DOC_KEYS` 加入 `unverifiedNumbers`；`toolCallsExcludeArgumentsAndResults` 的鍵改為 `toolName, status, errorCode, durationMs, citationId, target, dataTime, resultCount, resultHash`，且 `doc.toJson()` 仍不含 `RESULT-DATA`、`PURPOSE-TEXT`、`王小明`、`A123456789`（payload 改成合法 JSON 時也不得出現 data 內容）。

`MongoSchemaTest`：
```java
@Test void auditValidatorAcceptsCitationFields() { /* 直接 insert 含新欄位的文件成功 */ }
@Test void auditValidatorRejectsLongResultHash() { /* resultHash 65 字 → MongoWriteException */ }
```

- [ ] **Step 2：執行確認失敗** — `.\mvnw test -Dtest=AuditLoggerTest,MongoSchemaTest`。

- [ ] **Step 3：實作**
  - `AuditLogger.toCall`：只有 `o.citationId() != null` 時填 `citationId = citationNumbers.get(o.citationId())`、`target`（clip 100）、`dataTime`（第一個 Source 的 dataTime，clip 32）、`resultCount`、`resultHash`（`MessageDigest` SHA-256 of `payloadForModel` UTF-8，`HexFormat.of().formatHex`）；開關開啟時 `result = Document.parse(data 節點的 JSON)`。
  - `answerTruncated = entry.answerTruncated() || 超過 MAX_ANSWER`。
  - `unverifiedNumbers` 取前 20 個、每個 clip 32；null 時存空陣列。
  - `AuditRecord.toDocument()`：toolCall 的新鍵一律寫出（null 也寫），`result` 只有非 null 才寫。
  - `audit-validator.json`：`unverifiedNumbers`（array、maxItems 20、items string maxLength 32）；toolCalls items 加 `citationId`（`["int","null"]`, minimum 1）、`target`（`["string","null"]`, maxLength 100）、`dataTime`（`["string","null"]`, maxLength 32）、`resultCount`（`["int","null"]`, minimum 0）、`resultHash`（`["string","null"]`, minLength 64, maxLength 64）、`result`（`["object","null"]`）。`required` 不變。
  - `ChatOrchestrator.Turn.audit` 暫時傳 `false, Map.of(), List.of()`（Task 6 接上）。

- [ ] **Step 4：執行確認通過** — `.\mvnw test`，全部 PASS。

---

### Task 6：ChatOrchestrator 接上 JSON 回答、引用與 Prompt 規則

**Files:**
- Create: `prompt/AnswerFormatRules.java`
- Modify: `prompt/PromptAssembler.java`、`chat/ChatOrchestrator.java`、`chat/ChatResult.java`、`chat/dto/ChatResponseBody.java`、`application.yml`（`max-output-tokens: 1500`）
- Test: `chat/ChatOrchestratorTest.java`、`chat/ScriptedChatModel.java`、`chat/ChatControllerTest.java`、`prompt/PromptAssemblerTest.java`
- Create test: `chat/ChatCitationFlowTest.java`（整合測試，extends `IntegrationTestBase`）

**Interfaces:**
- Consumes: `AnswerSchema`（Task 1）、`CitationIds`／`CitationDetail`（Task 2）、`AnswerAssembler`／`AssembledAnswer`／`AnswerBlock`（Task 4）、新 `AuditEntry`（Task 5）。
- Produces:
  - `record ChatResult(String requestId, List<AnswerBlock> blocks, String answer, List<CitationView> citations, Instant queriedAt, String disclaimer, List<ToolCallSummary> toolCalls)`
  - `record ChatResult.CitationView(int id, String toolName, String title, String target, String excerpt, String description, String sourceName, String url, String dataTime, Instant queriedAt, boolean cited)`
  - `ChatResponseBody(requestId, blocks, answer, citations, queriedAt, disclaimer, toolCalls)`；`BlockBody(String type, String text, List<Integer> citations)`（type 為小寫字串）；`CitationBody` 欄位同 CitationView，`queriedAt` 為 `OffsetDateTime`（`AdminTime.at`）。移除 `SourceBody`、`SourceView`。
  - `AnswerFormatRules.TEXT`：spec 第 5 節的文字逐字照抄。
  - `ScriptedChatModel` 新增 `static String blocks(String... blockJson)`、`static String block(String type, String text, String... sources)`、`static ChatResponse answer(int totalTokens, String... paragraphs)`（每段一個無來源的 paragraph），JSON 用 Jackson 組，不手串字串。

- [ ] **Step 1：Prompt 規則測試（PromptAssemblerTest）**

```java
@Test void formatRulesFollowSafetyRulesAtTheEnd() {
    assertThat(message).endsWith(SafetyRules.TEXT + "\n\n" + AnswerFormatRules.TEXT);
}
@Test void formatRulesText() {
    assertThat(AnswerFormatRules.TEXT).startsWith("# 回答格式規則");
    assertThat(AnswerFormatRules.TEXT.lines().filter(l -> l.startsWith("- "))).hasSize(5);
}
```
> 組裝順序維持現行「System Prompt → 問題分類 → 安全規則」，回答格式規則接在安全規則之後。spec 第 5 節寫的「安全規則 → 回答格式規則 → System Prompt → 問題分類」與現行程式不同，Task 9 修正 spec 文字。

- [ ] **Step 2：改寫 ChatOrchestratorTest**：所有 `text("…", n)` 最終回答改成 `answer(n, "…")` 或 `text(blocks(...), n)`；`result.sources()` 的斷言改成 `result.citations()`；`stripTrailingDisclaimer*`、`keeps*Disclaimer*` 這組測試刪除（已由 AnswerAssemblerTest 涵蓋），留一個 `disclaimerBlockDroppedForMarketAnswer` 走完整流程。新增：

```java
@Test void everyRequestCarriesSchemaWithIssuedIds() {
    // tool call getCustomerProfile → 回答
    assertThat(sourcesItems(model.requests().get(0))).isInstanceOf(JsonStringSchema.class);
    assertThat(((JsonEnumSchema) sourcesItems(model.requests().get(1))).enumValues()).containsExactly("S1");
    assertThat(toolResultText(model.requests().get(1))).contains("\"citationId\":\"S1\"");
}
@Test void forcedFinalRoundAlsoCarriesSchema() { /* 達上限後無工具的最後一回合 responseFormat 仍存在，enum 為 S1..S5 */ }
@Test void blocksAndCitationsInResult() {
    // getStockQuote 2330 + getCustomerHoldings；回答 heading + bullet[S1] + paragraph[]
    assertThat(r.blocks()).extracting(AnswerBlock::citations).containsExactly(List.of(), List.of(1), List.of());
    assertThat(r.citations()).extracting(CitationView::id, CitationView::toolName, CitationView::cited)
            .containsExactly(tuple(1, "getStockQuote", true), tuple(2, "getCustomerHoldings", false));
    CitationView quote = r.citations().get(0);
    assertThat(quote.title()).isEqualTo("上市個股行情");
    assertThat(quote.target()).isEqualTo("台積電 2330");
    assertThat(quote.sourceName()).isEqualTo("臺灣證券交易所 OpenAPI");
    assertThat(quote.dataTime()).isEqualTo("2026-10-16");
    assertThat(quote.queriedAt()).isEqualTo(NOW);
    assertThat(r.answer()).doesNotContain("[1]");
    assertThat(singleAudit().answer()).contains(" [1]");
    assertThat(singleAudit().citationNumbers()).containsEntry("S1", 1);
}
@Test void sameToolTwiceGivesTwoCitations() {
    // getStockQuote 2330 與 2317 → 兩筆 citations，target 分別為 "台積電 2330"、"鴻海 2317"
}
@Test void citationFieldsAreRedacted() {
    // 用只含一個 FakeTool 的 registry 另建 orchestrator：工具回 success(Map.of(), List.of(), new CitationDetail("測試", "王小明", "聯絡 0912-000-111", "【本顧客】資料", 1))
    // → target "本顧客"、excerpt "聯絡 【已遮蔽】"、description "本顧客資料"
}
@Test void failedToolHasNoCitation() { /* getStockQuote 9999 → citations 為空，toolCalls 仍有 ERROR */ }
@Test void invalidJsonAnswerFailsAndAudits() {
    model.reply(text("純文字回答", 100));
    assertThatThrownBy(() -> ask("概況？")).isInstanceOfSatisfying(AssistantException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_ANSWER_FORMAT_INVALID));
    assertThat(singleAudit().outcome()).isEqualTo("FAILED");
    assertThat(singleAudit().errorCode()).isEqualTo("AI_ANSWER_FORMAT_INVALID");
}
@Test void unverifiedNumbersReachAudit() { /* 回答「收盤 2420 元，目標價 3000 元」引用 S1 → audit.unverifiedNumbers == ["3000"] */ }
```
並修改 `applicationLogHoldsCountersButNoContent`：log 行另外要有 `blocks=`、`citedBlocks=`、`peakOutputTokens=`，且仍不含問題、回答與模型原始 JSON。

- [ ] **Step 3：整合測試 `ChatCitationFlowTest`**（Mockito `when(chatModel.chat(any())).thenReturn(toolCall getCustomerHoldings, JSON 回答引用 S1)`，`fc.officer` 問 C001）：
  - 回應 `$.keys()` 為 `requestId, blocks, answer, citations, queriedAt, disclaimer, toolCalls`；`$.blocks[0].type == "paragraph"`、`$.blocks[0].citations[0] == 1`；`$.citations[0].title == "顧客持有商品"`、`$.citations[0].target` 為 null、`$.citations[0].queriedAt` 以 `+08:00` 結尾、`$.citations[0].cited == true`。
  - 稽核文件（以 requestId 查）：`answer` 含 ` [1]`、`toolCalls[0].citationId == 1`、`resultHash` 長度 64、沒有 `result` 鍵、`unverifiedNumbers` 存在。
  - `ChatControllerTest`：`@BeforeEach` 的回答改為 `ScriptedChatModel.answer(100, "本顧客概況如下")`；`$.keys()` 斷言同上。

- [ ] **Step 4：執行確認失敗** — `.\mvnw test -Dtest=ChatOrchestratorTest,ChatControllerTest,ChatCitationFlowTest,PromptAssemblerTest`。

- [ ] **Step 5：實作**
  - `PromptAssembler.systemMessage()`：安全規則之後加 `"\n\n" + AnswerFormatRules.TEXT`。
  - `callModel` 的 `ChatRequest` 一律帶 `.responseFormat(AnswerSchema.responseFormat(citationIds.issued()))`。
  - `answer()` 回傳模型原始文字（null 視同空字串，交給 assembler 判為 FORMAT_INVALID），不再先 redact；`result()` 呼叫 `assembler.assemble(raw, successful, customer, disclaimer != null)`。`disclaimer` 判斷不變（有成功的 MarketTool）。移除 `stripTrailingDisclaimer`、`SourceView` 組裝、`MARKET_DISCLAIMER`／`DECORATION` 常數（改用 `AnswerAssembler.MARKET_DISCLAIMER`）。
  - `CitationView`：依 `citationNumbers` 順序，對應的 `ExecutedTool` 取 `toolName`、`queriedAt`；`CitationDetail` 為 null 時 title 用 `purpose`；`target`、`excerpt`、`description` 先 `redact` 再把 `【本顧客】` 換成「本顧客」；`sourceName`、`url`、`dataTime` 取第一個 `Source`（沒有則 null）；`cited = assembled.cited().contains(id)`。
  - `audit(...)` 傳 `assembled.annotatedAnswer()`、`assembled.truncated()`、`assembled.citationNumbers()`、`assembled.unverifiedNumbers()`；失敗時 `null, false, Map.of(), List.of()`。
  - log 計數：`blocks`（區塊數）、`citedBlocks`（非 heading 且有引用的區塊數）、`peakOutputTokens`（各回合 `TokenUsage.outputTokenCount()` 的最大值）。
  - `ChatOrchestrator` 建構子加 `AnswerAssembler`；測試裡用 `new AnswerAssembler(redactor, mapper)`。

- [ ] **Step 6：執行確認通過** — `.\mvnw test`，全部 PASS。

> **Phase A 結束**：主控者整理 `git add backend/` 與 commit message（素材：各 Task 標題），交給使用者，等確認後再進 Phase B。

---

## Phase B：前端

### Task 7：型別、store 與 AnswerBlocks

**Files:**
- Modify: `frontend/src/features/ai-assistant/types.ts`、`stores/useAssistantStore.ts`、`markdown/parser.ts`、`markdown/SafeMarkdown.ts`
- Create: `components/AnswerBlocks.ts`
- Test: `components/__tests__/AnswerBlocks.spec.ts`、`stores/__tests__/useAssistantStore.spec.ts`、`markdown/__tests__/parser.spec.ts`

**Interfaces:**
- Produces（`types.ts`）：
```ts
export type AnswerBlockType = 'heading' | 'paragraph' | 'bullet' | 'numbered'
export interface AnswerBlock { type: AnswerBlockType; text: string; citations: number[] }
export interface Citation {
  id: number; toolName: string; title: string; target: string | null
  excerpt: string | null; description: string | null
  sourceName: string | null; url: string | null; dataTime: string | null
  queriedAt: string; cited: boolean
}
```
  `ChatResponse` 與 `ChatMessage` 加 `blocks`、`citations`（ChatMessage 為選填），移除 `sources` 與 `SourceView`。
- `parser.ts`：`export function parseInline(s: string, depth = 0): Inline[]`
- `SafeMarkdown.ts`：`export function renderInlines(nodes: Inline[]): (VNode | string)[]`（就是現在的 `inlines`，改名並 export）
- `AnswerBlocks.ts`：`defineComponent`，props `blocks: AnswerBlock[]`、`citations: Citation[]`，emits `cite: (id: number) => true`。

- [ ] **Step 1：寫失敗測試 `AnswerBlocks.spec.ts`**

```ts
it('groups consecutive bullets and numbered items into lists', () => {
  // heading, bullet, bullet, numbered, paragraph
  expect(w.find('h3').text()).toBe('持有標的')
  expect(w.findAll('ul > li')).toHaveLength(2)
  expect(w.findAll('ol > li')).toHaveLength(1)
  expect(w.find('p').text()).toContain('結語')
})
it('renders inline bold and safe links only', () => {
  // text '**重點** [官網](https://www.twse.com.tw) [壞](javascript:alert(1)) <script>x</script>'
  expect(w.find('strong').text()).toBe('重點')
  expect(w.findAll('a')).toHaveLength(1)
  expect(w.find('a').attributes('href')).toBe('https://www.twse.com.tw/')
  expect(w.find('script').exists()).toBe(false)
  expect(w.text()).toContain('<script>x</script>')
})
it('adds an accessible citation button per citation', async () => {
  const btn = w.get('button.ai-cite')
  expect(btn.attributes('aria-label')).toBe('引用 1：上市個股行情')
  expect(btn.text()).toBe('1')
  await btn.trigger('click')
  expect(w.emitted('cite')).toEqual([[1]])
})
it('skips citation ids that are not in the list', () => {
  // block citations [1, 9]，citations 只有 id 1 → 只有一個按鈕
})
it('headings get no citation buttons', () => { /* heading citations [1] → 沒有 button */ })
```
store 測試：回應的 `blocks`、`citations` 存到 bot message；`content` 仍是 `answer`；歷史仍送 `answer`。fixture 的 `sources: []` 改為 `blocks: [], citations: []`。

- [ ] **Step 2：執行確認失敗** — `npm test -- AnswerBlocks useAssistantStore`。

- [ ] **Step 3：實作**
  - `AnswerBlocks` 根元素 `h('div', { class: 'safe-markdown answer-blocks' }, …)`（沿用 AssistantMessage 既有的 `.safe-markdown` 樣式）；文字經 `renderInlines(parseInline(text))`；按鈕包在 `h('sup', …)` 裡，`type: 'button'`、`class: 'ai-cite'`，`title` 取自 citations 對應項目。
  - 樣式放在 `AssistantMessage.vue` 的 scoped `:deep(.ai-cite)`（小圓角、`--color-primary` 系列、focus-visible 外框）。
  - store：`bot.blocks = res.blocks; bot.citations = res.citations`，移除 `bot.sources`。

- [ ] **Step 4：執行確認通過** — `npm test -- AnswerBlocks useAssistantStore parser SafeMarkdown`。

---

### Task 8：CitationList 與 AssistantMessage

**Files:**
- Create: `components/CitationList.vue`、`components/__tests__/CitationList.spec.ts`
- Modify: `components/AssistantMessage.vue`、`components/__tests__/AssistantMessage.spec.ts`
- Delete: `components/SourceList.vue`

**Interfaces:**
- Consumes: `Citation`、`AnswerBlocks`（Task 7）、`formatTaipeiTime`、`formatTaipei`（`taipeiTime.ts`）。
- Produces: `CitationList.vue`，props `citations: Citation[]`，`defineExpose({ reveal })`，`reveal(id: number): void`。

- [ ] **Step 1：寫失敗測試 `CitationList.spec.ts`**

```ts
it('lists cited items first and groups uncited under 其他查詢資料', () => {
  expect(w.get('.ai-citations__label').text()).toBe('引用（3）')
  expect(w.findAll('.ai-citations__cited > li')).toHaveLength(2)
  expect(w.get('.ai-citations__other-label').text()).toBe('其他查詢資料')
  expect(w.findAll('.ai-citations__other > li')).toHaveLength(1)
})
it('shows external excerpt and internal description as plain text', () => {
  // excerpt '<b>x</b>' → text 含 '<b>x</b>'，沒有 <b> 元素
  // 內部項目顯示 '3 筆'
})
it('summary and meta lines', () => {
  // [1] 上市個股行情 · 台積電 2330；meta 含 '資料 2026-10-21'、'查詢 14:20'、連結 'https://openapi.twse.com.tw/'（target=_blank, rel 含 noopener）
  // 內部項目 summary 沒有 ' · '，meta 有 '平台顧客資料（模擬）' 純文字（url 為 null）
})
it('reveal opens, scrolls to and flashes the item', async () => {
  vi.useFakeTimers()
  const scroll = vi.fn(); Element.prototype.scrollIntoView = scroll
  w.vm.reveal(2)
  const item = w.get('[data-citation-id="2"]')
  expect((item.element as HTMLDetailsElement).open).toBe(true)
  expect(scroll).toHaveBeenCalled()
  expect(item.classes()).toContain('ai-citation--flash')
  vi.advanceTimersByTime(1500); await nextTick()
  expect(item.classes()).not.toContain('ai-citation--flash')
})
```
`AssistantMessage.spec.ts`：原本的 sources 測試改為 citations；新增「點擊上標按鈕後對應項目展開並高亮」（整合 AnswerBlocks → CitationList）；`ai-meta` 只有在有工具或有引用時出現；複製按鈕仍複製 `content`。

- [ ] **Step 2：執行確認失敗** — `npm test -- CitationList AssistantMessage`。

- [ ] **Step 3：實作**
  - 每一項是 `<li><details class="ai-citation" :data-citation-id="c.id">`；summary：`[id] title`，有 `target` 時加 ` · target`；meta 行：`資料 X`（有 dataTime 才顯示，格式同原 `SourceList.dataTime()`）、`查詢 HH:mm`、`sourceName`（url 為 http(s) 時是 `<a target="_blank" rel="noopener noreferrer">` 加 ↗）；展開內容 `excerpt ?? description`。
  - `reveal`：設 `open = true`、`scrollIntoView?.({ block: 'nearest', behavior: 'smooth' })`、加 `ai-citation--flash`，1500 ms 後移除（樣式沿用原 SourceList 的配色與 `--duration-*` token）。
  - `AssistantMessage`：`<AnswerBlocks :blocks :citations @cite="onCite" />` 取代 `<SafeMarkdown>`；`onCite(id)` 呼叫 `citationList.value?.reveal(id)`；`<CitationList ref="citationList">` 取代 `<SourceList>`，分隔線 class 規則照舊。
  - 刪除 `SourceList.vue`；`grep -r "SourceList\|SourceView\|\.sources" frontend/src` 應沒有結果。

- [ ] **Step 4：執行確認通過** — `npm test` 全部 PASS；`npx vue-tsc -b` 無錯誤。

> **Phase B 結束**：主控者整理 `git add frontend/` 與 commit message，交給使用者，等確認後再進 Phase C。

---

## Phase C：文件與實測

### Task 9：規格文件同步

**Files:** `docs/spec/01-architecture.md`、`02-rest-api.md`、`03-tool-catalog.md`、`04-mongodb-schema.md`、`05-error-codes.md`、`06-conversation-scenarios.md`、`07-prompt-examples.md`、`docs/demo-checklist.md`、`docs/superpowers/specs/2026-10-22-answer-citations-design.md`

- [ ] **Step 1：照 spec 第 7 節逐項修改**，內容以實作為準（欄位名稱、型別、上限值對照 Task 1–8 的 Interfaces）。04 §3.3 的 Validation 直接貼 `audit-validator.json` 的新欄位。
- [ ] **Step 2：設計文件升為 v0.2，「狀態」改「已實作」，並把實作時的決定寫回對應小節：**
  - 第 5 節：組裝順序改為「System Prompt → 問題分類 → 安全規則 → 回答格式規則」。
  - 2.3：模型把 `[S1]` 等標註寫進文字時移入 sources；去除區塊文字開頭的 Markdown 符號；非 paragraph 的換行合併為空格；`numbered` 扁平化時依序編號；最終回答空白也算 `AI_ANSWER_FORMAT_INVALID`；refusal 由 `ContentFilteredException` 對應。
  - 2.5：`getCustomerProfile` 的欄位數不含 `subject`、`accessNote`；大盤 excerpt 的格式；公告無公司名稱時 target 只有代號。
  - 4.2：引用項目的摘要列為「[n] 標題 · 對象」，來源名稱放在第二行。
  - 若 Task 1 實測改用不限定 enum，寫進 2.2。
- [ ] **Step 3：demo-checklist 新增引用檢查步驟**（點上標 → 對應項目展開；外部項目有摘錄與連結；內部項目只有筆數；複製內容沒有編號；稽核文件有 `citationId`、`resultHash`、`unverifiedNumbers`，沒有 `result`）。
- [ ] **Step 4：檢查** — `grep -rn "sources" docs/spec/02-rest-api.md` 只剩說明「已移除」的文字；文件中提到的類別名稱都存在於程式碼。

### Task 10：真實模型驗證（主控者與使用者一起做）

- [ ] **Step 1：** 使用者啟動 MongoDB、後端、前端（setup.md），以 `fc.officer` 在 C001 依序問 demo-checklist 的 Q1–Q4、D1、X1、X3，各問一次。
- [ ] **Step 2：** 主控者從 application log 的 `chat answered`／`chat failed` 行彙整：`AI_ANSWER_FORMAT_INVALID` 次數、`citedBlocks / 非 heading 區塊數`、`peakOutputTokens` 最大值；從稽核 collection 取出各題的 `unverifiedNumbers`（查詢指令見 setup.md 7.2）。
- [ ] **Step 3：** 把結果寫進設計文件新增的「6.4 實測結果」表格（題號、JSON 失敗、有來源區塊比例、unverifiedNumbers、peakOutputTokens），並逐一判斷 unverifiedNumbers 是否誤判。
- [ ] **Step 4：** 依 `peakOutputTokens` 決定 `max-output-tokens`（最大值 × 1.3，取百位整數，不低於 1500），更新 `application.yml` 與 01／設計文件中的數值。

> **Phase C 結束**：主控者整理 `git add docs/ backend/src/main/resources/application.yml` 與 commit message，交給使用者。
