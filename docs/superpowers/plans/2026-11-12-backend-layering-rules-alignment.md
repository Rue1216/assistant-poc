# 後端分層守則細項對齊 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓 `backend/.../aiassistant` 符合 reference.md §3-0 守則細項中不需平台原始碼的部分，對外 API 與 Mongo 資料格式完全不變。

**Architecture:** 分四階段：Domain 清理 → 持久化（`MongoTemplate` + `@Document` + MapStruct）→ Application（command/query、聚合規則、UseCase 交易）→ Interface 與文件。每階段結束跑完整測試、交付 commit 指令並等使用者 commit。

**Tech Stack:** Java 25、Spring Boot 4.1.0、Spring Data MongoDB（`MongoTemplate`）、MapStruct 1.6.3、Lombok、JUnit 5 + AssertJ + Testcontainers（`mongo:8` replica set）、AspectJ（`spring-boot-starter-aspectj` 已在 pom）。

**Spec:** [docs/superpowers/specs/2026-11-12-backend-layering-rules-alignment-design.md](../specs/2026-11-12-backend-layering-rules-alignment-design.md)（執行者必讀，本計畫只寫 spec 沒決定的事）

## Global Constraints

- 所有路徑以 `backend/src/main/java/com/example/copilot/aiassistant/`（下稱 `main/`）與 `backend/src/test/java/com/example/copilot/aiassistant/`（下稱 `test/`）為根。指令在 `backend/` 執行，Testcontainers 需要 Docker 在跑。
- 對外行為不變：API 路徑、HTTP status、`ApiError` 的 `code` 與 `details[].field`、回應 JSON 欄位全部不變；前端零改動。
- Mongo 欄位不變：不寫 `_class`；現行明確寫入的 null 欄位仍存在；集合名稱維持 `COLL_ICRM_AI_ASSISTANT_CONFIG` / `_AUDIT` / `_EVAL_RUN`。
- 不得修改 `com.example.{aspect, client, constant, exception, filter, logging, persistence, platform, shared}`：那是使用者抄入的平台副本。可以 import `com.example.shared.annotation.UseCase`。
- **不要 commit**（使用者規則）。每階段最後一步是交付 `git add` 清單與 commit message，然後停下等使用者確認已 commit，才開始下一階段。
- 子代理不做個別 task 的 commit。
- 格式規則（逐字）：`CertNo.PATTERN = "^[A-Z0-9&]{8,12}$"`、`ConfigKey.PATTERN = "^(system|instruction)\\.[a-z0-9-]{1,50}$"`、`TraceId.PATTERN = "^[0-9a-f]{16,32}$"`。
- 交易管理器 bean 名稱：`aiAssistantMongoTransactionManager`。

## Review Focus

1. **改版前寫入的舊資料**（帶 `_class`、EvalRun 缺少後來新增的欄位如 `routeOk`、`routeSource`）必須仍可被新的 Document 類別讀回且數值相同 → Task 5、Task 7 加「讀舊格式文件」測試。
2. **多實例同時跑排程發布** → 恰好一個成功、輸家不記 WARN、封存不殘留 → Task 11 測試 `concurrentPublishScheduledPublishesOnce`。
3. **交易回滾時快取不得失效、提交後才失效** → Task 11 測試 `cacheInvalidatedOnlyAfterCommit`、`cacheKeptWhenPublishRollsBack`。
4. **`scheduledAt` 格式錯誤、空字串、null** → 錯誤回應與現在相同（`AI_INPUT_INVALID`，`details[0].field = "scheduledAt"`）→ Task 13 沿用並補測 `AdminConfigControllerTest`。
5. **path 參數格式不符**（certNo 小寫、configKey 大寫、traceId 太短）→ 回應仍是 400 + 原本的欄位名稱，不是 500 → Task 3 controller 測試。

---

## Task 0: 基準建置

**Files:** 無（只檢查）

- [ ] **Step 1: 跑基準**

Run: `./mvnw -q -o test`
Expected: BUILD SUCCESS。

- [ ] **Step 2: 失敗時停下**

2026-11-12 規劃時，`./mvnw -q -o compile` 失敗於 `com/example/platform/security/domain/SecurityUser.java:[140,12] invalid method declaration`，該檔是使用者抄入的平台副本（未納版控）。編譯或 Spring context 失敗若來自 `com.example.{aspect, client, constant, exception, filter, logging, persistence, platform, shared}`，**停下並回報使用者**，由使用者修正或移除副本；不要自行修改。基準全綠後才開始 Task 1。記下基準的測試數量，供各階段比對。

**2026-11-12 已完成**：使用者同意把 SecurityUser 相關的 5 個副本（`SecurityUser`、`CurrentUser`、`CurrentUserProvider`、`ClientIpResolver`、`AuditLogAspect`）收進 `stash@{0}`（"platform copies that do not compile yet (SecurityUser chain)"）。基準：`./mvnw -o test` → Tests run: 971, Failures: 0, Errors: 0, Skipped: 4，BUILD SUCCESS。不要 pop 這個 stash。

---

# 第 1 階段：Domain 清理

## Task 1: 三個值物件

**Files:**
- Create: `main/domain/customer/CertNo.java`、`main/domain/config/ConfigKey.java`、`main/domain/audit/TraceId.java`
- Test: `test/domain/customer/CertNoTest.java`、`test/domain/config/ConfigKeyTest.java`、`test/domain/audit/TraceIdTest.java`

**Interfaces:**
- Produces:
  - `record CertNo(String value)`、`public static final String PATTERN`；`toString()` 回傳 `"CertNo[***]"`（身分值不進 log）。
  - `record ConfigKey(String value)`、`PATTERN`、`public static final ConfigKey SYSTEM_MAIN`（`"system.main"`）、`boolean isSystemPrompt()`（等於 `system.main`）、`boolean isInstruction()`（以 `instruction.` 開頭）；`toString()` 回傳 `value`（現有 log 用 `configKey={}`）。
  - `record TraceId(String value)`、`PATTERN`；`toString()` 回傳 `value`。
  - 三者建構子：null 或不符 PATTERN → `new AssistantException(ErrorCode.AI_INPUT_INVALID, List.of(new FieldDetail("<certNo|configKey|traceId>")))`。

- [ ] **Step 1: 寫失敗測試**

每個值物件一個測試類別，至少包含：

```java
@Test void acceptsValid()            { assertThat(new ConfigKey("instruction.asset-liability").value()).isEqualTo("instruction.asset-liability"); }
@Test void rejectsInvalid()          { assertInvalid(() -> new ConfigKey("Instruction.X"), "configKey"); }
@Test void rejectsNull()             { assertInvalid(() -> new ConfigKey(null), "configKey"); }
@Test void systemMainIsSystemPrompt(){ assertThat(ConfigKey.SYSTEM_MAIN.isSystemPrompt()).isTrue(); assertThat(ConfigKey.SYSTEM_MAIN.isInstruction()).isFalse(); }
// CertNo：合法 "A123456789"、不合法 "a123456789" 與 "A12"；toString() 不含值
// TraceId：合法 16 與 32 碼小寫 hex；不合法 15 碼、33 碼、含大寫
```

`assertInvalid` 斷言拋出 `AssistantException`，`code() == AI_INPUT_INVALID`，details 只有一筆 `FieldDetail` 且 field 等於傳入的名稱。

- [ ] **Step 2: 確認失敗**

Run: `./mvnw -q -o test -Dtest='CertNoTest,ConfigKeyTest,TraceIdTest'`
Expected: 編譯失敗（類別不存在）。

- [ ] **Step 3: 實作三個 record**（compact constructor 驗證）

- [ ] **Step 4: 確認通過**

Run: 同 Step 2。Expected: PASS。

## Task 2: 集合名稱常數與 `@JsonIgnore`

**Files:**
- Create: `main/infrastructure/mongo/AiAssistantCollections.java`
- Modify: `main/domain/audit/AuditRecord.java`（刪 `COLLECTION`）、`main/domain/eval/EvalRun.java`（刪 `COLLECTION`）、`main/domain/privacy/CustomerContext.java`（刪兩個 `@JsonIgnore` 與 import）、`main/infrastructure/mongo/{AssistantConfigDocument, AuditRepositoryImpl, EvalRunRepositoryImpl, FeedbackStatsQueryRepositoryImpl, ConfigQueryRepositoryImpl, MongoSchemaInitializer}.java`、所有引用舊常數的測試（`grep -rn "AuditRecord.COLLECTION\|EvalRun.COLLECTION\|COLL_ICRM_AI_ASSISTANT" src`）
- Test: `test/domain/privacy/CustomerContextExposureTest.java`

**Interfaces:**
- Produces: `public final class AiAssistantCollections { public static final String CONFIG = "COLL_ICRM_AI_ASSISTANT_CONFIG"; AUDIT = "COLL_ICRM_AI_ASSISTANT_AUDIT"; EVAL_RUN = "COLL_ICRM_AI_ASSISTANT_EVAL_RUN"; }`（private 建構子、各常數 Javadoc，比照 `com.example.persistence.mongodb.common.constant.CommonCollections`）。

- [ ] **Step 1: 寫失敗測試 `CustomerContextExposureTest`**

掃描 `src/main/java/.../aiassistant/interfaces/rest/**/dto/*.java` 與 `infrastructure/mongo/*Document.java` 的原始碼，斷言沒有任何一個檔案出現 `CustomerContext` 這個字。另一個測試斷言 `src/main/java/.../aiassistant/domain` 下沒有任何檔案 import `com.fasterxml.jackson.annotation.JsonIgnore`。第二個測試目前會失敗。

- [ ] **Step 2: 確認失敗**

Run: `./mvnw -q -o test -Dtest=CustomerContextExposureTest`
Expected: FAIL（`CustomerContext.java` 有 `JsonIgnore`）。

- [ ] **Step 3: 實作**

刪 `@JsonIgnore`；新增 `AiAssistantCollections`；所有 `"COLL_ICRM_AI_ASSISTANT_*"` 字串與舊常數改引用它（`MongoSchemaInitializer` 的 validator 檔名不動）。

- [ ] **Step 4: 確認通過**

Run: `./mvnw -q -o test -Dtest='CustomerContextExposureTest,MongoSchemaTest,AuditLoggerTest'`
Expected: PASS。

## Task 3: 值物件推進到 application 入口與 domain

**Files（主要；以編譯錯誤為導引補齊）：**
- Modify domain: `domain/config/AssistantConfig.java`（`ConfigKey configKey`；`newDraft` 參數改 `ConfigKey`；刪 `SYSTEM_PROMPT_KEY`、`INSTRUCTION_KEY_PREFIX`）、`domain/eval/EvalRun.java`（`ConfigKey configKey`）、`domain/privacy/CustomerContext.java`（`CertNo certNo`）、`domain/repository/AssistantConfigRepository.java`、`domain/repository/EvalRunRepository.java`（configKey 參數改 `ConfigKey`）、`domain/repository/AuditRepository.java`（`setFeedback(TraceId traceId, ...)`）
- Modify application: `config/ConfigCommandService`、`config/ConfigQueryService`、`config/ConfigQueryRepository`、`feedback/FeedbackService`、`feedback/FeedbackQueryService`、`feedback/FeedbackStatsQueryRepository`、`evaluation/EvalRunService`、`chat/CustomerContextFactory`（`create(UserContext, CertNo)`、`forEvaluation(CertNo, AccessLevel)`；呼叫 port 時 `.value()`）、`chat/ChatOrchestrator`（`customer.certNo().value()` 傳給 port）、`evaluation/EvalRunner`（`new CertNo(c.certNo())`）
- Modify infrastructure: `mongo/AssistantConfigMapper`、`mongo/*RepositoryImpl`、`seed/SeedLoader`（字串 ↔ 值物件；Query 側投影 record 維持 `String`）
- Modify interfaces: 所有 controller（path variable 仍宣告 `String` 並保留 `@Pattern`，但改為 `@Pattern(regexp = ConfigKey.PATTERN)` / `CertNo.PATTERN` / `TraceId.PATTERN`；呼叫前 `new ConfigKey(configKey)`）、`config/dto/ConfigKeys.java`（刪 `PATTERN`，保留 `NAME_PATTERN`）、`config/dto/CreateConfigRequest.java`（`@Pattern(regexp = ConfigKey.PATTERN)`）、View 類別（`config.configKey().value()`）
- Modify tests：所有呼叫上述簽章的測試
- Test: `test/interfaces/rest/PathVariableFormatTest.java`（新）

**Interfaces:**
- Consumes: Task 1 的三個 record、Task 2 的 `AiAssistantCollections`。
- Produces（後續 task 依賴的簽章）：
  - `AssistantConfig(String id, ConfigKey configKey, ConfigType configType, ...其餘不變)`
  - `AssistantConfigRepository`：`findPublished(ConfigKey)`、`findWorkingCopy(ConfigKey)`、`findLatest(ConfigKey)`、`findVersion(ConfigKey, int)`，其餘不變
  - `EvalRunRepository.recent(ConfigKey, int)`；`AuditRepository.setFeedback(TraceId, String userId, FeedbackRating, Instant)`
  - `CustomerContext(CertNo certNo, AccessLevel, Set<String>, Set<String>)`
  - application 入口方法的 `configKey` / `certNo` / `traceId` 參數改為值物件；`ConfigSummary`、`VersionHistoryEntry`、`VersionFeedback`、`PromptVersion`、`AuditRecord.traceId`、`ChatResult.traceId`、`CustomerRecord`、`EvalCase` 維持 `String`（spec §3.3 表格）
  - port 介面不變

- [ ] **Step 1: 寫 `PathVariableFormatTest`（`extends IntegrationTestBase`）**

```java
// 每個請求以有權限的 TestUsers 登入（沿用 AdminConfigControllerTest / ChatControllerTest 的登入方式）
@Test void lowercaseCertNoIs400WithField()  // POST /api/ai-assistant/customers/a123456789/chat → 400, $.code == "AI_INPUT_INVALID", $.details[0].field == "certNo"
@Test void uppercaseConfigKeyIs400WithField() // GET /api/ai-assistant/admin/configs/Instruction.X/versions → 400, field "configKey"
@Test void shortTraceIdIs400WithField()      // PUT /api/ai-assistant/chat/abc/feedback → 400, field "traceId"
```

實際 URL 以 `AdminConfigControllerTest`、`ChatControllerTest`、`FeedbackControllerTest` 的常數為準。

- [ ] **Step 2: 在改版前的程式碼上跑，確認通過**（這是行為釘子，改版後必須仍通過）

Run: `./mvnw -q -o test -Dtest=PathVariableFormatTest`
Expected: PASS。

- [ ] **Step 3: 修改簽章**

依 Produces 改，跟著編譯錯誤逐一修正（main 與 test）。`createKey` 的 key/type 檢查暫時改用 `ConfigKey.isSystemPrompt()` / `isInstruction()`（Task 9 再移進聚合）。

- [ ] **Step 4: 完整測試**

Run: `./mvnw -q -o test`
Expected: BUILD SUCCESS；測試數 = 基準 + 新增數。

## Task 4: 第 1 階段交付

- [ ] **Step 1:** `git status --short`，確認只動到 `backend/src/**`。
- [ ] **Step 2:** 交付給使用者（不要自己執行）：

```
git add backend/src
git commit -m "refactor(domain): value objects for certNo/configKey/traceId, central collection names, drop @JsonIgnore"
```

- [ ] **Step 3:** 停下，等使用者確認已 commit。

---

# 第 2 階段：持久化

## Task 5: 欄位形狀測試、`_class` 設定、Config 改用 `MongoTemplate` + MapStruct

**Files:**
- Create: `test/infrastructure/mongo/DocumentShapeTest.java`、`backend/src/test/java/com/example/copilot/testsupport/ConfigDocumentStore.java`（與既有 `LogCapture`、`MockCustomerPorts` 同處）、`main/infrastructure/mongo/AssistantConfigDocumentMapper.java`
- Modify: `main/infrastructure/mongo/MongoConfiguration.java`、`AssistantConfigDocument.java`（刪 `@TypeAlias`）、`AssistantConfigRepositoryImpl.java`
- Delete: `main/infrastructure/mongo/AssistantConfigMongoRepository.java`、`AssistantConfigMapper.java`、`test/infrastructure/mongo/AssistantConfigMapperTest.java`（改由 `AssistantConfigDocumentMapperTest` 取代）
- Modify tests that inject `AssistantConfigMongoRepository`：`EvalRunServiceTest`、`AssistantConfigRepositoryImplTest`、`ConfigQueryRepositoryImplTest`、`MongoConfigurationTest`、`MongoSchemaTest`、`ScheduledPublishJobTest`、`SeedLoaderTest`、`AdminConfigControllerTest`、`EvalControllerTest`
- Test: `test/infrastructure/mongo/AssistantConfigDocumentMapperTest.java`

**Interfaces:**
- Consumes: Task 3 的 `AssistantConfigRepository` 簽章。
- Produces:
  - `@Mapper(componentModel = "spring") interface AssistantConfigDocumentMapper { AssistantConfigDocument toDocument(AssistantConfig c); AssistantConfig toDomain(AssistantConfigDocument d); default String map(ConfigKey k) {...}; default ConfigKey map(String s) {...} }`
  - 測試輔助 `@Component`-free 類別 `ConfigDocumentStore(MongoTemplate mongo)`：`deleteAll()`、`List<AssistantConfigDocument> findAll()`、`Optional<AssistantConfigDocument> findByConfigKeyAndStatus(String, ConfigStatus)`、`List<AssistantConfigDocument> findByConfigKeyOrderByVersionDesc(String)`、`Optional<AssistantConfigDocument> findTopByConfigKeyOrderByVersionDesc(String)`、`AssistantConfigDocument insert(AssistantConfigDocument)`、`AssistantConfigDocument save(AssistantConfigDocument)`。測試以 `new ConfigDocumentStore(mongoTemplate)` 取代原本注入的 Spring Data repository，呼叫端只改型別。

- [ ] **Step 1: 寫 `DocumentShapeTest`（`extends IntegrationTestBase`），在改版前的程式碼上跑**

三個測試：分別透過 `AssistantConfigRepository.insert`、`AuditRepository.insert`、`EvalRunRepository.insert` + `appendCase` + `complete` 寫入，再以 `mongo.getCollection(AiAssistantCollections.X).find(eq("_id", ...)).first()` 讀原生 `Document`，斷言 `keySet()` 等於寫死的預期集合（從現行 `AssistantConfigMapper.toDocument`、`AuditRecordMapper.toDocument`、`EvalRunRepositoryImpl.toDocument/summaryDocument/caseDocument/resultDocument/judgeDocument` 的 `append` 清單抄出，含巢狀層：`toolCalls[0]`、`toolCalls[0].result`、`cases[0].baseline`、`summary.baseline` 等），且頂層不含 `_class`。Audit 樣本要讓 `answer`、`errorCode`、`instructionKey`、`modelName` 為 null，並有一個 `toolCalls[0].result` 是巢狀 map（含 list 與子 map）。斷言 null 欄位 `containsKey` 且值為 null。

**Config 例外**：現行 config 會寫 `_class`（`@TypeAlias`）。config 的形狀測試斷言「不含 `_class`」，所以在改版前會失敗，這是預期的；audit 與 eval_run 兩個測試在改版前必須通過。

- [ ] **Step 2: 確認**

Run: `./mvnw -q -o test -Dtest=DocumentShapeTest`
Expected: audit、eval_run 通過；config 因 `_class` 失敗。

- [ ] **Step 3: 寫 `AssistantConfigDocumentMapperTest`**（純單元，`Mappers.getMapper(...)`）：全部 15 個欄位來回轉換相等；`configKey` 值物件 ↔ 字串。另加 `readsLegacyDocumentWithClassField`（整合測試，可放 `AssistantConfigRepositoryImplTest`）：以原生 `Document` 插入一筆帶 `_class: "com.example.copilot.aiassistant.config.AssistantConfig"` 的 config，`repository.findVersion(...)` 讀回欄位正確。

- [ ] **Step 4: 實作**

- `MongoConfiguration`：以 `MappingMongoConverter` 設 `setTypeMapper(new DefaultMongoTypeMapper(null))`（覆寫 Boot 自動設定的 converter bean，或用 `BeanPostProcessor` 修改，二擇一，以不破壞 Boot 的 `MongoCustomConversions` 為準）。
- 刪 `@TypeAlias`、刪 Spring Data 介面；`AssistantConfigRepositoryImpl` 以 `Query.query(where("configKey").is(key.value()).and("status").is(...))`、`.with(Sort.by(DESC, "version")).limit(1)` 等改寫 7 個查詢；寫入用 `mongo.insert` / `mongo.save`；`count()` 用 `mongo.count(new Query(), AssistantConfigDocument.class)`。`TransactionTemplate` 本階段保留。
- 注入 `AssistantConfigDocumentMapper`。

- [ ] **Step 5: 完整測試**

Run: `./mvnw -q -o test`
Expected: BUILD SUCCESS，`DocumentShapeTest` 三個都過。

## Task 6: Audit 改用 `@Document` 類別

**Files:**
- Create: `main/infrastructure/mongo/AuditRecordDocument.java`（含 static nested `ToolCallDocument`、`PromptVersionDocument`）、`main/infrastructure/mongo/AuditRecordDocumentMapper.java`
- Modify: `main/infrastructure/mongo/AuditRepositoryImpl.java`
- Delete: `main/infrastructure/mongo/AuditRecordMapper.java`
- Test: `test/infrastructure/mongo/AuditRecordDocumentMapperTest.java`

**Interfaces:**
- Consumes: `AuditRepository`（Task 3）、`AiAssistantCollections.AUDIT`。
- Produces: `@Mapper(componentModel = "spring") interface AuditRecordDocumentMapper { AuditRecordDocument toDocument(AuditRecord r); }`（Command 側只寫不讀，不需 `toDomain`）。

- [ ] **Step 1: 寫 `AuditRecordDocumentMapperTest`**：所有欄位（含 `toolCalls`、`promptVersions`、巢狀 `result` map）轉換後值相等；`createdAt` 保持 `Instant`。
- [ ] **Step 2: 確認失敗**：`./mvnw -q -o test -Dtest=AuditRecordDocumentMapperTest` → 編譯失敗。
- [ ] **Step 3: 實作**

`@Document(AiAssistantCollections.AUDIT)`、Lombok `@Getter`/`@Setter`、欄位 Javadoc；現行明確寫 null 的欄位（逐一比對舊 `AuditRecordMapper` 的 `append`，含 `feedback`、`feedbackAt`）加 `@Field(write = Field.Write.ALWAYS)`。`ToolCallDocument.result` 型別 `Map<String, Object>`，為 null 時**不寫入**（現行行為：`if (c.result() != null)`），所以 `result` 不加 ALWAYS。`AuditRepositoryImpl.insert` 改 `mongo.insert(mapper.toDocument(record))`；`setFeedback` 的 `findAndModify` 改用 `AuditRecordDocument.class`。

- [ ] **Step 4: 完整測試**：`./mvnw -q -o test` → BUILD SUCCESS（`DocumentShapeTest` 的 audit 形狀不變、`AuditLoggerTest`、`FeedbackStatsQueryRepositoryImplTest` 過）。

## Task 7: EvalRun 改用 `@Document` 類別

**Files:**
- Create: `main/infrastructure/mongo/EvalRunDocument.java`（nested：`PromptVersionDocument` 可共用 Task 6 的、`SummaryDocument`、`MetricsDocument`、`CaseDocument`、`ResultDocument`、`JudgeDocument`）、`main/infrastructure/mongo/EvalRunDocumentMapper.java`
- Modify: `main/infrastructure/mongo/EvalRunRepositoryImpl.java`（刪所有 `toDocument` / `fromDocument` / `*Record` / `num` / `intOrZero` / `instant` 靜態方法）
- Test: `test/infrastructure/mongo/EvalRunDocumentMapperTest.java`、`test/infrastructure/mongo/EvalRunRepositoryImplTest.java`（若不存在則新建，`extends IntegrationTestBase`）

**Interfaces:**
- Consumes: `EvalRunRepository`（Task 3）。
- Produces: `@Mapper(componentModel = "spring") interface EvalRunDocumentMapper { EvalRunDocument toDocument(EvalRun r); EvalRun toDomain(EvalRunDocument d); EvalRunDocument.CaseDocument toDocument(EvalCaseRecord c); EvalRunDocument.SummaryDocument toDocument(EvalSummary s); }`
- `EvalRunDocument.docVersion` 是普通 `long` 欄位（**不加** `@Version`：更新走條件式 `updateFirst`，不是 `save`）。數值欄位用原始型別 `int`/`long`，讓舊文件缺欄位時讀成 0（取代 `intOrZero`）。

- [ ] **Step 1: 寫測試**
  - `EvalRunDocumentMapperTest.roundTrip`：含 summary、2 筆 case（含 judge）的 `EvalRun` 轉成 Document 再轉回，`isEqualTo` 原物件。
  - `EvalRunRepositoryImplTest.readsLegacyRunWithoutRouteFields`：以原生 `Document` 插入一筆舊格式 run（`cases[0].baseline` 缺 `routeOk`、`routeSource`、`instructionKey`；`summary.baseline` 缺 `routeOk`、`routeCases`），`findById` 讀回：缺的 int 為 0、缺的字串為 null，其他欄位正確。這個測試先在改版前跑，確認通過。
- [ ] **Step 2: 確認**：`./mvnw -q -o test -Dtest='EvalRunDocumentMapperTest,EvalRunRepositoryImplTest'` → mapper 測試編譯失敗；legacy 測試在改版前通過。
- [ ] **Step 3: 實作**：`insert` 用 `mongo.insert(mapper.toDocument(run))` 並回傳產生的 id；`findById` / `findRunning` / `recent`（`recent` 以 `Query.fields().exclude("cases")` 維持現行不讀 cases 的行為）用 `EvalRunDocument.class`；`appendCase` 的 `push("cases", mapper.toDocument(c))`、`complete` 的 `set("summary", mapper.toDocument(s))` 照舊用 `Update`，由 `MongoTemplate` 轉換。null 欄位 ALWAYS 規則同 Task 6，逐一比對舊 `append` 清單。
- [ ] **Step 4: 完整測試**：`./mvnw -q -o test` → BUILD SUCCESS（含 `DocumentShapeTest`、`EvalRunServiceTest`、`EvalRunnerTest*`、`EvalControllerTest`）。

## Task 8: 第 2 階段交付

- [ ] **Step 1:** 確認 `grep -rn "org.bson.Document" backend/src/main/java/com/example/copilot/aiassistant/infrastructure/mongo` 只剩 `MongoSchemaInitializer`（validator 指令）與 `FeedbackStatsQueryRepositoryImpl`（aggregation 結果，Query 側）。
- [ ] **Step 2:** 交付：

```
git add backend/src
git commit -m "refactor(persistence): MongoTemplate + Criteria, @Document classes for audit and eval runs, MapStruct mappers, no _class"
```

- [ ] **Step 3:** 停下，等使用者確認已 commit。

---

# 第 3 階段：Application

## Task 9: command / query 目錄與改名（純搬移）

**Files:**（`git mv` 保留歷史）

| 原位置 | 新位置 |
| --- | --- |
| `main/application/config/ConfigCommandService.java` | `main/application/command/config/ConfigCommandUseCase.java` |
| `main/application/config/{SaveCommand, PublishCommand, ApplyCommand, PublishMode}.java` | `main/application/command/config/` |
| `main/application/config/SaveMode.java` | `main/domain/config/SaveMode.java` |
| `main/application/feedback/FeedbackService.java` | `main/application/command/feedback/FeedbackCommandUseCase.java` |
| `main/application/evaluation/EvalRunService.java` | 拆成 `main/application/command/evaluation/EvalRunCommandUseCase.java`（`start`、`EvalSnapshot`）與 `main/application/query/evaluation/EvalRunQueryService.java`（`current`、`get`、`recent`） |
| `main/application/chat/ChatOrchestrator.java`、`ChatCommand.java` | `main/application/command/chat/ChatUseCase.java`、`ChatCommand.java` |
| `main/application/config/{ConfigQueryService, ConfigQueryRepository, ConfigSummary, VersionHistoryEntry}.java` | `main/application/query/config/` |
| `main/application/feedback/{FeedbackQueryService, FeedbackStatsQueryRepository, VersionFeedback}.java` | `main/application/query/feedback/` |
| `main/application/chat/SuggestionService.java` | `main/application/query/chat/SuggestionQueryService.java` |

`ConfigCommandUseCase.validateWorkingCopy` 與 `findWorkingCopy` 移到 `ConfigQueryService`（`existingWorkingCopy` 的 NOT_FOUND / CONFLICT 判斷一起搬，Command 側也還要用：在兩邊各保留一份私有方法，或抽到 `AssistantConfigRepository` 的 default 方法；選前者，避免 domain repository 帶錯誤碼邏輯）。測試檔對應搬移並改名（`ConfigCommandServiceTest` → `test/application/command/config/ConfigCommandUseCaseTest` 等；`EvalRunServiceTest` 依方法拆兩檔）。

**Interfaces:**
- Produces: 類別名與套件如上表；4 個 Command 入口類別標 `@com.example.shared.annotation.UseCase`、移除 `@Service`；Query 入口維持 `@Service`。方法簽章不變（Task 3 的版本）。

- [ ] **Step 1: 搬移與改名**，修正所有 import（main、test、`DomainBeanConfiguration`、`EvalConfiguration`、controller、`ScheduledPublishJob`）。
- [ ] **Step 2: 完整測試**：`./mvnw -q -o test` → BUILD SUCCESS，測試數不變。
- [ ] **Step 3:** `grep -rn "ConfigCommandService\|ChatOrchestrator\|EvalRunService\|FeedbackService\b\|SuggestionService\b" backend/src` → 無結果。

## Task 10: 業務規則收進 `AssistantConfig`、`ScheduleWindow`

**Files:**
- Modify: `main/domain/config/AssistantConfig.java`、`main/application/command/config/ConfigCommandUseCase.java`、`main/infrastructure/config/DomainBeanConfiguration.java`
- Create: `main/domain/config/ScheduleWindow.java`
- Test: `test/domain/config/AssistantConfigTest.java`、`test/domain/config/ScheduleWindowTest.java`

**Interfaces:**
- Produces（`AssistantConfig`）：
  - `static AssistantConfig newKey(ConfigKey key, ConfigType type, String name, String content, String actor, Instant now)`：version 1 的 DRAFT；`SYSTEM_PROMPT` 必須 `key.isSystemPrompt()`、`SYSTEM_INSTRUCTION` 必須 `key.isInstruction()`，否則 `AI_INPUT_INVALID(configKey)`
  - `void requireDocVersion(Long expected)` → 不等於 `docVersion` 時 `AI_CONFIG_CONFLICT`
  - `AssistantConfig discard(String actor, Instant now)` → 非 DRAFT 時 `AI_CONFIG_INVALID_STATE`；結果 DISCARDED，保留原 `scheduledAt`，touched
  - `void requireApplicable()` → 非 PUBLISHED / ARCHIVED / DISCARDED 時 `AI_CONFIG_INVALID_STATE`
  - `boolean keepsScheduleOnSave(SaveMode mode)` → `status == SCHEDULED && mode == KEEP_STATUS`
  - `AssistantConfig edit(String name, String content, String changeNote, SaveMode mode, String actor, Instant now)` → 不維持排程且目前 SCHEDULED 時改 DRAFT、`scheduledAt = null`；內容更新、touched
  - `AssistantConfig schedule(Instant at, String actor, Instant now)` → SCHEDULED + `scheduledAt = at`，touched
  - `AssistantConfig publishNow(String actor, Instant now)` → PUBLISHED + `scheduledAt = null`，touched
  - `AssistantConfig copyAsDraft(int nextVersion, String actor, Instant now)` → 新 DRAFT（id、docVersion 為 null），沿用 type / name / content，`basedOnVersion = this.version`
  - `withStatus`、`withContent`、`withBasedOnVersion`、`touched` 改為 private 或刪除（`grep` 確認沒有外部呼叫後）
- Produces：`record ScheduleWindow(Duration minLead, Duration maxLead) { void requireWithin(Instant at, Instant now); }` → `at` 為 null、早於 `now + minLead` 或晚於 `now + maxLead` 時 `AI_INPUT_INVALID(scheduledAt)`；bean 由 `DomainBeanConfiguration` 以 `props.config().scheduleMinLead()` / `scheduleMaxLead()` 建立。

- [ ] **Step 1: 寫失敗測試**：`AssistantConfigTest` 對上面每個方法各至少一個合法、一個非法案例，斷言錯誤碼與 `FieldDetail`；`ScheduleWindowTest` 涵蓋 null、剛好等於下限（合法）、早一秒、剛好等於上限（合法）、晚一秒。
- [ ] **Step 2: 確認失敗**：`./mvnw -q -o test -Dtest='AssistantConfigTest,ScheduleWindowTest'` → 編譯失敗。
- [ ] **Step 3: 實作聚合方法與 `ScheduleWindow`**，`ConfigCommandUseCase` 改為呼叫它們（刪 `APPLICABLE`、`requireDocVersion`、`requireScheduleWindow`、key/type 判斷）。`createKey` 把 `AI_CONFIG_CONFLICT` 轉 `AI_CONFIG_INVALID_STATE` 的 catch 保留。
- [ ] **Step 4: 完整測試**：`./mvnw -q -o test` → BUILD SUCCESS（`ConfigCommandUseCaseTest`、`AdminConfigControllerTest` 行為不變）。

## Task 11: 交易移到 UseCase、排程迴圈移到 Job、提交後才清快取

**Files:**
- Create: `main/application/support/Transactions.java`、`main/application/command/config/ConfigPublishedEvent.java`、`main/application/command/config/ScheduledPublishOutcome.java`、`main/infrastructure/mongo/MongoConflicts.java`、`main/infrastructure/mongo/ConfigTransactionConflictAspect.java`
- Modify: `main/infrastructure/mongo/MongoConfiguration.java`、`main/infrastructure/mongo/AssistantConfigRepositoryImpl.java`、`main/domain/repository/AssistantConfigRepository.java`、`main/application/command/config/ConfigCommandUseCase.java`、`main/application/config/PublishedConfigCache.java`、`main/infrastructure/scheduler/ScheduledPublishJob.java`
- Test: `test/application/command/config/ConfigCommandUseCaseTransactionTest.java`（新，`extends IntegrationTestBase`）、修改 `AssistantConfigRepositoryImplTest`、`ScheduledPublishJobTest`、`MongoConfigurationTest`、`PublishedConfigCacheTest`

**Interfaces:**
- Produces:
  - `final class Transactions { public static final String MONGO = "aiAssistantMongoTransactionManager"; }`
  - `MongoConfiguration`：`@Bean(Transactions.MONGO) MongoTransactionManager aiAssistantMongoTransactionManager(MongoDatabaseFactory)`
  - `record ConfigPublishedEvent(ConfigKey configKey)`
  - `enum ScheduledPublishOutcome { PUBLISHED, REVERTED, SKIPPED }`（SKIPPED = 重驗失敗但 `revertScheduled` 回 false，表示已被改動）
  - `ConfigCommandUseCase`：`publish` 與 `applyVersion` 加 `@Transactional(transactionManager = Transactions.MONGO)`；新增 `List<AssistantConfig> findScheduledDue()`（無交易）、`@Transactional(transactionManager = Transactions.MONGO) ScheduledPublishOutcome publishScheduled(AssistantConfig due)`；刪除 `publishDue()`。發布成功時 `ApplicationEventPublisher.publishEvent(new ConfigPublishedEvent(key))`，不再直接呼叫 `cache.invalidate()`。
  - `AssistantConfigRepository.publishScheduled(AssistantConfig due, Instant now)` 改回傳 `void`：認領失敗拋 `AssistantException(AI_CONFIG_CONFLICT)`。`publish`、`replaceWorkingCopy`、`publishScheduled` 不再自己開交易（刪 `TransactionTemplate`、刪建構子的 `MongoTransactionManager` 參數）。
  - `final class MongoConflicts { static boolean isConflict(Throwable e); }`：現行 `isMongoConflict` 的邏輯（112、11000、TransientTransactionError、UnknownTransactionCommitResult label），`write()` 與 aspect 共用。
  - `ConfigTransactionConflictAspect`：`@Aspect @Component @Order(Ordered.HIGHEST_PRECEDENCE)`，`@Around("execution(public * com.example.copilot.aiassistant.application.command.config.ConfigCommandUseCase.*(..))")`；捕捉 `RuntimeException`，若非 `AssistantException` 且 `MongoConflicts.isConflict(e)` 為真，改拋 `AssistantException(AI_CONFIG_CONFLICT)`，否則原樣拋。
  - `PublishedConfigCache`：`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) public void onPublished(ConfigPublishedEvent event)` → `invalidate()`。
  - `ScheduledPublishJob.run()`：迴圈 `findScheduledDue()`，每筆呼叫 `publishScheduled(due)`；`isLostRace(RuntimeException)`（從 `AssistantConfigRepositoryImpl` 搬來，改用 `MongoConflicts`）為真記 debug，否則記 `log.warn("scheduled publish failed configKey={} version={} error={}", ...)`；PUBLISHED 筆數 > 0 時 `log.info("published {} scheduled config version(s)", n)`。重驗失敗的 WARN（`scheduled publish rejected ...`）留在 `publishScheduled`。MDC 處理照舊。

- [ ] **Step 1: 寫 / 改測試**

`ConfigCommandUseCaseTransactionTest`：
- `publishRollsBackWhenArchiveFails`：讓目前 PUBLISHED 版本的 docVersion 在 `publish` 讀取後被改動（沿用 `AssistantConfigRepositoryImplTest.publishedVersionChangedBeforeArchiveIsConflictAndRollsBack` 的手法），斷言拋 `AI_CONFIG_CONFLICT`、舊 PUBLISHED 仍在、工作版本仍是 DRAFT。
- `concurrentPublishScheduledPublishesOnce`：同一筆到期 SCHEDULED，兩個執行緒同時呼叫 `useCase.publishScheduled(due)`；斷言恰好一個回 PUBLISHED、另一個拋 `AI_CONFIG_CONFLICT`、該 key 只有一個 PUBLISHED、沒有多餘的 ARCHIVED。
- `cacheInvalidatedOnlyAfterCommit`：發布後 `PublishedConfigCache.current()` 反映新版本。
- `cacheKeptWhenPublishRollsBack`：先 `cache.current()` 暖快取，再觸發會回滾的 publish（同第一個案例），斷言 `onPublished` 沒被呼叫（以 `@MockitoSpyBean PublishedConfigCache` 驗證 `never()`）。
- `commitTimeConflictBecomesConfigConflict`：直接對 aspect 做單元測試，丟入 `new TransactionSystemException("x", new MongoCommandException(writeConflict112, ...))`（或以 `MongoException(112, ...)` 為 cause），斷言轉成 `AI_CONFIG_CONFLICT`；丟入一般 `IllegalStateException` 原樣拋出。

修改既有測試：`AssistantConfigRepositoryImplTest` 中針對 repository 交易的案例（`publishArchivesPreviousPublishedInOneTransaction`、`publishScheduledLosesRaceReturnsFalseAndRollsBackArchive`、`publishScheduledWriteConflictIsLostRaceLoggedAtDebug`、`lostRaceClassification` 等）改為透過 `ConfigCommandUseCase` 或 `ScheduledPublishJob` 驗證同一行為，斷言內容不變（例如「輸家不記 WARN」用 `LogCapture`）；`MongoConfigurationTest` 改注入 `@Qualifier(Transactions.MONGO) MongoTransactionManager` 並改用 `ConfigDocumentStore`；`ScheduledPublishJobTest` 改測 `run()` 的迴圈。

- [ ] **Step 2: 確認失敗**：`./mvnw -q -o test -Dtest=ConfigCommandUseCaseTransactionTest` → 編譯失敗。
- [ ] **Step 3: 實作** 依 Produces。
- [ ] **Step 4: 完整測試**：`./mvnw -q -o test` → BUILD SUCCESS。`grep -rn "TransactionTemplate" backend/src/main` → 無結果。

## Task 12: 第 3 階段交付

- [ ] **Step 1:** 交付：

```
git add backend/src
git commit -m "refactor(application): command/query packages with @UseCase, config rules in the aggregate, transactions on the use case"
```

- [ ] **Step 2:** 停下，等使用者確認已 commit。

---

# 第 4 階段：Interface 與收尾

## Task 13: Controller 清理

**Files:**
- Modify: `main/interfaces/rest/config/AdminConfigController.java`、`main/interfaces/rest/config/dto/PublishRequest.java`、`main/interfaces/rest/AssistantExceptionHandler.java`、`main/interfaces/rest/chat/ChatController.java`、`main/interfaces/rest/chat/SuggestionController.java`、`main/application/command/chat/ChatUseCase.java`、`main/application/query/chat/SuggestionQueryService.java`、`main/application/command/config/ConfigCommandUseCase.java`
- Test: `AdminConfigControllerTest`、`AssistantExceptionHandlerTest`、`ChatControllerTest`、`SuggestionControllerTest`、`ConfigCommandUseCaseTest`

**Interfaces:**
- Produces:
  - `PublishRequest.scheduledAt` 型別改 `OffsetDateTime`；`PublishCommand.scheduledAt` 由 controller 以 `request.scheduledAt() == null ? null : request.scheduledAt().toInstant()` 帶入（只在 SCHEDULED 模式）。
  - `AssistantExceptionHandler` 的 `HttpMessageNotReadableException` 處理：cause chain 中有 Jackson 3 的 `tools.jackson.databind.exc.MismatchedInputException` 時，取其 `getPath()` 最後一個有 `getPropertyName()` 的節點，產生 `List.of(new FieldDetail(name))`；否則空 list。
  - `ConfigCommandUseCase.saveWorkingCopy` 開頭：`content` 的 UTF-8 bytes > `ConfigValidator.MAX_BYTES` → `AI_INPUT_INVALID(content)`。
  - `ChatUseCase.chat(CertNo certNo, ChatCommand command, UserContext user)`：`contexts.create(user, certNo)`，空的時候 `AI_CUSTOMER_NOT_FOUND`，再委派既有 `chat(ChatCommand, UserContext, CustomerContext)`。
  - `SuggestionQueryService.suggestions(CertNo certNo, UserContext user)`：同樣先做存取檢查，再回傳現有結果；舊的無參數 `suggestions()` 刪除（`grep` 確認無其他呼叫者）。
  - `apply` 參數加 `@Valid`。

- [ ] **Step 1: 寫 / 確認測試**
  - `AdminConfigControllerTest` 第 200–204 行已有 `"not-a-date"` → `details[0].field == "scheduledAt"` 的斷言；新增 `scheduledAtEmptyStringIsInvalid`（`"scheduledAt":""`）與 `scheduledAtMissingIsInvalid`（SCHEDULED 但不帶），兩者都斷言 400、`$.code == "AI_INPUT_INVALID"`、`$.details[0].field == "scheduledAt"`。先在改版前跑，確認新增的兩個也通過。
  - `AssistantExceptionHandlerTest`：其他不可讀的 body（例如 JSON 語法錯誤）仍回空 `details`。
  - `ChatControllerTest` / `SuggestionControllerTest` 既有的「無權限顧客 → `AI_CUSTOMER_NOT_FOUND`」測試必須仍通過；檢查順序不變（controller 先檢查長度、再 rate limit、再呼叫 use case）。
- [ ] **Step 2: 實作** 依 Produces；刪 `parseScheduledAt` 與其 try-catch；`save` 內的 `MAX_BYTES` 檢查刪除。
- [ ] **Step 3: 完整測試**：`./mvnw -q -o test` → BUILD SUCCESS。

## Task 14: `ArchitectureRulesTest` 新規則

**Files:**
- Modify: `test/ArchitectureRulesTest.java`

**Interfaces:** 規則對照 spec §6.2 表格。

- [ ] **Step 1: 新增 / 修改測試**
  - `APPLICATION_FORBIDDEN` 移除 `"org.springframework.transaction."`，改加 `"org.springframework.transaction.support."`、`"org.springframework.transaction.interceptor."`；新測試 `transactionAnnotationOnlyInCommand`：`application/` 下 import `org.springframework.transaction.annotation.` 的檔案都在 `application/command/` 內。
  - `DOMAIN_FORBIDDEN` 加 `"com.fasterxml.jackson.annotation.JsonIgnore"`。
  - `INFRASTRUCTURE_FORBIDDEN` 加 `"org.springframework.data.mongodb.repository."`。
  - `controllersHaveNoTryCatch`：`interfaces/**/*Controller.java` 原始碼不含 `catch (`。
  - `transactionalOnlyOnUseCases`：任何含 `@Transactional` 的檔案都含 `@UseCase`。
  - `commandEntryPointsAreUseCases`：`application/command/**/*UseCase.java` 都含 `@UseCase`，且 `application/command/` 下含 `@UseCase` 的檔名都以 `UseCase.java` 結尾。
- [ ] **Step 2: 跑測試**：`./mvnw -q -o test -Dtest=ArchitectureRulesTest` → PASS（如果失敗，代表前面某個 task 漏了，回頭修 main，不改規則）。
- [ ] **Step 3: 反證**：暫時在任一 controller 加一行 `try { } catch (RuntimeException e) { }`，確認 `controllersHaveNoTryCatch` 失敗後移除。

## Task 15: 文件同步

**Files:**
- Modify: `backend/PORTING.md`、`docs/spec/01-architecture.md`、`docs/spec/03-tool-catalog.md`、`docs/spec/04-mongodb-schema.md`、`docs/superpowers/specs/2026-11-02-clean-architecture-layering-design.md`

- [ ] **Step 1: 依 spec §6.3 逐項修改。** 每處改完的內容必須與程式碼一致（類別名、套件、bean 名稱 `aiAssistantMongoTransactionManager`、`Transactions.MONGO`、`AiAssistantCollections`、`ScheduledPublishOutcome`）。PORTING.md 第 13 行「`domain/`、`application/` 的行為維持原樣」改為：行為維持原樣，但結構已依 §3-0 對齊（command / query、`@UseCase`、交易在 UseCase）。
- [ ] **Step 2: 檢查殘留舊名**：`grep -rn "ConfigCommandService\|ChatOrchestrator\|EvalRunService\|FeedbackService\b\|SuggestionService\b\|AssistantConfigMongoRepository\|TransactionTemplate\|org.bson.Document" backend/PORTING.md docs/spec` → 只允許出現在描述「已移除 / 歷史」的句子中，逐一確認。

## Task 16: 第 4 階段交付

- [ ] **Step 1:** `./mvnw -q -o test` → BUILD SUCCESS；回報測試總數（與 Task 0 基準比較）。
- [ ] **Step 2:** `git diff --stat HEAD -- frontend/` → 空（前端零改動）。
- [ ] **Step 3:** 交付：

```
git add backend/src backend/PORTING.md docs/spec docs/superpowers
git commit -m "refactor(interfaces): no try-catch or access checks in controllers; architecture rules and porting notes for §3-0"
```

- [ ] **Step 4:** 停下，等使用者確認。
