# 預約發布排程移到平台 `scheduler/`：推論與做法

> 用途：把小幫手的預約發布排程（POC 的 `ScheduledPublishJob`）從 `backend/` 抽出，改成平台 `scheduler/` 模組的一支 Job。
> 讀者：在新 session 內執行這件事的 agent。你沒有先前對話的內容，需要的事實都寫在這裡。
> 與 [PORTING.md](PORTING.md) 的關係：本文件是 PORTING.md 第 4 節的展開與修正。第 5 節的依賴盤點是對照程式碼重新算過的，與 PORTING.md §4.3 的清單不同，**不一致時以本文件為準**。其他主題（權限、OpenAI、資料庫）仍看 PORTING.md。
> 寫成時間：2026-11-17，對照 POC commit `d44bea9`；同日已在 POC 完成兩項準備（步驟 2 與持久化操作的抽離），本文件描述的是完成之後的程式狀態。
> 標示：**【已確認】** 是 POC 程式碼或平台規範文件（`reference.md`、`db-instructions.md`）明寫的事實；**【推論】** 是依線索推出、要你在平台程式碼裡驗證的判斷；**【預設】** 是使用者已選定的預設答案（第 9 節），照做即可，盤點結果與它衝突時才停下來問。

**重要限制**：寫這份文件的人看不到平台 `scheduler/` 與 `core/` 的程式碼，只看得到 POC 與兩份平台規範文件。所有關於平台現有寫法的描述都是【推論】，動手前先做第 6 節的盤點。

---

## 0. 執行守則

1. **先盤點、後動手**（第 6 節步驟 1）。盤點結果回報給使用者。第 9 節的五個問題已有預設答案，不用再問；盤點發現平台的實際情況與某個預設答案衝突時，才停下來說明衝突並詢問。
2. **以平台現有程式碼為準**。本文件的類別名稱、介面形狀都是建議；平台既有 Job 怎麼寫，就照它寫。
3. **不要 commit**。每個階段結束，列出 `git add` 清單與建議的 commit message 給使用者。
4. 每個階段跑平台的建置驗證（`reference.md` §1-2）：改 `core` 後跑 `mvn -pl backend -am compile -Pdev -U` 與 `mvn -pl scheduler -am compile -Pdev -U`；提交前跑 `mvn test`。結果原樣回報，失敗不要說成功。
5. POC repo 沒有 `scheduler/` 與 `core/` 模組。能在 POC 先做的準備已經做完（第 6 節步驟 2），其餘要到平台專案才能做；如果你的工作目錄是 POC，先告訴使用者。不要在 POC 刪除 backend 的排程，POC 的預約發布還要靠它運作。
6. log 不得出現 Prompt 內容、驗證訊息文字或例外訊息，只能有 `configKey`、`version`、規則代碼、例外類別名稱（POC 現行規則，搬過去要維持）。

---

## 1. 結論

- 排程入口與「逐筆發布」的流程放 `scheduler/`；它需要的持久化操作與驗證規則下沉到 `core/`；`backend/` 刪掉排程。
- 不採用「CronJob 呼叫 backend 內部 endpoint」。理由見第 3 節。
- PORTING.md §4.3 的方案 A 低估了下沉範圍：`ConfigValidator` 原本依賴 `ToolRegistry`，照搬會把 46 個類別（含整組工具與顧客 360 資料型別，以及 LangChain4j）拉進 `core`。POC 已把這個依賴縮成小介面 `ToolCatalog`，現在只剩 9 個類別（第 5.2 節）。
- 預約發布的持久化操作已抽成只認 Document 的 `AssistantConfigPublishStore`，相依範圍 5 個類別（第 5.1 節）。下沉 core 時搬這個類別即可。
- 建議 scheduler 到期重驗時**不檢查「工具是否存在、選項是否存在」**，其餘規則照舊。理由見第 4 節。這是行為上的小改變，使用者已採用（S1）。

---

## 2. POC 現況【已確認】

套件前綴 `com.example.copilot.aiassistant`，以下省略。

### 2.1 排程本體

`infrastructure/scheduler/ScheduledPublishJob`：

- `@Scheduled(fixedDelayString = "${ai-assistant.config.schedule-check-interval}")`，預設 30 秒；`@ConditionalOnProperty("ai-assistant.config.scheduler-enabled")`，測試設為 false；`@EnableScheduling` 在 `CopilotApplication`。
- `run()`：設定 MDC `traceId`、`spanId` → `ConfigCommandUseCase.findScheduledDue()` → 逐筆呼叫 `publishScheduled(due)` → 清 MDC。整輪的例外被吞掉，只記 WARN。
- 單筆失敗不影響其他筆。`isLostRace(e)` 判斷是否只是搶輸：Mongo 錯誤碼 112（write conflict）或 11000（duplicate key），或不帶 Mongo cause 的 `AI_CONFIG_CONFLICT`，記 DEBUG；其餘（含 transient、unknown commit result）記 WARN。

### 2.2 單筆發布的流程

`application/command/config/ConfigCommandUseCase.publishScheduled(AssistantConfig due)`，標 `@Transactional(transactionManager = Transactions.MONGO)`：

1. `ConfigValidator.validate(configType, name, changeNote, content)` 重新驗證。
2. 不通過 → `repository.revertScheduled(due, "system", now)`。命中回 `REVERTED` 並記 WARN（`configKey`、`version`、規則代碼）；沒命中（已被別人改過）回 `SKIPPED`。
3. 通過 → `repository.publishScheduled(due, now)`，再送出 `ConfigPublishedEvent`，回 `PUBLISHED`。

### 2.3 持久化操作

`infrastructure/mongo/AssistantConfigPublishStore`（`MongoTemplate` + `Criteria`，參數與回傳都是 `AssistantConfigDocument`），collection `COLL_ICRM_AI_ASSISTANT_CONFIG`：

| 操作 | 內容 |
|---|---|
| `findScheduledDue(now)` | `status = SCHEDULED` 且 `scheduledAt <= now`（index `IX_ICRM_AI_CFG_5`） |
| `archivePublished(configKey)` | 把該 key 的 PUBLISHED 條件更新成 ARCHIVED（只改 `status`、`docVersion + 1`，不動 `updatedBy`、`updatedAt`）；沒有 PUBLISHED 版本就什麼都不做。立即發布也用它 |
| `publishScheduled(due, now)` | (a) `archivePublished`；(b) 條件更新 `due`（`_id` 相符、仍是 SCHEDULED、`docVersion` 相同）：`status = PUBLISHED`、`updatedAt = now`、`docVersion + 1`。`updatedBy` 維持預約者，`scheduledAt` 保留。任一步沒命中就丟 Spring 的 `OptimisticLockingFailureException` |
| `revertScheduled(due, actor, now)` | 單一條件更新（條件同上）：`status = DRAFT`、清掉 `scheduledAt`、`updatedBy = actor`、`updatedAt = now`、`docVersion + 1`。回傳是否命中 |

**(a) 與 (b) 必須在同一個 MongoDB transaction 內**，而且 (b) 沒命中時要讓交易 rollback。否則 (a) 已生效、(b) 失敗，該 `configKey` 會沒有任何 PUBLISHED 版本。所以沒命中時是丟例外，不是回傳 false。

`AssistantConfigRepositoryImpl` 委派給這個類別，並把 `OptimisticLockingFailureException` 轉成不帶 cause 的 `AI_CONFIG_CONFLICT`（Job 的 `isLostRace` 靠「沒有 cause」判斷為搶輸）。這個類別目前不是 Spring bean，由 `AssistantConfigRepositoryImpl` 的建構子建立；搬進 core 後依平台慣例註冊。

多個執行個體同時跑是安全的：條件更新、transaction，加上 unique index `UK_ICRM_AI_CFG_2`（同一 `configKey` 只能有一個 PUBLISHED）。

### 2.4 周邊

- `ConfigTransactionConflictAspect`：把 commit 階段才冒出來的 Mongo 衝突轉成 `AI_CONFIG_CONFLICT`。切點綁在 backend 的 `ConfigCommandUseCase`，**在 scheduler 不會生效**，所以 scheduler 的 Job 會直接收到原始的 Mongo／Spring 例外。
- `PublishedConfigCache`：每個程序快取已發布設定 60 秒；收到 `ConfigPublishedEvent` 後在 commit 之後清快取，只清本程序。
- 交易管理器：`infrastructure/mongo/MongoConfiguration` 宣告具名 bean `aiAssistantMongoTransactionManager`（常數 `Transactions.MONGO`）。transaction 需要 replica set 或 mongos。
- 設定：`ai-assistant.config.allowed-url-hosts`（驗證用）、`schedule-min-lead`／`schedule-max-lead`（API 檢查預約時間用，與排程無關）。

### 2.5 涵蓋預約發布的測試

搬移時這些測試要跟著對應的程式走，不要直接刪：

| 測試類別 | 相關測試 |
|---|---|
| `infrastructure/scheduler/ScheduledPublishJobTest` | 全部（發布、未到期、並行只發布一次、搶輸記 DEBUG、非競爭失敗記 WARN、單筆失敗不中斷、traceId） |
| `application/command/config/ConfigCommandUseCaseTest` | 名稱以 `publishDue` 開頭的測試、`publishScheduledLeavesAnInvalidVersionChangedMeanwhileAlone` |
| `application/command/config/ConfigCommandUseCaseTransactionTest` | `concurrentPublishScheduledPublishesOnce`、`scheduledPublishDoesNotInvalidateCache` |
| `infrastructure/mongo/AssistantConfigPublishStoreTest` | 全部（7 個，文件層的查詢、封存、認領、退回、交易 rollback）。跟著 `AssistantConfigPublishStore` 搬到 core |
| `infrastructure/mongo/AssistantConfigRepositoryImplTest` | `publishScheduled*`（3 個）、`revertScheduled*`（2 個）、`findScheduledDue` 的查詢 |
| `domain/config/ConfigValidatorTest`、`WorkflowValidatorTest`、`domain/prompt/WorkflowParserTest`、`ExampleQuestionParserTest` | 全部（驗證器下沉時一起搬） |

`ScheduledPublishJobTest` 裡的 `saveAfterJobPublishedReturnsConflict`、`discardAfterJobPublishedReturnsConflict` 測的是「排程發布後，使用者拿舊的 `docVersion` 操作會得到 409」。這是 backend 的行為，要留在 backend 的測試，前置步驟改用 core 的持久化操作製造「已被排程發布」的狀態。

---

## 3. 平台限制與推論

### 3.1 平台規範【已確認】

出處：`reference.md` §1-2、§6-3、§6-4，`db-instructions.md` §4、§6-1、§11。

- 排程用 Serverless SCP CronJob，不使用 Spring Batch。
- 依賴方向固定為 `core` ← `backend`／`scheduler`。
- `scheduler/` 放批次入口、批次流程、BatchLog；透過 `core` 使用共用 persistence。禁止反向依賴 `backend`，禁止放 API controller 或共用 persistence 的實作。
- `backend/` 禁止放批次入口。
- `core/` 放 backend 與 scheduler 共用的 Mongo document／repository。禁止依賴應用模組，禁止放單一 bounded context 的 use case 或 controller。
- 日誌：`@SchedulerLog` + `SchedulerLogAspect` 寫 `BatchLog` logger 與 `COLL_SCHEDULER_EXECUTE_LOG`（欄位 `jobName`、`arguments`、`executionStatus`、`exitCode`、`startTime`、`endTime`、`durationMs`、`message`、`errorStack`）。`log.error` 只能在 `SchedulerProcessor`，而且要帶 job 參數。

### 3.2 平台現有排程的型態【推論】

- 每次啟動帶參數指定 job、跑完以 exit code 結束（依據：執行紀錄有 `jobName`、`arguments`、`exitCode`）。
- 現有 job 直接讀寫 MongoDB，不是呼叫 backend API（依據：db-instructions 記載 scheduler 寫入顧客快照的 stage collection 再切換、以 raw `Document` 寫入客群資料、`RefreshHrDataJob` 做 swap）。
- 既有 Job 範例：`RefreshHrDataJob`、`RefreshCustomerProfileService`、`RefreshSegmentProfileService`。

### 3.3 由此得到的結論

- 發布流程不能留在 backend 由 scheduler 呼叫（scheduler 不得依賴 backend），也不適合用 CronJob 打 backend 的內部 endpoint：批次流程會留在 API 模組，還要另外做服務間認證，而且和現有 job 的做法不同。
- 「逐筆驗證、退回或發布」是流程，屬於 scheduler。core 只放它需要的持久化操作與驗證規則，不放這個流程本身（core 禁止放 use case）。

---

## 4. 方案【預設 S1、S2：採 A′】

| 方案 | 做法 | 代價 |
|---|---|---|
| **A′（採用）** | 持久化操作與驗證器下沉 core；驗證器先縮小依賴（5.2）；scheduler 到期重驗，但不查工具目錄 | 驗證器是小幫手的領域規則，放 core 屬灰色地帶，需使用者同意；重驗範圍少一項 |
| A（PORTING.md 原案） | 同上，但重驗範圍完全不變 | scheduler 必須知道有哪些工具與選項。整組工具下沉 core 不可行（5.2）；改在 core 放一份工具名稱與選項的常數，則 backend 新增工具、scheduler 還沒重新部署時，scheduler 會把合格的預約版本誤退回草稿 |
| B | 只下沉持久化操作；scheduler 到期不重驗，直接發布 | 行為改變較大：預約後若調整 `allowed-url-hosts` 或禁止樣式，不合規的版本仍會發布；規格 04 §2.4 要改 |
| C | CronJob 呼叫 backend 內部 endpoint | 見 3.3，不建議 |

採用 A′ 的理由：

- 規格（04 §2.4）要求到期重驗，是因為「驗證規則會變，例如 `allowed-url-hosts` 調整或新增禁止樣式」。這些規則都不需要工具目錄，A′ 完整保留。
- 少掉的那一項（工具或選項在預約之後被移除）在執行期已有保護：`WorkflowRunner` 遇到未註冊的工具會略過該步驟並記 WARN（`reason=TOOL_NOT_REGISTERED`），不會讓對話失敗【已確認】。選項不存在時執行期的行為沒有確認過，請在盤點時看 `WorkflowRunner` 與 `ToolGuard`。
- A 的誤退回是靜默的錯誤（使用者預約的版本沒有上線），比 A′ 少查一項的後果嚴重。

盤點後若發現驗證器確實不能放進 core（例如平台有檢查 core 內容的規則或審查），先停下來詢問；備案是 B，並同步修改規格。

---

## 5. 依賴盤點【已確認，對照程式碼算出】

### 5.1 持久化（必須下沉）

`AssistantConfigRepositoryImpl` 的相依範圍共 16 個類別，含 `AssistantConfig` 聚合、`ConfigKey`、`SaveMode`、`AssistantException`、`ErrorCode` 等。PORTING.md §4.3 建議把這一組都搬進 core。

改成**以 Document 為介面，只下沉最少的東西**【預設 S5】。POC 已把預約發布用到的操作抽成 `AssistantConfigPublishStore`（2.3），它的相依範圍只有 5 個類別：

| 下沉到 core | 留在 backend |
|---|---|
| `AssistantConfigPublishStore`、`AssistantConfigDocument`、`ConfigStatus`、`ConfigType`、`AiAssistantCollections` 的 CONFIG 常數；另外搬 `MongoConflicts`（scheduler 的 Job 判斷搶輸要用） | `AssistantConfig` 聚合、`ConfigKey`、`SaveMode`、`AssistantConfigDocumentMapper`、`AssistantException`／`ErrorCode`、`AssistantConfigRepository` 介面與 `AssistantConfigRepositoryImpl` |

理由：`AssistantConfig` 帶有存檔、套用、捨棄等業務規則，`ErrorCode` 含全部 `AI_*` 錯誤碼與 HTTP 狀態，都是單一 bounded context 的東西；scheduler 的流程只需要讀 Document 的幾個欄位。平台 core 既有的 repository 若是回傳 domain 物件而不是 Document，就照平台的形狀，回報這個差異即可。

- 封存舊版的邏輯只有一份（`archivePublished`），backend 的立即發布也委派給它；搬到 core 後維持這樣。
- `AiAssistantCollections` 還有稽核與評測的 collection 名稱，那兩個留在 backend；只把 CONFIG 的常數移到 core。

### 5.2 驗證器（方案 A、A′ 需要）

`ConfigValidator` 原本的相依範圍是 **46 個類別**，原因是這條鏈：

```
ConfigValidator → WorkflowValidator → ToolRegistry → AssistantTool
  → ToolContext / ToolResult / AccessLevel / UserContext / CustomerContext
  → Customer360Record 等 21 個顧客 360 資料型別
```

`AssistantTool` 還 import LangChain4j 的 `ToolSpecification`；要真的組出 `ToolRegistry`，另外需要 5 個工具實作與 `TwseGateway`。這些不能進 core。

`WorkflowValidator` 實際只用到工具的兩件事：工具名稱是否存在、工具有哪些選項 key。POC 已把依賴縮成 `domain/config/ToolCatalog`：

```java
public interface ToolCatalog {
    /** 工具的選項 key（空 list 表示沒有選項）；工具不存在時回傳 empty。 */
    Optional<List<String>> optionKeys(String toolName);
}
```

- `ToolRegistry implements ToolCatalog`，backend 仍把 `ToolRegistry` 傳給 `ConfigValidator`，驗證行為與錯誤訊息完全不變。
- 沒有工具目錄的呼叫端用兩個參數的建構子：`new ConfigValidator(exampleQuestionParser, allowedUrlHosts)`。這時跳過「找不到工具」與選項檢查，其餘照做：標記格式、標記未結尾、不同工具最多 3 個、`## 適用問題` 與 `## 範例問題` 不可放標記、System Prompt 不可放標記。scheduler 用這個建構子。
- `ArchitectureRulesTest.configValidationDoesNotDependOnToolsOrCustomerData` 會擋下之後又把工具或顧客資料的依賴加回驗證器。

現在驗證器下沉的範圍是 9 個類別：`ConfigValidator`、`WorkflowValidator`、`ToolCatalog`、`ConfigType`、`ValidationError`、`ErrorDetail`（`ValidationError` 實作的空標記介面）、`ExampleQuestionParser`、`WorkflowParser`、`WorkflowStep`。

- 顧客編號的 regex 已移到 `ConfigValidator.CERT_NO_REGEX`，`PiiRedactor` 改引用它。`PiiRedactor` 依賴 `CustomerContext`，留在 backend。
- `ErrorDetail` 在 `domain/exception`，是給 `AssistantException` 帶細節用的標記介面。下沉時二選一：跟著搬到 core，或讓 core 的 `ValidationError` 不實作它、在 backend 轉換。照平台對 core 例外型別的慣例決定。
- `allowed-url-hosts` 由呼叫端傳入建構子，core 不讀 `ai-assistant.*` 設定。
- backend 還有其他地方用到這些類別（`ConfigQueryService`、`EvalRunCommandUseCase`、`CredentialMasker`、`WorkflowRunner`、`SuggestionQueryService`、`PiiRedactor`、`ToolRegistry`），下沉後改 import 即可。

---

## 6. 實作步驟

### 步驟 1：盤點平台（只讀不改）

| 要找的東西 | 從哪裡找 | 決定什麼 |
|---|---|---|
| Job 的分派方式、共同介面、exit code | 從 `SchedulerLogServiceImpl`、`SchedulerProcessor` 的呼叫端往回找；打開 `RefreshHrDataJob` | 新 Job 的類別結構與註冊方式 |
| 程式跑一次就結束，還是常駐 | `grep -rnE "CommandLineRunner\|ApplicationRunner\|SpringApplication.exit\|@EnableScheduling" scheduler/src/main/java` | 入口寫法 |
| `@SchedulerLog` 的掛法、traceId 怎麼產生 | 既有 Job、`SchedulerLogAspect` | 取代 POC 自己設 MDC 的程式 |
| scheduler 有沒有 Mongo 的交易管理器 | `grep -rn "MongoTransactionManager\|TransactionTemplate" core scheduler` | 2.3 的交易怎麼提供 |
| core 既有 repository 回傳 Document 還是 domain 物件、套件命名 | `core/` 的 `persistence/mongodb` | 5.1 的介面形狀與套件位置 |
| CronJob 排程設定檔的位置、最小間隔、是否禁止重疊執行 | K8s manifest／Helm／SCP 設定 | 步驟 5 |
| scheduler 的 `application-{env}.yml` 與設定綁定方式 | `scheduler/src/main/resources` | `allowed-url-hosts` 放哪裡 |
| scheduler 的測試怎麼連 MongoDB | `scheduler/src/test` | 測試搬到哪裡、怎麼寫 |
| 小幫手是否已搬進平台 backend，搬的是哪個版本 | PORTING.md §7 的「搬程式」階段是否完成；平台 backend 裡有沒有 `ToolCatalog`、`AssistantConfigPublishStore` | 還沒搬完就等搬完；搬來的版本沒有這兩個類別時，先補上本節步驟 2 的三項準備 |
| 選項不存在時的執行期行為 | backend 的 `WorkflowRunner`、`ToolGuard` | 補上第 4 節缺的那項確認 |

回報盤點結果與第 9 節的問題，等使用者回覆。

### 步驟 2：在 backend 內的準備（POC 已完成，2026-11-17）

行為不變的重構，POC 全部 1,059 個測試通過（另有 4 個需要真實 OpenAI 金鑰的測試照例略過）：

1. 新增 `ToolCatalog`，`WorkflowValidator`、`ConfigValidator` 改依賴它，並提供沒有工具目錄的建構子（5.2）。
2. `CERT_NO_REGEX` 從 `PiiRedactor` 移到 `ConfigValidator`。
3. 預約發布的持久化操作抽成 `AssistantConfigPublishStore`，`AssistantConfigRepositoryImpl` 委派給它（2.3、5.1）。

如果平台 backend 裡的小幫手是從這個 commit 之前的 POC 搬過去的，先把這三項補上，再做步驟 3。

### 步驟 3：下沉到 core

1. 依 5.1、5.2 建立套件（位置照 core 既有命名），搬入類別，改 backend 的 import。
2. `AssistantConfigPublishStore` 依平台慣例註冊成 bean（或改成 core repository 的介面加實作），backend 的 `AssistantConfigRepositoryImpl` 改用注入的實例。
3. 把 `AssistantConfigPublishStoreTest` 與驗證器的測試搬到 core。
4. 兩個模組都編譯通過後，backend 的測試應該全數通過（此時 backend 的排程還在）。

### 步驟 4：scheduler 新增 Job

建議名稱 `PublishScheduledAssistantConfigJob`，結構照既有 Job。流程：

1. `findScheduledDue(now)`。
2. 逐筆處理，每筆各自一個交易，一筆失敗不影響其他筆：
   - 用 core 的 `ConfigValidator` 驗證（兩個參數的建構子，不提供工具目錄；`allowed-url-hosts` 從 scheduler 設定讀入）。
   - 不通過 → `revertScheduled(due, "system", now)`；命中記 WARN（`configKey`、`version`、規則代碼），沒命中略過。
   - 通過 → 在交易內呼叫 `publishScheduled(due, now)`。
3. 搶輸與失敗的區分照 POC 的 `isLostRace`（2.1）。scheduler 沒有 `ConfigTransactionConflictAspect`，要直接判斷原始例外：`MongoConflicts.isWriteConflictOrDuplicateKey(e)`，或 `AssistantConfigPublishStore` 丟出的 `OptimisticLockingFailureException`，視為搶輸；其餘是失敗。
4. 結束時記錄發布筆數。exit code 的表示方式依平台慣例；判定規則是全部處理完才決定，只有「非競爭的失敗」才算 Job 失敗，驗證不過被退回不算失敗【預設 S4】。
5. 掛 `@SchedulerLog`；traceId 用平台 scheduler 的機制，不要搬 `RequestContext`。
6. scheduler 的設定檔加入 `ai-assistant.config.allowed-url-hosts`，**值必須與 backend 相同**。兩邊不一致會造成「存檔時通過、到期時被退回」。
7. 測試：把 2.5 的 `ScheduledPublishJobTest` 與 `ConfigCommandUseCaseTest` 的 `publishDue*` 改寫到 scheduler，至少涵蓋：到期發布（`updatedBy` 維持預約者、`updatedAt` 為實際時間、`scheduledAt` 保留、舊版 ARCHIVED 且 `updatedBy`／`updatedAt` 不變）、未到期不動、驗證不過退回 DRAFT、兩個執行個體同時跑只發布一次、認領失敗時封存有 rollback、單筆失敗不中斷整輪。

### 步驟 5：CronJob 設定

照既有 Job 的方式新增排程，頻率每分鐘【預設 S3】。平台的最小間隔大於 1 分鐘時，用平台允許的最小值，並在回報中寫出實際延遲。平台若有禁止重疊執行的設定就照平台；沒有也安全（2.3）。

### 步驟 6：backend 移除排程

- 只在平台專案做，不要在 POC 做。
- 刪除 `ScheduledPublishJob`、`ScheduledPublishOutcome`、`ConfigCommandUseCase.findScheduledDue` 與 `publishScheduled`、`AssistantConfigRepository` 介面與實作上的 `findScheduledDue`／`publishScheduled`／`revertScheduled`。
- 刪除設定 `ai-assistant.config.scheduler-enabled`、`schedule-check-interval`，`AiAssistantProperties.Config.scheduleCheckInterval`，以及 `IntegrationTestBase` 裡對 `scheduler-enabled` 的設定。
- 保留：`schedule-min-lead`、`schedule-max-lead`、`ScheduleWindow`、`SCHEDULED` 狀態在存檔／套用／捨棄的處理、`ConfigPublishedEvent`（立即發布仍在用）。
- 2.5 提到要留在 backend 的兩個測試，改用 core 的持久化操作準備資料。

---

## 7. 搬移後的行為差異

| 項目 | POC | 搬移後 |
|---|---|---|
| 發布延遲 | 到期後最多約 30 秒 | CronJob 間隔（通常最小 1 分鐘）加上冷啟動，約 1～2 分鐘【推論】 |
| 使用者看到新版 | 發布的那個程序立即生效，其他程序最多 60 秒 | scheduler 清不到 backend 的快取，所有 pod 都要等快取到期，最多再 60 秒。合計最多約 3 分鐘 |
| 到期重驗 | 全部規則 | A′：不查工具與選項是否存在 |
| 驗證規則的版本 | 與 backend 同一個程序 | scheduler 與 backend 分開部署，規則以 scheduler 當時部署的版本為準 |

---

## 8. 驗證

1. 建置：第 0 節第 4 點的三個命令。
2. dev 環境手動驗證：
   - 預約 2 分鐘後發布 → 觸發 Job → 該版本變 PUBLISHED、舊版變 ARCHIVED、`COLL_SCHEDULER_EXECUTE_LOG` 有一筆成功紀錄。
   - 預約一個到期時會驗證失敗的版本（例如預約後把內容裡的網址 host 從 scheduler 的 `allowed-url-hosts` 移除）→ 被改回 DRAFT，`updatedBy = "system"`。
   - 同時啟動兩個 Job → 只發布一次，沒有 WARN。
   - 發布後最多 60 秒，backend 的對話用到新版 Prompt。
3. dev 的 MongoDB 若是 standalone，交易會失敗（`Transaction numbers are only allowed on a replica set member or mongos`），要先換成單節點 replica set（PORTING.md §5.3）。

---

## 9. 決定事項【預設】

對應 PORTING.md 的 D8。使用者於 2026-11-17 選定下列預設答案。照做即可；只有「何時要停下來問」那一欄的情況發生時才詢問。

| # | 問題 | 預設答案 | 何時要停下來問 |
|---|---|---|---|
| S1 | 到期重驗的範圍 | A′：保留重驗，但不檢查工具與選項是否存在 | 盤點發現執行期遇到不存在的選項會讓對話失敗（而不是略過） |
| S2 | `ConfigValidator` 等驗證規則可否放進 core | 可以；在 PORTING.md §9 的偏離表記錄 | 平台有規則或審查明確擋下這類內容進 core |
| S3 | CronJob 頻率與延遲 | 每分鐘執行；接受使用者看到新版最多晚約 3 分鐘 | 平台的最小間隔或冷啟動讓延遲明顯超過 3 分鐘 |
| S4 | 有版本發布失敗時 Job 的 exit code | 全部處理完才判定；有非競爭的失敗才算失敗，驗證不過被退回不算 | 平台慣例是遇到第一個失敗就中止 |
| S5 | core repository 的介面 | 以 Document 為介面，不下沉 `AssistantConfig` 聚合 | 平台 core 既有的 repository 一律回傳 domain 物件 |

---

## 10. 完成後要同步的文件

- `docs/spec/04-mongodb-schema.md` §2.4「排程器」：執行位置、頻率與延遲、重驗範圍（S1）、快取不再由發布清除。
- `docs/spec/01-architecture.md`：分層圖與套件樹裡的 `ScheduledPublishJob`、設定範例的 `schedule-check-interval`。
- `docs/spec/06-conversation-scenarios.md` M6 與 `docs/demo-checklist.md`：「到期後約 30 秒內自動發布」的時間。
- `docs/platform-setup-requests.md` §6 的 `ScheduledPublishJob` 那一列。
- PORTING.md §4.3、§9（偏離表的 `ConfigValidator` 放 core 那一列）。
- 前端預約畫面若有說明生效時間的文字，一併確認。
