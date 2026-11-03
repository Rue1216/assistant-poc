# Prompt 管理 review 修正設計

- 日期：2026-11-02
- 版本：v0.2
- 狀態：待審
- 起因：2026-11-02 對 Prompt 管理的 API endpoint、錯誤碼與 Mongo schema 做架構 review，挑出需要修正的項目。
- 前提：[Clean Architecture 分層對齊](2026-11-02-clean-architecture-layering-design.md) 已完成（commit `bef958c`–`660eea2`）。本次修正寫在分層後的結構上，各項的落點見第 9 節，且必須維持 `ArchitectureRulesTest` 全綠。
- v0.2（2026-11-03）：依重構後的程式調整 4.1（錯誤細節的型別放在 domain，時區轉換留在 interfaces 層）、4.2（重複 key 的判斷方式）、3.2（到期退回的落點），並以第 9 節「實作落點」取代原本的「與分層重構的順序」。
- 相關規格：[01-architecture.md](../../spec/01-architecture.md) 套件結構、[02-rest-api.md](../../spec/02-rest-api.md) 3、[04-mongodb-schema.md](../../spec/04-mongodb-schema.md) 2、[05-error-codes.md](../../spec/05-error-codes.md) 1、2.3

## 1. 範圍與決策

| 項目 | 決策 | 節 |
| --- | --- | --- |
| 編輯與發布權限的不一致 | 不改程式。M26 已確認後台管理人員與 IT 人員都同時有編輯與發布權限；只在 02 §3 記下這個前提 | 7 |
| 名稱與修改說明的禁止內容 | 每次儲存都檢查，不通過回 422；內容維持只在發布前檢查 | 2.1 |
| 立即發布已排程的版本 | 清空 `scheduledAt` | 3.1 |
| 排程到期 | 先重新驗證；不通過就退回 `DRAFT` | 3.2 |
| 錯誤 `details` | 改為具型別的 record，JSON 格式不變 | 4.1 |
| System Prompt 的 key | 只能是 `system.main`；資料庫 validator 也檢查類型與 key 前綴一致 | 4.2 |
| 套用版本的回應碼 | 200 改為 201，與其他建立型端點一致 | 4.3 |
| 建立草稿的來源 | 也記錄 `basedOnVersion`；版本歷程文字改為「以 X 的版本為底」 | 4.4 |
| 設定列表的查詢 | 改為三個以索引支撐的步驟，成本只與 key 數量有關 | 5.1 |
| `content` 上限的單位 | 規格說明 API 以 UTF-8 bytes、Mongo 以字元計算 | 5.3 |

不做的事：

- 不拆分編輯與發布權限的程式檢查（見第 7 節的前提說明）。
- 不讓前端跳過 `AI_CONFIG_OVERWRITE_PENDING` 的第一趟請求；目前流程正確，只是多一次往返。
- 201 回應不加 `Location` header：回應本體已有 `configKey` 與 `version`，前端用不到。
- 初始資料在多台機器同時首次啟動時會撞索引，這次只在 04 §2.7 註明，不改程式；正式環境改用 migration。
- 不改評測相關端點（02 §3.12–3.16），只有 `AI_EVAL_RUNNING` 的 `details` 跟著 4.1 換成具型別的 record。

## 2. 驗證

### 2.1 名稱與修改說明在儲存時檢查

- `PUT .../draft` 在任何狀態、任何 `saveMode` 下，都對 `name` 與 `changeNote` 套用 04 §2.5 的禁止內容規則（`FORBIDDEN_CONTENT`）。不通過回 422 `AI_CONFIG_VALIDATION_FAILED`，`details` 格式同 02 §3.8 的 `errors`。
- `content` 維持現狀：`DRAFT` 儲存時不檢查；`SCHEDULED` 以 `KEEP_STATUS` 儲存時做完整的發布前驗證。
- 發布前驗證（02 §3.8 檢查、§3.9 發布、§3.12 開始評測，以及本文件 3.2 的排程到期）一律包含 `name`、`changeNote`、`content` 三者。
- `changeNote` 為 null 或空字串時略過。
- 檢查順序：`configKey` 不存在 404 → 沒有編輯中版本或 `docVersion` 不一致 409 → 驗證失敗 422。與今日相同。
- 錯誤訊息沿用名稱的格式，只換欄位名稱：`修改說明疑似包含{類型}，請移除`，例如 `修改說明疑似包含手機號碼，請移除`。
- 錯誤排列順序：內容的錯誤在前，接著名稱，最後修改說明。
- 已存在、修改說明含禁止內容的草稿，下次儲存或發布時會被擋，使用者修改說明後即可繼續。
- 前端不需要改：儲存遇到 422 時已經就地顯示錯誤清單。

## 3. 發布與排程

### 3.1 立即發布時清空預約時間

- `publishMode: IMMEDIATE` 在發布交易中把編輯中版本改為 `PUBLISHED`，同時把 `scheduledAt` 設為 null。草稿與已排程版本都一樣。
- 只有排程器到期發布時保留 `scheduledAt`，作為「原預約時間」紀錄（04 §2.1 原有規則）。
- 被套用取代而改為 `DISCARDED` 的已排程版本，仍保留 `scheduledAt`（原有規則，不變）。
- 重構後的命令服務在立即發布處有一行註解，明寫「立即發布不清空 `scheduledAt`」；這次改為傳入 null，並移除該註解。

### 3.2 排程到期時重新驗證

排程器每次處理一個到期版本時：

1. 對它做完整的發布前驗證（2.1）。
2. **通過**：照今日流程，在交易中封存舊版本、發布這一版、清除 prompt 快取。
3. **不通過**：以條件更新（`_id` 相符、`status` 仍為 `SCHEDULED`、`docVersion` 相符）把它退回草稿：
    - `status = DRAFT`，`scheduledAt = null`
    - `updatedBy = "system"`，`updatedAt = 現在`
    - `docVersion` 加 1
    - 單一文件的更新，不需要交易；舊的已發布版本不動，快取不清除，不計入本輪發布數。
4. 退回成功時記 WARN log：只含 `configKey`、`version` 與未通過的規則代碼（例如 `FORBIDDEN_CONTENT`），不含內容或訊息文字。
5. 條件更新沒有命中，表示這一版在讀取後已被改動或已被另一台機器處理，略過，與今日的「搶輸」處理相同。

分層落點：

- 驗證在 application 層的排程發布流程裡執行，因為驗證器屬於 domain，repository 不應知道驗證規則。
- 退回草稿是 domain repository 介面的新方法，例如「退回到期排程」。它在 infrastructure 以上述條件更新實作，命中回 true，沒有命中回 false，與既有的排程發布方法同一種回傳方式。
- `"system"` 這個操作者目前是初始資料匯入器裡的私有常數。改為 domain 的共用常數，讓初始資料與到期退回共用同一個值。

理由：驗證規則本身會變，例如 `allowed-url-hosts` 調整或新增禁止樣式。排程中儲存時的驗證只保證當下合格。退回草稿而不是保留排程，是為了讓管理頁看得到狀態變化，也避免排程器每 30 秒重試一個註定失敗的版本。

畫面影響：版本列表會顯示這份草稿由 `system` 在退回時間儲存。管理者開啟後，自動執行的發布前檢查會列出未通過的規則。

## 4. API 與錯誤碼

### 4.1 具型別的錯誤 `details`

- 在 domain 的例外套件新增錯誤細節介面，`AssistantException` 的 `details` 改為這個介面的清單，不再是 `List<Object>`。
- 細節 record 是純 Java，時間欄位維持 `Instant`。domain 與 application 不得依賴 interfaces 層，而 +08:00 是 admin API 的呈現規則，所以不放進 domain。
- interfaces 層的例外處理器負責把細節轉成 JSON：編輯中版本細節的兩個時間在這裡轉成 +08:00，其他細節原樣輸出。轉換集中在這一處，admin controller 不再攔截套用的例外、逐一轉換 map 裡的 `Instant`。
- `ApiError.details` 是轉換後的輸出，維持 interfaces 層自己的型別。
- 細節有四種，JSON 欄位名稱、順序與值都和今日相同：

| 細節 | JSON | 用於 |
| --- | --- | --- |
| 欄位錯誤 | `{ "field": "scheduledAt" }` | `AI_INPUT_INVALID` |
| 驗證錯誤（既有的 `ValidationError`） | `{ "rule": "...", "message": "..." }` | `AI_CONFIG_VALIDATION_FAILED` |
| 編輯中版本 | `{ "status": "SCHEDULED", "scheduledAt": "...+08:00", "updatedAt": "...+08:00", "docVersion": 7 }` | `AI_CONFIG_OVERWRITE_PENDING` |
| 執行中的評測 | `{ "runId", "configKey", "triggeredBy", "completedCases", "totalCases" }` | `AI_EVAL_RUNNING` |

- 編輯中版本細節的 `scheduledAt` 為 null 時照樣輸出 `"scheduledAt": null`，與今日相同。
- 擺放位置：欄位錯誤、編輯中版本、執行中的評測三種 record 放在 domain 的例外套件；`ValidationError` 留在 domain 的設定套件，改為實作同一個介面。介面不宣告為 sealed，因為 Java 在非模組化專案中要求 sealed 的實作類別位於同一個套件。
- 05 §1 補一張表，列出每個錯誤碼的 `details` 格式（同上表）；沒有列出的錯誤碼 `details` 為空陣列。

### 4.2 System Prompt 的 key 只能是 `system.main`

`POST /admin/configs`（02 §3.2）：

| 情境 | 回應 |
| --- | --- |
| `configType = SYSTEM_PROMPT` 但 `configKey` 不是 `system.main` | 400 `AI_INPUT_INVALID`，`details: [{ "field": "configKey" }]` |
| `configType = SYSTEM_INSTRUCTION` 但 `configKey` 不是 `instruction.` 開頭 | 400 `AI_INPUT_INVALID`，`details: [{ "field": "configKey" }]`（今日回報的欄位是 `configType`，改為 `configKey`） |
| `configKey` 已存在任何版本 | 409 `AI_CONFIG_INVALID_STATE` |
| 兩人同時建立同一個 key，後到者撞上 `UK_ICRM_AI_CFG_1` | 409 `AI_CONFIG_INVALID_STATE`（今日是 `AI_CONFIG_CONFLICT`） |

- 拿掉「已有任何 System Prompt」的額外查詢：key 固定為 `system.main` 後，重複由唯一索引擋下。這個查詢沒有其他用途，所以從 domain repository 介面、它的 Mongo 實作、Spring Data repository 與對應的測試一併移除。
- 同時建立的判斷方式：infrastructure 的 repository 會把撞上唯一索引轉成 `AI_CONFIG_CONFLICT`，application 層看不到原始的 Mongo 錯誤。新增設定時插入的是全新的 key，文件沒有舊的 `docVersion` 可以不一致，唯一可能的衝突就是 key 已被別人建立。所以新增設定在插入時遇到 `AI_CONFIG_CONFLICT`，就改回 `AI_CONFIG_INVALID_STATE`。repository 介面不需要為此新增方法。
- `configKey` 的格式（`ConfigKeys.PATTERN`）不變。
- Mongo validator 在既有的 `$or` 之外，再加兩條類型與 key 的一致性規則，三條以 `$and` 合併：

```json
"$and": [
  { "$or": [ { "status": { "$ne": "SCHEDULED" } }, { "scheduledAt": { "$type": "date" } } ] },
  { "$or": [ { "configType": { "$ne": "SYSTEM_PROMPT" } }, { "configKey": "system.main" } ] },
  { "$or": [ { "configType": { "$ne": "SYSTEM_INSTRUCTION" } }, { "configKey": { "$regex": "^instruction\\." } } ] }
]
```

- `validationLevel: strict` 會讓既有文件在下次更新時也受檢查。初始資料都符合規則，所以不需要資料遷移。

### 4.3 套用版本回 201

- `POST .../versions/{version}/apply` 成功時由 200 改為 201，回應本體不變。
- 理由：套用會新增一份版本文件，屬於建立資源。新增設定（3.2）與建立草稿（3.5）已經回 201，三個建立型端點因此一致。
- 前端只檢查是否為 2xx，不需要改。

### 4.4 建立草稿也記錄 `basedOnVersion`

- `POST .../draft`（3.5）建立的草稿，`basedOnVersion` 記錄複製來源的 `version`：有已發布版本時是它，沒有時是最新版本（與今日選擇來源的規則相同）。
- 新增設定的 v1 草稿與初始資料，`basedOnVersion` 仍為 null。
- 已存在的草稿不回填。
- 版本歷程的來源文字改為中性的寫法，讓建立草稿與套用兩種來源都說得通：
    - 找得到來源版本：`以 {來源版本的 updatedAt} 的版本為底`
    - 找不到：`以先前的版本為底`
- 這是本次唯一的前端修改。

## 5. Mongo

### 5.1 設定列表的查詢

今日的做法是讀出整個 collection（排除 `content`），在應用程式裡依 `configKey` 分組。版本永久保留（04 §2.8），所以成本隨版本累積線性成長。

改為三個步驟，每一步都由索引支撐，成本只與 key 的數量有關：

| 步驟 | 查詢 | 索引 |
| --- | --- | --- |
| 1 | `distinct("configKey")` 取得所有 key | `UK_ICRM_AI_CFG_1`（以 `configKey` 開頭，可走 DISTINCT_SCAN） |
| 2 | `status ∈ { PUBLISHED, DRAFT, SCHEDULED }` 的所有版本，只投影列表需要的欄位 | `IX_ICRM_AI_CFG_4`（以 `status` 開頭） |
| 3 | 對步驟 1 有、步驟 2 沒有的 key，各查最新的一個版本（`configKey` 相符，`version` 由大到小取 1 筆），同樣只投影 | `UK_ICRM_AI_CFG_1` |

- 投影欄位：`configKey`、`configType`、`name`、`status`、`version`、`scheduledAt`、`updatedBy`、`updatedAt`。不讀 `content` 與 `changeNote`。
- 步驟 3 只處理「沒有已發布版本、也沒有編輯中版本」的 key。曾發布過的 key 一定有一份 `PUBLISHED`，所以這種 key 只會是「新增後就捨棄、從未發布」，數量很少。
- key 集合取步驟 1 與步驟 2 的聯集，避免兩次查詢之間剛新增的 key 被漏掉。
- 每個 key 的結果與今日相同：
    - `published`：該 key 的 `PUBLISHED` 版本，沒有則為 null
    - `workingCopy`：該 key 的 `DRAFT` 或 `SCHEDULED` 版本，沒有則為 null
    - `name`：已發布版本的名稱，其次編輯中版本，再其次步驟 3 的最新版本
    - 排序：先依 `configType`，再依 `configKey`
- 實作落在 infrastructure 的設定查詢 repository，介面與 application 層不變。每個 key 的版本子集合，也就是步驟 2 的結果或步驟 3 的一筆，直接交給既有的列表摘要工廠方法。該方法只在已發布與編輯中版本都不存在時才用到最新版本，所以子集合的結果與今日相同，不需要修改。
- 未採用的方案：在 Mongo 端用單一 aggregation 依 `configKey` 分組。寫法較簡單，但伺服器仍要掃過每一個版本，沒有解決成本隨版本成長的問題。

### 5.2 Validator

見 4.2 的 `$and` 規則。

### 5.3 `content` 上限的兩種單位

在 02 §3 開頭的「內容上限」、04 §2.1 的 `content` 欄位與 04 §2.3 的 validator 說明補上：

- API 與發布前驗證以 **UTF-8 bytes** 計算，上限 65,536 bytes，這是實際生效的限制。
- Mongo validator 的 `maxLength` 以**字元**計算，上限 65,536 字元。
- 任何字元編碼成 UTF-8 至少 1 byte，所以通過 API 的內容一定通過 Mongo；validator 只是防止繞過 API 的寫入，例如手動修改資料庫。

## 6. 規格文件更新

| 文件 | 位置 | 修改 |
| --- | --- | --- |
| 01 | 套件結構圖的 `domain/exception/` | 補錯誤細節介面與三種細節 record |
| 02 | §3 開頭「權限」 | 補第 7 節的前提 |
| 02 | §3 開頭「內容上限」 | 補 5.3 的單位說明 |
| 02 | §3.1 | 範例與說明補上 `published` 也有 `status`（固定為 `PUBLISHED`）與 `scheduledAt`（到期自動發布的版本保留原預約時間，其他為 null） |
| 02 | §3.2 | 4.2 的 key 規則與回應表 |
| 02 | §3.3 | `basedOnVersion` 的畫面文字改為「以 … 的版本為底」，並說明建立草稿與套用都會記錄 |
| 02 | §3.5 | 沒有已發布版本時以最新版本為底；記錄 `basedOnVersion` |
| 02 | §3.6 | 任何狀態儲存時，`name`、`changeNote` 不通過禁止內容規則回 422 |
| 02 | §3.8 | 檢查範圍包含 `name` 與 `changeNote` |
| 02 | §3.9 | `IMMEDIATE` 清空 `scheduledAt` |
| 02 | §3.10 | 200 改為 201 |
| 04 | §2.1 | `basedOnVersion` 改為「建立草稿或套用時的來源版本」；`scheduledAt` 補立即發布時清空；`content` 補 5.3 |
| 04 | §2.3 | validator 換成 4.2 的 `$and` 版本，並補 5.3 |
| 04 | §2.4 | 操作表：建立草稿記錄 `basedOnVersion`、立即發布清空 `scheduledAt`、儲存時檢查名稱與修改說明；排程器段落補 3.2 的到期驗證 |
| 04 | §2.5 | 禁止內容規則套用於 `name` 與 `changeNote`，且在每次儲存時檢查；補修改說明的訊息格式 |
| 04 | §2.7 | 多台機器同時首次啟動時初始資料匯入會撞索引，正式環境改用 migration |
| 05 | §1 | 4.1 的 `details` 格式表 |
| 05 | §2.3 | `AI_CONFIG_NOT_FOUND` 的情境拿掉「草稿不存在」，註明 key 存在但沒有編輯中版本時回 `AI_CONFIG_CONFLICT`；`AI_CONFIG_INVALID_STATE` 的「key 已存在」補「含同時建立」；`AI_CONFIG_VALIDATION_FAILED` 補「儲存時名稱或修改說明含禁止內容」 |
| 06 | 情境 M4、M15 的預期結果 | 「套用自 {時間} 的版本」改為「以 {時間} 的版本為底」 |
| 2026-10-28 Prompt 管理頁精簡設計 | 4.1 與 4.3 的兩處「套用自」 | 改為「以 … 的版本為底」 |

## 7. 權限的前提

02 §3 的權限段落補上：

> 後台管理人員與 IT 人員同時擁有 `AI_CONFIG_EDIT` 與 `AI_CONFIG_PUBLISH`（M26）。已排程版本以 `KEEP_STATUS` 儲存只需要編輯權限，但內容會在到期時上線；若日後把編輯與發布拆給不同的人，這個操作要改為需要發布權限。

## 8. 測試

後端：

- 驗證器：`changeNote` 含禁止內容時回傳 `修改說明疑似包含…`；null 或空字串略過；錯誤順序為內容、名稱、修改說明。
- 儲存：`DRAFT` 的名稱或修改說明含禁止內容回 422；內容含禁止內容的 `DRAFT` 仍可儲存。
- 立即發布：已排程版本立即發布後 `scheduledAt` 為 null；排程器到期發布的版本仍保留 `scheduledAt`。
- 排程到期：
    - 驗證不通過時退回 `DRAFT`，`scheduledAt` 為 null，`updatedBy` 為 `system`，`docVersion` 加 1。
    - 舊的已發布版本不變，本輪發布數為 0。
    - 讀取後被改動的到期版本，退回與發布都不會發生。
- 新增設定：4.2 回應表的四種情境各一個測試；validator 拒絕類型與 key 不一致的直接寫入。
- 套用回 201。
- 建立草稿：有已發布版本時 `basedOnVersion` 為它的版本號；沒有時為最新版本號。
- 設定列表：
    - 只有已捨棄版本的 key 仍出現在列表，名稱取自最新版本。
    - 結果與今日相同：沿用既有的列表測試。
    - 不讀取 `content`。
- 錯誤 `details`：四種細節各一個 controller 層級的 JSON 測試，斷言欄位名稱、順序與 null 值和今日相同。
- 分層：`ArchitectureRulesTest` 維持全綠，新增的細節 record 與 repository 方法不得引入被禁止的依賴。

各項測試所在的類別：

| 測試 | 類別 |
| --- | --- |
| 驗證器 | `domain/config/ConfigValidatorTest` |
| 儲存、立即發布、排程到期、新增設定、建立草稿 | `application/config/ConfigCommandServiceTest` |
| 到期退回的條件更新；移除 `existsByConfigType` 的測試 | `infrastructure/mongo/AssistantConfigRepositoryImplTest` |
| 設定列表 | `infrastructure/mongo/ConfigQueryRepositoryImplTest` |
| validator 的類型與 key 一致性 | `infrastructure/mongo/MongoSchemaTest` |
| 錯誤細節的 JSON、套用回 201 | `interfaces/rest/AssistantExceptionHandlerTest`、`interfaces/rest/config/AdminConfigControllerTest`、`interfaces/rest/evaluation/EvalControllerTest` |
| 開始評測的驗證包含修改說明 | `application/evaluation/EvalRunServiceTest` |

前端：

- 版本歷程的來源文字：找得到來源與找不到來源兩種情況。

## 9. 實作落點

分層對齊已完成，本次修正依各層的職責放置：

| 層 | 變更 |
| --- | --- |
| domain | 錯誤細節介面與三種細節 record（4.1）；`ValidationError` 實作該介面；`AssistantException` 的 `details` 改型別；驗證器多收修改說明，並新增只檢查名稱與修改說明的入口（2.1）；`"system"` 操作者常數（3.2）；repository 介面新增到期退回、移除 `existsByConfigType`（3.2、4.2） |
| application | 命令服務：儲存時檢查名稱與修改說明、立即發布清空預約時間、到期前驗證與退回、新增設定的 key 規則與衝突改判、建立草稿記錄來源版本；評測服務的驗證帶入修改說明，執行中細節改用 record |
| infrastructure | 設定 repository 實作到期退回並移除 `existsByConfigType`；設定查詢 repository 改為三步驟查詢（5.1）；`config-validator.json` 加入 `$and` 規則（4.2）；初始資料匯入器改用共用的操作者常數 |
| interfaces | 例外處理器轉換細節並套用 +08:00（4.1）；admin controller 移除套用的例外攔截，套用改回 201（4.3） |
| 前端 | 版本歷程的來源文字（4.4） |
