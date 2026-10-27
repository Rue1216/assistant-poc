# 行銷人員顧問小幫手：需要平台協助設定的項目

> 對象：平台後端、基礎架構、DBA 同事
> 用途：小幫手後端（`aiassistant` 套件）平移進平台時，需要平台端事先設定或確認的項目。程式碼平移本身（實作 Port、調整套件名稱等）見 [01-architecture.md](spec/01-architecture.md) 第 8 節。
> 版本基準：平台 root pom（Spring Boot 4.1.0、Java 25）與 frontend/package.json。POC 已改成相同版本並通過測試。

## 1. Maven 依賴

### 1.1 第三方 jar（公司環境無法從外部下載）

LangChain4j 和它帶進來的間接依賴，放在 POC 的 `backend/lib-repo/`（Maven 目錄結構）。請依平台管理第三方 jar 的方式匯入（內部 Nexus / Artifactory，或比照專案內 lib-repo）。

| groupId : artifactId | 版本 | 授權 |
|---|---|---|
| dev.langchain4j : langchain4j | 1.22.0 | Apache 2.0 |
| dev.langchain4j : langchain4j-open-ai | 1.22.0 | Apache 2.0 |
| dev.langchain4j : langchain4j-core | 1.22.0 | Apache 2.0 |
| dev.langchain4j : langchain4j-http-client | 1.22.0 | Apache 2.0 |
| dev.langchain4j : langchain4j-http-client-jdk | 1.22.0 | Apache 2.0 |
| dev.langchain4j : langchain4j-reactive-streaming | 1.22.0-beta32 | Apache 2.0 |
| org.apache.opennlp : opennlp-tools | 2.5.11 | Apache 2.0 |
| io.smallrye.reactive : mutiny-zero | 1.3.1 | Apache 2.0 |
| com.knuddels : jtokkit | 1.1.0 | MIT |

另外有 4 個只有 pom 的 parent POM：`mutiny-zero-parent 1.3.1`、`smallrye-build-parent 51`、`opennlp 2.5.11`、`org.apache:apache 39`。SHA-256 可用 `sha256sum` 對 `backend/lib-repo` 內的檔案計算比對。

如果採用專案內 lib-repo：公司 `settings.xml` 的 `<mirrorOf>*</mirrorOf>` 會把 file repository 也導到內部 mirror，需改為 `external:*` 或 `*,!project-local`（見 01-architecture.md 7.1）。

### 1.2 後端模組 pom 需要的依賴

| 依賴 | 用途 | 請確認 |
|---|---|---|
| `dev.langchain4j:langchain4j`、`langchain4j-open-ai` | 呼叫 GPT、tool calling | 匯入 1.1 的 jar 後加入 |
| Jackson 2（`com.fasterxml.jackson.core`） | 只有 LangChain4j 使用 | 平台已用 `jackson.databind.v2.version`（2.22.1）管理 core 與 databind，不需另外處理 |
| `spring-boot-starter-webmvc` | REST API | 平台應已有 |
| `spring-boot-starter-security` | `@PreAuthorize` 權限檢查 | 平台應已有；平台的 security 設定需有 `@EnableMethodSecurity`（POC 寫在 mock 的 `SecurityConfig`，不會一起平移） |
| `spring-boot-starter-validation` | Request DTO 驗證（`jakarta.validation`） | 確認後端模組有引入 |
| `spring-boot-starter-data-mongodb` | 設定與稽核 collection、transaction | 平台應已有 |
| `spring-boot-starter-log4j2` | 日誌 | 平台已採用 |
| `org.testcontainers:mongodb`（test） | 整合測試 | 確認平台的 Testcontainers 1.21.4 有這個模組；CI 需要能執行 Docker |

## 2. 權限

小幫手使用 4 個權限碼，請加進平台的權限設定，並指派給對應角色：

| 權限碼 | 用途 | 預計角色 |
|---|---|---|
| `AI_ASSISTANT_USE` | 使用小幫手對話 | 理專、AO、業務及其主管等 |
| `AI_CONFIG_READ` | 檢視 Prompt 管理頁 | 後台管理、IT 管理 |
| `AI_CONFIG_EDIT` | 編輯 Prompt、開始 Prompt 評測 | 後台管理、IT 管理 |
| `AI_CONFIG_PUBLISH` | 發布、回復 Prompt | 後台管理、IT 管理 |

權限碼名稱可依平台規範調整（見 questions-for-manager.md M26）。

## 3. 設定與 secret

| 項目 | 說明 |
|---|---|
| `ai-assistant.*` 設定 | 照 POC 的 `application.yml` 放進平台各環境的設定檔（限制值、TWSE 連線設定、評測設定 `ai-assistant.eval.*` 等） |
| `OPENAI_API_KEY` | 透過平台的 secret 機制（例如 K8s Secret）注入，不可寫進設定檔或版控。POC 用的 `spring.config.import` 讀 `.env` 要移除 |
| `OPENAI_MODEL` | 各環境使用的模型名稱 |
| GPT 端點 | POC 直連 OpenAI。如果公司改用 Azure OpenAI 或內部 AI Gateway，需要提供端點與認證方式，後端要配合調整 `LlmConfiguration` |

## 4. 對外連線（K8s pod → 外部）

| 目的地 | 用途 | 使用的 HTTP client |
|---|---|---|
| `api.openai.com:443`（或 3. 的 GPT 端點） | GPT 呼叫 | JDK HttpClient（LangChain4j） |
| `openapi.twse.com.tw:443` | 上市個股行情、大盤指數、重大訊息 | JDK HttpClient（Spring `RestClient`） |

- 需要開通防火牆或 proxy（見 questions-for-manager.md M18）。
- 兩個 client 都沒有在程式中設定 proxy。如果要經過 proxy，請提供設定方式（例如 JVM 參數 `https.proxyHost`、`https.proxyPort`），我們會實測確認兩條連線都生效。
- 單次對話的後端上限是 60 秒（`request-timeout`，在每個步驟之間檢查），進行中的 GPT 呼叫還可能多花 30 秒；前端最多等 150 秒。Nginx、Ingress 的 `proxy_read_timeout` 建議設為 150 秒以上，避免被代理層先切斷。

## 5. MongoDB

| 項目 | 說明 |
|---|---|
| 建立 collection | `COLL_ICRM_AI_ASSISTANT_CONFIG`、`COLL_ICRM_AI_ASSISTANT_AUDIT`、`COLL_ICRM_AI_ASSISTANT_EVAL_RUN`，含 JSON Schema Validation 與 13 個 index（CONFIG 5 個、AUDIT 5 個、EVAL_RUN 3 個，見 [04-mongodb-schema.md](spec/04-mongodb-schema.md)）。validator 檔在 `backend/src/main/resources/mongo/`。POC 由啟動程式建立，平台要改用 migration 或由 DBA 建立 |
| 新增的 index | 稽核 collection 新增 `IX_ICRM_AI_AUDIT_4`（`requestId`，回饋更新）與 `IX_ICRM_AI_AUDIT_5`（`promptVersions.configKey`、`promptVersions.version`，partial：`feedback` 為字串，回饋統計）。評測紀錄 collection 有 `UK_ICRM_AI_EVAL_1`（`status`，unique，partial：`status = "RUNNING"`，保證同時只有一個評測）、`IX_ICRM_AI_EVAL_1`（`configKey`、`startedAt` 遞減）、`IX_ICRM_AI_EVAL_2`（`startedAt`，TTL 180 天） |
| 初始資料 | System Prompt 與問題分類的初始版本（見 04-mongodb-schema.md 2.7、07-prompt-examples.md） |
| TTL | 稽核紀錄與評測紀錄 180 天自動刪除，保存期限待法遵確認（M4） |
| Transaction | 設定 collection 的存檔與發布使用 transaction，需透過 replica set 或 mongos 連線 |
| Sharding | 設定 collection 不 shard；稽核紀錄全行開放後很可能需要 shard，待確認（S18、M36，見 04-mongodb-schema.md 第 5 節） |
| 帳號權限 | 如果 collection 與 index 改由 migration 建立，應用程式帳號只需要這三個 collection 的 readWrite |

## 6. 多個 pod 部署時需要討論的行為

POC 是單一程序。平台在 K8s 上會跑多個 pod，以下 4 個元件的行為會不同，需要和平台同事決定做法：

| 元件 | POC 做法 | 多 pod 時的影響 | 可能的調整 |
|---|---|---|---|
| `ScheduledPublishJob`（預約發布） | 應用程式內 `@Scheduled`，每 30 秒檢查一次 | 每個 pod 都會執行。發布用 transaction 搶占，同一版本只會發布一次，結果正確；但平台原則是排程使用 Serverless SCP CronJob | 改成由 CronJob 呼叫內部 endpoint 觸發，或確認可保留應用程式內排程。保留的話，平台要有 `@EnableScheduling`（POC 寫在 `CopilotApplication`，不會一起平移） |
| `PublishedConfigCache`（Prompt 快取） | 每個程序各自快取 60 秒 | 發布後最多 60 秒內，不同 pod 可能用不同版本的 Prompt 回答 | 可接受就維持；不行就改用平台的 Redis L2 Cache 或發布時通知各 pod 清除快取 |
| `PerUserRateLimiter`（每人每分鐘 10 次） | 存在程序記憶體 | 每個 pod 各算各的，實際上限變成「pod 數 × 10」 | 改用 Redis 計數，或沿用平台既有限流機制（S17） |
| `EvalRunner`（Prompt 評測） | 收到請求的程序以單一 daemon 執行緒背景執行（數分鐘）；「同時只有一個」由 MongoDB 的 `UK_ICRM_AI_EVAL_1` partial unique index 保證 | 唯一性在多 pod 下仍成立；但評測只在接到請求的 pod 上跑，該 pod 重啟或被回收時評測中斷，約 10 分鐘後被標為「已中斷」，需重新執行。正式環境的 pod 若有 graceful shutdown 時間限制，也可能中斷 | 可接受就維持（評測是管理者偶爾手動執行）；否則評測改由專用的 Job 或常駐 pod 執行 |

## 7. 前端

- 套件版本已照平台 frontend/package.json 固定（見 01-architecture.md 第 7 節）。
- `npm audit` 回報平台指定的 axios 1.17.0 有高風險漏洞（受影響範圍 1.0.0～1.19.0），建議升到 1.20.0 以上；vite 7.3.5（只影響 dev server）、vue 3.5.33（只影響 SSR）也有回報。版本是否調整由平台決定。
- 前端整合時把 `platform-mock` 的 `apiClient`、`useToast`、`useAuthStore` 換成平台元件；CSRF 依平台在 sit/uat/prod 的設定處理。

## 8. Prompt 評測需要的資源

Prompt 評測（管理者在 Prompt 管理頁用固定題目比較已發布版本與編輯中版本，設計見 [prompt-evaluation-design](superpowers/specs/2026-10-24-prompt-evaluation-design.md)）需要平台協助以下兩項。

### 8.1 專用的假測試顧客

評測的題目綁定固定的測試顧客（`backend/src/main/resources/eval/eval-cases.yml` 的 `customerId` 與 `accessLevel`），評測流程**不經過** `CustomerAccessPort`，直接透過 `CustomerDataPort` 讀取這些顧客的資料並送進 GPT。因此：

| 項目 | 需求 |
|---|---|
| 顧客資料 | 請平台提供**專用的假顧客**（姓名、身分證號、手機、持股、互動紀錄、活動成效都是虛構資料），不得使用真實顧客。POC 目前用的是 `mockplatform` 的 C001（完整資料）與 C005（供 `LIMITED` 題目使用）。正式環境至少要有：一位資料完整的顧客（有持股、近期互動與待辦事項、活動成效紀錄），一位用於 `LIMITED` 題目的顧客 |
| 要改的檔案 | 依實際的假顧客，改 `eval-cases.yml` 的 `customerId`，以及各題 `mustNotContain` 裡該顧客的姓名、身分證號、手機，和 `rubric` 裡提到的互動日期、待辦事項等具體內容 |
| 權限 | 這些顧客**不需要**在任何使用者的範圍內（評測身分是系統內建的 `eval-runner`），但必須能被 `CustomerDataPort.findCustomer` 等方法依編號查到 |
| 資料穩定性 | 假顧客的資料要固定，否則兩次評測的結果不能比較；若資料集檔案內容有變動，評測歷程會標示「題目已變更」 |
| 保密 | 評測紀錄（`COLL_ICRM_AI_ASSISTANT_EVAL_RUN`）保存去識別化後的提問與回答，與稽核紀錄同級；只有 `AI_CONFIG_READ` 可讀 |

### 8.2 GPT 呼叫與費用

| 項目 | 說明 |
|---|---|
| 呼叫量 | 每次評測 16 題 × 2 個版本，每題作答最多 3 輪模型往返，再加每個成功的回答 1 次評審（最多 32 次） |
| Token 估計 | **每次約 16 萬 token**（2026-10-27 實測：作答約 14.5 萬、評審約 1.4 萬，整次約 2 分鐘）；預算以每次 20 萬 token 估計 |
| 頻率控制 | 同時只有一個評測；題數上限 30、每題沿用單次提問的 token 上限（16,000）與逾時、整次評測上限 30 分鐘；不受使用者每分鐘 10 次的限流 |
| 預算 | 評測用的是同一組 `OPENAI_API_KEY`（OpenAI 專案的預算與 rate limit 要把評測算進去），預算歸屬見 [questions-for-manager.md](questions-for-manager.md) M37、M38 |
| 對外連線 | 沒有新的目的地；評測走與對話相同的 GPT 連線（第 4 節），市場題同樣會呼叫 TWSE |
