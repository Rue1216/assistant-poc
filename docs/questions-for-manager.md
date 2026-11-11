# 行銷人員顧問小幫手：前置確認問題清單

> 對應文件：[requirements.md](../requirements.md)
> 用途：開發與規格撰寫前，釐清既有平台現況與需要主管決策的事項。

## 使用說明

- **Part 1 自查清單**（編號 `S`）：看既有平台程式碼就能回答，不需要主管決策。
- **Part 2 主管確認清單**（編號 `M`）：需要主管決策、授權或提供資源。
- 每題都附上「預設假設」。主管沒意見時就採用預設；預設不對時，把正確答案填在「回覆」欄。
- 標 ⚠️ 的題目會影響整體架構或合規，請優先確認。

---

## Part 1：自查清單（看既有程式碼）

### S1. 後端架構型態 ⚠️

- **問題**：既有平台是單體、微服務，還是 BFF？小幫手後端要放在哪個應用裡？
- **為何要問**：這決定 tool 怎麼取得顧客資料：可以直接呼叫 service，或必須走內部 HTTP 並轉傳使用者身分。
- **預設假設**：單體。小幫手是同一個 Spring Boot 應用裡的獨立模組，tool 直接呼叫既有的 application service。
- **回覆**：

### S2. Clean Architecture 分層與套件命名

- **問題**：既有 feature 模組怎麼分層，套件命名是什麼樣子（例如 `adapter / application / domain`，或 `controller / usecase / gateway`）？有沒有可以參考的模組？
- **為何要問**：要能「複製貼上平移」，結構就必須和既有模組一致。
- **預設假設**：比照一個既有 feature 模組的結構。
- **回覆**：平台守則為 `domain / application / interfaces / infrastructure`（reference.md §3），2026-11-02 已對齊，見 [01-architecture.md](spec/01-architecture.md) 5.1

### S3. 既有 API 是否自動過濾顧客資料範圍 ⚠️

- **問題**：查詢顧客資料的 service 或 API，會不會自動依登入者權限（分行、業務歸屬、客群等）過濾？還是呼叫端要自己檢查？
- **為何要問**：這決定 ToolGuard 要不要自己做資源範圍檢查，屬於安全評分重點。
- **預設假設**：既有 service 會過濾，tool 層仍再做一次範圍檢查（縱深防禦）。
- **回覆**：

### S4. 登入者資訊的取得方式

- **問題**：後端怎麼取得目前登入者、角色和權限（Spring Security `SecurityContext`、自訂 Session 物件、或自訂注解）？在 virtual threads 底下有沒有既有的 context 傳遞做法？
- **為何要問**：Java 執行 tool 時需要知道使用者是誰。用 ThreadLocal 存 context 時，跨執行緒會遺失。
- **預設假設**：使用 `SecurityContext`。tool 在同一個請求執行緒內同步執行，不另外開執行緒。
- **回覆**：

### S5. 角色與權限碼的定義方式

- **問題**：角色和權限碼怎麼定義（enum、DB、設定檔）？API 權限寫法是 `@PreAuthorize`、自訂注解，還是其他方式？
- **為何要問**：Tool Registry 要依權限動態篩選工具，管理頁也需要權限控管。
- **預設假設**：POC 依平台實際的 9 個角色模擬，權限碼為 `CUSTOMER_360_VIEW`、`AI_ASSISTANT_USE`、`AI_CONFIG_READ/EDIT/PUBLISH`（見 [02-rest-api.md](spec/02-rest-api.md) 1.2），正式開發時對應到平台既有的權限碼，寫法沿用既有方式。
- **回覆**：

### S6. 前端 api-client 與 composable

- **問題**：`api-client` 和 composable 的典型寫法是什麼（型別定義、錯誤攔截）？有沒有支援用 `AbortController` 取消請求？
- **為何要問**：對話視窗需要「取消」，也要沿用統一的呼叫方式。
- **預設假設**：`api-client` 支援傳入 `AbortSignal`，新增 `useAiAssistantChat` composable。
- **回覆**：（2026-11-11，依 `frontend-instructions.md` §4）一律透過 `utils/api-client.ts` 的 `apiClient` 呼叫後端；API 函式放 `views/{模組}/{模組}-api.ts`，跨頁共用的查詢邏輯放 `composables/use-*.ts`；回應型別為 `ApiResponse<T>`、`PageResponse<T>`（`beans/shared-bean.ts`）；`timeout` 30 秒、`withCredentials`。小幫手前端已依此分層放置（見 `frontend/PORTING.md`）。**待確認**：`apiClient` 是否支援傳入 `AbortSignal`，以及對話請求需要的 150 秒逾時能否逐次覆寫。

### S7. 錯誤回應格式與全域 Toast

- **問題**：後端錯誤回應的結構是什麼（例如 `{ code, message, traceId }`）？前端怎麼把錯誤對應到 Toast？錯誤碼有沒有命名規則？
- **為何要問**：錯誤碼清單必須和既有格式對齊。
- **預設假設**：沿用既有錯誤回應結構，新增 `AI_` 前綴的錯誤碼。
- **回覆**：（2026-11-11，依 `frontend-instructions.md` §4-1）回應為 `ApiResponse<T>`（`success`、`errorMessage`、`data`、`serviceCode`、`unavailableSections`、`traceId`、`timestamp`）。HTTP 200 且 `success=false`、以及 400/404/405/409，由 `apiClient` 顯示後端 `errorMessage` 並 reject `HandledError`，呼叫端以 `isHandledError()` 跳過；401 導向 ADFS 登入；403 導向 `/forbidden`；5xx 或斷線導向 `/internal-error`。POC 目前是 reject `ApiError` 並由呼叫端讀 `code`，與此不同，需前後端一起調整（`frontend/PORTING.md` 第 4 節）。**待確認**：錯誤碼的命名規則，以及呼叫端要自行顯示錯誤（不跳全域提示）時的作法。

### S8. 對話視窗掛載位置

- **問題**：登入後的頁面有沒有共用 layout（App.vue、MainLayout 或 route meta）？要掛在哪裡，才能讓「所有已授權頁面」都顯示助理按鈕？
- **為何要問**：決定 widget 的掛載點，以及頁面沒權限時要怎麼隱藏。
- **預設假設**：（已確定）只放在個人 360 頁面元件中，不掛全域 layout；需確認平台 360 頁面元件的位置與如何取得頁面上的顧客編號。
- **回覆**：（2026-11-11，依 `frontend-instructions.md` §2）頁面在 `views/management/customer/`，共用 layout 在 `components/layout/`。小幫手元件放 `components/ai-assistant/`，在個人 360 頁以 `<assistant-launcher :cert-no="…">` 掛載（`frontend/PORTING.md` 3.2）。**待確認**：平台個人 360 頁元件的實際檔名，以及頁面上顧客證號的取得方式。

### S9. 路由權限守衛與後台選單

- **問題**：管理頁的路由和選單怎麼註冊？權限守衛怎麼寫？
- **為何要問**：Prompt / Skill 管理頁要放進既有後台。
- **預設假設**：依照既有 router meta 權限機制。
- **回覆**：

### S10. MongoDB 慣例 ⚠️

- **問題**：
    1. ~~`COLL_PROJECT` 前綴的實際值是什麼？~~（SP 計畫已指定 `COLL_ICRM_`）collection 名稱常數寫在哪裡？
    2. 是否使用 Spring Data MongoDB？有沒有既有的 `@Version` 樂觀鎖用法？
    3. Index 和 JSON Schema Validation 由誰建立：程式啟動時建立、migration script，還是 DBA？
    4. [db-instructions.md](../db-instructions.md) §2 規定 collection 名稱為 `COLL_<模組>_<描述>`，模組只有 `MANAGEMENT`／`MARKETING`／`MAINTENANCE`／`COMMON`／`SCHEDULER`；小幫手目前沿用 SP 計畫指定的 `COLL_ICRM_AI_ASSISTANT_*`。以哪一個為準？如果改用模組前綴，小幫手屬於哪個模組？新的 collection 要不要登記到 db-instructions §4、§6-2、§11 的表格？
- **為何要問**：規格審查要求事先定義 collection、index 和 sharding。collection 改名要搬資料，越晚改代價越高。
- **預設假設**：使用 Spring Data MongoDB，collection 名為 `COLL_ICRM_AI_ASSISTANT_CONFIG`，index 和 validation 用 migration script 建立；collection 前綴維持 `COLL_ICRM_`。
- **補充**：TTL index 已依 §2 改名為 `IX_ICRM_AI_AUDIT_TTL`、`IX_ICRM_AI_EVAL_TTL`。已有舊 index 的環境要在維護時段先刪舊的再建新的（§11），見 [platform-setup-requests.md](platform-setup-requests.md)。
- **回覆**：

### S11. 既有稽核機制

- **問題**：
    1. 平台有沒有既有的稽核紀錄機制（audit collection、AOP 或稽核服務）？
    2. [db-instructions.md](../db-instructions.md) §4、§11 顯示平台有 `AuditLogAspect`，寫入 `COLL_COMMON_AUDIT_LOG`（`operatorId`、`operatedAt`，保存 5 年）。要怎麼掛上（annotation 名稱與參數）？會記錄哪些欄位？會不會連 request body 一起保存？
    3. 小幫手的哪些操作應該走平台稽核：使用者對回答按讚／倒讚、Prompt 管理的存檔／發布／套用／捨棄、開始評測？
- **為何要問**：每次 tool 呼叫都要留下稽核資訊（使用者、工具名稱、問題、LLM 回答、執行結果、耗時與時間）。回饋目前只覆寫稽核紀錄上的最後一次評分與時間，沒有操作軌跡（是否需要見 M4）；Prompt 管理的每個版本都保留操作者與時間，版本歷程本身可追溯。提問與回答屬敏感內容，如果 Aspect 會保存 request body，就不能直接套在對話 API 上。
- **預設假設**：對話稽核維持獨立的 `COLL_ICRM_AI_ASSISTANT_AUDIT`，因為欄位多，而且含工具呼叫明細。回饋先只記應用程式 log（`feedback set`，見 S12）。確認 Aspect 不保存敏感內容後，再把回饋掛上平台稽核。
- **補充**：這個 repo 沒有 `AuditLogAspect` 的程式碼，上面的資訊只來自 db-instructions。
- **回覆**：

### S12. Log 規範

- **問題**：
    1. logger 怎麼使用？有沒有既有的遮罩工具，或 MDC traceId？
    2. 應用程式 log 輸出到哪裡（console 由容器收集、寫檔，或集中式 log 平台）？保留多久？開發與維運人員在哪裡查詢？
- **為何要問**：提問與回答要進稽核紀錄，但不應出現在應用程式 log；工具參數等敏感資料兩邊都不記。小幫手目前沒有自己的 log 設定，沿用 Spring Boot 預設，只輸出到 console。`chat answered`、`feedback set` 等 log 是排查問題的依據；保留期限決定這些 log 能不能拿來追溯操作。
- **預設假設**：沿用既有 logger、traceId 與平台的 log 收集方式。應用程式 log 只用於排查問題，不當作稽核依據；稽核依據是稽核 collection。
- **回覆**：

### S13. 既有依賴與版本

- **問題**：
    1. 有沒有引入 LangChain4j？是哪個版本，和 Spring Boot 4.1 / Java 25 相容嗎？
    2. 前端有沒有 Markdown 渲染套件（markdown-it 或 marked）、sanitizer（DOMPurify）、Markdown 編輯器元件？
    3. 既有平台怎麼管理第三方 jar（內部 Nexus / Artifactory 的匯入流程、專案內的 lib 資料夾，或其他方式）？Maven 的 `settings.xml` 有沒有設定 `mirrorOf`，值是什麼？
- **為何要問**：新增依賴可能需要審核。LangChain4j 和 Spring Boot 4 的相容性需要確認。公司環境連不到外部 Maven repo，新依賴必須能離線建置；`mirrorOf` 設成 `*` 會攔截專案內的 repository。
- **預設假設**：LangChain4j 只用核心套件 1.22.0（不用 beta 的 Spring Boot starter）；（已確定公司環境連不到外部 Maven repo）jar 放在專案內的 `backend/lib-repo/`，以 file repository 引用（見 [01-architecture.md](spec/01-architecture.md) 7.1）；前端 Markdown 渲染與 sanitizer 自行實作，不引入套件。
- **回覆**：

### S14. 設定檔與 secret 注入

- **問題**：yaml 怎麼分環境？secret 用環境變數、Vault，還是其他方式注入？
- **為何要問**：GPT API key 只能由後端透過 yaml config 注入。
- **預設假設**：`application-{env}.yml` 引用環境變數，key 不進版控。
- **回覆**：

### S15. 對外 HTTP 連線

- **問題**：既有對外呼叫用哪個 HTTP client（RestClient、WebClient 或其他）？要不要經過 proxy？timeout 的設定慣例是什麼？
- **為何要問**：外部市場 API client 必須設定 timeout、回應大小上限和 Allowlist host。
- **預設假設**：使用 `RestClient`，在 client 層加上 timeout、大小上限和 host 白名單。
- **回覆**：

### S16. 測試框架與慣例

- **問題**：後端用什麼測試框架（JUnit 5、Mockito、Testcontainers），前端用什麼（Vitest 或其他）？有沒有覆蓋率門檻？
- **預設假設**：後端 JUnit 5 加 Mockito；前端 Vitest；沒有硬性覆蓋率門檻。
- **回覆**：

### S17. 既有限流機制

- **問題**：平台有沒有每個使用者的 API 限流或防重送機制？
- **為何要問**：GPT 呼叫有費用，需要防止濫用。
- **預設假設**：沒有。在 chat endpoint 自行加上每位使用者的限流。
- **回覆**：

### S18. MongoDB sharding 慣例 ⚠️

- **問題**：
    1. 叢集的 Hash Sharding 是所有 collection 都 shard，還是只有資料量大的 collection？既有 collection 的 shard key 怎麼選？
    2. `sh.shardCollection` 由誰、在什麼時機執行（migration、DBA 手動）？新建的 collection 會不會預設 shard？
    3. 在 sharded collection 上，既有程式怎麼處理 unique index（例如以 shard key 開頭）？
    4. 應用程式是不是透過 mongos 連線？既有程式有沒有使用 transaction？
- **為何要問**：全行開放後，稽核紀錄很可能需要 shard（見 M36）；設定 collection 依賴 unique index 和 transaction，不能隨意 shard（見 [04-mongodb-schema.md](spec/04-mongodb-schema.md) 第 5 節）。
- **預設假設**：只有資料量大的 collection 才 shard，由 migration 或 DBA 執行；應用程式透過 mongos 連線，可使用 transaction。
- **回覆**：

### S19. 呼叫端帶入的 `X-B3-TraceId` ⚠️

- **問題**：
    1. gateway／ingress 會不會帶入或覆寫 `X-B3-TraceId`？瀏覽器（使用者端）自己帶的值會原樣傳到後端，還是被移除、改寫？
    2. 帶入時的格式是什麼（長度、大小寫、是否含連字號）？是否每個請求唯一，還是同一個使用者操作的多個請求共用？
    3. 平台有沒有對這個 header 做格式或長度檢查？其他應用程式怎麼處理？
- **為何要問**：平台 `TraceIdFilter` 會原樣沿用呼叫端帶入的 `X-B3-TraceId`，不檢查內容，小幫手現在只用這個 `traceId` 當追蹤碼（見 [02-rest-api.md](spec/02-rest-api.md) 2.3 節）。這有三個影響：
    1. 若使用者端的值能直達後端，登入者可以帶入含空白與 `key=value` 的文字，讓應用程式 log 行看起來多了假欄位，或帶很長的值讓每行 log 變長。回應 JSON、稽核寫入（截到 64 字）與回饋 API（格式不符回 400）不受影響；能否帶入換行字元尚未驗證。
    2. 回饋 API 只接受 16–32 碼小寫十六進位的 `traceId`。gateway 帶入的若是大寫或含連字號的值，所有回答都無法評分。
    3. 同一個 `traceId` 用在多次提問時，評分會記到該使用者最新的那一筆，而不是被點的那一則。
- **預設假設**：gateway 不帶入也不轉傳使用者端的 `X-B3-TraceId`，由平台 filter 產生 32 碼小寫十六進位、每個請求唯一的值；小幫手維持原樣沿用，不另外檢查。預設不成立時，在小幫手這邊加檢查：格式不符（非英數字與連字號，或超過 64 字）就改用自己產生的值，並依實際格式調整回饋 API 的檢查規則。
- **回覆**：

---

## Part 2：主管確認清單

### A. 個資與合規（最優先）

#### M1. SIT 顧客資料能否送往外部 GPT ⚠️

- **問題**：SIT 測資裡的顧客資料，能不能送到外部 GPT（OpenAI 或 Azure OpenAI）？
- **為何要問**：這是顧客經營問答能否成立的前提，也是安全評分的重點。
- **預設假設**：（POC 已採用）任何含顧客實際身分的資料都由程式去識別化後才送 LLM：不送顧客編號、姓名、身分證號、電話、Email、地址、帳號；年齡與金額泛化為區間（見 [03-tool-catalog.md](spec/03-tool-catalog.md) 1.6）。需確認 SIT 測資在這個前提下可以使用。
- **回覆**：

#### M2. 欄位分級與遮罩規則 ⚠️

- **問題**：哪些顧客欄位可以送給 GPT？哪些要遮罩，哪些必須排除？有沒有既有的個資分級表？
- **預設假設**：識別欄位一律刪除（連遮罩後的姓名也不送）；客群、往來年資、風險屬性、標籤可以送；經營洞見、活動摘要等自由文字先經 `PiiRedactor` 遮蔽後再送。
- **回覆**：

#### M3. 資安與法遵審查

- **問題**：需要資安或法遵單位審查嗎？什麼時候審？
- **預設假設**：在規格審查時一併審查。
- **回覆**：

#### M4. 稽核資料保存

- **問題**：稽核資料（含提問與 LLM 回答全文）要保存多久？需要查詢介面嗎？誰可以查看提問與回答明細？提問或回答含顧客資料時，需要額外遮罩或加密嗎？使用者對回答按讚／倒讚，需要留下可稽核的操作軌跡（每次變更的時間與內容）嗎？
- **預設假設**：使用者回饋不需要可稽核的操作軌跡：稽核紀錄只保存最後一次評分與時間（`feedback`、`feedbackAt`），每次變更另記一行應用程式 log（`feedback set`）；需要時改走平台稽核（見 S11）。保存 5 年（1,825 天，對齊平台稽核日誌 `COLL_COMMON_AUDIT_LOG`，待法遵確認），不做查詢介面，只存資料；**稽核不記錄顧客編號**（顧客編號 `certNo` 是身分證字號，已於 2026-10-28 設計調整為不存入稽核，欄位與依顧客查詢的索引已移除）；提問存去識別化後的版本（本顧客自己的 `certNo` 會換成【本顧客】）並遮罩疑似憑證；以 `traceId`、使用者與時間追溯，無法反查是哪位顧客。
- **補充**：保存期限也決定稽核紀錄要不要 sharding，見 M36。
- **回覆**：

### B. GPT 供應商與費用

#### M5. 供應商與模型 ⚠️

- **問題**：使用 OpenAI 直連還是 Azure OpenAI？指定哪個模型？
- **為何要問**：LangChain4j 的模組、端點設定和資料落地位置都不同。
- **預設假設**：依主管指定；未指定時，選用支援 tool calling 的 GPT 系列模型。
- **回覆**：

#### M6. API key、預算與 rate limit

- **問題**：API key 什麼時候提供？每月預算或額度是多少？供應商的 rate limit 是多少？
- **回覆**：

#### M7. 單次提問的限額數值

- **問題**：需求要求限制 tool call 次數、遞迴深度、timeout 和 token，具體數值由誰決定？
- **預設假設**：
    - 單次提問最多 5 次 tool call，最多 3 輪模型往返
    - 單次 GPT 呼叫 timeout 30 秒，整體上限 60 秒
    - 單一 tool timeout 5 秒（顧客工具 3 秒）
    - 單次提問累計 token 上限 16,000（含路由與所有回合），單次回應輸出上限 1,500 token（回答改為 JSON 輸出後調高；見 [01-architecture.md](spec/01-architecture.md) 第 6 節）
- **回覆**：

#### M8. 是否需要串流回應

- **問題**：需求寫「同步 REST API」，代表不做串流（SSE）嗎？
- **預設假設**：不做串流，回應一次回傳。
- **回覆**：

#### M9. 「取消顯示」的語意

- **問題**：對話視窗的「取消」是指前端不再等待就好，還是後端也要中止 GPT 請求？
- **預設假設**：前端中止等待並丟棄結果；後端照常完成並寫入稽核，但結果不回傳。
- **回覆**：

### C. 內部平台 API（Allowlist）

#### M10. 可用的平台 API 清單 ⚠️

- **問題**：哪些平台 API 可以列入 Allowlist？下列資料分別對應哪一支 API？
    - 顧客基本資料
    - 往來概況
    - 既有標籤
    - 近期互動紀錄
    - 活動結果
- **為何要問**：Tool Catalog 的資料來源必須是核准過的 API。
- **回覆**：

#### M11. 使用者怎麼指定顧客

- **問題**：使用者在對話中要怎麼指定顧客：輸入顧客編號、用姓名搜尋，還是從目前開啟的顧客頁面自動帶入？
- **為何要問**：用姓名搜尋牽涉個資，會多出一個搜尋工具和更多權限風險。
- **預設假設**：（已確定）小幫手只在個人 360 頁面出現，對話自動綁定該頁顧客；使用者透過輸入顧客編號或從列表點擊進入 360 頁面。
- **回覆**：

#### M12. 「受控查詢」的定義

- **問題**：需求 2-2 提到的「受控查詢」具體指什麼？
- **預設假設**：Java 預先寫好的參數化固定查詢。模型只能選擇查詢並填入參數，不能產生查詢語句。
- **回覆**：

#### M13. SIT 測試帳號與測資

- **問題**：能不能提供不同角色的 SIT 測試帳號（一般使用者、管理者、權限受限者），以及固定的測試顧客？
- **為何要問**：越權情境的測試需要多種角色的帳號。
- **回覆**：

#### M14. 「其他」類問答的方向

- **問題**：需求 3-2 第 4 類「其他」可以自由發揮，主管有沒有偏好方向？
- **候選方向**：
    - 客群統計概況
    - 依互動紀錄建議下次聯繫時機
    - 商品持有概況
    - 結合市場資訊準備聯繫話題
- **預設假設**：（POC 已採用）做「電話開場白」（見 [06-conversation-scenarios.md](spec/06-conversation-scenarios.md) Q9），以及需求三類之外的「資產負債」「會員等級」「往來與通路」「市場快訊」分類。原本的「聯繫話題」（持股加市場資訊）因持股工具於 2026-10-28 移除而取消（ideas-backlog 想法 9）。
- **回覆**：

### D. 外部市場資料

#### M15. 核准的市場資料來源 ⚠️

- **問題**：哪些公開資料來源已經核准，或可以送審？
- **候選來源**：TWSE OpenAPI（台灣證交所）、TPEx OpenAPI（櫃買中心）、市場新聞 API。
- **預設假設**：以 TWSE OpenAPI 為主（免費，公開使用條款）。
- **回覆**：

#### M16. 市場範圍

- **問題**：範圍是台股、美股，還是兩者都要？需要新聞嗎？
- **預設假設**：只做台股指數和個股行情，新聞列為選做。
- **回覆**：

#### M17. 資料來源 key 與使用條款

- **問題**：如果資料來源需要 API key 或付費，由誰申請？使用條款由誰審？
- **回覆**：

#### M18. 對外連線開通

- **問題**：開發和 SIT 環境要怎麼申請對外 host 的防火牆或 proxy 開通？需要多久？
- **回覆**：

### E. Prompt / Skill 管理

#### M19. Skill 的定位 ⚠️

- **問題**：Skill Markdown 在系統裡扮演什麼角色？
    - (a) 固定附加在 System Prompt 後面
    - (b) 依問題類型動態載入
    - (c) 和特定 tool 綁定，提供使用說明
- **預設假設**：（POC 已採用）改稱 System Instructions：每份是一個問題分類，含適用問題、回答套版、範例問題；每次提問先以一次模型呼叫依「適用問題」選出分類（b 的做法），回答時只載入該分類，快捷按鈕直接指定分類，路由失敗才載入全部；模型再從可用工具中挑選（工具依存取等級提供）；範例問題顯示為快捷按鈕（見 [07-prompt-examples.md](spec/07-prompt-examples.md)、[01-architecture.md](spec/01-architecture.md) 4.1）。
- **回覆**：

#### M20. configKey 與 configType 的命名

- **問題**：`configType` 的列舉值和 `configKey` 的命名規則？
- **預設假設**：（POC 已採用）`configType` 為 `SYSTEM_PROMPT` 或 `SYSTEM_INSTRUCTION`；`configKey` 例如 `system.main`、`instruction.contact-prep`。
- **回覆**：

#### M21. 狀態機與發布審核

- **問題**：狀態怎麼流轉？發布需要第二個人審核嗎？
- **預設假設**：（POC 已採用）狀態為 `DRAFT`、`SCHEDULED`（預約發布）、`PUBLISHED`、`ARCHIVED`（被新版取代）、`DISCARDED`（未上線就被捨棄或被套用取代），同一個 configKey 同時只有一個 `PUBLISHED` 與一個編輯中版本（`DRAFT` 或 `SCHEDULED`），沒有任何操作會刪除版本（見 [04-mongodb-schema.md](spec/04-mongodb-schema.md) 2.4）；後台管理與 IT 管理人員可以直接發布或預約發布，不需要第二人審核。
- **回覆**：

#### M22. 「回復前一版本」的語意

- **問題**：回復是把舊版複製成新版本再發布，還是直接把舊版切回 `PUBLISHED`？
- **預設假設**：（POC 已採用）不只回復上一版：可任選兩版比較，並把任一已發布 / 已封存版本套用為草稿，再立即或預約發布；歷程完整保留。畫面以時間辨識版本，不顯示版本號（`version` 只作內部流水號）。
- **回覆**：

#### M23. 樂觀鎖欄位與業務版本號

- **問題**：`@Version` 樂觀鎖欄位和業務上的 `version` 欄位要分開嗎？
- **為何要問**：每個版本是獨立的 document，業務版本號是 1、2、3 遞增；樂觀鎖則是防止同一份 document 被同時修改，兩者用途不同。
- **預設假設**：分開。`docVersion` 給 `@Version` 用，`version` 是業務版本號。
- **回覆**：

#### M24. 發布前的驗證規則

- **問題**：「必要章節」「禁止內容」「大小上限」的具體規則由誰定義？
- **預設假設**：（POC 已採用，見 [04-mongodb-schema.md](spec/04-mongodb-schema.md) 2.5）
    - 大小上限 64KB
    - 必要章節：System Prompt 不要求（安全規則與回答格式規則寫死在程式碼、固定附加在可編輯內容之後，管理者改壞 prompt 也不影響）；問題分類只要求 `## 適用問題`（路由依據），範例問題 0–5 題、每題 40 字內
    - 禁止內容用 regex 檢查：疑似 API key 或 token、密碼字樣、未列入白名單的網址、IP、身分證號、手機號碼、顧客編號
    - 「忽略以上規則」這類越權指令**尚未納入**：自然語言樣式誤判率高，且安全規則在 prompt 最後、要求忽略資料中的指令；若主管要求再加
- **回覆**：

#### M25. 版本保留與 sharding

- **問題**：版本要保留多久？需要 TTL、封存或 sharding 嗎？
- **預設假設**：保留所有版本，不設 TTL，不 sharding（資料量小）。
- **回覆**：

#### M26. 管理頁的位置與權限

- **問題**：管理頁放在哪個後台應用或選單下？哪些角色可以編輯和發布？
- **回覆**：管理頁只有後台管理人員和IT人員可以進入，這兩個角色都先讓他們有編輯和發布的權限

#### M27. 草稿試用功能

- **問題**：要不要讓管理者用草稿版本先試問，再決定是否發布？
- **預設假設**：核心功能不做，改用加分項「Prompt 評測集」比較發布前後的效果。
- **回覆**：不做

### F. 前端行為

#### M28. 「對話預設不保存」的範圍

- **問題**：切換頁面時要保留對話嗎？重新整理後呢？
- **預設假設**：存在 Pinia 記憶體裡，以顧客為單位；換到另一位顧客的 360 頁面、重新整理或登出就清空，不寫入 localStorage 或後端。
- **回覆**：

#### M29. 多語系

- **問題**：需要 i18n 嗎？
- **預設假設**：只做繁體中文，但字串集中管理，方便之後接 i18n。
- **回覆**：先不考慮i18n

#### M30. 輸入長度上限

- **問題**：前後端的輸入長度上限要設多少？
- **預設假設**：單則訊息最多 1000 字；送回後端的上下文最多 10 輪、總計 8000 字，超過就截掉最早的訊息。
- **回覆**：

### G. 交付與時程

#### M31. 時程與里程碑

- **問題**：整體時程多長？規格審查、開發完成、驗收各在什麼時候？
- **回覆**：

#### M32. 規格審查流程與文件位置

- **問題**：規格由誰審？文件放在哪裡（本 repo 的 `docs/`、Confluence，或既有專案）？
- **預設假設**：放在 repo 的 `docs/spec/`，用 .md 格式，送審時提供連結。
- **回覆**：

#### M33. 測試要求

- **問題**：有沒有測試要求，例如單元測試覆蓋率、E2E 測試、安全測試案例？
- **預設假設**：核心邏輯要有單元測試（tool calling 迴圈、權限驗證、內容驗證），另外附安全情境測試清單。
- **回覆**：

#### M34. 加分項與評分細項

- **問題**：
    1. 加分項目（回答引用、Prompt 評測集、回饋、可觀測性）是預期要做，還是選做？
    2. SP 計畫評分表「安全與系統韌性 (25%)」的細項只有 8% + 9%，第 10 項（推算 8%）缺漏，內容是什麼？
    3. 第 11 項「規格與測試」的檢核標準寫到「API、Tool、MongoDB 與」就斷了，完整內容是什麼？
- **預設假設**：「回答引用」和核心功能一起做；「Prompt 評測集」視時程決定。
- **回覆**：回答引用、Prompt 評測集、回饋、可觀測性現在看起來時間上都來的及，做出來好了

#### M35. 平移交付方式

- **問題**：「複製貼上平移」的單位是什麼（整個後端模組、前端資料夾、設定檔）？需要附上平移說明文件嗎？
- **預設假設**：後端一個獨立套件（加上 `backend/lib-repo/` 的第三方 jar）；前端依平台分層目錄放置，照目錄複製，平台替身不複製（2026-11-11 起，清單與接入步驟見 `frontend/PORTING.md`）；設定集中管理，並附上平移步驟說明。
- **回覆**：

### H. 資料量與擴充

#### M36. 稽核紀錄的資料量與 sharding ⚠️

- **問題**：
    1. 預計開放給哪些人員（理專、AO、業務等）、大約多少人？每人每天大約提問幾次？
    2. 稽核紀錄要保存多久（同 M4）？法遵是否要求保存 5 年以上？
    3. 稽核紀錄是否要保存工具查到的原始資料？（目前不保存，只記錄工具名稱、狀態與耗時。）
    4. 如果要 shard，shard key 由誰決定、誰審核？
- **為何要問**：這幾項決定稽核紀錄的資料量。以 1 萬人、每人每天 20 題、每筆 2 KB 估算：保存 180 天約 80 GB，保存 5～7 年約 0.8～1 TB；如果要保存工具原始資料，資料量會再放大 5～10 倍。估算細節見 [04-mongodb-schema.md](spec/04-mongodb-schema.md) 第 5 節。
- **預設假設**：稽核紀錄目前保存 5 年（同 M4），全行開放後很可能需要 shard，shard key 用 `{ userId: "hashed" }`；設定 collection 不 shard。**POC 階段先不改動作法**：程式不修改，`sh.shardCollection` 在整合時放進平台的 migration。
- **回覆**：

### I. Prompt 評測

#### M37. 正式環境能否以測試顧客呼叫 GPT 跑評測

- **問題**：Prompt 評測會在後端用固定的測試顧客資料（去識別化後）呼叫 GPT，比較已發布版本與編輯中版本。正式環境（含 SIT／UAT）能不能跑評測？測試顧客要由誰提供、需不需要另外的法遵或資安核准？
- **為何要問**：評測不經過顧客範圍規則，直接讀資料集指定的顧客（見 [platform-setup-requests.md](platform-setup-requests.md) 第 8 節），送給 GPT 的內容與一般對話相同（去識別化），但評測的提問與回答會另存在評測紀錄 collection，保留 180 天。若測試顧客是虛構資料，資料外送的疑慮與 M1 不同。
- **預設假設**：只使用平台提供的專用假顧客（不含任何真實資料），不得以真實顧客跑評測；假顧客資料可以送往 GPT（前提同 M1 的供應商核准）；評測只由有 `AI_CONFIG_EDIT` 的管理者手動觸發，不自動排程。
- **回覆**：

#### M38. 評測 token 預算的歸屬

- **問題**：每次評測約消耗 45 萬 token（推估：2026-10-29 以 30 題實測每個回答平均約 6,000–6,300 token；現為 34 題 × 2 個版本約 42 萬，加上每個回答一次評審約 3 萬；10/27 的 16 題版本為 16 萬）。這筆費用由誰負擔、算在哪個預算？每月允許跑幾次？OpenAI 專案的預算與 rate limit（M6）要不要把評測另外算？
- **為何要問**：評測用的是同一組 API key，一次評測的 token 相當於 70 次左右的一般提問。後端已限制同時只有一個評測、題數上限 40、整次最長 30 分鐘，但沒有「每月次數」的限制。
- **預設假設**：POC 階段由專案預算支付，不另設次數限制；正式環境由 Prompt 管理的負責單位（後台管理或 IT 管理）編列預算，並與 M6 的每月額度合併管理；若主管要求次數上限，再加入設定。
- **回覆**：

### J. 個人 360 內容重寫

> 對應 [customer360 內容重寫設計](superpowers/specs/2026-10-28-customer360-content-rewrite-design.md) 第 10 節。`reference.md` 沒有定義的欄位，POC 先依現有示意卡片推定，以下請平台確認或修正。

#### M39. 360 資料的推定項目（E1–E13）

- **問題**：下列 13 項 `reference.md` 沒有定義的內容，是否與平台實際一致？
    1. （E1）`RadarChartData.rank` 是否為 0–100 的指數；`info` 是否為顯示用原值文字（例如「180 萬」）。
    2. （E2）`CategoryData` 的 `ratioList`、`amountList` 的順序是否為 [顧客, 客群平均, 客群 T10]。
    3. （E3）`CustomFlagging` 的結構與類別：`{type, label, expiryDate?}`，`type` ∈ 不宜行銷、客訴、高資產、專區顧客、專投。
    4. （E4）`KeyAlerts` 的結構與類別：`{items: [{type, title, detail, date?}]}`，`type` ∈ 理財會員降等、大額金流流入、資金活水、停利停損、其他待辦。
    5. （E5）`ProfilePhoto` 是否為 `{tags: string[]}`（畫像標籤）。
    6. （E6）`upgradeOrDownGradeAlert` 是否為一句文字，沒有提醒時為 null。
    7. （E7）`memberRank` 是否另有等級代碼欄位 `memberRankCode`（P、Q、N…）。
    8. （E8）`CustomerDto` 頂層是否有 `dataDate`（`yyyy/MM/dd`）。
    9. （E9）各等級權益文案是否由平台提供；mock 沿用 prototype 的權益文字並標示「示意」。
    10. （E10）`channelBehavior` 是否有回應率；DTO 目前只有顧客與客群指數，小幫手只回答「最常使用」，不回答「最有回應的通路」。
    11. （E11）`productMix` 的 `ldr`、`casaAndWealthAumRatio` 是否為百分比數值（62 表示 62%）；`aumYield` 是否為百分比數字字串（`"1.86"`）；DTO 沒有客群的存量收益率。
    12. （E12）`CampaignDetail.campaignEnabled` 是否為 `"Y"`／`"N"`。
    13. （E13）信用卡額度：DTO 只有已使用額度（`ccBalance`），沒有額度；小幫手只回答已使用額度。
- **為何要問**：小幫手的工具與 mock 以這些推定撰寫，與平台不一致時，數字與說法會錯。
- **預設假設**：全部依上列推定項目實作；與平台實際不一致時，只需調整 `getCustomer` 的格式化對應與 mock 資料，工具介面、prompt、評測集不受影響。
- **回覆**：

#### M40. 平台 API 欄位拼法

- **問題**：mock API 的欄位名 `segement`、`financialCounsultant`、`segementProgressPercent` 是否為平台實際拼法（reference 原樣）？
- **為何要問**：整合時 mock 型別要能直接對上平台的 `CustomerDto`；Java record 欄位用正確拼字、以 `@JsonProperty` 標註平台拼法。
- **預設假設**：照 reference 原樣（含拼錯的字）。
- **回覆**：

#### M41. 職稱對角色碼

- **問題**：mock 帳號的職稱對業務角色碼的對應是否正確？目前：總行查詢人員、區域查詢人員 → ANALYST；分行單位主管、理專主管、AO主管 → MANAGER；理專經辦 → RM；AO經辦 → AO；後台管理人員、IT 管理人員 → ADMIN。
- **為何要問**：`roleKey` 是稽核與統計的依據，顧客範圍另由 `scope` 決定；對應錯誤會讓統計失真。
- **預設假設**：照上列對應（推定）。
- **回覆**：

#### M42. 共銷註記（`coMarketingFlag`）

- **問題**：共銷註記如何影響 RM、AO 可見的顧客？
- **為何要問**：目前 mock 的範圍規則不含共銷註記，之後依實際角色權限區分。
- **預設假設**：POC 不處理，列為待確認。
- **回覆**：

#### M43. 只能看部分顧客資料的角色（LIMITED 是否保留）

- **問題**：平台是否有只能看部分顧客資料的角色？
- **為何要問**：目前後台、IT 管理人員已改為 `FULL`，沒有 mock 角色是 `LIMITED`；機制（去識別化基本輪廓）保留，需要平台答案才能決定是否保留或移除。
- **預設假設**：保留機制，評測集仍以 `accessLevel: LIMITED` 直接測；之後依平台實際角色權限對應。
- **回覆**：

#### M44. 極致／私銀等級的門檻與防降線

- **問題**：極致（代碼 V）、私銀（K）的升等門檻與防降線是多少？登峰（M）的目標等級門檻？
- **為何要問**：目前這兩類只回傳等級名稱與權益，不回傳進度與差額；登峰不回傳升等差額。
- **預設假設**：維持未定，工具不回傳差額。
- **回覆**：

#### M45. 特殊重要顧客能否使用小幫手

- **問題**：KYC 身分代碼 A1、A2、A3、G1、G2、AML 政治敏感性人物、PEP 關係人等特殊重要顧客，能否使用小幫手？可以時，「填寫查詢原因後方可調閱」的查詢原因是否要寫入稽核？
- **為何要問**：這類顧客的調閱流程另有規定，小幫手若繞過會有合規問題。
- **預設假設**：POC 不處理，另開 spec。
- **回覆**：

#### M46. 成年監護（輔助）顧客

- **問題**：成年監護（輔助）的顧客，小幫手是否要另外提示需有監護人或輔助人在場？
- **為何要問**：避免在不適當的情境下提供行銷建議。
- **預設假設**：POC 不處理（CIP 的 `grownUpGuaridanshipCode` 不使用）。
- **回覆**：

#### M47. `regCountryCode` 的用途

- **問題**：CIP 的 `regCountryCode` 是否影響行銷限制？
- **為何要問**：目前小幫手只使用 `specialCareCustomer` 布林值，不使用該欄位。
- **預設假設**：不使用。
- **回覆**：

#### M48. certNo 的完整格式與 LIMITED 遮罩寫法

- **問題**：
    1. 顧客編號 `certNo` 的完整格式（身分證、居留證、外籍人士證號、統編等）是什麼？
    2. `LIMITED` 時的遮罩寫法：reference 範例 `A123****6789` 與規則文字（首碼＋後四碼）不一致，以哪個為準？
- **為何要問**：`PiiRedactor` 與 `ConfigValidator` 的 `certNo` 偵測規則、路徑參數驗證與畫面遮罩都依賴格式。
- **預設假設**：路徑驗證 `^[A-Z0-9&]{8,12}$`（依 Format 規則 7 的範例 `A123456789`、`HKC123456`、`&F12345678` 推定，只接受大寫半形）；`PiiRedactor` 偵測 `[A-Z][12]\d{8}`、`[A-Z]{3}\d{6}`、`&[A-Z]\d{8}`（後兩者推定）；遮罩依規則文字「首碼＋後四碼」，例如 `A*****6789`。
- **回覆**：

#### M49. 路徑含 certNo 的 access log

- **問題**：對話與快速提問 API 的路徑含 `certNo`（`/api/ai-assistant/customers/{certNo}/…`），會出現在 Nginx／ingress 的 access log；是否改放 request body，或由平台遮罩 log？
- **為何要問**：`certNo` 是身分證字號，出現在 access log 屬於個資外洩面。小幫手自己的 log 與稽核已不含 `certNo`。
- **預設假設**：POC 維持路徑（與既有路由一致）；正式環境依平台決議（改放 body 或遮罩 log）。
- **回覆**：

#### M50. 正式環境的評測集

- **問題**：評測集資料檔在版控中，內含 `certNo`；正式環境是否只能使用測試顧客的 `certNo`？
- **為何要問**：版控中的檔案不得含真實顧客的身分證字號；與 M37 的測試顧客一併處理。
- **預設假設**：正式環境的評測集只使用平台提供的測試顧客 `certNo`。
- **回覆**：

---

## 附錄：需求章節與題號對照

| 需求章節                 | 對應題號                                |
| ------------------------ | --------------------------------------- |
| 2. 技術與架構要求        | S1, S2, S4, S6, S10, S13, S15, M10, M15 |
| 2-1. GPT API key         | S14, M5, M6                             |
| 2-2. Tool calling 流程   | S1, S3, M7, M12                         |
| 3-1. 對話視窗            | S6, S7, S8, M8, M9, M28, M29, M30       |
| 3-2. 顧客經營問答        | S3, M1, M2, M10, M11, M13, M14          |
| 3-3. 市場資訊調查        | S15, M15, M16, M17, M18                 |
| 3-4. Tool Registry       | S4, S5, S11, S12, M4, M7                |
| 3-5. Prompt / Skill 管理 | S9, S10, M19 – M27                      |
| 3-6. 對話安全與錯誤處理  | S7, S17, S19, M1, M2, M3, M30           |
| 3-7. 文件                | M32                                     |
| 4. 加分挑戰              | M27, M34, M37, M38                      |
| 5. 評分標準              | M33, M34                                |
| 平移需求（開頭）         | S2, M35                                 |
| 資料量與 sharding        | S10, S18, M4, M25, M36                  |
| 個人 360 內容重寫        | M39 – M50（M37 與 M50 一併處理）        |
