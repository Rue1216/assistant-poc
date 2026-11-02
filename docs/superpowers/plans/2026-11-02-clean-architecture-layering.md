# Clean Architecture 分層對齊 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `backend/.../aiassistant` 從功能切分改為平台守則的 `domain / application / interfaces / infrastructure` 四層，domain 零 Spring/MongoDB、application 零 Spring Data、config 與 feedback 拆 command/query，並以測試固定依賴規則。

**Architecture:** 四個階段：(1) 套件搬移 + domain 去框架 + 依賴規則測試；(2) 資料層隔離（domain repository 介面、Document/Domain 分離、Mongo 實作搬到 infrastructure）；(3) CQRS 拆分；(4) 文件。每個 task 結束時 `mvn -q test` 全綠；行為不變（REST API、Mongo 文件格式、稽核內容與既有測試的斷言都不改）。

**Tech Stack:** Java 25、Spring Boot 4.1、Spring Data MongoDB、LangChain4j 1.x、JUnit 5、Testcontainers（MongoDB 8）。本機有 Maven 3.9.16 與 JDK 25。

**Spec:** [docs/superpowers/specs/2026-11-02-clean-architecture-layering-design.md](../specs/2026-11-02-clean-architecture-layering-design.md)（含守則抄錄、例外決定、目標結構、依賴規則表）

## Global Constraints

- 套件根維持 `com.example.copilot.aiassistant`；`aiassistant` 底下只允許 `domain`、`application`、`interfaces`、`infrastructure` 四個子套件與 `AiAssistantProperties`（spec §2、§4）。
- Domain 禁止 import：`org.springframework.`、`com.mongodb.`、`org.bson.`、`jakarta.servlet.`、`jakarta.annotation.`、`aiassistant.AiAssistantProperties`、`aiassistant.application.`、`aiassistant.interfaces.`、`aiassistant.infrastructure.`、`copilot.mockplatform.`（spec §4）。
- Application 禁止 import：`org.springframework.data.`、`org.springframework.transaction.`、`com.mongodb.`、`org.bson.`、`jakarta.servlet.`、`aiassistant.interfaces.`、`aiassistant.infrastructure.`、`copilot.mockplatform.`。
- Interfaces 禁止 import：`aiassistant.domain.repository.`、`aiassistant.infrastructure.`、`org.springframework.data.`、`com.mongodb.`、`org.bson.`、`copilot.mockplatform.`。
- Infrastructure 禁止 import：`aiassistant.interfaces.`、`copilot.mockplatform.`。
- 實作類別以 `Impl` 結尾；介面不以 `I` 開頭；不新增 `app/` 套件。
- 測試目錄與 main 一一對應：測試類別跟著被測類別搬到相同的新套件（package-private 存取因此不變）。
- 不改 REST 路徑、request/response JSON、Mongo collection 名稱與文件欄位、index 名稱、錯誤碼。
- 既有測試方法一律保留其斷言；只能改 import、建構方式與（Phase 2）測試所在的類別。
- 使用者不要自動 commit：每個 Phase 結束時提供 `git add` 指令與 commit message，**停下來等使用者確認已 commit** 再開始下一個 Phase。
- 驗證指令：`cd backend && mvn -q test`（需要 Docker 啟動中，Testcontainers 會拉 `mongo:8`）。

## Review Focus

1. **同套件引用遺漏 import**：今日同套件的類別互相引用不需 import（例如 `ChatController` → `ChatOrchestrator`），搬到不同層後必須補 import；編譯錯誤就是清單，不得改用 wildcard import。→ Task 1 的編譯步驟。
2. **樂觀鎖與交易行為在 Repository 搬家後走樣**：`publish`、`applyVersion`、`publishScheduled` 的封存/認領/回滾邏輯從 `ConfigService` 搬進 `AssistantConfigRepositoryImpl`，若漏了 `status.setRollbackOnly()` 或條件式 `updateFirst`，兩個實例同時發布會出現兩份 PUBLISHED。→ Task 4 保留 `ConfigServiceTest`/`ScheduledPublishJobTest` 的競賽測試，搬到 `AssistantConfigRepositoryImplTest` 時一個都不能少。
3. **Spring Data `@Version` 在 record ↔ document 映射下失效**：`save` 時 document 的 `docVersion` 必須來自 record（非 null）才會做條件更新；若 mapper 把 docVersion 丟掉，`save` 會變成 insert 或無條件覆寫。→ Task 4 的 `saveWithStaleDocVersionThrowsConflict` 測試。
4. **稽核 `result` 欄位型別改變**：`AuditRecord.ToolCall.result` 從 bson `Document` 改成 `Map<String,Object>`，寫入時必須再轉回 `Document`，否則巢狀物件可能以 `LinkedHashMap` 序列化但 `store-tool-results` 測試讀取 `getInteger("close")` 失敗。→ Task 5 保留 `AuditLoggerTest.storesToolResultWhenEnabled`（既有）。
5. **Bean 註冊漏掉某個工具**：拿掉 `@Component` 後，若 `DomainBeanConfiguration` 漏註冊一個 `AssistantTool`，`ToolRegistry` 少一個工具、`EvalDataset` 啟動驗證（`relatedTools`）會失敗或模型少一個工具但測試不一定發現。→ Task 2 新增 `DomainBeanConfigurationTest.registersAllFiveTools`。

---

## Phase 1：套件搬移、domain 去框架、依賴規則測試

### Task 1: 套件搬移（不改行為）

**Files:**
- Move: 下表全部 main 類別與對應測試（`src/test/java/com/example/copilot/aiassistant/**`）
- Modify: `backend/src/main/java/com/example/copilot/mockplatform/**`（只改 import）
- Modify: `backend/src/test/java/com/example/copilot/{TestUsers,TestCustomers,IntegrationTestBase}.java`、`testsupport/*`（只改 import）

**Interfaces:**
- Produces: 新套件路徑（後續 task 全以新路徑為準）。類別名稱、建構子、方法簽名在本 task 一律不變。

搬移對照表（舊套件相對 `com.example.copilot.aiassistant` → 新套件相對同根）：

| 舊 | 類別 | 新 |
| --- | --- | --- |
| `answer` | 全部 6 個 | `domain.answer` |
| `audit` | `AuditEntry`、`AuditRecord`、`CredentialMasker` | `domain.audit` |
| `audit` | `AuditLogger` | `application.audit` |
| `chat` | `ChatResult`、`EvaluationReport` | `domain.chat` |
| `chat` | `ChatCommand`、`ChatOrchestrator` | `application.chat` |
| `chat` | `ChatController`、`SuggestionController` | `interfaces.rest.chat` |
| `chat.dto` | 全部 3 個 | `interfaces.rest.chat.dto` |
| `common` | `AssistantException`、`ErrorCode` | `domain.exception` |
| `common` | `ApiError`、`AssistantExceptionHandler`、`RequestIdFilter`、`PerUserRateLimiter`、`RateLimitConfiguration` | `interfaces.rest` |
| `common` | `ClockConfiguration` | `infrastructure.config` |
| `config` | `AssistantConfig`、`ConfigStatus`、`ConfigType`、`ValidationError`、`ConfigValidator` | `domain.config` |
| `config` | `AssistantConfigRepository` | `domain.repository`（Phase 1 仍是 Spring Data 介面，Task 3 列為暫時豁免） |
| `config` | `ConfigService`、`ConfigSummary`、`ApplyCommand`、`PublishCommand`、`SaveCommand`、`SaveMode`、`PublishMode` | `application.config` |
| `config` | `ClasspathText` | `application.support`（改為 `public final class`） |
| `config` | `AdminConfigController` | `interfaces.rest.config` |
| `config.dto` | 全部 10 個 | `interfaces.rest.config.dto` |
| `config` | `MongoConfiguration`、`MongoSchemaInitializer` | `infrastructure.mongo` |
| `config` | `SeedLoader` | `infrastructure.seed` |
| `config` | `ScheduledPublishJob` | `infrastructure.scheduler` |
| `evaluation` | `CaseChecker`、`CaseResult`、`EvalCase`、`EvalCaseRecord`、`EvalDataset`、`EvalDatasetLoader`、`EvalErrors`、`EvalJudgePrompt`、`EvalMetrics`、`EvalRun`、`EvalStatus`、`EvalSummary`、`JudgeVerdict`、`RuleChecks` | `domain.eval` |
| `evaluation` | `EvalRunRepository` | `domain.repository`（Phase 1 仍是 MongoTemplate class，Task 3 列為暫時豁免） |
| `evaluation` | `EvalRunService`、`EvalRunner`、`EvalJudge` | `application.evaluation` |
| `evaluation` | `EvalConfiguration` | `infrastructure.config` |
| `evaluation` | `EvalController` | `interfaces.rest.evaluation` |
| `evaluation.dto` | 全部 6 個 | `interfaces.rest.evaluation.dto` |
| `feedback` | `FeedbackRating` | `domain.feedback` |
| `feedback` | `FeedbackService`、`VersionFeedback` | `application.feedback` |
| `feedback` | `FeedbackController` | `interfaces.rest.feedback` |
| `feedback` | `FeedbackRequest` | `interfaces.rest.feedback.dto` |
| `llm` | `LlmErrorMapper`、`LlmRequestParameters` | `application.llm` |
| `llm` | `LlmConfiguration` | `infrastructure.llm` |
| `port` | `AccessLevel`、`CustomerRecord` | `domain.customer` |
| `port` | `UserContext` | `domain.user` |
| `port` | `CurrentUserPort`、`CustomerAccessPort`、`CustomerDataPort` | `application.port` |
| `port.c360` | 全部 24 個 | `domain.customer.c360` |
| `privacy` | `CustomerContext`、`CustomerDeidentifier`、`PiiRedactor` | `domain.privacy` |
| `privacy` | `CustomerContextFactory` | `application.chat` |
| `prompt` | `AnswerFormatRules`、`AssembledPrompt`、`CustomerNotes`、`ExampleQuestionParser`、`InstructionSelection`、`PromptAssembler`、`RouteDecision`、`RouteSource`、`RouterPrompt`、`SafetyRules`、`SummaryPrompt`、`SummaryResult`、`SummaryStatus` | `domain.prompt` |
| `prompt` | `PromptVersion` | `domain.config` |
| `prompt` | `InstructionRouter`、`HistorySummarizer`、`SuggestionService` | `application.chat` |
| `prompt` | `PublishedConfigCache` | `application.config` |
| `tool` | 全部 13 個（含 `TurnCustomerData`） | `domain.tool` |
| `tool.customer`、`tool.customer.sections` | 全部 | `domain.tool.customer`、`domain.tool.customer.sections` |
| `tool.market` | `AbstractMarketTool`、`MarketTool`、三個 `Get*Tool`、`TwseParsing`、`TwseException` | `domain.tool.market` |
| `tool.market` | `TwseClient` | `infrastructure.market` |
| 根 | `AiAssistantProperties` | 不動 |

測試類別跟著搬：例如 `test/.../aiassistant/config/ConfigServiceTest` → `test/.../aiassistant/application/config/ConfigServiceTest`、`test/.../aiassistant/tool/testsupport/*` → `test/.../aiassistant/domain/tool/testsupport/*`、`test/.../aiassistant/common/testsupport/ThrowingController` → `test/.../aiassistant/interfaces/rest/testsupport/ThrowingController`、`test/.../aiassistant/chat/ScriptedChatModel` → `test/.../aiassistant/application/chat/ScriptedChatModel`、`test/.../aiassistant/evaluation/EvalRunnerTestBase` → `application/evaluation/`、`test/.../aiassistant/llm/OpenAiResponseFormatLiveTest` → `infrastructure/llm/`、`test/.../aiassistant/config/MongoSchemaTest`、`MongoConfigurationTest` → `infrastructure/mongo/`、`SeedLoaderTest` → `infrastructure/seed/`、`ScheduledPublishJobTest` → `infrastructure/scheduler/`、`EvalConfigurationTest` → `infrastructure/config/`、`AiAssistantPropertiesTest` 不動、`port/c360/C360JsonTest` → `domain/customer/c360/`。

- [ ] **Step 1: 寫搬移腳本（放 scratchpad，不進 repo）**

腳本輸入上表（每列 `舊FQCN 新FQCN`，main 與 test 各一份），對每一列：`git mv` 檔案到新路徑；改該檔的 `package` 行；在 `backend/src/main/java` 與 `backend/src/test/java` 全部 `.java` 中把 `舊FQCN` 後接非識別字元的出現（import、import static、FQCN 引用）替換成 `新FQCN`。替換前依 FQCN 長度由長到短排序，避免 `...config.AssistantConfig` 吃掉 `...config.AssistantConfigRepository`。`AssistantExceptionHandler` 的 `@RestControllerAdvice(basePackages = "com.example.copilot.aiassistant")` 不用改。

- [ ] **Step 2: 執行腳本，然後編譯**

Run: `cd backend && mvn -q -DskipTests compile test-compile`
Expected: 一批「cannot find symbol」錯誤，全部是原本同套件、現在跨套件的引用。

- [ ] **Step 3: 逐檔補 import（不得用 wildcard），直到編譯通過**

Run: `cd backend && mvn -q -DskipTests compile test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 確認 `src/main/java/com/example/copilot/aiassistant` 只剩四個子資料夾與 `AiAssistantProperties.java`**

Run: `ls backend/src/main/java/com/example/copilot/aiassistant`
Expected: `AiAssistantProperties.java application domain infrastructure interfaces`

- [ ] **Step 5: 跑全部測試**

Run: `cd backend && mvn -q test`
Expected: Tests run 全綠，Failures: 0, Errors: 0

- [ ] **Step 6: 不 commit；本 Phase 結束時統一交指令給使用者**

---

### Task 2: Domain 去框架、bean 集中註冊

**Files:**
- Create: `backend/src/main/java/com/example/copilot/aiassistant/infrastructure/config/DomainBeanConfiguration.java`
- Create: `backend/src/main/java/com/example/copilot/aiassistant/application/support/RequestContext.java`
- Create: `backend/src/main/java/com/example/copilot/aiassistant/domain/tool/market/TwseGateway.java`
- Create: `backend/src/main/java/com/example/copilot/aiassistant/domain/config/PublishedConfigs.java`
- Modify: `domain/exception/ErrorCode.java`、`domain/config/ConfigValidator.java`、`domain/tool/ToolExecutor.java`、`domain/tool/TurnCustomerData.java`、`domain/prompt/PromptAssembler.java`、`application/config/PublishedConfigCache.java`、`application/support/ClasspathText.java`、`infrastructure/market/TwseClient.java`、`domain/tool/market/AbstractMarketTool.java` 與三個 `Get*Tool`、`application/chat/ChatOrchestrator.java`、`application/evaluation/EvalRunner.java`、`application/evaluation/EvalRunService.java`、`interfaces/rest/RequestIdFilter.java`、`interfaces/rest/AssistantExceptionHandler.java`、`mockplatform/SecurityConfig.java`、`mockplatform/MockAuthController.java`、`mockplatform/customer/MockCustomerController.java`
- Modify（拿掉 `@Component`/`@Service`）: `domain/privacy/PiiRedactor`、`domain/audit/CredentialMasker`、`domain/prompt/ExampleQuestionParser`、`domain/prompt/CustomerNotes`、`domain/prompt/PromptAssembler`、`domain/answer/AnswerAssembler`、`domain/tool/ToolRegistry`、`domain/tool/ToolGuard`、`domain/tool/ToolExecutor`、`domain/tool/customer/GetCustomerTool`、`domain/tool/customer/GetRealTimeCustomerTool`、`domain/tool/market/GetStockQuoteTool`、`GetMarketIndexTool`、`GetCompanyAnnouncementsTool`、`domain/config/ConfigValidator`
- Test: `backend/src/test/java/com/example/copilot/aiassistant/infrastructure/config/DomainBeanConfigurationTest.java`（新）；調整 `application/chat/ChatOrchestratorTest`、`domain/prompt/PromptAssemblerTest`、`domain/tool/ToolExecutorTest`、`domain/tool/market/MarketToolsTest`、`infrastructure/market/TwseClientTest`、`application/evaluation/EvalRunServiceTest`

**Interfaces:**
- Produces:
  - `public record PublishedConfigs(AssistantConfig systemPrompt, List<AssistantConfig> instructions)` 在 `domain.config`（`PublishedConfigCache.PublishedConfigs` 刪除，所有 import 改指向新位置）
  - `public PromptAssembler(Clock clock)`；`snapshot()` 刪除
  - `public PublishedConfigCache(AssistantConfigRepository repository, Clock clock)`（不變），`current()`、`invalidate()`、`loadFresh()` 回傳 `domain.config.PublishedConfigs`
  - `public ToolExecutor(ToolGuard guard, PiiRedactor redactor, JsonMapper mapper, Duration toolTimeout, int toolResultMaxBytes)`；`public void shutdown()`
  - `public TurnCustomerData(Supplier<Optional<Customer360Record>> customer360, Supplier<Optional<RealTimeStatusRecord>> realTime)`
  - `public ConfigValidator(ExampleQuestionParser parser, Collection<String> allowedUrlHosts)`（唯一建構子，public）
  - `public interface TwseGateway { String MI_INDEX, STOCK_DAY_ALL, BWIBBU_ALL, COMPANY_ANNOUNCEMENTS; List<Map<String, String>> fetch(String path); }`（常數值照 `TwseClient` 今日的值）；`TwseClient implements TwseGateway`，建構子不變
  - `ErrorCode`：`int status()`（值為今日 `HttpStatus` 的 `value()`）
  - `public final class RequestContext { public static final String MDC_KEY = "requestId"; public static String currentRequestId(); }`
  - `public final class ClasspathText { public static String read(String path); }`（用 `ClassLoader.getResourceAsStream`，找不到丟 `UncheckedIOException`）

- [ ] **Step 1: 寫 `DomainBeanConfigurationTest`（整合測試，繼承 `IntegrationTestBase`）**

```java
@Test void registersAllFiveTools() {
    assertThat(registry.names()).containsExactlyInAnyOrder(
        "getCustomer", "getRealTimeCustomer", "getStockQuote", "getMarketIndex", "getCompanyAnnouncements");
}
@Test void domainBeansExist() {
    // PiiRedactor, CredentialMasker, ExampleQuestionParser, CustomerNotes, PromptAssembler, AnswerAssembler,
    // ToolRegistry, ToolGuard, ToolExecutor, ConfigValidator 各能從 ApplicationContext 取得一個 bean
}
```
工具名稱以 `domain/tool/customer/*Tool.name()`、`domain/tool/market/*Tool.name()` 的實際回傳為準。

- [ ] **Step 2: 跑新測試確認目前就通過（@Component 還在）**

Run: `cd backend && mvn -q test -Dtest=DomainBeanConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（基準線）

- [ ] **Step 3: 建 `DomainBeanConfiguration`（`@Configuration(proxyBeanMethods = false)`，infrastructure.config）並拿掉上列 15 個 domain 類別的 `@Component`/`@Service`/`@Autowired`**

`@Bean` 方法：`piiRedactor()`、`credentialMasker()`、`exampleQuestionParser()`、`customerNotes()`、`promptAssembler(Clock)`、`answerAssembler(PiiRedactor, JsonMapper)`、`configValidator(ExampleQuestionParser, AiAssistantProperties)`（傳 `props.config().allowedUrlHosts()`）、`toolRegistry(List<AssistantTool<?>>)`、`toolGuard(ToolRegistry, Validator, JsonMapper)`、`@Bean(destroyMethod = "shutdown") toolExecutor(ToolGuard, PiiRedactor, JsonMapper, AiAssistantProperties)`（傳 `props.limits().toolTimeout()`、`props.limits().toolResultMaxBytes()`）、`getCustomerTool(PiiRedactor)`、`getRealTimeCustomerTool(PiiRedactor)`、`getStockQuoteTool(TwseGateway)`、`getMarketIndexTool(TwseGateway)`、`getCompanyAnnouncementsTool(TwseGateway)`。`ToolExecutor` 的 `@PreDestroy` 與 `jakarta.annotation` import 移除，`shutdown()` 改 public。

- [ ] **Step 4: `TwseGateway` 介面；`AbstractMarketTool` 與三個市場工具改依賴 `TwseGateway`；`TwseClient implements TwseGateway`，常數改由介面提供（`TwseClient.MI_INDEX` 等引用改成 `TwseGateway.MI_INDEX`）**

- [ ] **Step 5: `PublishedConfigs` 升為 `domain.config` 頂層 record；`PromptAssembler` 改 `(Clock)` 並刪 `snapshot()`；`ChatOrchestrator` 建構子新增 `PublishedConfigCache cache` 參數，`turn.configs = cache.current()`；`EvalRunService`、`EvalRunner`、`SuggestionService`、`InstructionRouter` 的 import 改指向新 record**

`ChatOrchestratorTest` 裡 `new PromptAssembler(null, clock) { @Override snapshot() {...} }` 的 8 處改為 `new PublishedConfigCache(null, clock) { @Override public PublishedConfigs current() { ... } }` 傳給 orchestrator，`PromptAssembler` 改 `new PromptAssembler(clock)`；`PromptAssemblerTest` 的 `assembler.snapshot()` 改 `cache.current()`。

- [ ] **Step 6: `TurnCustomerData` 改接兩個 `Supplier`；`ChatOrchestrator` 以 `() -> port.findCustomer360(certNo)`、`() -> port.findRealTimeStatus(certNo)` 建立；`TurnCustomerDataTest` 改用 supplier 建構**

- [ ] **Step 7: `ErrorCode` 的 `HttpStatus` 改 `int`；`AssistantExceptionHandler.respond` 用 `ResponseEntity.status(code.status())`；`mockplatform/SecurityConfig` 用 `response.setStatus(ErrorCode.AI_UNAUTHENTICATED.status())`；`MockAuthController`、`MockCustomerController` 的 `ResponseEntity.status(int)`**

- [ ] **Step 8: `RequestContext`（application.support）；`ChatOrchestrator.Turn` 與 `EvalRunner` 改用 `RequestContext.currentRequestId()` / `RequestContext.MDC_KEY`；`RequestIdFilter.MDC_KEY` 改為 `RequestContext.MDC_KEY` 的別名（`public static final String MDC_KEY = RequestContext.MDC_KEY`），`RequestIdFilter.current()` 委派給 `RequestContext`**

- [ ] **Step 9: `ClasspathText` 改用 `ClassLoader`、public；`ConfigService`、`MongoSchemaInitializer`、`SeedLoader` import 改 `application.support.ClasspathText`**

- [ ] **Step 10: 確認 domain 沒有 Spring/Mongo/jakarta.annotation import**

Run: `grep -rln "import org.springframework\|import com.mongodb\|import org.bson\|import jakarta.annotation\|AiAssistantProperties" backend/src/main/java/com/example/copilot/aiassistant/domain`
Expected: 只剩 `domain/config/AssistantConfig.java`、`domain/repository/AssistantConfigRepository.java`、`domain/repository/EvalRunRepository.java`、`domain/audit/AuditRecord.java`（Phase 2 處理）

- [ ] **Step 11: 跑全部測試**

Run: `cd backend && mvn -q test`
Expected: 全綠

---

### Task 3: 依賴規則測試

**Files:**
- Create: `backend/src/test/java/com/example/copilot/aiassistant/ArchitectureRulesTest.java`

**Interfaces:**
- Produces: `ArchitectureRulesTest`，Phase 2 Task 7 會移除其暫時豁免並加上 application 規則。

- [ ] **Step 1: 寫測試**

純 JUnit，不加依賴。用 `Files.walk(Path.of("src/main/java/com/example/copilot/aiassistant"))` 讀每個 `.java`，取 `import ` 開頭的行（含 `import static`），依檔案所在層套用 spec §4 的禁止前綴表。測試方法：

```java
@Test void domainHasNoFrameworkImports()        // 禁止前綴：spec §4 domain 列
@Test void interfacesDoNotTouchRepositoriesOrInfrastructure()
@Test void infrastructureDoesNotDependOnInterfaces()
@Test void onlyFourLayersUnderAiassistant()     // aiassistant 直下只有 4 個資料夾 + AiAssistantProperties.java
@Test void noAppPackage()                       // 任何路徑都不含 "/app/"
```
失敗訊息列出「檔案路徑: 違規 import」每行一筆。

暫時豁免清單（常數 `PHASE2_EXEMPT`，Task 7 刪除）：`domain/config/AssistantConfig.java`、`domain/repository/AssistantConfigRepository.java`、`domain/repository/EvalRunRepository.java`、`domain/audit/AuditRecord.java`。本 task **不**檢查 application 層（Task 7 補）。

- [ ] **Step 2: 跑測試**

Run: `cd backend && mvn -q test -Dtest=ArchitectureRulesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS。若有違規，修正該 import（搬類別或補 port），不得擴大豁免清單。

- [ ] **Step 3: 跑全部測試**

Run: `cd backend && mvn -q test`
Expected: 全綠

- [ ] **Step 4: Phase 1 結束，交給使用者 commit**

提供：

```bash
git add backend/src docs/superpowers/specs/2026-11-02-clean-architecture-layering-design.md docs/superpowers/plans/2026-11-02-clean-architecture-layering.md
```
commit message：`refactor(backend): split aiassistant into domain/application/interfaces/infrastructure layers`（內文列：套件搬移；domain 去 Spring，bean 由 DomainBeanConfiguration 註冊；TwseGateway、RequestContext、PublishedConfigs；ArchitectureRulesTest 含 Phase 2 暫時豁免）。**停下來等使用者確認已 commit。**

---

## Phase 2：資料層隔離

### Task 4: AssistantConfig 聚合、Document、Repository 介面與實作

**Files:**
- Create: `domain/config/AssistantConfig.java`（改寫為 record）
- Create: `infrastructure/mongo/AssistantConfigDocument.java`（今日的 class 內容搬來，`@Document("COLL_ICRM_AI_ASSISTANT_CONFIG")`）
- Create: `infrastructure/mongo/AssistantConfigMapper.java`
- Create: `infrastructure/mongo/AssistantConfigMongoRepository.java`（`interface ... extends MongoRepository<AssistantConfigDocument, String>`，今日 `AssistantConfigRepository` 的 8 個 query method 改型別）
- Create: `infrastructure/mongo/AssistantConfigRepositoryImpl.java`
- Rewrite: `domain/repository/AssistantConfigRepository.java`（純介面，spec §5.1）
- Modify: `application/config/ConfigService.java`、`application/config/PublishedConfigCache.java`、`application/config/ConfigSummary.java`、`application/evaluation/EvalRunService.java`、`application/feedback/FeedbackService.java`、`application/chat/SuggestionService.java`、`application/chat/InstructionRouter.java`、`application/chat/ChatOrchestrator.java`、`domain/prompt/PromptAssembler.java`、`infrastructure/seed/SeedLoader.java`、`interfaces/rest/config/AdminConfigController.java`、`interfaces/rest/config/dto/ConfigVersionView.java`、`VersionHistoryItem.java`
- Test: 新 `infrastructure/mongo/AssistantConfigRepositoryImplTest.java`；調整 `application/config/ConfigServiceTest`、`infrastructure/scheduler/ScheduledPublishJobTest`、`infrastructure/mongo/MongoSchemaTest`、`infrastructure/seed/SeedLoaderTest`、`application/config/PublishedConfigCacheTest`、`application/chat/SuggestionServiceTest`、`application/chat/InstructionRouterTest`、`domain/prompt/PromptAssemblerTest`、`application/chat/ChatOrchestratorTest`、`application/evaluation/EvalRunServiceTest`、`EvalRunnerTestBase`、`EvalRunnerTest`、`interfaces/rest/config/AdminConfigControllerTest`、`interfaces/rest/evaluation/EvalControllerTest`

**Interfaces:**
- Produces:

```java
// domain.config
public record AssistantConfig(String id, String configKey, ConfigType configType, String name, String content,
        ConfigStatus status, int version, Integer basedOnVersion, String changeNote, String createdBy,
        Instant createdAt, String updatedBy, Instant updatedAt, Instant scheduledAt, Long docVersion) {
    public static AssistantConfig newDraft(String configKey, ConfigType type, String name, String content,
                                           int version, String actor, Instant now);   // status DRAFT, created/updated = actor/now
    public AssistantConfig withContent(String name, String content, String changeNote);
    public AssistantConfig withStatus(ConfigStatus status, Instant scheduledAt);
    public AssistantConfig withBasedOnVersion(Integer basedOnVersion);
    public AssistantConfig touched(String actor, Instant now);                           // updatedBy/updatedAt
    public boolean isWorkingCopy();                                                      // DRAFT || SCHEDULED
}
// domain.repository.AssistantConfigRepository：spec §5.1 的 13 個方法
// infrastructure.mongo
final class AssistantConfigMapper { static AssistantConfigDocument toDocument(AssistantConfig c); static AssistantConfig toDomain(AssistantConfigDocument d); }
@Repository public class AssistantConfigRepositoryImpl implements AssistantConfigRepository {
    public AssistantConfigRepositoryImpl(AssistantConfigMongoRepository mongoRepository, MongoTemplate mongo, MongoTransactionManager transactionManager);
}
```
- `ConfigService` 建構子改 `(AssistantConfigRepository repository, ConfigValidator validator, PublishedConfigCache cache, Clock clock, AiAssistantProperties properties)`；公開方法簽名不變，回傳型別由 class 改為 record（呼叫端 `getX()` → `x()`）。
- `ConfigSummary.VersionInfo.of(AssistantConfig)` 改讀 record accessor。

- [ ] **Step 1: 先寫 `AssistantConfigRepositoryImplTest`（繼承 `IntegrationTestBase`），把今日 `ConfigServiceTest` 與 `ScheduledPublishJobTest` 裡「以 delegating mock 注入 Spring Data 例外」「WriteConflict 112 / duplicate key 11000 / validation 121」「兩個實例競賽認領」的測試搬過來改成直接打 repository 實作**

至少包含：
```java
@Test void insertDuplicateKeyThrowsConflict()                 // 同 configKey+version 兩次 insert → AssistantException AI_CONFIG_CONFLICT
@Test void saveWithStaleDocVersionThrowsConflict()            // 讀出 → 另一份先 save → 再 save 舊的 → AI_CONFIG_CONFLICT
@Test void publishArchivesPreviousPublishedInOneTransaction() // 發布後只有一份 PUBLISHED，舊的 ARCHIVED 且 updatedAt 不變
@Test void publishScheduledLosesRaceReturnsFalseAndRollsBackArchive() // 認領失敗時先前的封存被回滾
@Test void validationFailure121Propagates()                   // 非衝突的 Mongo 錯誤原樣丟出（DataIntegrityViolationException，root cause code 121）
@Test void replaceWorkingCopyDiscardsOldAndInsertsDraftAtomically()
```

- [ ] **Step 2: 跑新測試確認失敗（類別不存在）**

Run: `cd backend && mvn -q test-compile`
Expected: 編譯失敗，`AssistantConfigRepositoryImpl` 不存在

- [ ] **Step 3: 實作 record、Document、Mapper、Spring Data 介面、`AssistantConfigRepositoryImpl`**

- 交易與衝突對應：今日 `ConfigService` 的 `write()`、`isMongoConflict()`、`isLostRace()`、`archivePublished()`、`publishScheduled()` 原樣搬進 Impl；`publish()`、`replaceWorkingCopy()`、`publishScheduled()` 用 `TransactionTemplate` 包。
- `save`：mapper 必須把 record 的 `id` 與 `docVersion` 帶進 document，交給 `mongoRepository.save` 讓 `@Version` 做條件更新；回傳 `toDomain(saved)`（docVersion 已 +1）。
- `insert`：`mongoRepository.insert(doc)`，docVersion 由 Spring Data 設為 0。

- [ ] **Step 4: `ConfigService` 改寫為只用 `AssistantConfigRepository`：拿掉 `MongoTemplate`、`TransactionTemplate`、`MongoException` 與所有 `org.springframework.data`/`transaction` import；`newVersion/touch/setX` 改用 record 的 `newDraft/with*/touched`；`publish()` 的即時分支呼叫 `repository.publish(wc.withStatus(PUBLISHED, null))`；`applyVersion()` 呼叫 `repository.replaceWorkingCopy(discarded, draft)`；`publishDue()` 對每筆 `repository.publishScheduled(due, now)`，`RuntimeException` 記 warn（lost race 已在 Impl 回 false，不再需要 `isLostRace`）；`list()` 需要不含 content 的投影，Phase 3 才拆到 QueryRepository，本 task 在 `AssistantConfigRepository` 暫加 `List<AssistantConfig> findAllWithoutContent()`（Impl 以 `Query.fields().exclude("content")` 實作，回傳的 record `content` 為 null），`list()` 改用它，`summarize()` 不變；Task 8 刪除此方法**

- [ ] **Step 5: 其他呼叫端改 record accessor 與新 repository 方法**：`PublishedConfigCache.loadFresh()` 用 `repository.findPublished()`；`SeedLoader` 用 `repository.count()` 與 `repository.insert(AssistantConfig.newDraft(...).withStatus(PUBLISHED, null))`（createdBy/updatedBy 為 `"system"`，version 1）；`EvalRunService` 拿掉 `ConfigService` 依賴，改 `repository.findLatest`、`repository.findWorkingCopy`；`FeedbackService.stats` 用 `repository.findLatest`；`AdminConfigController.view`、`ConfigVersionView.of`、`VersionHistoryItem.of`、`SuggestionService`、`InstructionRouter`、`PromptAssembler`、`ChatOrchestrator` 的 `getX()` → `x()`

- [ ] **Step 6: 調整測試**：`ConfigServiceTest` 保留流程測試（狀態機、驗證、排程視窗、NOT_FOUND/CONFLICT/INVALID_STATE 判斷），建構改 `newService(AssistantConfigRepository)`，以 `@Autowired AssistantConfigRepository` 或 Mockito mock 注入；搬去 Impl 測試的方法從這裡刪除（一個斷言都不能遺失，兩邊對照）。`MongoSchemaTest`、`SeedLoaderTest` 改用 `AssistantConfigMongoRepository`/`MongoTemplate` 直接插 document 觸發 validator 與 index。`PublishedConfigCacheTest`、`SuggestionServiceTest`、`InstructionRouterTest`、`PromptAssemblerTest` 的 `AssistantConfig` 建構改用 record 建構子或 `newDraft(...).withStatus(...)`。

- [ ] **Step 7: 跑全部測試**

Run: `cd backend && mvn -q test`
Expected: 全綠；`grep -rn "org.springframework.data\|org.springframework.transaction\|com.mongodb\|org.bson" backend/src/main/java/com/example/copilot/aiassistant/application/config` 無輸出

---

### Task 5: Audit Repository 與純 AuditRecord

**Files:**
- Modify: `domain/audit/AuditRecord.java`（拿掉 `@Id`、`@Document`、`org.bson`；`ToolCall.result` → `Map<String, Object>`；刪 `toDocument()`）
- Create: `domain/repository/AuditRepository.java`
- Create: `infrastructure/mongo/AuditRecordMapper.java`（今日 `toDocument()`/`toolCallDocument()`，`result` 以 `new Document(map)` 轉）
- Create: `infrastructure/mongo/AuditRepositoryImpl.java`
- Modify: `application/audit/AuditLogger.java`（`MongoTemplate` → `AuditRepository`；`resultDocument` 改回傳 `Map<String,Object>`：`data.isObject()` 時 `JSON.convertValue(data, new TypeReference<Map<String,Object>>(){})`，否則 `Map.of("value", JSON.convertValue(data, Object.class))`）
- Create: `application/feedback/FeedbackStatsQueryRepository.java`（介面，簽名見 Task 9 的 Interfaces）
- Create: `infrastructure/mongo/FeedbackStatsQueryRepositoryImpl.java`（今日 `FeedbackService.stats` 的 aggregation 搬來，`routedOnly=true` 時才加 `routeSource in (ROUTER, CHIP)` 條件）
- Modify: `application/feedback/FeedbackService.java`（建構子改 `(AuditRepository audits, FeedbackStatsQueryRepository stats, AssistantConfigRepository configs, Clock clock)`；`rate()` 改呼叫 `audits.setFeedback(...)`，回 false 時丟 `AI_FEEDBACK_NOT_FOUND`；`stats()` 改 `configs.findLatest` 決定 `routedOnly` 後呼叫 `stats.versionStats`；拿掉所有 Spring Data import。Task 9 再把 `stats()` 搬到 `FeedbackQueryService`）
- Test: `application/audit/AuditLoggerTest`（`@Autowired AuditLogger`、`MongoTemplate` 讀回驗證不變；「寫入失敗只記 log」測試改 mock `AuditRepository`）、`AuditValidatorLimitsTest`（建構子改 `(null, Clock, null, props, null)` 型別）、`application/chat/ChatOrchestratorTest`（`new AuditLogger(null, ...)` 第一參數型別）

**Interfaces:**
- Produces:
```java
public interface AuditRepository {
    void insert(AuditRecord record);
    boolean setFeedback(String requestId, String userId, FeedbackRating rating, Instant at); // rating null = 清除
}
public AuditLogger(AuditRepository repository, Clock clock, CredentialMasker masker, AiAssistantProperties props, PiiRedactor redactor);
```

- [ ] **Step 1: 在 `AuditLoggerTest` 補一個測試：`storedResultKeepsNestedNumbers`——`store-tool-results=true` 時 `result.close` 讀回是 `Integer 2420`（已有 `storesToolResultWhenEnabled` 的話直接沿用，不新增）**
- [ ] **Step 2: 跑 `AuditLoggerTest`，確認基準線通過**
- [ ] **Step 3: 實作 `AuditRepository`、`AuditRecordMapper`、`AuditRepositoryImpl`（`setFeedback` 是今日 `FeedbackService.rate` 的 `findAndModify`）、`FeedbackStatsQueryRepository` 與 Impl，改 `AuditLogger`、`FeedbackService`；`FeedbackControllerTest` 斷言不變**
- [ ] **Step 4: 跑全部測試**

Run: `cd backend && mvn -q test`
Expected: 全綠

---

### Task 6: EvalRunRepository 介面化

**Files:**
- Rewrite: `domain/repository/EvalRunRepository.java` → 純介面
- Create: `infrastructure/mongo/EvalRunRepositoryImpl.java`（今日 class 全部內容，`@Repository`，實作介面；`toDocument`/`fromDocument` 留 package-private static）
- Modify: `application/evaluation/EvalRunService.java`、`EvalRunner.java`（改 import；`EvalRunService.start` 原本 catch `DuplicateKeyException`，改為 catch `AssistantException` 且 `code() == AI_EVAL_RUNNING`，再以 `runningConflict()` 重新丟出含 details 的版本；其他 `AssistantException` 原樣往外丟）
- Test: 搬 `EvalRunServiceTest`、`EvalRunnerTest*` 用到 `EvalRunRepository` 的地方改型別；`EvalRunValidatorLimitsTest` 若引用 `EvalRunRepository.toDocument` 改成 `EvalRunRepositoryImpl.toDocument`，測試搬到 `infrastructure/mongo/`

**Interfaces:**
```java
public interface EvalRunRepository {
    String insert(EvalRun run);                 // 第二個 RUNNING → AssistantException(AI_EVAL_RUNNING)
    Optional<EvalRun> findById(String id);
    Optional<EvalRun> findRunning();
    List<EvalRun> recent(String configKey, int limit);
    boolean appendCase(String id, long docVersion, EvalCaseRecord c, Instant now);
    boolean touch(String id, long docVersion, Instant now);
    boolean complete(String id, long docVersion, EvalSummary s, Instant now);
    boolean fail(String id, long docVersion, String errorCode, Instant now);
}
```

- [ ] **Step 1: 改介面與 Impl，改呼叫端**
- [ ] **Step 2: `grep -rn "org.springframework.data\|org.bson\|com.mongodb" backend/src/main/java/com/example/copilot/aiassistant/application backend/src/main/java/com/example/copilot/aiassistant/domain`**

Expected: 無輸出

- [ ] **Step 3: 跑全部測試**

Run: `cd backend && mvn -q test`
Expected: 全綠

---

### Task 7: 依賴規則測試補 application 層並移除豁免

**Files:**
- Modify: `backend/src/test/java/com/example/copilot/aiassistant/ArchitectureRulesTest.java`

- [ ] **Step 1: 刪除 `PHASE2_EXEMPT`；新增 `applicationHasNoSpringDataOrMongoImports()`（spec §4 application 列）與 `implementationsEndWithImpl()`（`infrastructure` 底下所有實作 `domain.repository.*` 或 `application.*QueryRepository` 介面的 class 名稱以 `Impl` 結尾；用檔名 + `implements` 行判斷）**
- [ ] **Step 2: 跑測試**

Run: `cd backend && mvn -q test -Dtest=ArchitectureRulesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 3: 跑全部測試**

Run: `cd backend && mvn -q test`
Expected: 全綠

- [ ] **Step 4: Phase 2 結束，交給使用者 commit**

```bash
git add backend/src
```
commit message：`refactor(backend): move persistence behind domain repository interfaces`（內文：AssistantConfig 改 record 並與 Document 分離；AssistantConfigRepositoryImpl 負責交易與衝突對應；AuditRepository、EvalRunRepository 介面化；ArchitectureRulesTest 加 application 規則、移除豁免）。**停下來等使用者確認已 commit。**

---

## Phase 3：CQRS 拆分

### Task 8: Config 的 Command / Query 拆分

**Files:**
- Rename: `application/config/ConfigService.java` → `ConfigCommandService.java`（class 名同步；`ConfigServiceTest` → `ConfigCommandServiceTest`）
- Create: `application/config/ConfigQueryService.java`
- Create: `application/config/ConfigQueryRepository.java`
- Create: `application/config/VersionHistoryEntry.java`
- Create: `infrastructure/mongo/ConfigQueryRepositoryImpl.java`
- Modify: `domain/repository/AssistantConfigRepository.java`（刪 Task 4 暫加的 `findAllWithoutContent()`）、`infrastructure/mongo/AssistantConfigRepositoryImpl.java`
- Modify: `interfaces/rest/config/AdminConfigController.java`、`interfaces/rest/config/dto/VersionHistoryItem.java`
- Modify: `infrastructure/scheduler/ScheduledPublishJob.java`、`application/evaluation/EvalRunService.java`（若仍引用 `ConfigService`）
- Test: 新 `application/config/ConfigQueryServiceTest.java`、`infrastructure/mongo/ConfigQueryRepositoryImplTest.java`；`ConfigCommandServiceTest` 刪掉 `list/versions/version` 相關測試（搬到上述兩個新測試）；`AdminConfigControllerTest` 不改斷言

**Interfaces:**
```java
public record VersionHistoryEntry(int version, ConfigStatus status, Instant scheduledAt, String changeNote,
                                  Integer basedOnVersion, String createdBy, String updatedBy, Instant updatedAt) {}
public interface ConfigQueryRepository {
    List<ConfigSummary> listSummaries();                      // 不讀 content；依 configType、configKey 排序（今日 ConfigService.list + summarize）
    List<VersionHistoryEntry> versionHistory(String configKey); // 不讀 content；version 由大到小；無資料回空
}
@Service public class ConfigQueryService {
    public ConfigQueryService(ConfigQueryRepository queries, AssistantConfigRepository repository);
    public List<ConfigSummary> list();
    public List<VersionHistoryEntry> versions(String configKey);     // 空 → AssistantException(AI_CONFIG_NOT_FOUND)
    public AssistantConfig version(String configKey, int version);   // 缺 → AI_CONFIG_NOT_FOUND
}
// ConfigCommandService：今日 ConfigService 去掉 list/versions/version；保留 validateWorkingCopy、findWorkingCopy
```
- `VersionHistoryItem.of(VersionHistoryEntry)`；`AdminConfigController` 同時注入 `ConfigCommandService` 與 `ConfigQueryService`。

- [ ] **Step 1: 寫 `ConfigQueryRepositoryImplTest`（整合）：`listSummariesGroupsByKeyAndPrefersPublishedName`、`listSummariesQueryExcludesContent`（Impl 把 query 建構抽成 package-private static `summariesQuery()` 與 `historyQuery(String configKey)`，斷言 `getFieldsObject().get("content")` 等於 `0`）、`versionHistoryNewestFirst`、`versionHistoryUnknownKeyIsEmpty`**
- [ ] **Step 2: 寫 `ConfigQueryServiceTest`（單元，mock 兩個 repository）：`versionsThrowsNotFoundWhenEmpty`、`versionThrowsNotFoundWhenMissing`、`listDelegates`**
- [ ] **Step 3: 跑這兩個測試，確認編譯失敗**
- [ ] **Step 4: 實作 rename、`ConfigQueryRepository`/Impl、`ConfigQueryService`、controller 改接線、刪 `findAllWithoutContent`**
- [ ] **Step 5: 跑全部測試**

Run: `cd backend && mvn -q test`
Expected: 全綠

---

### Task 9: Feedback 的 Query 拆分

**Files:**
- Create: `application/feedback/FeedbackQueryService.java`
- Modify: `application/feedback/FeedbackService.java`（只剩 `rate`；建構子 `(AuditRepository, Clock)`；`stats()` 與 `FeedbackStatsQueryRepository`、`AssistantConfigRepository` 依賴搬到 `FeedbackQueryService`）
- Modify: `interfaces/rest/feedback/FeedbackController.java`（`stats` 改呼叫 `FeedbackQueryService`）
- Test: `interfaces/rest/feedback/FeedbackControllerTest` 斷言不變；新 `infrastructure/mongo/FeedbackStatsQueryRepositoryImplTest`（把 `FeedbackControllerTest` 中直接驗證統計規則的案例——routed only、fallback 排除、legacy 無 routeSource 排除——若已在 controller 測試覆蓋則不重複，只加 `routedOnlyFalseCountsEveryAnswer`）

**Interfaces:**
```java
public interface FeedbackStatsQueryRepository {
    List<VersionFeedback> versionStats(String configKey, boolean routedOnly); // version 由大到小；無評分的版本不出現
}
@Service public class FeedbackQueryService {
    public FeedbackQueryService(FeedbackStatsQueryRepository stats, AssistantConfigRepository configs);
    public List<VersionFeedback> stats(String configKey); // 未知 key → AI_CONFIG_NOT_FOUND；SYSTEM_INSTRUCTION → routedOnly=true
}
```

- [ ] **Step 1: 寫 `FeedbackStatsQueryRepositoryImplTest.routedOnlyFalseCountsEveryAnswer`（Task 5 的 Impl 已存在，測試檔若 Task 5 未建則在此新建）**
- [ ] **Step 2: 建 `FeedbackQueryService`，縮減 `FeedbackService`，`FeedbackController` 接線**
- [ ] **Step 3: 跑全部測試**

Run: `cd backend && mvn -q test`
Expected: 全綠

- [ ] **Step 4: Phase 3 結束，交給使用者 commit**

```bash
git add backend/src
```
commit message：`refactor(backend): split config and feedback into command and query services`。**停下來等使用者確認已 commit。**

---

## Phase 4：文件

### Task 10: 架構文件與待確認事項更新

**Files:**
- Modify: `docs/spec/01-architecture.md` §5.1（目錄樹與 Clean Architecture 對應表）、版本列
- Modify: `docs/questions-for-manager.md` S2 回覆欄
- Modify: `docs/spec/04-mongodb-schema.md`（若有提到 `AssistantConfig` class 的欄位對應，改為「`AssistantConfigDocument`（infrastructure）↔ `AssistantConfig` record（domain）」一句）

- [ ] **Step 1: 01-architecture §5.1 目錄樹換成 spec §3 的四層樹；對應表改為四列（Domain / Application / Interfaces / Infrastructure：package、允許依賴、本專案的類別舉例）；加一段「例外與驗證」：LangChain4j、Jackson、Bean Validation 在 domain 允許的理由；`ArchitectureRulesTest` 固定規則；CQRS 套用在 config 與 feedback，evaluation 不投影的理由。版本列加 v1.3（2026-11-02）**
- [ ] **Step 2: questions-for-manager S2 回覆：「平台守則為 `domain / application / interfaces / infrastructure`（reference.md §3），2026-11-02 已對齊，見 01-architecture §5.1」**
- [ ] **Step 3: 全文搜尋舊套件路徑**

Run: `grep -rn "aiassistant/chat/\|aiassistant/config/\|aiassistant\.common\|aiassistant/port\|aiassistant/prompt/" docs .vscode backend/README.md 2>/dev/null`
Expected: 只剩歷史性的 plan/spec 檔（`docs/superpowers/**` 不改）；`docs/spec/**`、`docs/*.md`、`.vscode/**` 無舊路徑

- [ ] **Step 4: 最後一次全部測試**

Run: `cd backend && mvn -q test`
Expected: 全綠

- [ ] **Step 5: 交給使用者 commit**

```bash
git add docs/spec/01-architecture.md docs/questions-for-manager.md docs/spec/04-mongodb-schema.md
```
commit message：`docs: describe the four-layer backend structure and answer S2`。
