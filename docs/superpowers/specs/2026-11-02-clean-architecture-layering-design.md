# Clean Architecture 分層對齊設計

> 版本 v0.1・2026-11-02。依平台「Clean Architecture 分層守則（MUST）」把 `backend/.../aiassistant` 從功能切分改為 `domain / application / interfaces / infrastructure` 四層。回答 [questions-for-manager.md](../../questions-for-manager.md) S2。

## 1. 平台守則（摘自 reference.md §3，reference.md 不進版控，故抄錄於此）

```
Interface Layer -> Application Layer -> Domain Layer -> Infrastructure Layer
```

| 層次           | Package           | 允許依賴                | 禁止依賴               |
| -------------- | ----------------- | ----------------------- | ---------------------- |
| Domain         | `domain/`         | 純 Java，無框架         | Spring、MongoDB        |
| Application    | `application/`    | Domain                  | Spring Data、MongoDB   |
| Interface      | `interfaces/`     | Application、Common     | Domain Repository 實作 |
| Infrastructure | `infrastructure/` | Domain 介面、Application | -                      |

- CQRS：Command → UseCase → Aggregate → Repository（Document ↔ Domain 完整映射）；Query → QueryService → QueryRepository（直接投影為 DTO，跳過 Domain model）。
- Schema 三層分離：Request DTO → Domain Model（record 不可變）→ MongoDB Document。
- 每個 Bounded Context 嚴格遵守 `domain/ → application/ → interfaces/ → infrastructure/`。
- 對外 port 放在 `application/port/`；套件命名統一 `application/*`，禁止 `app/`。
- 實作類別以 `Impl` 結尾；介面不以 `I` 開頭。
- 測試目錄與 main 一一對應。

## 2. 本專案的解讀與例外（已於 2026-11-02 與使用者確認）

| 項目 | 決定 |
| --- | --- |
| 「純 Java，無框架」的範圍 | Domain 禁止 `org.springframework.*`、`com.mongodb.*`、`org.bson.*`、`jakarta.servlet.*`、`jakarta.annotation.*`、`AiAssistantProperties`。**允許** LangChain4j（`dev.langchain4j.*`）、Jackson（`tools.jackson.*`、`com.fasterxml.jackson.annotation.*`）、Bean Validation（`jakarta.validation.*`）、SLF4J、SnakeYAML。理由：守則表格明列的禁止項只有 Spring 與 MongoDB；LangChain4j 是 01-architecture §1 已定的 AI orchestration 決策，工具的 `ToolSpecification` 與回答的 JSON Schema 與它綁定，包成 port 的成本高於收益。 |
| Application 可用的 Spring | 允許 `org.springframework.stereotype`、`beans.factory.annotation`、`context.annotation`（@Service/@Component/@Qualifier）與 `AiAssistantProperties`；**禁止** `org.springframework.data.*`、`org.springframework.transaction.*`、`com.mongodb.*`、`org.bson.*`、`jakarta.servlet.*`。交易邊界由 infrastructure 的 Repository 實作負責。 |
| Interface 可見的 Domain | 可讀取 `domain` 的資料型別（record、enum、`AssistantException`/`ErrorCode`）以組 DTO；**禁止** 引用 `domain.repository.*` 與任何 `infrastructure.*`。 |
| Port 位置 | `CurrentUserPort`、`CustomerAccessPort`、`CustomerDataPort` 放 `application/port/`。它們的資料型別（`UserContext`、`CustomerRecord`、`AccessLevel`、`c360/*`）是 domain 的值物件，放 `domain/customer/`、`domain/user/`，因為 domain 的工具與去識別化規則要用。Domain 不得引用 port 介面本身。 |
| CQRS 套用範圍 | config（主要 CRUD）拆 `ConfigCommandService` / `ConfigQueryService` + `ConfigQueryRepository`；feedback 拆 `FeedbackService`（評分）/ `FeedbackQueryService` + `FeedbackStatsQueryRepository`。evaluation 的查詢回傳整個 `EvalRun` 聚合（`recent` 已排除 `cases`），不另建投影；chat 本來就只有 command。 |
| `AiAssistantProperties` | 留在模組根（`aiassistant/AiAssistantProperties`），是 Spring Boot 的組態綁定；只有 application 與 infrastructure 可用，domain 類別改以建構子接收所需的值。 |
| `mockplatform` | POC 替身，整合時刪除；不分層，只因 port／型別搬家而改 import。 |
| `Impl` 後綴 | 套用於 `domain.repository.*` 與 `application.*QueryRepository` 的實作（`AssistantConfigRepositoryImpl` 等）。`TwseClient` 實作 `TwseGateway` 但保留原名，因 03-tool-catalog §3.1 與測試資料都以此名描述；`mockplatform` 的 `*Adapter` 整合時刪除，不改名。 |
| `RateLimitConfiguration` | 與 `PerUserRateLimiter` 一起放 `interfaces/rest/`（它建立的是 web 層的 bean；放 infrastructure 會讓 infrastructure 依賴 interfaces）。 |
| 不在本次範圍 | package root 改 `com.org`、Javadoc 全覆蓋（CheckStyle）、`ApiResponse<T>` 統一回應、環境設定 `{dev,sit,uat,prod}/` 分資料夾。 |

## 3. 目標結構

```
backend/src/main/java/com/example/copilot/aiassistant/
├─ AiAssistantProperties.java
├─ domain/
│  ├─ exception/   AssistantException、ErrorCode（HTTP 狀態改為 int）
│  ├─ config/      AssistantConfig（record）、ConfigStatus、ConfigType、ValidationError、PromptVersion、
│  │               PublishedConfigs、ConfigValidator
│  ├─ customer/    AccessLevel、CustomerRecord、c360/（Customer360Record、RealTimeStatusRecord 等 24 個 record）
│  ├─ user/        UserContext
│  ├─ chat/        ChatResult、EvaluationReport
│  ├─ answer/      AnswerAssembler、AnswerBlock、AnswerSchema、AssembledAnswer、BlockType、NumberChecker
│  ├─ privacy/     CustomerContext、CustomerDeidentifier、PiiRedactor
│  ├─ prompt/      PromptAssembler（只剩 assemble）、AssembledPrompt、InstructionSelection、AnswerFormatRules、
│  │               SafetyRules、CustomerNotes、ExampleQuestionParser、RouterPrompt、RouteDecision、RouteSource、
│  │               SummaryPrompt、SummaryResult、SummaryStatus
│  ├─ tool/        AssistantTool、ToolRegistry、ToolGuard、ToolExecutor、ToolResult、ToolContext、ToolOutcome、
│  │               ToolStatus、CitationDetail、CitationIds、Source、NoArgs、TurnCustomerData
│  │  ├─ customer/ GetCustomerTool、GetRealTimeCustomerTool、AbstractCustomerTool、CustomerSection、DisplayFormat、
│  │  │            MemberRankTable、RadarFacts、sections/*
│  │  └─ market/   TwseGateway（介面）、AbstractMarketTool、MarketTool、GetStockQuoteTool、GetMarketIndexTool、
│  │               GetCompanyAnnouncementsTool、TwseParsing、TwseException
│  ├─ audit/       AuditEntry、AuditRecord（純 record）、CredentialMasker
│  ├─ eval/        EvalRun、EvalStatus、EvalSummary、EvalMetrics、EvalCase、EvalCaseRecord、EvalDataset、
│  │               EvalDatasetLoader、EvalErrors、CaseResult、CaseChecker、RuleChecks、JudgeVerdict、EvalJudgePrompt
│  ├─ feedback/    FeedbackRating
│  └─ repository/  AssistantConfigRepository、AuditRepository、EvalRunRepository（介面）
├─ application/
│  ├─ port/        CurrentUserPort、CustomerAccessPort、CustomerDataPort
│  ├─ support/     ClasspathText、RequestContext（MDC requestId）
│  ├─ llm/         LlmErrorMapper、LlmRequestParameters
│  ├─ chat/        ChatOrchestrator、ChatCommand、CustomerContextFactory、InstructionRouter、HistorySummarizer、
│  │               SuggestionService
│  ├─ config/      ConfigCommandService、ConfigQueryService、ConfigQueryRepository（介面）、ConfigSummary、
│  │               VersionHistoryEntry、PublishedConfigCache、SaveCommand、PublishCommand、ApplyCommand、
│  │               SaveMode、PublishMode
│  ├─ audit/       AuditLogger
│  ├─ evaluation/  EvalRunService、EvalRunner、EvalJudge
│  └─ feedback/    FeedbackService、FeedbackQueryService、FeedbackStatsQueryRepository（介面）、VersionFeedback
├─ interfaces/rest/
│  ├─ ApiError、AssistantExceptionHandler、RequestIdFilter、PerUserRateLimiter、RateLimitConfiguration
│  ├─ chat/        ChatController、SuggestionController、dto/（ChatRequestBody、ChatResponseBody、HistoryItem）
│  ├─ config/      AdminConfigController、dto/（AdminTime、ApplyRequest、ConfigKeys、ConfigSummaryView、
│  │               ConfigVersionView、CreateConfigRequest、PublishRequest、SaveDraftRequest、ValidationReportView、
│  │               VersionHistoryItem）
│  ├─ evaluation/  EvalController、dto/*
│  └─ feedback/    FeedbackController、dto/FeedbackRequest
└─ infrastructure/
   ├─ config/      DomainBeanConfiguration（domain 類別的 @Bean）、ClockConfiguration、EvalConfiguration
   ├─ llm/         LlmConfiguration
   ├─ market/      TwseClient（implements TwseGateway）
   ├─ mongo/       AssistantConfigDocument、AssistantConfigMapper、AssistantConfigMongoRepository（Spring Data）、
   │               AssistantConfigRepositoryImpl、ConfigQueryRepositoryImpl、AuditRecordMapper、AuditRepositoryImpl、
   │               FeedbackStatsQueryRepositoryImpl、EvalRunRepositoryImpl、MongoConfiguration、MongoSchemaInitializer
   ├─ seed/        SeedLoader
   └─ scheduler/   ScheduledPublishJob
```

測試目錄 `src/test/java/.../aiassistant/` 與上面一一對應；`IntegrationTestBase`、`TestUsers`、`TestCustomers`、`testsupport/` 不動。

## 4. 依賴規則的自動驗證

`src/test/java/com/example/copilot/aiassistant/ArchitectureRulesTest` 掃描 `src/main/java` 的 `import` 行（不引入 ArchUnit，公司環境無法下載新套件），規則如下：

| 套件 | 禁止的 import 前綴 |
| --- | --- |
| `aiassistant.domain` | `org.springframework.`、`com.mongodb.`、`org.bson.`、`jakarta.servlet.`、`jakarta.annotation.`、`aiassistant.AiAssistantProperties`、`aiassistant.application.`、`aiassistant.interfaces.`、`aiassistant.infrastructure.`、`copilot.mockplatform.` |
| `aiassistant.application` | `org.springframework.data.`、`org.springframework.transaction.`、`com.mongodb.`、`org.bson.`、`jakarta.servlet.`、`aiassistant.interfaces.`、`aiassistant.infrastructure.`、`copilot.mockplatform.` |
| `aiassistant.interfaces` | `aiassistant.domain.repository.`、`aiassistant.infrastructure.`、`org.springframework.data.`、`com.mongodb.`、`org.bson.`、`copilot.mockplatform.` |
| `aiassistant.infrastructure` | `aiassistant.interfaces.`、`copilot.mockplatform.` |
| 全部 | 不得存在 `aiassistant.app.` 套件；`aiassistant` 底下只能有 `domain`、`application`、`interfaces`、`infrastructure` 四個子套件與 `AiAssistantProperties` |

## 5. 關鍵設計

### 5.1 AssistantConfig 聚合與 Document 分離

- `domain/config/AssistantConfig` 是不可變 record（15 個欄位同今日的 class），提供 `newDraft(...)`、`withContent(name, content, changeNote)`、`withStatus(status, scheduledAt)`、`withBasedOnVersion(v)`、`touched(actor, now)`、`isWorkingCopy()`。
- `infrastructure/mongo/AssistantConfigDocument` 是今日的 `@Document` class（`@Id`、`@Version docVersion`），只有 infrastructure 看得到；`AssistantConfigMapper.toDocument / toDomain` 完整映射。
- `domain/repository/AssistantConfigRepository` 以聚合為單位，把今日 `ConfigService` 裡的 Mongo 交易與衝突對應收進實作：

```java
public interface AssistantConfigRepository {
    Optional<AssistantConfig> findPublished(String configKey);
    Optional<AssistantConfig> findWorkingCopy(String configKey);        // DRAFT 或 SCHEDULED
    Optional<AssistantConfig> findLatest(String configKey);
    Optional<AssistantConfig> findVersion(String configKey, int version);
    List<AssistantConfig> findPublished();                                // 全部 PUBLISHED，給快取
    List<AssistantConfig> findScheduledDue(Instant now);
    boolean existsByConfigType(ConfigType type);
    long count();
    AssistantConfig insert(AssistantConfig config);                       // 新版本；unique index 衝突 → AI_CONFIG_CONFLICT
    AssistantConfig save(AssistantConfig config);                         // 以 docVersion 為條件；樂觀鎖失敗 → AI_CONFIG_CONFLICT
    AssistantConfig publish(AssistantConfig workingCopy);                 // 交易：封存目前 PUBLISHED + 儲存為 PUBLISHED
    AssistantConfig replaceWorkingCopy(AssistantConfig discarded, AssistantConfig draft); // 交易；discarded 可為 null
    boolean publishScheduled(AssistantConfig due, Instant now);           // 交易：封存 + 條件式認領；輸掉競賽回 false
}
```

- 衝突（`OptimisticLockingFailureException`、`DuplicateKeyException`、WriteConflict 112、duplicate key 11000、transient transaction label）一律由 `AssistantConfigRepositoryImpl` 轉成 `AssistantException(AI_CONFIG_CONFLICT)`；其他 Mongo 錯誤（例如 121 validation）原樣往外丟。`publishScheduled` 把衝突視為輸掉競賽回 `false`。

### 5.2 Audit

- `domain/audit/AuditRecord` 去掉 `@Id` 與 `org.bson.Document`：`ToolCall.result` 改為 `Map<String, Object>`；`toDocument()` 搬到 `infrastructure/mongo/AuditRecordMapper`。
- `domain/repository/AuditRepository`：`void insert(AuditRecord record)`；`boolean setFeedback(String requestId, String userId, FeedbackRating rating, Instant at)`（找不到 ANSWERED 的紀錄回 false）。
- `application/audit/AuditLogger` 改依賴 `AuditRepository`；寫入失敗仍只記 log，不往外丟。

### 5.3 Query 側

- `application/config/ConfigQueryRepository`：`List<ConfigSummary> listSummaries()`（投影，不含 content）、`List<VersionHistoryEntry> versionHistory(String configKey)`（投影，不含 content，新到舊）。版本明細需要全部欄位，`ConfigQueryService.version(key, v)` 直接用 `AssistantConfigRepository.findVersion`。
- `application/feedback/FeedbackStatsQueryRepository`：`List<VersionFeedback> versionStats(String configKey, boolean routedOnly)`（今日 `FeedbackService.stats` 的 aggregation）。

### 5.4 Domain 去框架的具體改法

| 類別 | 改法 |
| --- | --- |
| `PiiRedactor`、`CredentialMasker`、`ExampleQuestionParser`、`CustomerNotes`、`PromptAssembler`、`AnswerAssembler`、`ToolRegistry`、`ToolGuard`、`ToolExecutor`、`GetCustomerTool`、`GetRealTimeCustomerTool`、三個市場工具 | 拿掉 `@Component`，由 `infrastructure/config/DomainBeanConfiguration` 以 `@Bean` 建立 |
| `ConfigValidator` | 拿掉 `@Autowired` 建構子，只留 `(ExampleQuestionParser, Collection<String> allowedUrlHosts)`；bean 方法傳 `props.config().allowedUrlHosts()` |
| `ToolExecutor` | 建構子改 `(ToolGuard, PiiRedactor, JsonMapper, Duration toolTimeout, int toolResultMaxBytes)`；`@PreDestroy shutdown()` 改為 public，bean 以 `destroyMethod = "shutdown"` 註冊 |
| `PromptAssembler` | 建構子改 `(Clock)`，移除 `snapshot()`；呼叫端改用 `PublishedConfigCache.current()` |
| `PublishedConfigs` | 從 `PublishedConfigCache` 內部 record 升為 `domain/config/PublishedConfigs` |
| `TurnCustomerData` | 建構子改 `(Supplier<Optional<Customer360Record>>, Supplier<Optional<RealTimeStatusRecord>>)`；`ChatOrchestrator` 以 `() -> port.findCustomer360(certNo)` 建立 |
| `ErrorCode` | `HttpStatus` 改 `int status`；`AssistantExceptionHandler`、`mockplatform` 用 `ResponseEntity.status(int)` / `response.setStatus(int)` |
| `ClasspathText` | 改用 `ClasspathText.class.getClassLoader().getResourceAsStream(path)`，搬到 `application/support/`，public |
| `TwseClient` | 抽出 `domain/tool/market/TwseGateway`（四個路徑常數 + `List<Map<String,String>> fetch(String path)`），`TwseClient implements TwseGateway` 搬到 `infrastructure/market/` |
| `RequestIdFilter.current()` 在 application 的使用 | 新增 `application/support/RequestContext`（`MDC_KEY = "requestId"`、`static String currentRequestId()`）；`RequestIdFilter` 引用其常數 |

## 6. 文件更新

- [01-architecture.md](../../spec/01-architecture.md) §5.1：目錄樹改為四層；Clean Architecture 對應表改為各層 package 與允許依賴；註明 LangChain4j／Jackson／Bean Validation 例外與 `ArchitectureRulesTest`。
- [questions-for-manager.md](../../questions-for-manager.md) S2：回覆欄填「依平台守則 `domain / application / interfaces / infrastructure`，已於 2026-11-02 對齊」。
