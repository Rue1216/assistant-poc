# 後端分層守則細項對齊設計

> 版本 v0.1・2026-11-12。依 reference.md 新增的「§3-0 守則細項」，調整 `backend/.../aiassistant` 中**不需要平台原始碼就能完成**的部分，讓搬進平台時只剩「把替身換成平台正本」。
> 前一版分層設計：[2026-11-02-clean-architecture-layering-design.md](2026-11-02-clean-architecture-layering-design.md)。本文件取代該文件 §2 中「交易邊界由 infrastructure 負責」與「Domain 允許 Jackson 註解」兩項決定，其餘仍然有效。

## 1. 平台守則（摘自 reference.md §3-0，reference.md 不進版控，故抄錄於此）

- **3-0-1 Domain**：禁止 `import org.springframework`；value object 用 `record`，封裝格式驗證；Domain model 與 Document 嚴格分離；有界集合可嵌入 Aggregate，無界集合獨立 Collection、Aggregate 僅持有 ID。
- **3-0-2 Application**：Command 側以 `@UseCase` 標註，放 `application/command/`；協調 domain 物件，不含業務規則；`@Transactional` 只宣告於 UseCase / ApplicationService。
- **3-0-3 Interface**：無業務邏輯，無 try-catch；回傳 `ResponseEntity<ApiResponse<T>>`；Request DTO 必須 `@Valid`；REST API method 必須 `@AuditLog(module=AuditModule.XXX)`；授權由 `SecurityConfig` 以 `anyRequest().authenticated()` 控管，方法層不標角色 `@PreAuthorize`。
- **3-0-4 Infrastructure**：實作 Domain Repository Interface，使用 `MongoTemplate` + `Criteria`；Document 攜帶 `@Document`、`@Id`、`@Version`，Domain 層禁止；Command 側 Document ↔ Domain 完整映射，Query 側直接投影 DTO。
- **3-0-5 MapStruct**：機敏資料遮罩在 DTO 轉換時處理（`MaskUtils`）；DocumentMapper 僅 Command 側使用；`@Mapper(componentModel="spring")`。
- **3-0-6 Audit Log**：`@AuditLog` + `AuditLogAspect` 寫入 `COLL_COMMON_AUDIT_LOG`；`capturePayload` 預設 false；索引用 `indexOps(...).createIndex(...)`。
- 另見 §7：Domain 禁止 `@JsonIgnore`。

## 2. 範圍

### 2.1 本次要做

| 守則 | 項目 |
| --- | --- |
| 3-0-1 | 移除 domain 的 `@JsonIgnore`；集合名稱常數移出 domain；新增 `CertNo`、`ConfigKey`、`TraceId` 值物件 |
| 3-0-2 | `application/command/`、`application/query/` 目錄；`@UseCase` 替身；`AssistantConfig` 狀態規則收進聚合；`@Transactional` 移到 UseCase |
| 3-0-3 | controller 移除 try-catch 與內容大小檢查；`ApplyRequest` 加 `@Valid` |
| 3-0-4 | `MongoTemplate` + `Criteria` 取代 Spring Data 衍生查詢；Audit / EvalRun 改用 `@Document` 類別 |
| 3-0-5 | 三個 DocumentMapper 改用 MapStruct |
| 文件 | `backend/PORTING.md`、`01-architecture.md`、`03-tool-catalog.md`、`04-mongodb-schema.md`、11-02 spec 註記 |

### 2.2 本次不做（搬進平台後再調整，已記在 PORTING.md）

| 項目 | 原因 |
| --- | --- |
| 移除方法層 `@PreAuthorize` | 依平台 `SecurityConfig` 與模組權限模型決定；reference.md 3-0-3 與「安全開發規範 §4」互相矛盾。拿掉會讓任何登入者都能操作 Prompt 管理。 |
| `ResponseEntity<ApiResponse<T>>` 統一回應 | 錯誤轉換依賴平台 `GlobalExceptionHandler` / `DomainException`；前後端要一起改，現在仿寫會改兩次。 |
| `@AuditLog` | `AuditModule` 沒有小幫手的值（要平台新增），repo 也沒有 `AuditLogAspect`。 |
| `MaskUtils` | 目前沒有任何 API 回傳要顯示的顧客個資，不適用。 |

### 2.3 完成標準

- 四個階段各自 `mvn test` 全過。
- 前端零改動：API 路徑、請求、回應、錯誤碼（含 `details` 內的欄位名稱）完全不變。
- Mongo 中 config、audit、eval_run 的欄位集合不變（不寫 `_class`、原本明確寫入的 null 欄位仍存在）。

## 3. 第 1 階段：Domain 清理

### 3.1 `@JsonIgnore`

- 移除 `domain/privacy/CustomerContext` 兩個欄位上的 `@JsonIgnore`。
- 原本的用意是防止真實身分值被序列化。改由測試保證：掃描 `interfaces/rest/**/dto` 與 `infrastructure/mongo/*Document` 的欄位型別，任何一層都不得出現 `CustomerContext`。`toString()` 遮蔽維持。

### 3.2 集合名稱

- 新增 `infrastructure/mongo/AiAssistantCollections`（寫法比照平台 `CommonCollections`）：`CONFIG`、`AUDIT`、`EVAL_RUN` 三個常數，值維持 `COLL_ICRM_AI_ASSISTANT_*`。
- 刪除 `AuditRecord.COLLECTION`、`EvalRun.COLLECTION`；`@Document(...)`、`MongoSchemaInitializer`、`FeedbackStatsQueryRepositoryImpl`、各 RepositoryImpl 改引用此類別。

### 3.3 值物件

| 值物件 | 位置 | 格式（沿用現行） | 驗證失敗 |
| --- | --- | --- | --- |
| `CertNo` | `domain/customer/` | `^[A-Z0-9&]{8,12}$` | `AssistantException(AI_INPUT_INVALID, FieldDetail("certNo"))` |
| `ConfigKey` | `domain/config/` | `^(system\|instruction)\.[a-z0-9-]{1,50}$` | `AssistantException(AI_INPUT_INVALID, FieldDetail("configKey"))` |
| `TraceId` | `domain/audit/` | `^[0-9a-f]{16,32}$` | `AssistantException(AI_INPUT_INVALID, FieldDetail("traceId"))` |

- 每個 record 公開 `PATTERN` 常數，是格式規則的唯一來源。REST DTO 與 controller 的 `@Pattern` 改引用它（例如 `@Pattern(regexp = ConfigKey.PATTERN)`），原本的 `ConfigKeys.PATTERN` 刪除（`NAME_PATTERN` 保留）。保留 `@Pattern` 是為了讓錯誤回應的欄位資訊與現在完全相同；值物件建構子再驗一次作為防線。
- `ConfigKey` 另提供 `isSystemPrompt()`、`isInstruction()`，取代 `AssistantConfig.SYSTEM_PROMPT_KEY` / `INSTRUCTION_KEY_PREFIX` 的字串比對。

**推進範圍（決定 A）**：application 入口與 domain 用值物件，邊界維持 `String`。

| 使用值物件 | 維持 `String` |
| --- | --- |
| Command / Query 的方法參數（`ConfigCommandUseCase`、`ConfigQueryService`、`EvalRunCommandUseCase`、`EvalRunQueryService`、`FeedbackCommandUseCase`、`FeedbackQueryService`、`ChatUseCase`、`SuggestionQueryService`） | REST DTO 欄位、path variable 宣告型別 |
| `AssistantConfig.configKey`、`EvalRun.configKey` | Mongo Document 欄位、查詢投影（`ConfigSummary`、`VersionHistoryEntry`、`VersionFeedback`） |
| `CustomerContext.certNo` | 3 個 port（`CustomerAccessPort`、`CustomerDataPort`、`CurrentUserPort`）：搬遷時由平台實作，不讓平台 adapter 依賴小幫手型別；application 呼叫時以 `.value()` 解開 |
| `AssistantConfigRepository`、`EvalRunRepository` 的 configKey 參數；`AuditRepository.setFeedback` 的 traceId 參數 | `CustomerRecord`、`c360/*`（來自 port 的資料）、`EvalCase`（資料集）、`PromptVersion`、`AuditRecord.traceId`、`ChatResult.traceId` |

- `AuditRecord.traceId` 維持 `String`：它來自 MDC，可能是上游 `X-B3-TraceId` 帶進來的任意值，不能因格式不符讓對話失敗。`TraceId` 只用在回饋 API 的輸入。
- 轉換位置只有兩處：controller（`String` → 值物件）與 mapper（值物件 ↔ `String`）。

### 3.4 測試

- 三個值物件各一個單元測試（合法、不合法、null）。
- `CustomerContext` 不出現在 DTO / Document 的測試。
- 既有測試改用值物件建構參數後全過。

## 4. 第 2 階段：持久化

### 4.1 `MongoTemplate` + `Criteria`

- 刪除 `AssistantConfigMongoRepository`（Spring Data 介面）。
- 其 7 個衍生查詢在 `AssistantConfigRepositoryImpl` 內改寫為 `Query` + `Criteria`（排序、`findTop` 以 `with(Sort)` + `limit(1)` 表達）；`insert` / `save` / `count` 改用 `mongo.insert` / `mongo.save` / `mongo.count`。`@Version` 樂觀鎖在 `MongoTemplate.save` 上同樣有效。

### 4.2 `@Document` 類別

| 類別 | 內容 |
| --- | --- |
| `AssistantConfigDocument` | 維持；移除 `@TypeAlias` |
| `AuditRecordDocument`（新增） | 對應 `AuditRecord`；巢狀 `ToolCallDocument`、`PromptVersionDocument`；`ToolCall.result` 為 `Map<String, Object>` |
| `EvalRunDocument`（新增） | 對應 `EvalRun`；巢狀 baseline、summary、case 子文件類別 |

- 寫法比照平台 `AuditLogDocument`：Lombok `@Getter` / `@Setter`、欄位 Javadoc。
- 現行手組 `org.bson.Document` 時明確寫入 null 的欄位（例如 `answer`、`errorCode`、`instructionKey`、`modelName`、`feedback`、`feedbackAt`，EvalRun 的 `summary`、`finishedAt` 等），一律加 `@Field(write = Field.Write.ALWAYS)`，維持「欄位一定存在」。實作時以現行 `toDocument` 的 `append` 清單逐一比對，不得遺漏。
- validator（`mongo/*-validator.json`）必填欄位皆非 null，`additionalProperties: true`，查詢也沒有依賴 `$exists`；以上做法不會被 validator 擋下。
- EvalRun 的增量更新（`push("cases", ...)`、`inc("completedCases")`、`set("summary", ...)`）保留 `Update`；被寫入的子文件改傳 Document 類別，由 `MongoTemplate` 轉換。
- 讀取：`mongo.findById` / `mongo.findOne(query, XxxDocument.class, collection)`，再經 mapper 轉回 domain。

### 4.3 `_class`

- 比照平台 db-instructions §8：`MongoConfiguration` 設定全域 `DefaultMongoTypeMapper(null)`，所有 Document 都不寫 `_class`。
- 既有帶 `_class` 的舊資料可正常讀取（讀取以實體類別為準，欄位被忽略）。
- 搬遷時刪除 POC 的此設定，沿用平台的。

### 4.4 MapStruct

| Mapper | 取代 |
| --- | --- |
| `AssistantConfigDocumentMapper` | `AssistantConfigMapper`（手寫） |
| `AuditRecordDocumentMapper` | `AuditRecordMapper`（手寫，產生 `org.bson.Document`） |
| `EvalRunDocumentMapper` | `EvalRunRepositoryImpl` 內的 `toDocument` / `fromDocument` 等靜態方法 |

- 一律 `@Mapper(componentModel = "spring")`，由 RepositoryImpl 建構子注入。
- 值物件 ↔ `String` 以 mapper 內的 `default` 方法轉換；`Instant` 直接對應（Spring Data 原生支援）。
- 依 3-0-5，DocumentMapper 只給 Command 側（`AssistantConfigRepositoryImpl`、`AuditRepositoryImpl`、`EvalRunRepositoryImpl`）使用；Query 側（`ConfigQueryRepositoryImpl`、`FeedbackStatsQueryRepositoryImpl`）維持直接投影。

### 4.5 交易

本階段 `TransactionTemplate` 保留在 `AssistantConfigRepositoryImpl`，第 3 階段再移。

### 4.6 測試

- 既有 repository 整合測試全過。
- 新增「欄位集合」測試：分別寫入 config、audit、eval_run（含 null 欄位與 `toolCalls.result` 巢狀 map），以原生 `getCollection(...).find()` 讀回，斷言鍵集合與改版前相同、沒有 `_class`、null 欄位存在。預期鍵集合直接寫在測試中（取自現行 `toDocument` 的 `append` 清單）。

## 5. 第 3 階段：Application

### 5.1 目錄與命名（Command 採「一個聚合一個 UseCase」）

```
application/
├─ command/
│  ├─ config/      ConfigCommandUseCase（原 ConfigCommandService）、SaveCommand、PublishCommand、ApplyCommand、SaveMode、PublishMode、ConfigPublishedEvent
│  ├─ feedback/    FeedbackCommandUseCase（原 FeedbackService）
│  ├─ evaluation/  EvalRunCommandUseCase（原 EvalRunService.start 與 EvalSnapshot）
│  └─ chat/        ChatUseCase（原 ChatOrchestrator；寫入稽核，屬 Command）、ChatCommand
├─ query/
│  ├─ config/      ConfigQueryService、ConfigQueryRepository、ConfigSummary、VersionHistoryEntry
│  │               （另收原 Command 側的 validateWorkingCopy、findWorkingCopy，兩者只讀）
│  ├─ feedback/    FeedbackQueryService、FeedbackStatsQueryRepository、VersionFeedback
│  ├─ evaluation/  EvalRunQueryService（原 EvalRunService 的 current / get / recent）
│  └─ chat/        SuggestionQueryService（原 SuggestionService）
├─ chat/           InstructionRouter、HistorySummarizer、WorkflowRunner、StepArgumentExtractor、CustomerContextFactory、RoutingInput
├─ config/         PublishedConfigCache
├─ evaluation/     EvalRunner、EvalJudge
├─ audit/          AuditLogger
├─ llm/、port/、support/（不變）
```

- `command/`、`query/` 只放入口類別與它們專用的 Command record / QueryRepository；輔助類別留在原功能套件（決定 A）。
- `@UseCase`：使用者已抄入平台正本的副本 `com.example.shared.annotation.UseCase`（帶 `@Service`），直接使用；搬遷時改 import 平台正本。
- Command 入口類別一律標 `@UseCase`；Query 入口維持 `@Service`。

### 5.2 業務規則收進 `AssistantConfig`

| 現在在 `ConfigCommandService` 的規則 | 改為 |
| --- | --- |
| key 與 type 必須相符（`system.main` ↔ `SYSTEM_PROMPT`、`instruction.*` ↔ `SYSTEM_INSTRUCTION`） | `AssistantConfig.newKey(ConfigKey, ConfigType, name, content, actor, now)`：不符拋 `AI_INPUT_INVALID(configKey)` |
| `docVersion` 必須相符 | `config.requireDocVersion(Long expected)`：不符拋 `AI_CONFIG_CONFLICT` |
| 只有 DRAFT 能捨棄 | `config.discard(actor, now)`：非 DRAFT 拋 `AI_CONFIG_INVALID_STATE` |
| 只有 PUBLISHED / ARCHIVED / DISCARDED 能套用 | `config.requireApplicable()`：否則拋 `AI_CONFIG_INVALID_STATE` |
| 存檔：排程中且保留狀態時維持排程，其他情況退回 DRAFT | `config.edit(name, content, changeNote, SaveMode, actor, now)`；`config.keepsScheduleOnSave(SaveMode)` 告訴 UseCase 該做完整驗證還是只驗 metadata |
| 排程 / 立即發布的狀態轉移 | `config.schedule(at, actor, now)`、`config.publishNow(actor, now)` |
| 由某版本建立新草稿（createDraft、apply） | `source.copyAsDraft(nextVersion, actor, now)`（帶 `basedOnVersion`） |
| 預約時間窗 | domain `ScheduleWindow(Duration minLead, Duration maxLead)` record，`requireWithin(at, now)`；不符拋 `AI_INPUT_INVALID(scheduledAt)`。由 `infrastructure/config/DomainBeanConfiguration` 以 `AiAssistantProperties` 建立 bean |

- `ConfigValidator` 維持 domain service；UseCase 呼叫它屬於「協調」。
- `ConfigCommandUseCase` 只做：讀取 → 呼叫聚合方法 / `ConfigValidator` / `ScheduleWindow` → 儲存 → 發事件。
- `SaveMode` 從 application 搬到 `domain/config/`（聚合方法要用）；`PublishMode` 留在 application。

### 5.3 交易

- 交易管理器改為具名 bean `aiAssistantMongoTransactionManager`（`MongoConfiguration`）；常數 `application/support/Transactions.MONGO` 存放 bean 名稱。
- `@Transactional(transactionManager = Transactions.MONGO)` 標於：
  - `ConfigCommandUseCase.publish`（立即發布：封存舊版 + 寫入新版）
  - `ConfigCommandUseCase.applyVersion`（捨棄舊工作版本 + 建立新草稿）
  - `ConfigCommandUseCase.publishScheduled(AssistantConfig due)`（新方法，見下）
- `AssistantConfigRepository.publish`、`replaceWorkingCopy`、`publishScheduled` 不再自行開交易；刪除 `TransactionTemplate`。`publishScheduled` 認領失敗（docVersion 已變）時改拋 `AI_CONFIG_CONFLICT`，讓外層交易回滾（取代現在的 `setRollbackOnly()` + 回 false）。
- **排程迴圈移到 `ScheduledPublishJob`**（Spring `@Transactional` 對同類別內部呼叫無效，每筆要各自一個交易）：

```text
ScheduledPublishJob.run():
  for due in useCase.findScheduledDue():
    try:
      outcome = useCase.publishScheduled(due)   // PUBLISHED | REVERTED（重驗失敗退回草稿）
    catch AssistantException(AI_CONFIG_CONFLICT) 或 Mongo 衝突（112 / duplicate key）:
      log.debug lost race                        // 與現在相同
    catch RuntimeException:
      log.warn scheduled publish failed          // 與現在相同
  published > 0 時 log.info（與現在相同）
```

- 重驗失敗退回草稿（`revertScheduled`）的判斷與 WARN log 留在 `publishScheduled` 內；`isLostRace` 從 `AssistantConfigRepositoryImpl` 搬到 Job。
- **快取失效改為交易提交後**：`publish`、`publishScheduled` 成功時發布 `ConfigPublishedEvent`；`PublishedConfigCache` 以 `@TransactionalEventListener(phase = AFTER_COMMIT)` 監聽並 `invalidate()`。避免提交前就有請求把舊內容重新載入快取。
- **衝突轉換**：
  - 寫入當下的衝突（`OptimisticLockingFailureException`、`DuplicateKeyException`、`TransientClientSessionException`、Mongo 112 / 11000）仍由 repository 的 `write()` 轉成 `AI_CONFIG_CONFLICT`。
  - 提交時才發生的衝突（`TransactionSystemException`、帶 `TransientTransactionError` / `UnknownTransactionCommitResult` label 的 Mongo 例外）會在交易代理層拋出。由 infrastructure 新增的 `ConfigTransactionConflictAspect` 轉換：`@Around` 包住 `ConfigCommandUseCase` 的公開方法，順序設為 `Ordered.HIGHEST_PRECEDENCE`（在交易攔截器外層），cause chain 中有 Mongo 衝突就改拋 `AI_CONFIG_CONFLICT`，其餘原樣拋出。不放在 `AssistantExceptionHandler`，因為 interfaces 禁止 import `com.mongodb.`；判斷邏輯與 repository `write()` 共用同一個 `MongoConflicts.isConflict(Throwable)`。
  - `createKey` 把 `AI_CONFIG_CONFLICT` 轉成 `AI_CONFIG_INVALID_STATE` 的邏輯維持（無交易，衝突在寫入當下發生）。

### 5.4 測試

- `AssistantConfig` 每個新方法的單元測試（合法轉移、非法轉移拋出的錯誤碼）。
- `ScheduleWindow` 單元測試。
- 既有 config 整合測試全過，另補：
  - 立即發布時封存失敗 → 整筆回滾（舊 PUBLISHED 仍在、新版未變 PUBLISHED）。
  - 兩個執行緒同時 `publishScheduled` 同一筆 → 恰好一個成功，Job 不記 WARN。
  - 發布成功後快取在提交後才失效（以事件監聽測試驗證 AFTER_COMMIT；交易回滾時不失效）。

## 6. 第 4 階段：Interface 與收尾

### 6.1 Interface

- `PublishRequest.scheduledAt` 改為 `OffsetDateTime`，由 Jackson 解析；刪除 `AdminConfigController.parseScheduledAt` 與其 try-catch。
  - 解析失敗會拋 `HttpMessageNotReadableException`。`AssistantExceptionHandler` 的對應處理方法改為：若 cause 為 Jackson 的 `MismatchedInputException`（含 `InvalidFormatException`），以其 path 的最後一個欄位名稱產生 `FieldDetail`，讓 `scheduledAt` 解析失敗仍回 `AI_INPUT_INVALID` + `details: [{field: "scheduledAt"}]`，與現在相同；其他不可讀的 body 照舊回空 `details`。
  - `SCHEDULED` 模式但 `scheduledAt` 為 null 或空字串 → `ScheduleWindow.requireWithin(null, now)` 拋 `AI_INPUT_INVALID(scheduledAt)`，與現在相同。
- 內容大小檢查（`ConfigValidator.MAX_BYTES`）從 `AdminConfigController.save` 移到 `ConfigCommandUseCase.saveWorkingCopy` 開頭，拋出的錯誤碼與欄位（`AI_INPUT_INVALID(content)`）不變。
- 顧客存取檢查移出 controller：`ChatController`、`SuggestionController` 現在自己呼叫 `CustomerContextFactory.create(...)` 並在找不到時拋 `AI_CUSTOMER_NOT_FOUND`，改為 `ChatUseCase.chat(CertNo, ChatCommand, UserContext)` 與 `SuggestionQueryService.suggestions(CertNo, UserContext)` 內部處理。原本的 `ChatUseCase.chat(ChatCommand, UserContext, CustomerContext)` 保留給評測與既有測試使用。`ChatController` 的輸入長度檢查與 rate limit 屬輸入驗證與 web 層節流，留在 controller；檢查順序（輸入長度 → rate limit → 存取檢查）不變。
- controller 只剩：輸入驗證、`String` → 值物件、組 Command record、呼叫 UseCase / QueryService、組 View。
- `apply` 的 `@RequestBody(required = false) ApplyRequest` 加 `@Valid`。

### 6.2 `ArchitectureRulesTest`

| 規則 | 變更 |
| --- | --- |
| application 禁止 `org.springframework.transaction.` | 改為：只有 `application/command/**` 可 import `org.springframework.transaction.annotation.`；整個 application 可 import `org.springframework.transaction.event.`（`PublishedConfigCache` 的 AFTER_COMMIT 監聽）；其餘仍禁止 |
| domain 禁止 `com.fasterxml.jackson.annotation.JsonIgnore` | 新增 |
| infrastructure 禁止 `org.springframework.data.mongodb.repository.` | 新增（不得使用 Spring Data 衍生查詢介面） |
| interfaces 下 `*Controller.java` 不得出現 `catch (` | 新增（文字掃描） |
| `@Transactional` 只出現在標了 `@UseCase` 的類別 | 新增 |
| `application/command/**` 中的入口類別（`*UseCase`）必須標 `@UseCase` | 新增 |
| `aiassistant` 底下只能有四層 + `AiAssistantProperties` | 維持 |

### 6.3 文件同步

- [backend/PORTING.md](../../../backend/PORTING.md)：
  - §0.3：`com.example.shared.annotation.UseCase` 與其他平台仿製品一樣不搬，改 import 平台正本。
  - §4：`publishDue` 已拆成 Job 的迴圈 + `ConfigCommandUseCase.publishScheduled`；改寫成平台 scheduler Job 時沿用此切法。方案 A 中的「`publishScheduled` 的 transaction claim」改為描述 UseCase 交易。
  - §5.1：交易改為 UseCase 上的 `@Transactional(transactionManager = Transactions.MONGO)`；`TransactionTemplate` 已移除。
  - §5.2：`_class` 列改為「已比照平台全域不寫」；集合名稱列改為「已集中在 `AiAssistantCollections`，改名只動此類別與 validator 檔名」；交易管理器列改為「POC 已用具名 bean，平台有 Mongo 交易管理器時把 `Transactions.MONGO` 改成平台 bean 名稱並刪除 POC 的 bean」。
  - §6：套件列補 `application/command/`、`application/query/` 已建立；「Command 側」列改為「已標 `@UseCase`（平台副本）、`@Transactional` 已在 UseCase，換成平台正本即可」；回應格式、稽核、權限三列維持待辦。
  - §9：同步偏離清單。
- [01-architecture.md](../../spec/01-architecture.md) §5.1：目錄樹與分層表（command / query、值物件、`@UseCase`、交易位置、MapStruct）。
- [03-tool-catalog.md](../../spec/03-tool-catalog.md)：提到 `ChatOrchestrator` 等改名類別處跟著改名。
- [04-mongodb-schema.md](../../spec/04-mongodb-schema.md) 第 632 行附近：「寫入一律用明確的 `org.bson.Document`」改為「`@Document` 類別 + `@Field(write = ALWAYS)`，全域 `DefaultMongoTypeMapper(null)` 不寫 `_class`」；集合名稱常數位置。
- [2026-11-02 spec](2026-11-02-clean-architecture-layering-design.md)：開頭加註「§2 的交易邊界、Domain 允許 Jackson 註解兩項已由 2026-11-12 spec 取代」，其餘不改。

## 7. 階段與交付

| 階段 | 內容 | 結束條件 |
| --- | --- | --- |
| 1 | Domain 清理（§3） | `mvn test` 全過；交付 `git add` 清單與 commit message，等使用者 commit 後再進下一階段 |
| 2 | 持久化（§4） | 同上 |
| 3 | Application（§5） | 同上 |
| 4 | Interface 與收尾（§6） | 同上；另以 `git diff --stat frontend/` 確認前端零改動 |
