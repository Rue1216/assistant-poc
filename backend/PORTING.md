# 小幫手後端平移說明（給公司專案內的 agent）

> 用途：把 POC 的 `backend/`（行銷人員顧問小幫手，套件 `com.example.copilot.aiassistant`）搬進公司平台的 maven reactor（`core/`、`backend/`、`scheduler/`）。
> 讀者：在公司專案內執行平移的 agent。前端已先搬完（見 `frontend/PORTING.md`），目前卡在**前端權限控管沒有接上平台後端的實際設定，本機看不到畫面**；後端平移要一併解決這件事。
> 規範來源：公司專案內的 `reference.md`（平台技術決策、分層、log、安全、外部 client）、`db-instructions.md`（MongoDB）、`frontend-instructions.md`。本文件與它們衝突時，**以平台規範與平台現有程式碼為準**，並把偏離之處列到第 9 節回報。
> 本文件標示 **【已確認】** 的是 POC 程式碼或平台文件明寫的事實；**【推論】** 是依線索推出、需要你在平台程式碼中驗證的判斷；**【待決定】** 必須停下來問使用者。

---

## 0. 執行守則

1. **先讀平台、後動手**。每一節都附「去平台找什麼」的線索，先找到平台既有的寫法（範例模組、既有 Job、既有 security 設定），照它寫，不要照 POC 的替身寫。
2. **小幫手核心邏輯不改**：`domain/`、`application/` 的行為維持原樣，結構已依 §3-0 對齊（`application/command/`、`application/query/`、`@UseCase`、交易在 UseCase 上），只換掉「平台替身」與「平台規範要求的外殼」（套件名、回應格式、權限表示式、設定注入、建表方式、排程入口）。
3. **POC 裡的平台仿製品不要搬**：POC 的 `com.example.{aspect, client, constant, exception, filter, logging, persistence, platform, shared}` 是照平台手抄的替身（例如 `platform/security/domain/SecurityUser.java` 甚至有錯字、編譯不過），平台已有正本。`com.example.shared.annotation.UseCase`（標在 `application/command/` 的入口類別上）也是平台副本，同樣不搬。搬過去後把 import 改指平台正本即可。
4. **`mockplatform/` 整個不搬**（假登入、假帳號、假顧客、`SecurityConfig`、`PlatformFilterConfiguration`）。它們的職責改由平台實作 port 承接（第 2、3 節）。
5. **不要 commit**。每完成一個階段，列出 `git add` 清單與建議的 commit message 給使用者。
6. 每個階段結束都要跑平台的建置驗證（`reference.md` §1-2、§8-4）：改 `core` 後跑 `mvn -pl backend -am compile -Pdev -U` 與 `mvn -pl scheduler -am compile -Pdev -U`；提交前跑 `mvn test`。結果原樣回報，失敗不要說成功。

---

## 1. POC 後端概況

### 1.1 要搬的東西

| POC 位置 | 內容 | 搬到哪裡 |
|---|---|---|
| `src/main/java/com/example/copilot/aiassistant/**` | 小幫手本體（domain / application / infrastructure / interfaces 四層） | 平台 `backend/` 內的新 bounded context（套件依平台 `com.org.icrm...` 命名，【待決定】BC 名稱與位置，例如 `aiassistant/`） |
| `aiassistant/infrastructure/scheduler/ScheduledPublishJob.java` | 預約發布排程 | **不放 backend**，改寫成 `scheduler/` 的 Job（第 4 節） |
| `src/main/resources/seed/**` | 初始 System Prompt 與 7 個問題分類（`manifest.json` + `.md`） | backend resources（或改為 DBA 初始資料腳本，第 5 節） |
| `src/main/resources/mongo/*-validator.json` | 3 個 collection 的 JSON Schema Validation | 交給 DBA（第 5 節），程式不再建立 |
| `src/main/resources/eval/eval-cases.yml` | Prompt 評測題目（綁定假測試顧客 `certNo`） | backend resources；`certNo` 要換成平台提供的假顧客 |
| `src/main/resources/application.yml` 的 `ai-assistant.*` | 小幫手設定 | 平台各環境的 `application.yml` / `application-{env}.yml`（第 3 節） |
| `lib-repo/` | LangChain4j 1.22.0 及其間接依賴（公司環境無法從外部下載） | 依平台管理第三方 jar 的方式匯入（Nexus 或專案內 lib-repo）；`settings.xml` 若是 `<mirrorOf>*</mirrorOf>` 要改成 `external:*` 或 `*,!project-local` |
| `src/test/**` | 單元與整合測試（Testcontainers MongoDB） | 對應到平台測試目錄；替身相關的測試改寫或刪除 |

### 1.2 REST API（全部在 `/api/ai-assistant/**`）

| 方法 | 路徑 | POC 權限 |
|---|---|---|
| POST | `/api/ai-assistant/customers/{certNo}/chat` | `AI_ASSISTANT_USE` |
| GET | `/api/ai-assistant/customers/{certNo}/suggestions` | `AI_ASSISTANT_USE` |
| PUT | `/api/ai-assistant/chat/{traceId}/feedback` | `AI_ASSISTANT_USE` |
| GET/POST | `/api/ai-assistant/admin/configs`、`/{configKey}/versions[/{version}]` | `AI_CONFIG_READ` / `AI_CONFIG_EDIT` |
| POST/PUT/DELETE | `/api/ai-assistant/admin/configs/{configKey}/draft[/validate]` | `AI_CONFIG_EDIT` |
| POST | `/api/ai-assistant/admin/configs/{configKey}/publish` | `AI_CONFIG_PUBLISH` |
| POST | `/api/ai-assistant/admin/configs/{configKey}/versions/{version}/apply`（舊版本套用為草稿） | `AI_CONFIG_EDIT` |
| GET | `/api/ai-assistant/admin/tools`、`/admin/configs/{configKey}/feedback-stats` | `AI_CONFIG_READ` |
| POST/GET | `/api/ai-assistant/admin/configs/{configKey}/evaluations`、`/admin/evaluations/{current,runId}`、`/admin/evaluation-cases` | 開始評測 `AI_CONFIG_EDIT`，讀取 `AI_CONFIG_READ` |

`/api/mock/auth/**`、`/api/mock/customers/**` 是替身，不搬。

### 1.3 小幫手只透過 3 個 port 取得平台能力【已確認】

```java
public interface CurrentUserPort {      // 目前登入者 → UserContext(userId, displayName, roleKey, permissions)
  UserContext currentUser();
}
public interface CustomerAccessPort {   // 登入者能否看這位顧客 → NONE | LIMITED | FULL；顧客不存在也回 NONE
  AccessLevel checkAccess(UserContext user, String certNo);
}
public interface CustomerDataPort {     // 平台顧客資料
  Optional<CustomerRecord> findCustomer(String certNo);              // 身分與範圍欄位（不送模型）
  Optional<Customer360Record> findCustomer360(String certNo);        // = 平台 getCustomer（CustomerDto）
  Optional<RealTimeStatusRecord> findRealTimeStatus(String certNo);  // = 平台 getRealTimeCustomer（RealTimeCustomerDto）
}
```

平台端要寫 3 個 adapter 實作它們（第 2 節說明權限相關的兩個）。`CustomerDataPort` 的 adapter 要對 `getCustomer`、`getRealTimeCustomer` 設 HTTP 逾時（建議 ≤ 3 秒），因為「本顧客註記」在請求執行緒上讀取即時資料，此時工具逾時還沒生效。

---

## 2. 權限控管

### 2.1 POC 目前的做法【已確認】

**登入（替身，整合時刪除）**

- `mockplatform/SecurityConfig`：Spring Security session 登入；9 個假帳號放在 `InMemoryUserDetailsManager`（共用密碼 `poc1234`，BCrypt）；`POST /api/mock/auth/login` 以帳密登入、寫入 `HttpSession`；`GET /api/mock/auth/me` 回 `username, displayName, roleKey, roleName, permissions`。
- CSRF：`csrf.spa()` + `CookieCsrfTokenRepository.withHttpOnlyFalse()`（`XSRF-TOKEN` cookie），每個回應都寫 cookie。
- 401：自訂 `AuthenticationEntryPoint` 回 `ApiError(AI_UNAUTHENTICATED)` JSON。
- `@EnableMethodSecurity` 寫在 mock 的 `SecurityConfig`，**不會跟著搬**；平台若沒有開，所有 `@PreAuthorize` 會靜默失效。

**API 權限（扁平權限碼）**

- 每個假帳號的 `GrantedAuthority` = 權限碼集合 + `ROLE_{roleKey}`。
- 權限碼 5 個：`CUSTOMER_360_VIEW`、`AI_ASSISTANT_USE`（所有帳號）、`AI_CONFIG_READ`、`AI_CONFIG_EDIT`、`AI_CONFIG_PUBLISH`（只有後台管理人員、IT 管理人員；M26 已回覆：這兩個角色都可編輯與發布）。
- Controller 用 `@PreAuthorize("hasAuthority('AI_...')")`。
- `roleKey`（`ANALYST`/`MANAGER`/`RM`/`AO`/`ADMIN`，【推論】的職稱對應，M41 待確認）只寫進稽核紀錄（`AuditLogger.MAX_ROLE = 32` 字），不參與授權。

**顧客範圍（資料層權限）**

- `ChatController`／`SuggestionController` 收到請求後，存取檢查在 `ChatUseCase.chat(CertNo, ChatCommand, UserContext)` 與 `SuggestionQueryService.suggestions(CertNo, UserContext)` 內：經 `CustomerContextFactory` 呼叫 `CustomerAccessPort.checkAccess(user, certNo)`（無權限或查無顧客 → `AI_CUSTOMER_NOT_FOUND`）；`NONE` 直接拒絕，`LIMITED` 只給去識別化的基本輪廓，`FULL` 可用全部工具（`ToolRegistry` 依存取等級篩選工具，不看角色）。
- POC 的範圍規則在 `MockCustomerAccessAdapter`，依帳號屬性 `MockScope`（ALL / REGION / BRANCH / BRANCH_WEALTH / ASSIGNEE_WEALTH / MARKETING_UNIT / ASSIGNEE_MARKETING）比對顧客的主經營行、利潤行、理財行、分派人員、行銷單位。
- `stopUseCustomerDataFlag = "Y"`（停止蒐集利用個資）的顧客，任何角色一律 `NONE`。
- 評測（`EvalRunner.customerFor`）在背景執行緒用合成的 `UserContext(triggeredBy, triggeredBy, "EVAL", Set.of())`（觸發者身分，非系統帳號）呼叫 `CustomerAccessPort.checkAccess`；結果為 `NONE` 就略過該題，否則才由 `CustomerContextFactory.forEvaluation` 讀假測試顧客。**平台 adapter 必須使用傳入的 `UserContext` 參數，不可改讀 `CurrentUserProvider`／`SecurityContext`**（背景執行緒沒有 request context，會取不到使用者）。

**其他**：`PerUserRateLimiter` 每人每分鐘 10 次（存在程序記憶體）。

### 2.2 平台的做法（線索）

| 線索 | 出處 | 代表什麼 |
|---|---|---|
| OAuth2 Code Flow 對接 ADFS → `OAuth2AdfsUserService` 解析 claims → Redis Session，前端以 `SESSION` cookie 維持 | reference.md 安全開發規範 §1、frontend-instructions §8 | 登入完全由平台處理，小幫手不用管 |
| dev 有 `DevAuthController`（`@Profile("dev")`）`POST /api/auth/dev-login`，CSRF ignore 特例；dev 停 CSRF，sit/uat/prod 啟用 | reference.md §1、技術決策 | **本機看畫面要走這條**（2.4） |
| `/api/auth/me` 回 `roles`（去掉 `ROLE_` 前綴的業務角色碼）、`permissions`、`menuUris`；角色不在前端白名單 `constants/role-code.ts` 就導 `/forbidden` | frontend-instructions §5、§8 | 前端權限資料的來源 |
| `SecurityUser.permissions: Map<模組 id, ModulePermission(canEdit, canView, canReview, canRelease)>`、`visibleMenuUris`、`visibleDeptKey`（個人 360 可視分行）、`employeeNo`、`employeeDeptCode` | 平台 `platform/security/domain/SecurityUser`、`definition/ModulePermission`、`Permission`（EDIT/VIEW/REVIEW/RELEASE） | **平台權限是「模組 × 動作旗標」，不是扁平權限碼** |
| 前端路由 `meta.requiredModule`（例：快取監控頁 `requiredModule: CACHE`） | frontend-instructions §18 | 頁面用模組 id 控管 |
| 角色碼 `ICRM_ROLE_0001`～`0015`（15 個），`@PreAuthorize` 用 Spring `ROLE_*` 字串 | reference.md 安全開發規範 §2 | 角色定義 |
| 業務模組取登入者一律注入 `platform/security/application/CurrentUserProvider`，不得自己解析 `SecurityContextHolder` | reference.md 安全守則 §4 | `CurrentUserPort` adapter 要用它 |
| 矛盾：§3-0-3 寫「`anyRequest().authenticated()`，方法層不標 `@PreAuthorize`（角色控管待規劃）」；安全守則 §4 寫「每個 API 必須 `@PreAuthorize`（deny by default）」 | reference.md | **以平台現有 controller 的實際寫法為準**，並回報給使用者 |

去平台找：`SecurityConfig`（有沒有 `@EnableMethodSecurity`、白名單）、`OAuth2AdfsUserService` 與 `DevAuthController`（`permissions`、`visibleMenuUris` 從哪裡組出來：EIAM API？代碼表 `COLL_MAINTENANCE_CODE`？臨時使用者 `COLL_MAINTENANCE_TEMPORARY_USER`？）、任何一個既有 controller 的授權寫法、前端 `use-auth-store` 的 `permissions` 型別與路由守衛如何讀 `requiredModule`。

### 2.3 對應方式【推論，對應表需使用者確認】

建議把 POC 的扁平權限碼對應到平台的模組權限，**轉換集中在平台 adapter 與 controller 的授權表示式**，小幫手的 application/domain 不改：

| POC 權限碼 | 平台模組 id（建議名稱） | 旗標 | 預計角色 |
|---|---|---|---|
| `AI_ASSISTANT_USE` | `AI_ASSISTANT` | `canView` | 理專、AO、各主管、查詢人員等（**【待決定】**也可以直接沿用個人 360 模組的 `canView`，因為小幫手只掛在個人 360 頁） |
| `AI_CONFIG_READ` | `AI_CONFIG` | `canView` | 後台管理人員 `ICRM_ROLE_0013`、IT 管理人員 `ICRM_ROLE_0014` |
| `AI_CONFIG_EDIT` | `AI_CONFIG` | `canEdit` | 同上 |
| `AI_CONFIG_PUBLISH` | `AI_CONFIG` | `canRelease` | 同上（Prompt 發布沒有覆核流程，`canReview` 不用） |
| `CUSTOMER_360_VIEW` | 平台個人 360 既有模組 | `canView` | 小幫手本身不檢查它，只有替身用 |

實作步驟：

1. **`CurrentUserPort` adapter**（平台端，例：`PlatformCurrentUserAdapter`）：從 `CurrentUserProvider.getCurrentUser().securityUser()` 轉成 `UserContext`：
   - `userId` = `employeeNo`（稽核、限流、回饋都用它；回饋 API 用 `userId` 定位稽核紀錄）
   - `displayName` = `displayName`
   - `roleKey` = 登入者的角色碼（多個時取一個代表值或以逗號串接，總長 ≤ 32；【待決定】）
   - `permissions` = 依上表從 `SecurityUser.permissions` **換算回** POC 的權限碼集合，這樣小幫手內部完全不用改
   - 未登入（`securityUser == null`）時丟 `AssistantException(AI_UNAUTHENTICATED)`，與 POC 的 `MockCurrentUserAdapter` 一致
2. **Controller 授權表示式**：把 `hasAuthority('AI_...')` 換成平台的寫法。若平台已有檢查模組權限的 bean（例如 `@PreAuthorize("@xxx.canView('AI_CONFIG')")`），照用；若沒有，【待決定】是新增一個共用 bean（放 `platform/security/`），還是在 `OAuth2AdfsUserService`/`DevAuthController` 把模組權限展開成 `GrantedAuthority`（會動到平台登入流程，影響較大，不建議）。
3. **確認 `@EnableMethodSecurity`**：平台沒開就要告知使用者，不要默默加（會讓平台其他寫了 `@PreAuthorize` 的地方開始生效）。
4. **`CustomerAccessPort` adapter**：重用平台個人 360 頁面「能不能查這位顧客」的同一套判斷（線索：`SecurityUser.visibleDeptKey`、顧客的分行／理專欄位、`coMarketingFlag` 共銷註記由 Service/Repository 過濾），不要把 POC 的 `MockScope` 規則搬過去。必須滿足：
   - `stopUseCustomerDataFlag = "Y"` 一律 `NONE`
   - 顧客不存在回 `NONE`（不要丟例外）
   - 平台若沒有「只能看部分資料」的角色，就只會回 `NONE`/`FULL`（M43）；`LIMITED` 機制保留
   - 特殊重要顧客（KYC A1/A2/A3/G1/G2、AML PEP）在個人 360 要先填查詢原因才可調閱，小幫手是否開放【待決定】（M45）；決定前建議回 `NONE`，最保守
5. **前端對齊**：前端小幫手用 `useAuthStore().hasPermission(code)` 判斷 3 處（`AI_ASSISTANT_USE`、`AI_CONFIG_EDIT`、`AI_CONFIG_PUBLISH`），Prompt 管理頁路由用 `meta: { permission: 'AI_CONFIG_READ' }`（見 `frontend/PORTING.md` §3、§4）。改成平台的 `permissions[模組].canXxx` 與 `requiredModule: AI_CONFIG`，模組 id 與後端一致。
6. **CSRF**：sit/uat/prod 平台啟用 CSRF，前端照平台 `apiClient` 的方式帶 token；後端不用為小幫手另外設定。POC 的 `CsrfCookieFilter` 不搬。
7. **401/403 回應格式**：平台前端 `apiClient` 的 401 會導 ADFS、403 導 `/forbidden`（frontend-instructions §4-1）。小幫手 controller 不要自己回 401/403，交給平台 security 與 `GlobalExceptionHandler`。

### 2.4 讓本機能看到畫面（解決目前卡住的問題）

目標：本機 `dev` profile 用 `dev-login` 登入後，`/api/auth/me` 回來的 `permissions`／`menuUris` 含小幫手的模組，前端個人 360 頁顯示小幫手、後台選單出現 Prompt 管理。

1. 找出 `DevAuthController` 組 `SecurityUser` 的方式，以及 `permissions`、`visibleMenuUris` 的資料來源。
2. 依來源加入 `AI_ASSISTANT`、`AI_CONFIG` 模組與 Prompt 管理頁的 menu URI：
   - 若來自代碼表／Mongo 設定 → 準備 dev 環境的初始資料（mongosh 腳本或平台既有的 dev seed 方式），**不要**改程式寫死
   - 若來自 EIAM 等外部系統 → dev 通常走 mock server（`icrm.mock.mockService`、`icrm.client.*` 在 dev 覆寫為 `http://localhost:9999/mock`），在 mock 回應加上模組
3. 確認 dev-login 使用的角色在前端白名單 `constants/role-code.ts` 內。
4. 個人 360 頁需要顧客資料：dev 的 `getCustomer`／`getRealTimeCustomer` 怎麼取得（mock server？`COLL_MANAGEMENT_CUSTOMER_SNAPSHOT_*`？）要一起確認，否則小幫手的工具會查不到資料。
5. 驗證：`dev-login` → `GET /api/auth/me` 看到模組 → 開個人 360 頁，小幫手可提問並回答 → 用沒有 `AI_CONFIG` 的角色登入，Prompt 管理頁與 API 都被擋（403）。

---

## 3. OpenAI API key 的接介方式

### 3.1 POC 目前的做法【已確認】

- **SDK**：LangChain4j 1.22.0（`langchain4j`、`langchain4j-open-ai`），底層 HTTP 是 JDK `HttpClient`。不用 Spring AI、不用 LangChain4j 的 Spring Boot starter。
- **唯一知道供應商的地方**：`infrastructure/llm/LlmConfiguration`，產生一個 `ChatModel` bean（`OpenAiChatModel`），其他程式只依賴 `ChatModel` 介面。每次請求的差異參數（JSON schema、`reasoning-effort`）由 `application/llm/LlmRequestParameters` 組。
- **認證**：OpenAI 標準 `Authorization: Bearer <api-key>`，key 來自 `ai-assistant.llm.api-key: ${OPENAI_API_KEY}`。
- **端點**：`ai-assistant.llm.base-url` 留空 = OpenAI 公開端點；填入即改走 **OpenAI 相容**的 AI Gateway（例：`https://gateway.example/v1`），認證方式不變。
- **key 的來源（POC 專用，要移除）**：`spring.config.import: optional:file:./.env[.properties]` 讀 `backend/.env`（已 gitignore）。
- **防外洩**：`AiAssistantProperties.Llm.toString()` 遮蔽 key；`logRequests(false)`/`logResponses(false)`（請求內容含 prompt 與顧客資料）；Actuator 只開 `health`；錯誤訊息不含 key。
- **呼叫行為**：`maxRetries(0)`（逾時、限流直接回報使用者，不重試）；`timeout 30s`；`maxCompletionTokens 1500`；`strictJsonSchema(true)`（回答是 JSON 區塊）；`temperature` 留空不傳（`gpt-6-luna` 只接受預設值）；`reasoning-effort: none`（帶工具時只接受 `none`）；路由與評測裁判可各自覆寫 `router-reasoning-effort`、`judge-reasoning-effort`。
- **日誌**：`OutboundLlmLogListener`（LangChain4j `ChatModelListener`）每次模型呼叫寫一行平台格式的 `Outbound` log（`target_system=OpenAI`、`purpose`=ROUTE/SUMMARY/STEP_ARGUMENTS/ANSWER/JUDGE、token 用量、耗時、錯誤類型），不記 prompt、回答、例外訊息。
- **評測**：Prompt 評測用同一把 key，每次約 45 萬 token（預算歸屬 M37、M38 待確認）。

### 3.2 平台規範（線索）

- 設定檔機敏資訊必須 Jasypt `ENC(...)`，演算法 `PBEWITHHMACSHA512ANDAES_256`，金鑰由環境變數／K8s Secret 注入（reference.md §7）。
- 範例同時存在兩種：OAuth2 `client-secret: ${OAUTH2_CLENT_SECRET}`、Redis `password: ${REDIS_PASSWORD}` 用**環境變數**；CSIP `app-key: ENC(...)` 用 **Jasypt**（reference.md §8-3）。
- OAuth2 等分環境的設定放 `application-{env}.yml`；dev 覆寫成本機 mock。
- 所有外部 HTTP 呼叫放 `core` 的 `com.org.icrm.client`，Port → Adapter → ApiClient → `RestClient`，方法標 `@OutboundLog`（reference.md §23）。

### 3.3 建議做法

1. **刪除** `spring.config.import` 的 `.env` 載入。
2. **key 注入**【待決定，建議 A】：
   - A：K8s Secret → 環境變數 `OPENAI_API_KEY`，yml 寫 `api-key: ${OPENAI_API_KEY}`（與 OAuth2 secret、Redis 密碼同一做法，repo 內完全沒有 key，連密文也沒有）。
   - B：`application-{env}.yml` 寫 `api-key: ENC(...)`（與 CSIP app-key 同一做法）。換 key 要重新加密並改設定檔。
   - 先看平台其他外部 API 的 key 實際用哪一種，跟著做。
   - dev：本機以 IDE run configuration 或 shell 環境變數提供 `OPENAI_API_KEY`；不要再用 `.env`，也不要把 dev key 寫進 `application-dev.yml`。
3. **設定位置**：`ai-assistant.llm.model-name`、`base-url` 依環境放 `application-{env}.yml`；其餘 `ai-assistant.*`（limits、eval、market.twse）放 base `application.yml`。
4. **LangChain4j 不改寫成平台 `RestClient` client**（屬於對 §23 的偏離，列入第 9 節）。理由：tool calling、JSON schema、token 計算都靠 LangChain4j；`LlmConfiguration` 已是唯一接觸點，`OutboundLlmLogListener` 已輸出平台格式的 Outbound log。LLM 呼叫**不要**寫進 `COLL_COMMON_FOREIGN_LOG` 或 `@ClientSnapshot`（內容含顧客資料）。
5. **端點型態**【待決定，M5】：
   - OpenAI 直連：`base-url` 留空。
   - 公司內部 OpenAI 相容 AI Gateway：只填 `base-url`。若 Gateway 要額外 header（例如平台 CSIP 的 `X-Timestamp`、client id），用 `OpenAiChatModel.builder().customHeaders(...)`，只改 `LlmConfiguration`。
   - Azure OpenAI 或其他認證方式：需要使用者提供端點與認證方式；可能要改用 `langchain4j-azure-open-ai`（新 jar，需匯入 lib-repo）或確認 Azure 的 OpenAI 相容端點能否用目前的 client，**實測後再定**。只改 `LlmConfiguration` 與 `LlmRequestParameters`。
6. **對外連線**：pod 要能連到 `api.openai.com:443`（或 Gateway）與 `openapi.twse.com.tw:443`。兩個 client 都沒有設 proxy；平台若要走 proxy，用 JVM 參數 `https.proxyHost`/`https.proxyPort` 並實測兩條連線。Nginx/Ingress 的 `proxy_read_timeout` 要 ≥ 150 秒（單次對話後端上限 60 秒 + 進行中 GPT 呼叫最多 30 秒，前端最多等 150 秒）。
7. **驗證**：啟動 log 與 `/actuator/*` 都看不到 key；故意給錯 key，對話回錯誤碼、`Outbound` log 有 `status_code=401` 且沒有 key；正確 key 能完成一次對話並看到 token 用量。

---

## 4. 排程：抽離到平台 `scheduler/`

### 4.1 POC 目前的做法【已確認】

- 只有一個排程：`infrastructure/scheduler/ScheduledPublishJob`，`@Scheduled(fixedDelayString = "${ai-assistant.config.schedule-check-interval}")`，每 30 秒一次；`@EnableScheduling` 寫在 `CopilotApplication`（不會跟著搬）；`ai-assistant.config.scheduler-enabled`（測試設 false）控制是否註冊。
- 做的事：Job 先呼叫 `ConfigCommandUseCase.findScheduledDue()`，再**逐筆**呼叫 `ConfigCommandUseCase.publishScheduled(due)`（每筆各自一個交易，一筆失敗不影響其他筆）。流程：
  1. `findScheduledDue()` → `repository.findScheduledDue(now)`：找 `status = SCHEDULED` 且 `scheduledAt <= now` 的版本（index `IX_ICRM_AI_CFG_5`，partial）
  2. 對每個版本重跑 `ConfigValidator.validate(...)`（規則或 `allowed-url-hosts` 可能在預約後改變）
  3. 不通過 → `revertScheduled`（結果 `ScheduledPublishOutcome.REVERTED`；已被別人改過則 `SKIPPED`）：條件更新（仍是 SCHEDULED 且 `docVersion` 相同）改回 DRAFT、清掉 `scheduledAt`、`updatedBy = "system"`
  4. 通過 → `repository.publishScheduled`：在 UseCase 的 **MongoDB transaction**（`@Transactional(transactionManager = Transactions.MONGO)`）內把同 `configKey` 的 PUBLISHED 改 ARCHIVED，再以條件更新把這版改 PUBLISHED，結果 `PUBLISHED`；搶輸（被別的執行個體先發布、或被使用者改過）以 `AI_CONFIG_CONFLICT` 丟出，Job 記 debug 並略過，不算錯
  5. 發布後 UseCase 送出 `ConfigPublishedEvent`，`PublishedConfigCache` 在 `@TransactionalEventListener(AFTER_COMMIT)` 清快取（只清**本程序**的快取）
- 多執行個體同時跑是安全的（條件更新 + transaction + unique index `UK_ICRM_AI_CFG_2`：同一 `configKey` 只能有一個 PUBLISHED）。
- 讀取端：`PublishedConfigCache` 每個程序快取 60 秒，所以別的程序發布後，最多 60 秒才看到新版。
- 其他背景工作：`EvalRunner`（Prompt 評測）是收到請求後在單一 daemon 執行緒跑數分鐘，**不是排程**，這次不搬（4.4）。

### 4.2 平台規範（線索）

- 排程用 Serverless SCP CronJob，**不使用 Spring Batch**；`scheduler/` 放批次入口、批次流程、BatchLog；透過 `core` 使用共用 persistence；**禁止反向依賴 `backend`**、禁止把 controller 或共用 persistence 實作放進排程模組（reference.md §1-2）。
- `core/` 放 backend 與 scheduler 共用的 persistence model/repository、共用 Mongo document/repository；**禁止放單一 bounded context 的 use case**。
- 日誌：`@SchedulerLog` + `SchedulerLogAspect` → `BatchLog` logger 與 `COLL_SCHEDULER_EXECUTE_LOG`（`jobName`、`arguments`、`executionStatus`、`exitCode`、`startTime`…）；`log.error` 只能在 `SchedulerProcessor` 統一出口，且必須帶 job 參數（reference.md §6-3、§6-4，db-instructions §11）。
- 既有 Job 範例：`RefreshHrDataJob`、`RefreshCustomerProfileService`、`RefreshSegmentProfileService`（db-instructions §4、§6-1）。**先打開其中一個，照它的結構寫**：入口類別、參數、exit code、`@SchedulerLog` 掛法、CronJob 的 cron 設定檔在哪裡（K8s manifest／Helm／SCP 設定）。

### 4.3 建議做法

**依賴問題**：`publishScheduled` 用到 `AssistantConfigRepository`（實作在 backend infrastructure）、`ConfigValidator`（domain，純 Java）、`AiAssistantProperties.Config.allowedUrlHosts`（驗證規則用）。scheduler 不能依賴 backend，所以要把**共用的部分下沉到 `core`**。

【待決定】三個方案，建議 A：

| 方案 | 做法 | 優點 | 缺點 |
|---|---|---|---|
| **A（建議）** | `core` 放：`AssistantConfigDocument`（+ `AssistantConfigDocumentMapper`，其對應目標 `AssistantConfig`、`ConfigKey` 一併下沉；`PromptVersionDocument` 屬稽核紀錄，不需要）、`AssistantException`／`ErrorCode`、`MongoConflicts`、`AiAssistantCollections` 的設定集合名稱、以 `MongoTemplate` + `Criteria` 實作的預約發布查詢與更新（POC 已不用 Spring Data 的 `AssistantConfigMongoRepository` 介面）、預約發布用的三個持久化操作（`findScheduledDue`、`publishScheduled`、`revertScheduled`；claim 是條件更新，交易由呼叫端提供）、`ConfigValidator` 及它依賴的 domain 型別（`ConfigType`、`ConfigStatus`、`ValidationError`…）。`scheduler/` 新增 Job 呼叫它們（Job 的 `isLostRace` 判斷——只把 112 與 11000 視為輸掉競爭、其餘為 WARN——要隨 Job 一起搬，因為 backend 的 `ConfigTransactionConflictAspect` 不會套用在 scheduler）；`backend` 的 `AssistantConfigRepositoryImpl` 改委派 core 的實作 | 符合「共用 persistence 放 core、scheduler 只依賴 core」；行為與 POC 完全相同（含到期重驗） | `ConfigValidator` 是小幫手的領域規則，放進 core 有爭議（core 禁止的是 use case，驗證規則屬灰色地帶），需使用者同意 |
| B | 同 A，但 `ConfigValidator` 留在 backend；scheduler 到期**不重驗**，直接發布 | core 只放 persistence，最乾淨 | 行為改變：預約後若改了 `allowed-url-hosts` 等規則，不合規的版本仍會被發布；需同時改規格 04 §2.4 |
| C | CronJob 只呼叫 backend 的內部 endpoint（例：`POST /internal/ai-assistant/configs/publish-due`），邏輯留在 backend | 程式改動最小 | 要做服務間認證與白名單；違反「批次流程放 scheduler」的精神；backend 全部 pod 忙碌時排程也受影響 |

方案 A 的步驟：

1. `core`：建立小幫手共用 persistence 套件（依 core 既有命名，例如 `persistence/mongodb/aiassistant/`），搬入上表項目。`allowedUrlHosts` 不要讓 core 讀 `ai-assistant.*`，改由呼叫端（backend service、scheduler Job）把設定值傳進 `ConfigValidator`。
2. `backend`：`AssistantConfigRepositoryImpl` 改委派 core 的實作，`ConfigCommandUseCase` 行為不變（POC 已把 `publishDue` 拆成 Job 的迴圈加 `ConfigCommandUseCase.publishScheduled`，交易在 UseCase 上；scheduler Job 沿用「逐筆、每筆一個交易」的切法，交易提供方式依平台 scheduler 慣例，須讓 ARCHIVED＋PUBLISHED 兩步原子執行）；**刪除** `ScheduledPublishJob`、`ai-assistant.config.scheduler-enabled`、`schedule-check-interval` 與測試中對 `scheduler-enabled` 的設定（`IntegrationTestBase`）。`schedule-min-lead`、`schedule-max-lead` 留在 backend（API 驗證預約時間用）。
3. `scheduler`：新增 Job（建議名 `PublishScheduledAssistantConfigJob`），照既有 Job 範本掛 `@SchedulerLog`；流程照 4.1 的 1～4 步（逐筆處理、每筆一個交易）；回傳／exit code 依平台慣例（有個別版本失敗時，記錄 configKey、version，是否算整體失敗【待決定】，建議「全部處理完、有失敗才算失敗」）。POC 自己設 MDC traceId 的程式（`RequestContext`）改用平台 scheduler 的 traceId 機制。
4. `scheduler` 的 `application-{env}.yml` 要有 `ai-assistant.config.allowed-url-hosts`，**與 backend 同一份值**（兩邊不一致會讓「存檔時通過、到期時被退回」）。
5. CronJob 設定：照平台既有 Job 的設定方式新增排程。**頻率**：K8s 類 CronJob 最小粒度通常是 1 分鐘（以平台實際為準），加上 serverless 冷啟動，實際發布會比預約時間晚 1～2 分鐘；再加上 backend 各 pod 的 `PublishedConfigCache` 最多 60 秒，**使用者看到新版最多晚約 3 分鐘**。請使用者確認可接受；前端預約畫面的說明文字要同步（目前預約下限 1 分鐘）。
6. 重疊執行：上一輪還沒跑完下一輪就開始時，POC 的條件更新已保證同一版本只發布一次，可以不另外加鎖；若平台 CronJob 有 `concurrencyPolicy: Forbid` 之類設定，照平台慣例。
7. 驗證：在 dev 預約 2 分鐘後發布 → 手動觸發／等 CronJob → `COLL_ICRM_AI_ASSISTANT_CONFIG` 該版變 PUBLISHED、舊版變 ARCHIVED、`COLL_SCHEDULER_EXECUTE_LOG` 有一筆 SUCCEEDED；預約一個到期時會驗證失敗的版本 → 被改回 DRAFT；同時跑兩個 Job 實例 → 只發布一次。

### 4.4 不搬進 scheduler 的背景工作（列出供決定）

| 元件 | 現況 | 多 pod 影響 | 建議 |
|---|---|---|---|
| `EvalRunner`（Prompt 評測） | 收到請求的 pod 以單一 daemon 執行緒跑數分鐘；「同時只有一個」由 unique index `UK_ICRM_AI_EVAL_1` 保證 | 唯一性仍成立；該 pod 重啟會中斷，約 10 分鐘後標為「已中斷」 | 先留在 backend（管理者偶爾手動執行）；若不可接受，再改成 scheduler 的 on-demand Job |
| `PublishedConfigCache` | 每個 pod 快取 60 秒 | 發布後最多 60 秒內不同 pod 用不同版本 | 可接受就維持；不行就改用平台 Redis L2 Cache 或發布時通知各 pod |
| `PerUserRateLimiter` | 程序記憶體，每人每分鐘 10 次 | 實際上限變成 pod 數 × 10 | 平台有限流機制就沿用；沒有就改 Redis 計數（S17） |

---

## 5. 資料庫

### 5.1 POC 目前的做法【已確認】

- MongoDB 8，本機 docker compose **單節點 replica set**（`rs0`），URI `mongodb://localhost:27017/copilot?replicaSet=rs0`。要 replica set 是因為設定 collection 的存檔與發布用 **transaction**。
- `infrastructure/mongo/MongoConfiguration` 宣告具名的 `MongoTransactionManager` bean（`aiAssistantMongoTransactionManager`，常數 `application/support/Transactions.MONGO`）；交易只在 `ConfigCommandUseCase` 的 `publish`、`applyVersion`、`publishScheduled` 三個方法上以 `@Transactional(transactionManager = Transactions.MONGO)` 宣告（`ChatUseCase`、`FeedbackCommandUseCase`、`EvalRunCommandUseCase` 沒有交易），`TransactionTemplate` 已移除；`ConfigTransactionConflictAspect` 把交易衝突轉成 `AI_CONFIG_CONFLICT`。
- 3 個 collection：

| Collection | 用途 | 量 | TTL | 寫入方式 |
|---|---|---|---|---|
| `COLL_ICRM_AI_ASSISTANT_CONFIG` | System Prompt 與 7 個 System Instruction 的所有版本 | 數十～數百筆 | 無 | `@Document` 類別 `AssistantConfigDocument` + `MongoTemplate` 條件更新 + UseCase 上的 transaction；樂觀鎖欄位 `docVersion`（`version` 是業務版本號） |
| `COLL_ICRM_AI_ASSISTANT_AUDIT` | 稽核：每次提問 1 筆（提問、回答、工具呼叫、token、回饋） | 全行開放後每天可達 20 萬筆 | `createdAt` 1,825 天（`IX_ICRM_AI_AUDIT_TTL`，對齊平台 `COLL_COMMON_AUDIT_LOG` 5 年；M4 待法遵確認） | 只新增；回饋只更新 `feedback`、`feedbackAt`，條件含 `userId` + `traceId` |
| `COLL_ICRM_AI_ASSISTANT_EVAL_RUN` | Prompt 評測，每次 1 筆（題目結果嵌入，最大約 1.3 MB） | 180 天內數百筆 | `startedAt` 180 天（`IX_ICRM_AI_EVAL_TTL`） | `@Document` 類別 `EvalRunDocument` + MapStruct mapper |

- 13 個 index（CONFIG 5、AUDIT 5、EVAL_RUN 3）：
  - CONFIG：`UK_ICRM_AI_CFG_1`（`configKey, version` unique）、`UK_ICRM_AI_CFG_2`（`configKey` unique，partial `status=PUBLISHED`）、`UK_ICRM_AI_CFG_3`（`configKey` unique，partial `status=DRAFT`）、`IX_ICRM_AI_CFG_4`（`status, configType`）、`IX_ICRM_AI_CFG_5`（`status, scheduledAt`，partial `status=SCHEDULED`）
  - AUDIT：`IX_ICRM_AI_AUDIT_TTL`、`IX_ICRM_AI_AUDIT_2`（`userId, createdAt:-1`）、`IX_ICRM_AI_AUDIT_4`（`traceId`）、`IX_ICRM_AI_AUDIT_5`（`promptVersions.configKey, promptVersions.version`，partial `feedback` 為字串）＋ `_id`
  - EVAL_RUN：`UK_ICRM_AI_EVAL_1`（`status` unique，partial `status=RUNNING`：同時只有一個評測）、`IX_ICRM_AI_EVAL_1`（`configKey, startedAt:-1`）、`IX_ICRM_AI_EVAL_TTL`
- 建立方式：啟動時 `MongoSchemaInitializer`（`ApplicationRunner`）冪等建立 collection、套 validator（`resources/mongo/*-validator.json`）、建 index、刪舊名稱 index（`IX_ICRM_AI_AUDIT_1`、`IX_ICRM_AI_EVAL_2`）。
- 初始資料：`infrastructure/seed/SeedLoader`（`ApplicationRunner`）在 CONFIG 為空時，把 `resources/seed/manifest.json` 列的 8 份內容寫成已發布的 v1。**多個 pod 同時首次啟動時可能互相衝突**（只有第一個成功，其餘因 unique index 失敗）。

### 5.2 平台規範（線索）與衝突點

| 平台規範 | 出處 | 與 POC 的差異 → 處理 |
|---|---|---|
| Sharding、Replica Set、JSON Schema Validation 由 DBA 管理，不在應用程式內設定 | db-instructions 開頭 | POC 啟動時套 validator → **移除**，validator JSON 交給 DBA |
| `auto-index-creation` 未啟用；index 由 owner 在 `@PostConstruct` 以 `indexOps(...).createIndex(...)` 建立，或由 DBA 以 mongosh 建立；正式環境在維護時段建 | db-instructions §6-1 | 【待決定】index 改由 DBA 建，或保留程式建立但改成平台 owner 寫法（`@PostConstruct`）。改名 index 的遷移照 §11：先刪舊名稱再建新名稱 |
| Collection 命名 `COLL_<模組>_<描述>`，模組只有 `MANAGEMENT`/`MARKETING`/`MAINTENANCE`/`COMMON`/`SCHEDULER`；名稱集中為常數 | db-instructions §2 | POC 用 SP 計畫指定的 `COLL_ICRM_AI_ASSISTANT_*`。【待決定】（S10）維持，或改名（例：稽核 `COLL_MANAGEMENT_AI_ASSISTANT_AUDIT`、設定與評測 `COLL_MAINTENANCE_AI_ASSISTANT_CONFIG`/`_EVAL_RUN`）。**上線前定案**，之後改名要搬資料。POC 已集中在 `infrastructure/mongo/AiAssistantCollections`，改名只動此類別與 validator 檔名 |
| 新 collection 要登記到 db-instructions §4、§6-2、§11 的表 | db-instructions §4 | 平移完成時補登記（3 個 collection、13 個 index、2 個 TTL） |
| 並行更新的 document 要有 `@Version Long version` | db-instructions §3、§15 | POC 樂觀鎖叫 `docVersion`，因為 `version` 已是業務版本號（M23）。**維持 `docVersion`**，列為偏離 |
| Document 不寫 `_class`（`DefaultMongoTypeMapper(null)`）；實作 `BaseNumberEnum` 的 enum 存 Integer，其餘存名稱 | db-instructions §8 | POC 已比照平台全域不寫 `_class`（`MongoConfiguration` 的 static `BeanPostProcessor` 設 `DefaultMongoTypeMapper(null)`；平台已有同設定時刪除此 bean）。POC enum（`ConfigStatus`、`ConfigType`…）存名稱，沒有實作 `BaseNumberEnum`，相容；確認平台的 converter 設定套用後，讀寫結果不變（跑整合測試） |
| `MongoTemplate` + `Criteria`，禁止字串拼接；Aggregation 首 stage 為索引 `$match` | db-instructions §9 | POC 已遵守；`FeedbackStatsQueryRepositoryImpl` 的 aggregation 首 stage 確認有用到 `IX_ICRM_AI_AUDIT_5` |
| PostgreSQL / EDB 也在平台中（JDBC） | reference.md 技術決策 | **重要**：平台若已有 JDBC 的 `DataSourceTransactionManager`，再加一個 `MongoTransactionManager` bean 會讓不帶 qualifier 的 `@Transactional` 出現 `NoUniqueBeanDefinitionException`。POC 已用具名、非 `@Primary` 的 bean `aiAssistantMongoTransactionManager`，UseCase 以 `Transactions.MONGO` 指定。先查平台有沒有 Mongo 的 transaction manager：有就把 `Transactions.MONGO` 改成平台 bean 名稱，並刪除 POC 的 bean；沒有就沿用 POC 的具名 bean |

### 5.3 連線與環境

1. 刪除 POC 的 `spring.mongodb.uri` 與 `.env`，改用平台既有的 MongoDB 連線設定（屬性名稱以平台為準；reference.md 的 dev 範例是 `spring.data.mongodb.uri`，與 POC 用的 Boot 4 寫法 `spring.mongodb.uri` 不同，以平台實際生效的為準）。資料庫名稱也跟平台（例：`icrm_dev`）。
2. **transaction 需要 replica set 或 mongos**。平台 dev 範例 URI 沒有 `replicaSet` 參數；若本機平台 MongoDB 是 standalone，Prompt 管理的存檔／發布會失敗（`Transaction numbers are only allowed on a replica set member or mongos`）。先確認平台本機 MongoDB 的型態；必要時照 POC 的 docker compose 用單節點 replica set。sit/uat/prod 透過 mongos 連線則沒問題。
3. 應用程式帳號只需要這 3 個 collection 的 `readWrite`（index、validator 改由 DBA 建立時）。

### 5.4 建立方式（取代 `MongoSchemaInitializer`、`SeedLoader`）

1. 產出給 DBA 的 mongosh 腳本：建 3 個 collection（含 validator，`validationLevel: strict`、`validationAction: error`；AUDIT 只設 validator）、13 個 index（名稱與 key 照 5.1）、刪舊名稱 index。放在平台存放 DB 腳本的位置（先找平台既有的 migration／DBA 腳本目錄）。
2. `MongoSchemaInitializer`：依 5.2 的決定刪除，或縮減成只建 index 的 `@PostConstruct` owner。
3. `SeedLoader`：【待決定】改成 DBA 初始資料腳本（內容從 `resources/seed/*.md` 產生），或保留程式但處理多 pod 同時首次啟動的衝突（例如捕捉 duplicate key 視為已完成）。建議改腳本，與「DB 由 DBA 管理」一致。
4. 驗證：在全新 DB 跑腳本 → 啟動 backend → Prompt 管理頁看到 8 份已發布設定；`db.<coll>.getIndexes()` 與 5.1 一致；`explain()` 確認主要查詢走 IXSCAN（找 PUBLISHED、找到期 SCHEDULED、依 `userId` 查稽核、依 `traceId` 更新回饋、評測依 `configKey` 列表）。

### 5.5 Sharding 推論

平台 MongoDB 8.0 叢集使用 Hash Sharding；db-instructions 只規定「1,000 萬筆規模的顧客核心 collection 必須 hash sharding」，sharding 由 DBA 執行。小幫手 3 個 collection 的判斷：

**容量估算（假設 1 萬人、每人每天 20 題、每筆 2 KB；POC 實測平均 0.8 KB、最大 1.1 KB）**

| 稽核保存期限 | 筆數 | 資料量（含 index） | 是否需要 shard |
|---|---|---|---|
| 180 天 | 約 3,600 萬 | 約 80 GB | 不一定 |
| 1 年 | 約 7,300 萬 | 約 160 GB | 不一定 |
| 5 年（目前 TTL） | 約 3.6 億 | 約 0.8 TB | **建議 shard** |

- 寫入平均每秒約 7 筆、尖峰數十筆，壓力在累積量，不在寫入速度。
- 若開啟 `ai-assistant.audit.store-tool-results`（預設關閉，保存工具回傳的顧客資料），每筆可能到 10～20 KB，資料量放大 5～10 倍，而且開啟前需要法遵同意（M36）。

**各 collection 建議**

| Collection | 建議 | 推論依據 |
|---|---|---|
| `..._AUDIT` | 上線前就以 **`{ userId: "hashed" }`** shard（量已達 1,000 萬級，符合平台「1,000 萬筆必須 hash sharding」的精神） | 沒有 unique index，現有 index 與 validator 都可保留；寫入分散；回饋更新的條件含 `userId`，可定位到單一 shard；依使用者查詢只查一個 shard；單一使用者 5 年約 3.6 萬筆、不會形成 jumbo chunk；TTL 在 sharded collection 照常運作。不要用 `{ createdAt: 1 }`（遞增鍵，新資料全進同一個 shard）；`{ _id: "hashed" }` 也可行但依使用者查詢會 scatter-gather。上線後才 shard 雖可用 `reshardCollection`，但量大時成本高，**建議在空 collection 時就 shard** |
| `..._CONFIG` | **不 shard** | 量極小；依賴 3 個以 `configKey` 開頭的 unique index 與 transaction。若 DBA 規定一律 shard，唯一可行的方向是以 `configKey` 為 shard key（三個 unique index 都以它開頭）；hashed shard key 搭配這些 unique／partial unique index 是否被接受，**需 DBA 實測**，不要自行假設 |
| `..._EVAL_RUN` | **不 shard** | 量小（數百筆）；`UK_ICRM_AI_EVAL_1`（`status` unique）無法以 shard key 開頭，shard 後「同時只有一個評測」的保證會失效。若被要求 shard，要先把這個保證搬到別處（例如未 shard 的鎖文件） |

- 未 shard 的 collection 在 sharded cluster 中放在該資料庫的 primary shard，應用程式一樣透過 mongos 存取，程式不用改。
- `sh.shardCollection(...)` 寫進給 DBA 的腳本，與建 index 同一份，**順序：建 collection → 建 shard key 需要的 index → shardCollection → 其餘 index**。
- 若之後要在伺服器保存對話紀錄供使用者回看，那個 collection 量會比稽核大，而且幾乎只查自己的對話，一開始就用 `{ userId: "hashed" }`。
- 以上是推論，最後由 DBA 與使用者確認（S18、M25、M36）。

---

## 6. 其他必須對齊平台的項目

| 項目 | POC | 平台規範 | 處理 |
|---|---|---|---|
| 套件 | `com.example.copilot.aiassistant` | `com.org.icrm...`，四層 `domain/application/interfaces/infrastructure`，禁止 `app/` | 改套件名；分層已對齊（2026-11-02），`application/command/`、`application/query/` 已建立（2026-11-12），不用重排 |
| 回應格式 | 成功回 DTO；錯誤回 `ApiError`（`AssistantExceptionHandler`、`ErrorCode` AI_* 搭配 HTTP 404/409/422/429…） | 一律 `ResponseEntity<ApiResponse<T>>`（`success`、`errorMessage`、`data`、`serviceCode`、`traceId`、`timestamp`）；業務錯誤 `DomainException` → HTTP 200 + `serviceCode=2003`；controller 無 try-catch | **影響前端**，先和使用者確認改法（建議：`AssistantException` 對應到 `DomainException` 語意，`errorMessage` 用 `ErrorCode` 的中文訊息；前端改讀 `ApiResponse`）。前後端要同一個 commit 範圍內一起改 |
| 稽核 | 自己的 `COLL_ICRM_AI_ASSISTANT_AUDIT` | 每個 REST method 必須 `@AuditLog(module=AuditModule.XXX)`，`capturePayload` 預設 false（不存 body） | 兩者並存：controller 補 `@AuditLog`（不開 `capturePayload`，提問與回答不能進平台稽核），小幫手稽核 collection 照舊；`AuditModule` 新增值【待決定】 |
| Command 側 | 已標 `@UseCase`（`com.example.shared.annotation.UseCase`，平台副本）、放 `application/command/`；`@Transactional(transactionManager = Transactions.MONGO)` 已在 `ConfigCommandUseCase` 的 `publish`／`applyVersion`／`publishScheduled` | `@UseCase` 標註、放 `application/command/`；`@Transactional` 只在 UseCase/ApplicationService | 換成平台正本的 `@UseCase` import 即可；`Transactions.MONGO` 依 5.2 處理 |
| Log | `log.warn` 為主；`Outbound` logger 由 listener 輸出 | `log.error` 只能在統一出口（`InboundLogAspect`、OAuth2 failureHandler、`SchedulerProcessor`）；同一失敗只記一處；第三方 UAT/PROD = ERROR | 掃一遍 `log.error`，不在統一出口的改掉或移除；`OutboundLlmLogListener` 的 `OUTBOUND.error` 等同外部呼叫統一出口，保留並列入第 9 節 |
| TraceId | 用平台 `TraceIdFilter` 的 `traceId`（MDC）；回饋 API 只接受 16–32 碼小寫十六進位 | 同一個 filter，`X-B3-TraceId` 原樣沿用 | 確認 gateway 是否帶入或改寫 `X-B3-TraceId`（S19），格式不符會讓回饋 API 全部 400 |
| CheckStyle | 未檢查 | `JavadocType`/`JavadocMethod`/`JavadocVariable`（所有欄位）強制，`failsOnError=true` | 最後一個階段補 Javadoc；禁止 `@author`、`@date` |
| 依賴 | `pom.xml` properties 已與平台 root pom 對齊 | — | 只需加入 `langchain4j`、`langchain4j-open-ai`；其餘平台已有。`spring-boot-starter-aspectj`、`validation` 確認 backend 模組有引入 |
| `certNo` 格式 | `CertNo.PATTERN` 是唯一來源（POC 假設的身分證格式），所有 chat／suggestions 請求與評測資料集驗證（`EvalDatasetLoader`）都用它；`PiiRedactor`、`ConfigValidator` 的顧客編號規則同為此假設 | 平台實際格式（含外籍、統編等，M48） | 先改 `CertNo.PATTERN`，再逐一確認 `PiiRedactor`、`ConfigValidator` 並補測試 |
| 評測假顧客 | `eval-cases.yml` 綁 POC 的假顧客（`A123456789` 等） | 平台需提供專用假顧客（不得用真實顧客） | 換 `certNo` 與題目中的姓名、金額等內容；沒有假顧客前，評測功能先不開放 |

---

## 7. 建議執行順序

每個階段結束：建置驗證 → 回報結果 → 列 `git add` 清單與 commit message → 等使用者確認再進下一階段。

1. **盤點**（只讀不改）：依第 2.2、3.2、4.2、5.2 節的線索，找到平台對應程式與設定，把找到的路徑與結論回報；同時把第 8 節的【待決定】一次問完。
2. **搬程式**：`aiassistant` 進 backend（改套件、改 import 指向平台正本、不搬替身），加入 LangChain4j 依賴；先讓 `mvn -pl backend -am compile` 通過（port 先用暫時的 stub adapter 也可以，但要列出來）。
3. **權限與 port adapter**（第 2 節）：`CurrentUserPort`、`CustomerAccessPort`、`CustomerDataPort` 平台實作；controller 授權表示式；dev-login 權限資料。**完成後本機應該可以看到畫面**（2.4 的驗證）。
4. **OpenAI 設定**（第 3 節）。
5. **資料庫**（第 5 節）：DBA 腳本、移除啟動建表／seed、transaction manager、連線設定。
6. **排程**（第 4 節）：core 下沉、scheduler Job、移除 backend 排程。
7. **規範對齊**（第 6 節）：回應格式（連同前端）、`@AuditLog`、log、CheckStyle。
8. **測試**：搬測試、改替身相關測試，`mvn test` 全過；本機手動跑一輪：提問、回饋、Prompt 存檔／發布／預約發布、評測。

---

## 8. 需要使用者決定的事項（彙整）

| # | 問題 | 建議 |
|---|---|---|
| D1 | 小幫手 bounded context 的名稱與位置 | backend 內新 BC `aiassistant` |
| D2 | 權限模組 id 與旗標對應（2.3 表）；`AI_ASSISTANT_USE` 要獨立模組還是沿用個人 360 的 `canView` | 獨立 `AI_ASSISTANT` + `AI_CONFIG` |
| D3 | 授權表示式寫法（平台無現成 bean 時） | 新增共用模組權限檢查 bean |
| D4 | `roleKey` 取法（多角色時） | 取權限最高的一個角色碼 |
| D5 | 特殊重要顧客能否使用小幫手（M45） | 決定前回 `NONE` |
| D6 | OpenAI key 注入：環境變數或 Jasypt `ENC` | 跟平台其他外部 key 一致；無前例時用環境變數 |
| D7 | GPT 端點：OpenAI 直連／內部 Gateway／Azure（M5） | 依公司規定 |
| D8 | 排程方案 A/B/C；發布延遲最多約 3 分鐘可否接受 | A |
| D9 | Collection 前綴維持 `COLL_ICRM_` 或改模組前綴（S10） | 上線前定案 |
| D10 | Index 由 DBA 建，或程式 owner 建 | DBA（與 validator 一致） |
| D11 | 初始 Prompt 用腳本或程式 seed | 腳本 |
| D12 | 稽核 collection 上線前即 shard（`{ userId: "hashed" }`）；保存期限（M4） | 上線前 shard |
| D13 | 錯誤回應改為平台 `ApiResponse` 的方式（影響前端） | 見第 6 節 |
| D14 | `AuditModule` 新增值 | 依平台命名 |

---

## 9. 對平台規範的偏離（平移後仍保留，需記錄）

| 偏離 | 規範 | 理由 |
|---|---|---|
| LLM 呼叫用 LangChain4j（JDK HttpClient），不放 `core/client`、不走 `RestClient`、不標 `@OutboundLog` | reference.md §23 | tool calling、JSON schema 依賴 LangChain4j；`OutboundLlmLogListener` 輸出同格式 Outbound log；不寫 foreign log 以免存到顧客資料 |
| 樂觀鎖欄位名 `docVersion` | db-instructions §3（`@Version Long version`） | `version` 是 Prompt 的業務版本號 |
| 小幫手另有稽核 collection | reference.md §3-0-6 | 欄位多（工具呼叫、token、Prompt 版本、回饋），且提問與回答不能進平台稽核的 payload |
| `OutboundLlmLogListener` 以 `Outbound` logger 輸出 ERROR | reference.md §6-3（log.error 統一出口） | 等同外部呼叫的統一出口 |
| （若採方案 A）`ConfigValidator` 放 core | reference.md §1-2 | scheduler 到期重驗需要同一套規則 |
| （若平台沒有 Mongo 交易管理器）具名 bean `aiAssistantMongoTransactionManager` 與 `Transactions.MONGO` | reference.md 技術決策（交易沿用平台既有設定） | 平台有 JDBC 交易管理器，不具名會造成 `NoUniqueBeanDefinitionException`；平台有 Mongo 的就改用平台 bean，本列刪除 |
| （若 collection 前綴維持）`COLL_ICRM_AI_ASSISTANT_*` | db-instructions §2 | SP 計畫指定 |

> 平移過程中發現其他偏離，追加到這張表，並在階段回報中說明。
