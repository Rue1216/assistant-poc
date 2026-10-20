# 02 REST API 規格

> 版本：v0.7（POC）・2026-10-20

## 1. 共通規則

| 項目 | 規則 |
|---|---|
| Base path | `/api/ai-assistant` |
| 驗證 | 沿用平台 Session（POC 由 `mockplatform` 的 Spring Security session 提供），未登入回 401 |
| CSRF | 寫入類請求需帶 `X-XSRF-TOKEN` header（值取自 `XSRF-TOKEN` cookie，axios 會自動處理） |
| 格式 | `application/json; charset=UTF-8` |
| 時間 | ISO 8601，含時區，例如 `2026-10-19T14:03:22+08:00` |
| 追蹤 | 每個回應都有 `X-Request-Id` header；錯誤回應的 body 也帶 `requestId` |
| 錯誤格式 | 見第 5 節與 [05-error-codes.md](05-error-codes.md) |

### 1.1 使用範圍

| 功能 | 入口 | 可使用的角色 |
|---|---|---|
| 對話小幫手 | 顧客經營模組（`/customer`）→ 個人 360 頁面（`/customer/{customerId}`）右下角 | 9 個角色（見 1.2） |
| Prompt 管理 | 後台管理模組（`/setting`）→ Prompt 管理（`/setting/prompt-management`） | 後台管理人員、IT 管理人員 |

- 小幫手**只**出現在個人 360 頁面，其他頁面不顯示。
- 使用者透過輸入顧客編號，或從顧客列表點擊，進入個人 360 頁面。
- 換到另一位顧客的頁面時，前端清空對話。

### 1.2 角色與權限碼

| 權限碼 | 用途 |
|---|---|
| `CUSTOMER_360_VIEW` | 進入個人 360 頁面（平台既有權限，POC 由 `mockplatform` 模擬） |
| `AI_ASSISTANT_USE` | 使用對話小幫手 |
| `AI_CONFIG_READ` | 進入 Prompt 管理頁、檢視設定與版本 |
| `AI_CONFIG_EDIT` | 新增設定、編輯與捨棄草稿 |
| `AI_CONFIG_PUBLISH` | 發布、回復 |

| 角色 key | 帳號 | 角色 | 分行 / 區域 / 單位 | 權限 |
|---|---|---|---|---|
| `HQ_VIEWER` | `hq.viewer` | 總行查詢人員 | 總行 | `CUSTOMER_360_VIEW`、`AI_ASSISTANT_USE` |
| `REGION_VIEWER` | `region.north` | 區域查詢人員 | 北區（B101、B102） | 同上 |
| `BRANCH_MANAGER` | `branch.mgr` | 分行單位主管 | B101 | 同上 |
| `FC_SUPERVISOR` | `fc.supervisor` | 理專主管 | B101 | 同上 |
| `FC_OFFICER` | `fc.officer` | 理專經辦 | B101 | 同上 |
| `AO_SUPERVISOR` | `ao.supervisor` | AO 主管 | B101、行銷單位 MU-N1 | 同上 |
| `AO_OFFICER` | `ao.officer` | AO 經辦 | B101、行銷單位 MU-N1 | 同上 |
| `BACKOFFICE_ADMIN` | `backoffice.admin` | 後台管理人員 | 總行 | 同上 + `AI_CONFIG_READ`、`AI_CONFIG_EDIT`、`AI_CONFIG_PUBLISH` |
| `IT_ADMIN` | `it.admin` | IT 管理人員 | 總行 | 同上 + `AI_CONFIG_READ`、`AI_CONFIG_EDIT`、`AI_CONFIG_PUBLISH` |

- 所有帳號的 POC 密碼統一為 `poc1234`，只用於本機 demo。
- 後台管理人員和 IT 管理人員都可以編輯、發布與預約發布 Prompt；每個版本記錄 `createdBy`、`updatedBy` 和對應時間，發布資訊記在 `updatedBy`、`updatedAt`，可追溯每次變更。

### 1.3 顧客範圍規則

使用者能否存取某位顧客，取決於顧客的以下屬性：

| 顧客屬性 | 說明 |
|---|---|
| 主經營行 | 顧客的主要往來分行 |
| 利潤分行 | 顧客業績歸屬的分行 |
| 理財分行 | 顧客財管業務歸屬的分行 |
| 生效名單分派人員 | 目前生效的名單分派對象（人員本人，以及該人員所屬分行） |
| 行銷單位 | 顧客歸屬的 AO 行銷單位 |

| 角色 | 存取等級 | 符合以下任一條件即可存取 |
|---|---|---|
| 總行查詢人員 | `FULL` | 所有分行的顧客 |
| 區域查詢人員 | `FULL` | 主經營行、利潤分行、理財分行、分派人員所屬分行，任一在**所屬區域**內 |
| 分行單位主管 | `FULL` | 主經營行、利潤分行、理財分行、分派人員所屬分行，任一等於**所屬分行** |
| 理專主管 | `FULL` | 主經營行、理財分行、分派人員所屬分行，任一等於所屬分行 |
| 理專經辦 | `FULL` | 主經營行或理財分行等於所屬分行，或**分派人員是本人** |
| AO 主管 | `FULL` | 主經營行、利潤分行、分派人員所屬分行，任一等於所屬分行；或行銷單位等於所屬單位 |
| AO 經辦 | `FULL` | 主經營行或利潤分行等於所屬分行，或分派人員是本人，或行銷單位等於所屬單位 |
| 後台管理人員 | `LIMITED` | 全行顧客，但只能看去識別化的基本輪廓 |
| IT 管理人員 | `LIMITED` | 同上 |

- 範圍規則屬於平台既有邏輯，由 `CustomerAccessPort` 判斷（POC 由 `mockplatform` 實作），小幫手只使用結果。
- `FULL` 和 `LIMITED` 可用的工具見 [03-tool-catalog.md](03-tool-catalog.md) 1.7。
- 「理專經辦」「AO 經辦」的「主經營行等於所屬分行」依提供的規則照實作；若實際規則是「主經營行且分派給本人」，只需調整 `mockplatform` 的判斷。

### 1.4 POC 存取矩陣

依 [03-tool-catalog.md](03-tool-catalog.md) 5.2 的假資料推得：

| 顧客 | 總行 | 區域（北） | 分行主管 | 理專主管 | 理專經辦 | AO 主管 | AO 經辦 | 後台 / IT | 關鍵屬性 |
|---|---|---|---|---|---|---|---|---|---|
| C001 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 去識別化 | 全部屬性都在 B101 |
| C002 | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | ✗ | 去識別化 | 只有理財分行在 B101 |
| C003 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | 去識別化 | 分派給 fc.officer（B101） |
| C004 | ✓ | ✓ | ✓ | ✗ | ✗ | ✓ | ✓ | 去識別化 | 利潤分行 B101、行銷單位 MU-N1 |
| C005 | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | 去識別化 | 全部在南區 |
| C006 | ✓ | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ | 去識別化 | 只有行銷單位 MU-N1 |

---

## 2. 對話 API

### 2.1 `POST /api/ai-assistant/customers/{customerId}/chat`

在個人 360 頁面上，針對這位顧客送出一個問題。同步回應，不串流。

- **權限**：`AI_ASSISTANT_USE`，且對 `{customerId}` 的存取等級不是 `NONE`
- **限流**：每位使用者每分鐘 10 次
- **`customerId`**：POC 格式 `^C\d{3}$`。只在後端用來判斷權限和查資料，**不會送進 LLM**。

#### 處理順序

1. 驗證路徑參數格式，不符 → 400 `AI_INPUT_INVALID`。
2. `CustomerAccessPort` 判斷存取等級，`NONE` 或顧客不存在 → 404 `AI_CUSTOMER_NOT_FOUND`（兩者回應相同，不透露顧客是否存在）。
3. 建立 `CustomerContext`，以 `PiiRedactor` 處理提問和歷史，進入 tool calling 迴圈。

#### Request

| 欄位 | 型別 | 必填 | 規則 |
|---|---|---|---|
| `message` | string | ✓ | 去除前後空白後 1–1000 字 |
| `history` | array | | 最多 20 則（10 輪），內容總長最多 8000 字；超過時後端從最舊的開始截掉 |
| `history[].role` | string | ✓ | `user` 或 `assistant` |
| `history[].content` | string | ✓ | 1–4000 字 |

```json
{
  "message": "王小明最近有什麼互動？幫我準備下次聯繫重點",
  "history": [
    { "role": "user", "content": "幫我整理這位顧客的概況" },
    { "role": "assistant", "content": "本顧客為 VIP 客群，年齡 55-64 歲，往來 8 年……" }
  ]
}
```

> 這個範例中，送給 LLM 的提問是「【本顧客】最近有什麼互動？幫我準備下次聯繫重點」。

> 對話預設不保存：後端不儲存對話歷史供使用者回看，由前端每次帶回；歷史每次都會重新經過 `PiiRedactor`。工具結果不會回傳給前端，也不會跨輪保留。

#### Response 200

| 欄位 | 型別 | 說明 |
|---|---|---|
| `requestId` | string | 追蹤用 |
| `answer` | string | Markdown 格式的回答，前端必須用 `SafeMarkdown` 渲染；以「本顧客」稱呼顧客 |
| `sources` | array | 本次回答使用的資料來源，由 Java 依工具結果產生 |
| `sources[].name` | string | 來源名稱 |
| `sources[].url` | string \| null | 只有公開外部來源才有，內部來源一律為 `null` |
| `sources[].dataTime` | string \| null | 資料本身的時間（例如收盤日） |
| `sources[].queriedAt` | string | 查詢時間 |
| `queriedAt` | string | 本次提問的處理時間 |
| `disclaimer` | string \| null | 用到市場工具時為「資訊僅供內部經營參考，不構成投資建議」 |
| `toolCalls` | array | 本次的工具呼叫摘要，沒有呼叫工具時為空陣列 |
| `toolCalls[].name` | string | 工具名稱 |
| `toolCalls[].purpose` | string | 中文用途 |
| `toolCalls[].status` | string | `SUCCESS`、`ERROR`、`DENIED`、`TIMEOUT` |
| `toolCalls[].durationMs` | number | 耗時（毫秒） |

`toolCalls` **不包含**參數、工具結果、顧客個資、內部網址、錯誤細節。

```json
{
  "requestId": "a1b2c3d4",
  "answer": "### 近期互動\n- 10/02 電話：本顧客詢問月配息商品，希望每月現金流約 3 萬，已約定 10 月下旬面談\n……\n### 下次聯繫重點\n1. ……",
  "sources": [
    { "name": "平台顧客資料（模擬）", "url": null, "dataTime": null, "queriedAt": "2026-10-19T14:03:22+08:00" }
  ],
  "queriedAt": "2026-10-19T14:03:24+08:00",
  "disclaimer": null,
  "toolCalls": [
    { "name": "getRecentInteractions", "purpose": "查詢近期互動紀錄", "status": "SUCCESS", "durationMs": 12 },
    { "name": "getCampaignResults", "purpose": "查詢顧客的活動回應", "status": "SUCCESS", "durationMs": 9 }
  ]
}
```

#### 錯誤

| HTTP | code | 情境 |
|---|---|---|
| 400 | `AI_INPUT_INVALID` | 欄位格式錯誤、超過長度、顧客編號格式錯誤 |
| 401 | `AI_UNAUTHENTICATED` | 未登入 |
| 403 | `AI_FORBIDDEN` | 沒有 `AI_ASSISTANT_USE` |
| 404 | `AI_CUSTOMER_NOT_FOUND` | 顧客不存在，或不在使用者的範圍內 |
| 422 | `AI_TOKEN_BUDGET_EXCEEDED` | 超過 token 預算 |
| 429 | `AI_RATE_LIMITED` | 超過限流 |
| 503 | `AI_LLM_RATE_LIMITED` / `AI_LLM_UNAVAILABLE` | GPT 限流 / 不可用 |
| 504 | `AI_LLM_TIMEOUT` / `AI_REQUEST_TIMEOUT` | GPT 逾時 / 整體逾時 |

> 工具失敗（例如 TWSE 逾時）**不會**回傳錯誤，而是 200，並在 `toolCalls[].status` 標示，由回答說明。

### 2.2 `GET /api/ai-assistant/customers/{customerId}/suggestions`

取得對話視窗的快捷問題。內容來自所有已發布 System Instructions 中的「範例問題」。

- **權限**：同 2.1；仍先判斷使用者能否存取這位顧客，不能存取時回 404。
- **不依存取等級篩選**：所有可存取者拿到相同的分類。`LIMITED` 使用者點了需要 `FULL` 工具的問題（例如「聯繫準備」）時，模型拿不到對應工具，會回答「依您的權限無法查看這類資料」（見 [07-prompt-examples.md](07-prompt-examples.md) 第 2 節）。

```json
{
  "categories": [
    { "configKey": "instruction.customer-overview", "name": "顧客概況", "questions": ["幫我整理這位顧客的概況", "這位顧客有哪些標籤？"] },
    { "configKey": "instruction.contact-prep", "name": "聯繫準備", "questions": ["整理近期互動，幫我準備下次聯繫重點"] }
  ]
}
```

- 錯誤：同 2.1 的 400、401、403、404。

---

## 3. Prompt 管理 API

- **權限**：
  - 查詢類（3.1、3.3、3.4）：`AI_CONFIG_READ`
  - 編輯類（3.2、3.5–3.8）：`AI_CONFIG_EDIT`
  - 發布類（3.9、3.10）：`AI_CONFIG_PUBLISH`
- **設定類型**：
  - `SYSTEM_PROMPT`：只有一份 `system.main`，定義角色、安全規則、回答格式
  - `SYSTEM_INSTRUCTION`：可以有多份，**每份代表一個問題分類**，包含適用問題、回答套版、範例問題
- **`configKey` 格式**：`^(system|instruction)\.[a-z0-9-]{1,50}$`
- **內容上限**：`content` UTF-8 編碼後最多 65,536 bytes
- **狀態與操作**：每個 key 同時只有一份「編輯中版本」（`DRAFT` 或 `SCHEDULED`）。狀態轉換與 `updatedBy` 規則見 [04-mongodb-schema.md](04-mongodb-schema.md) 2.4。

### 3.0 編輯頁操作與 API 對照

```
┌ 聯繫準備（instruction.contact-prep）── v3・已排程 2026/10/24 09:00 ┐
│ [textarea 編輯區]              │ [預覽]                          │
├──────────────────────────────────────────────────────────────────┤
│ 發布方式：(•) 立即發布  ( ) 預約發布 [2026/10/24] [09:00]          │
│                                                                  │
│            [存成草稿]   [儲存]   [發布]                            │
└──────────────────────────────────────────────────────────────────┘
```

| 按鈕 | 草稿時 | 已排程時 | API |
|---|---|---|---|
| 儲存 | 儲存內容，維持草稿 | 儲存內容，保留排程 | `PUT .../draft`，`saveMode: "KEEP_STATUS"` |
| 存成草稿 | 同「儲存」 | 儲存內容，取消排程、退回草稿 | `PUT .../draft`，`saveMode: "DRAFT"` |
| 發布（立即） | 立即發布 | 立即發布（不等預約時間） | 先儲存，再 `POST .../publish`，`publishMode: "IMMEDIATE"` |
| 發布（預約） | 改為已排程 | 修改預約時間 | 先儲存，再 `POST .../publish`，`publishMode: "SCHEDULED"` |

- 「發布」由前端 composable 依序呼叫儲存與發布，用儲存回傳的 `docVersion` 接著發布。
- 已排程時，編輯頁頂端顯示預約時間；「發布方式」預設選「預約發布」並帶入目前的預約時間。

### 3.1 `GET /api/ai-assistant/admin/configs`

列出所有設定。管理頁依 `configType` 分成「System Prompt」和「System Instructions」兩個分頁顯示。

```json
[
  {
    "configKey": "system.main",
    "configType": "SYSTEM_PROMPT",
    "name": "主要系統提示",
    "published": { "version": 2, "updatedBy": "it.admin", "updatedAt": "2026-10-19T09:30:00+08:00" },
    "workingCopy": { "version": 3, "status": "DRAFT", "scheduledAt": null, "updatedBy": "backoffice.admin", "updatedAt": "2026-10-19T10:00:00+08:00" }
  },
  {
    "configKey": "instruction.contact-prep",
    "configType": "SYSTEM_INSTRUCTION",
    "name": "聯繫準備",
    "published": { "version": 1, "updatedBy": "system", "updatedAt": "2026-10-19T08:00:00+08:00" },
    "workingCopy": { "version": 2, "status": "SCHEDULED", "scheduledAt": "2026-10-24T09:00:00+08:00", "updatedBy": "it.admin", "updatedAt": "2026-10-19T11:00:00+08:00" }
  }
]
```

- `workingCopy` 是編輯中版本（`DRAFT` 或 `SCHEDULED`），沒有時為 `null`。
- 清單上以標籤顯示「草稿」或「已排程 10/24 09:00」。

### 3.2 `POST /api/ai-assistant/admin/configs`

新增一個設定 key，並建立 v1 草稿。用於新增問題分類（System Instruction）。

| 欄位 | 型別 | 必填 | 規則 |
|---|---|---|---|
| `configKey` | string | ✓ | 格式如上；不可與既有 key 重複 |
| `configType` | string | ✓ | `SYSTEM_PROMPT` 或 `SYSTEM_INSTRUCTION`，且必須和 key 前綴一致（`system.` / `instruction.`） |
| `name` | string | ✓ | 1–50 字，例如「聯繫準備」 |

- 新增的草稿內容預設帶入該類型的範本（見 [07-prompt-examples.md](07-prompt-examples.md)），方便 IT 人員填寫。
- **201**：回傳新草稿（格式同 3.4）
- **409** `AI_CONFIG_INVALID_STATE`：key 已存在；或要新增第二份 `SYSTEM_PROMPT`

### 3.3 `GET /api/ai-assistant/admin/configs/{configKey}/versions`

列出版本歷程，依版本號由新到舊，不含內容。

```json
[
  { "version": 3, "status": "SCHEDULED", "scheduledAt": "2026-10-24T09:00:00+08:00", "changeNote": "調整回答格式", "basedOnVersion": null, "createdBy": "backoffice.admin", "updatedBy": "backoffice.admin", "updatedAt": "..." },
  { "version": 2, "status": "PUBLISHED", "scheduledAt": null, "changeNote": "新增安全規則", "basedOnVersion": null, "createdBy": "it.admin", "updatedBy": "it.admin", "updatedAt": "..." },
  { "version": 1, "status": "ARCHIVED", "scheduledAt": null, "changeNote": "初始版本", "basedOnVersion": null, "createdBy": "system", "updatedBy": "it.admin", "updatedAt": "..." }
]
```

### 3.4 `GET /api/ai-assistant/admin/configs/{configKey}/versions/{version}`

取得單一版本的完整內容。

```json
{
  "configKey": "instruction.contact-prep",
  "configType": "SYSTEM_INSTRUCTION",
  "name": "聯繫準備",
  "content": "## 適用問題\n……\n\n## 回答套版\n……\n\n## 範例問題\n- 整理近期互動，幫我準備下次聯繫重點\n\n## 注意事項\n……",
  "exampleQuestions": ["整理近期互動，幫我準備下次聯繫重點"],
  "status": "PUBLISHED",
  "version": 1,
  "basedOnVersion": null,
  "changeNote": "初始版本",
  "createdBy": "system",
  "createdAt": "2026-10-19T08:00:00+08:00",
  "updatedBy": "system",
  "updatedAt": "2026-10-19T08:00:00+08:00",
  "scheduledAt": null,
  "docVersion": 0
}
```

- `exampleQuestions` 由後端從 `## 範例問題` 章節解析，唯讀；`SYSTEM_PROMPT` 為空陣列。
- **404** `AI_CONFIG_NOT_FOUND`

### 3.5 `POST /api/ai-assistant/admin/configs/{configKey}/draft`

以目前已發布版本為底，建立草稿（`version` = 最大版本號 + 1）。

- **201**：回傳草稿
- **409** `AI_CONFIG_INVALID_STATE`：已經有編輯中版本（`DRAFT` 或 `SCHEDULED`）

### 3.6 `PUT /api/ai-assistant/admin/configs/{configKey}/draft`

儲存編輯中版本。對應編輯頁的「儲存」和「存成草稿」。

| 欄位 | 型別 | 必填 | 規則 |
|---|---|---|---|
| `name` | string | ✓ | 1–50 字 |
| `content` | string | ✓ | 最多 65,536 bytes；`DRAFT` 時允許空白 |
| `changeNote` | string | | 最多 200 字 |
| `saveMode` | string | ✓ | `KEEP_STATUS`（儲存）或 `DRAFT`（存成草稿） |
| `docVersion` | number | ✓ | 讀取時拿到的 `docVersion` |

| 目前狀態 | `saveMode` | 結果 |
|---|---|---|
| `DRAFT` | 任一 | 維持 `DRAFT` |
| `SCHEDULED` | `KEEP_STATUS` | 維持 `SCHEDULED`，保留 `scheduledAt`；**先執行發布前驗證**，確保到期時能成功發布 |
| `SCHEDULED` | `DRAFT` | 改為 `DRAFT`，清空 `scheduledAt`（取消排程） |

- `updatedBy`、`updatedAt` 都更新為操作者與現在時間。排程中按「儲存」視為由此人重新確認排程，到期自動發布時記的是此人。
- **200**：回傳更新後的版本（`docVersion` 會加 1）
- **404** `AI_CONFIG_NOT_FOUND`：沒有編輯中版本
- **409** `AI_CONFIG_CONFLICT`：`docVersion` 不一致，代表已被他人修改（包括排程剛好到期、已被系統發布）
- **422** `AI_CONFIG_VALIDATION_FAILED`：已排程版本的內容未通過驗證

### 3.7 `DELETE /api/ai-assistant/admin/configs/{configKey}/draft?docVersion={n}`

捨棄草稿。只能捨棄 `DRAFT`；已排程的版本需先「存成草稿」取消排程。

- **204**
- **404** `AI_CONFIG_NOT_FOUND`
- **409** `AI_CONFIG_CONFLICT`
- **409** `AI_CONFIG_INVALID_STATE`：目前是 `SCHEDULED`

### 3.8 `POST /api/ai-assistant/admin/configs/{configKey}/draft/validate`

不發布，只執行發布前驗證，讓管理頁事先顯示問題。

```json
{
  "valid": false,
  "errors": [
    { "rule": "REQUIRED_SECTION", "message": "缺少必要章節：## 回答套版" },
    { "rule": "EXAMPLE_QUESTIONS", "message": "範例問題需有 1–5 題，每題最多 40 字" },
    { "rule": "FORBIDDEN_CONTENT", "message": "第 12 行疑似包含身分證號" }
  ]
}
```

### 3.9 `POST /api/ai-assistant/admin/configs/{configKey}/publish`

發布編輯中版本，可以立即發布或預約發布。兩種方式都會先執行發布前驗證。

| 欄位 | 型別 | 必填 | 規則 |
|---|---|---|---|
| `publishMode` | string | ✓ | `IMMEDIATE` 或 `SCHEDULED` |
| `scheduledAt` | string | `SCHEDULED` 必填 | ISO 8601 含時區；至少 1 分鐘後、最多 90 天內 |
| `docVersion` | number | ✓ | |

```json
{ "publishMode": "SCHEDULED", "scheduledAt": "2026-10-24T09:00:00+08:00", "docVersion": 5 }
```

| `publishMode` | 結果 |
|---|---|
| `IMMEDIATE` | 在同一個交易中：舊的 PUBLISHED → ARCHIVED，編輯中版本 → PUBLISHED。已排程的版本也可以提前立即發布 |
| `SCHEDULED` | 編輯中版本 → `SCHEDULED`，寫入 `scheduledAt`；已排程時即修改預約時間。到期由排程器自動發布，`updatedBy` 維持預約者 |

- **200**：回傳發布後或排程後的版本
- **400** `AI_INPUT_INVALID`：`scheduledAt` 缺少或超出範圍
- **404** `AI_CONFIG_NOT_FOUND`：沒有編輯中版本
- **409** `AI_CONFIG_CONFLICT`
- **422** `AI_CONFIG_VALIDATION_FAILED`：`details` 格式同 3.8 的 `errors`

### 3.10 `POST /api/ai-assistant/admin/configs/{configKey}/rollback`

回復到上一個版本：把「目前已發布版本之前的最新 ARCHIVED 版本」的內容複製成新版本並立即發布，記錄 `basedOnVersion`。不影響編輯中版本。

| 欄位 | 型別 | 必填 | 規則 |
|---|---|---|---|
| `expectedPublishedVersion` | number | ✓ | 使用者看到的目前已發布版本 |
| `confirmScheduledOverride` | boolean | 有排程時必填 `true` | 使用者已確認「排程中的版本到期後會覆蓋這次回復」 |

```json
{ "expectedPublishedVersion": 3, "confirmScheduledOverride": true }
```

**有排程時的流程**

1. 前端送出回復（不帶 `confirmScheduledOverride`）。
2. 後端發現該 key 有 `SCHEDULED` 版本，回 409 `AI_CONFIG_SCHEDULE_PENDING`：

   ```json
   {
     "code": "AI_CONFIG_SCHEDULE_PENDING",
     "message": "此設定仍有預約發布的版本，到期後會覆蓋這次回復",
     "requestId": "a1b2c3d4",
     "details": [ { "scheduledVersion": 5, "scheduledAt": "2026-10-24T09:00:00+08:00" } ]
   }
   ```

3. 前端跳出確認視窗：「此設定仍有 v5 預約於 2026/10/24 09:00 發布，到期後會覆蓋這次回復。確定要回復嗎？」
4. 使用者確認後，前端帶 `confirmScheduledOverride: true` 重送；回復立即生效，排程維持不變，到期時照常發布。

**回應**

- **200**：回傳新的已發布版本
- **409** `AI_CONFIG_SCHEDULE_PENDING`：有排程但未確認
- **409** `AI_CONFIG_CONFLICT`：目前已發布版本不是 `expectedPublishedVersion`（有人剛發布過，或排程剛到期）
- **409** `AI_CONFIG_INVALID_STATE`：沒有可回復的上一版
- **422** `AI_CONFIG_VALIDATION_FAILED`：舊內容不符合目前的驗證規則

---

## 4. 平台替身 API（POC 專用，整合時刪除）

| Method | Path | 說明 |
|---|---|---|
| `POST` | `/api/mock/auth/login` | `{ username, password }`，成功後建立 session |
| `POST` | `/api/mock/auth/logout` | 登出 |
| `GET` | `/api/mock/auth/me` | `{ username, displayName, roleKey, roleName, permissions[] }` |
| `GET` | `/api/mock/customers` | 目前使用者可存取的顧客清單，供顧客列表頁使用；後台 / IT 看到的是遮罩後的資料 |
| `GET` | `/api/mock/customers/{customerId}` | 個人 360 頁面資料，回應含 `accessLevel`；`NONE` 或不存在回 404 |

> 平台替身的 360 頁面為了 demo 會顯示虛構的姓名等資料（`LIMITED` 時遮罩），這是**平台畫面**的行為；送進 LLM 的內容一律經過去識別化。

---

## 5. 錯誤回應格式

```json
{
  "code": "AI_CONFIG_CONFLICT",
  "message": "內容已被他人更新，請重新載入後再編輯",
  "requestId": "a1b2c3d4",
  "details": []
}
```

- `message` 一律是給使用者看的友善訊息，不包含 stack trace、供應商原始錯誤或內部網址。
- 完整錯誤碼見 [05-error-codes.md](05-error-codes.md)。
