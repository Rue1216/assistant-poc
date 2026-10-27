# 02 REST API 規格

> 版本：v1.0（POC）・2026-10-26

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
- 對話視窗標題列的「開新對話」：先前的對話留在畫面上，之後的提問從新的一輪開始，`history` 只帶這一輪的訊息。不提供對話紀錄與刪除對話。

### 1.2 角色與權限碼

| 權限碼 | 用途 |
|---|---|
| `CUSTOMER_360_VIEW` | 進入個人 360 頁面（平台既有權限，POC 由 `mockplatform` 模擬） |
| `AI_ASSISTANT_USE` | 使用對話小幫手、對回答按讚／倒讚 |
| `AI_CONFIG_READ` | 進入 Prompt 管理頁、檢視設定與版本、查看回饋統計與評測結果 |
| `AI_CONFIG_EDIT` | 新增設定、編輯與捨棄草稿、套用歷史版本為草稿、開始評測 |
| `AI_CONFIG_PUBLISH` | 立即發布、預約發布 |

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
3. 建立 `CustomerContext`，以 `PiiRedactor` 處理提問和歷史。
4. 決定載入哪個問題分類：request 帶有效的 `categoryKey` 時直接載入（`CHIP`），否則先以一次模型呼叫路由（`ROUTER`，失敗則 `FALLBACK` 載入全部），再進入 tool calling 迴圈（設計見 [instruction-routing-design](../superpowers/specs/2026-10-27-instruction-routing-design.md)）。

#### Request

| 欄位 | 型別 | 必填 | 規則 |
|---|---|---|---|
| `message` | string | ✓ | 去除前後空白後 1–1000 字 |
| `history` | array | | 最多 20 則（10 輪），內容總長最多 8000 字；超過時後端從最舊的開始截掉。前端只帶目前這一輪（按「開新對話」之後）的訊息，見 1.1 |
| `history[].role` | string | ✓ | `user` 或 `assistant` |
| `history[].content` | string | ✓ | 1–4000 字 |
| `categoryKey` | string | | 選填。快捷按鈕所屬問題分類的 `configKey`，格式 `^instruction\.[a-z0-9-]{1,50}$`，格式不符 → 400 `AI_INPUT_INVALID`。格式正確但不是本次快照中已發布的問題分類（例如按鈕在分類下架前載入、之後才被點）時**忽略**，改走路由，不回錯誤。自行輸入的提問與輸入框上方的追問建議按鈕不帶此欄位，一律走路由 |

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
| `blocks` | array | 回答的區塊，前端依序渲染；文字已去識別化，以「本顧客」稱呼顧客。最多 40 個區塊，文字合計最多 8000 字 |
| `blocks[].type` | string | `heading`、`paragraph`、`bullet`、`numbered` |
| `blocks[].text` | string | 區塊文字，只含行內格式（粗體、連結），不含標題或清單符號，也不含 `[S1]` 這類標註 |
| `blocks[].citations` | number[] | 支持這個區塊的引用編號（1 起算），對應 `citations[].id`；`heading` 或沒有依據時為空陣列 |
| `answer` | string | 由 `blocks` 扁平化的 Markdown（不含引用編號），供對話歷史與複製使用；前端不直接渲染它 |
| `citations` | array | 本次每一次成功的工具呼叫各一筆；被回答引用的在前（依首次出現順序），未被引用的依呼叫順序接在後面 |
| `citations[].id` | number | 引用編號，1 起算 |
| `citations[].toolName` | string | 工具名稱 |
| `citations[].title` | string | 引用標題（例如「上市個股行情」）；工具沒有提供時為該工具的中文用途 |
| `citations[].target` | string \| null | 查詢對象（例如「台積電 2330」），只有外部資料工具有 |
| `citations[].excerpt` | string \| null | 外部資料的原文摘錄，前端只以純文字顯示 |
| `citations[].description` | string \| null | 內部資料的說明（例如「3 筆」），不含顧客資料內容 |
| `citations[].sourceName` | string \| null | 來源名稱 |
| `citations[].url` | string \| null | 只有公開外部來源才有，內部來源一律為 `null` |
| `citations[].dataTime` | string \| null | 資料本身的時間（例如收盤日） |
| `citations[].queriedAt` | string | 查詢時間 |
| `citations[].cited` | boolean | 回答是否引用了這筆；`false` 表示只是查過 |
| `queriedAt` | string | 本次提問的處理時間 |
| `disclaimer` | string \| null | 用到市場工具時為「資訊僅供內部經營參考，不構成投資建議」 |
| `toolCalls` | array | 本次的工具呼叫摘要，沒有呼叫工具時為空陣列 |
| `toolCalls[].name` | string | 工具名稱 |
| `toolCalls[].purpose` | string | 中文用途 |
| `toolCalls[].status` | string | `SUCCESS`、`ERROR`、`DENIED`、`TIMEOUT` |
| `toolCalls[].durationMs` | number | 耗時（毫秒） |

`toolCalls` **不包含**參數、工具結果、顧客個資、內部網址、錯誤細節。

> 原本的 `sources` 欄位已移除，由 `citations` 取代。`citations[].title`、`target`、`excerpt`、`description` 與回答文字一樣經過 `PiiRedactor` 處理。各工具的引用內容見 [03-tool-catalog.md](03-tool-catalog.md) 第 1.9 節。

```json
{
  "requestId": "a1b2c3d4",
  "blocks": [
    { "type": "heading", "text": "近期互動", "citations": [] },
    { "type": "bullet", "text": "10/02 電話：本顧客詢問月配息商品，希望每月現金流約 3 萬，已約定 10 月下旬面談", "citations": [1] },
    { "type": "bullet", "text": "近期活動反應：……", "citations": [2] },
    { "type": "heading", "text": "下次聯繫重點", "citations": [] },
    { "type": "numbered", "text": "……", "citations": [] }
  ],
  "answer": "### 近期互動\n\n- 10/02 電話：本顧客詢問月配息商品，希望每月現金流約 3 萬，已約定 10 月下旬面談\n- 近期活動反應：……\n\n### 下次聯繫重點\n\n1. ……",
  "citations": [
    { "id": 1, "toolName": "getRecentInteractions", "title": "近期互動紀錄", "target": null, "excerpt": null,
      "description": "3 筆，最近一筆 10/02", "sourceName": "平台顧客資料（模擬）", "url": null,
      "dataTime": null, "queriedAt": "2026-10-19T14:03:22+08:00", "cited": true },
    { "id": 2, "toolName": "getCampaignResults", "title": "行銷活動反應", "target": null, "excerpt": null,
      "description": "2 筆", "sourceName": "平台顧客資料（模擬）", "url": null,
      "dataTime": null, "queriedAt": "2026-10-19T14:03:23+08:00", "cited": true }
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
| 502 | `AI_ANSWER_FORMAT_INVALID` | 模型的最終回答格式不符、回答為空，或模型拒絕回答 |
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

### 2.3 `PUT /api/ai-assistant/chat/{requestId}/feedback`

對一則成功的回答按讚／倒讚，或取消。設計見 [prompt-evaluation-design](../superpowers/specs/2026-10-24-prompt-evaluation-design.md) 第 3 節。

- **權限**：`AI_ASSISTANT_USE`
- **`requestId`**：對話回應中的 `requestId`，格式 `^[0-9a-f]{8}$`，不符 → 400 `AI_INPUT_INVALID`
- **不計入提問頻率限制**（`AI_RATE_LIMITED` 只針對對話 API）。

Request：

```json
{ "rating": "UP" }
```

| 欄位 | 型別 | 規則 |
|---|---|---|
| `rating` | string \| null | `UP`（有幫助）、`DOWN`（沒有幫助）或 `null`（取消）；其他值 → 400 `AI_INPUT_INVALID` |

- **204**，沒有 body。
- 處理：更新符合 `{ requestId, userId: 目前使用者, outcome: "ANSWERED" }` 的稽核紀錄，寫入 `feedback` 與 `feedbackAt`（取消時 `feedback` 為 null，`feedbackAt` 仍更新）。`requestId` 只有 8 碼十六進位，所以一定同時比對 `userId`；同一人有兩筆相同 `requestId` 時更新 `createdAt` 最新的一筆。
- 對同一則回答可重複呼叫，以最後一次為準；前端對同一則訊息的請求逐一送出，所以伺服器最後保存的是最後一次點擊。

| HTTP | code | 情境 |
|---|---|---|
| 400 | `AI_INPUT_INVALID` | `requestId` 格式錯誤、`rating` 不是 `UP`／`DOWN`／`null` |
| 401 | `AI_UNAUTHENTICATED` | 未登入 |
| 403 | `AI_FORBIDDEN` | 沒有 `AI_ASSISTANT_USE` |
| 404 | `AI_FEEDBACK_NOT_FOUND` | 找不到自己的、成功的對應回答。別人的 `requestId`、不存在的 `requestId`、失敗的提問、稽核寫入失敗的回答，一律回同樣的 404，不透露紀錄是否存在 |

---

## 3. Prompt 管理 API

- **權限**：
  - 查詢類（3.1、3.3、3.4、3.11、3.13–3.16）：`AI_CONFIG_READ`
  - 編輯類（3.2、3.5–3.8、3.10、3.12 開始評測）：`AI_CONFIG_EDIT`
  - 發布類（3.9）：`AI_CONFIG_PUBLISH`
- **版本的辨識**：`version` 是內部流水號，只用於 API 路徑與唯一索引（需求 3-5），**畫面上不顯示**。畫面一律以時間辨識版本：已發布 / 已封存的版本顯示 `updatedAt`（即生效時間）與 `updatedBy`，草稿顯示「最後儲存」時間，已捨棄的版本顯示捨棄時間與捨棄者。
- **設定類型**：
  - `SYSTEM_PROMPT`：只有一份 `system.main`，定義角色、安全規則、回答格式
  - `SYSTEM_INSTRUCTION`：可以有多份，**每份代表一個問題分類**，包含適用問題、回答套版、範例問題
- **`configKey` 格式**：`^(system|instruction)\.[a-z0-9-]{1,50}$`
- **內容上限**：`content` UTF-8 編碼後最多 65,536 bytes
- **狀態與操作**：每個 key 同時只有一份「編輯中版本」（`DRAFT` 或 `SCHEDULED`）。狀態轉換與 `updatedBy` 規則見 [04-mongodb-schema.md](04-mongodb-schema.md) 2.4。

### 3.0 編輯頁操作與 API 對照

畫面結構以 `prototype/prompt-management.html` 為準（設定清單、編輯區、發布前檢查、動作列），但**不顯示平台安全規則**。

```
┌ 聯繫準備（instruction.contact-prep）── 已排程 2026/10/24 09:00 ───┐
│ [ 編輯 | 預覽 ]   ← 分頁切換，一次只顯示一個                         │
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │ 編輯分頁：textarea                                           │ │
│ │ 預覽分頁：SafeMarkdown 渲染結果                                │ │
│ └──────────────────────────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────────────────┤
│ 發布方式：( ) 立即發布  (•) 預約發布 [2026/10/24] [09:00]          │
│                       [捨棄草稿] [存成草稿] [儲存] [更新預約]       │
└──────────────────────────────────────────────────────────────────┘
```

| 按鈕 | 草稿時 | 已排程時 | API |
|---|---|---|---|
| 儲存 | 儲存內容，維持草稿 | 儲存內容，保留排程 | `PUT .../draft`，`saveMode: "KEEP_STATUS"` |
| 存成草稿 | 同「儲存」 | 儲存內容，取消排程、退回草稿 | `PUT .../draft`，`saveMode: "DRAFT"` |
| 發布（立即）／按鈕文字「立即發布」 | 立即發布 | 立即發布（不等預約時間） | 先儲存，再 `POST .../publish`，`publishMode: "IMMEDIATE"` |
| 發布（預約）／按鈕文字「預約發布」或「更新預約」 | 改為已排程 | 修改預約時間 | 先儲存，再 `POST .../publish`，`publishMode: "SCHEDULED"` |
| 捨棄草稿 | 草稿改為「已捨棄」，內容保留在版本歷程 | 停用（需先存成草稿） | `DELETE .../draft` |

- 「發布」由前端 composable 依序呼叫儲存與發布，用儲存回傳的 `docVersion` 接著發布。
- 已排程時，編輯頁頂端顯示預約時間；「發布方式」預設選「預約發布」並帶入目前的預約時間。
- 「編輯 / 預覽」是同一區塊的兩個分頁；切到預覽時以目前 textarea 的內容（含未儲存的修改）渲染。
- 編輯頁標題列（設定名稱與狀態標籤同一列）另有「評測」按鈕，切換到評測檢視（3.11–3.16）。評測使用**已儲存**的編輯中版本，有未儲存的修改時不能執行；發布不需要先評測。

### 3.0.1 版本歷程：比較與套用

- 版本清單列出該設定的所有版本（由新到舊），每列顯示狀態標籤、時間、操作者、修改說明；**不顯示版本號**。
- 狀態標籤：草稿、已排程、已發布、已封存（曾上線、後被取代）、已捨棄（未上線就被放棄）。已捨棄的版本預設收合，以「顯示已捨棄的版本」切換；已捨棄列另外顯示原作者（`createdBy`），若曾排程則顯示「原預約 {scheduledAt}」。
- **比較**：任選兩個版本（任何狀態皆可），以逐行差異顯示 `name` 與 `content` 的不同（新增、刪除標色）。比較在前端進行：以 3.4 分別取得兩版內容後計算差異，不需要額外 API。
- **檢視**：點任一版本可唯讀檢視其內容（`SafeMarkdown` 渲染）。
- **套用**：對任一已發布、已封存或已捨棄的版本按「套用」，以它的 `name` 與 `content` 新增一份草稿（3.10），之後照一般流程儲存、立即發布或預約發布。原本的草稿或排程會改為「已捨棄」，不會遺失。

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
- 清單上以標籤顯示「草稿」或「已排程 10/24 09:00」；`version` 只供後續 API 呼叫使用，不顯示。

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

列出版本歷程，由新到舊（依內部 `version` 排序），不含內容。

```json
[
  { "version": 3, "status": "SCHEDULED", "scheduledAt": "2026-10-24T09:00:00+08:00", "changeNote": "調整回答格式", "basedOnVersion": null, "createdBy": "backoffice.admin", "updatedBy": "backoffice.admin", "updatedAt": "2026-10-20T14:32:00+08:00" },
  { "version": 2, "status": "PUBLISHED", "scheduledAt": null, "changeNote": "新增安全規則", "basedOnVersion": null, "createdBy": "it.admin", "updatedBy": "it.admin", "updatedAt": "2026-10-19T09:30:00+08:00" },
  { "version": 1, "status": "ARCHIVED", "scheduledAt": null, "changeNote": "初始版本", "basedOnVersion": null, "createdBy": "system", "updatedBy": "system", "updatedAt": "2026-10-19T08:00:00+08:00" }
]
```

- 已封存版本的 `updatedAt` / `updatedBy` 是它當初生效的時間與操作者（封存時不改動，見 04 §2.4）。
- `basedOnVersion` 不為 null 時，畫面顯示「套用自 {該版本的 updatedAt} 的版本」。

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
- **404** `AI_CONFIG_NOT_FOUND`：`configKey` 不存在
- **409** `AI_CONFIG_CONFLICT`：`docVersion` 不一致，或這個 `configKey` 已沒有編輯中版本，代表已被他人修改（包括排程剛好到期、已被系統發布，或已被他人捨棄、發布）
- **422** `AI_CONFIG_VALIDATION_FAILED`：已排程版本的內容未通過驗證

### 3.7 `DELETE /api/ai-assistant/admin/configs/{configKey}/draft?docVersion={n}`

捨棄草稿：把 `DRAFT` 改為 `DISCARDED`，**不刪除**，內容保留在版本歷程中，之後仍可比較或套用回來。只能捨棄 `DRAFT`；已排程的版本需先「存成草稿」取消排程。

- **204**
- **404** `AI_CONFIG_NOT_FOUND`：`configKey` 不存在
- **409** `AI_CONFIG_CONFLICT`：`docVersion` 不一致，或這個 `configKey` 已沒有編輯中版本（被排程器發布，或被他人捨棄、發布）
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

- **404** `AI_CONFIG_NOT_FOUND`：`configKey` 不存在
- **409** `AI_CONFIG_CONFLICT`：這個 `configKey` 已沒有編輯中版本（被排程器發布，或被他人捨棄、發布）

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
| `IMMEDIATE` | 在同一個交易中：舊的 PUBLISHED → ARCHIVED（不改動它的 `updatedBy`、`updatedAt`），編輯中版本 → PUBLISHED。已排程的版本也可以提前立即發布 |
| `SCHEDULED` | 編輯中版本 → `SCHEDULED`，寫入 `scheduledAt`；已排程時即修改預約時間。到期由排程器自動發布，`updatedBy` 維持預約者 |

- **200**：回傳發布後或排程後的版本
- **400** `AI_INPUT_INVALID`：`scheduledAt` 缺少或超出範圍
- **404** `AI_CONFIG_NOT_FOUND`：`configKey` 不存在
- **409** `AI_CONFIG_CONFLICT`：`docVersion` 不一致，或這個 `configKey` 已沒有編輯中版本（被排程器發布，或被他人捨棄、發布）
- **422** `AI_CONFIG_VALIDATION_FAILED`：`details` 格式同 3.8 的 `errors`

### 3.10 `POST /api/ai-assistant/admin/configs/{configKey}/versions/{version}/apply`

套用任一已發布、已封存或已捨棄的版本：以它的 `name` 與 `content` **新增一份草稿**，記錄 `basedOnVersion`。**不會直接發布**，之後照 3.9 立即發布或預約發布（因此套用的內容一樣會經過發布前驗證，也可以預約）。取代原本的「回復上一版」。

| 欄位 | 型別 | 必填 | 規則 |
|---|---|---|---|
| `workingCopyDocVersion` | number \| null | 有編輯中版本時必填 | 使用者看到的編輯中版本 `docVersion`；沒有編輯中版本時為 `null` |
| `confirmOverwrite` | boolean | 有編輯中版本時必填 `true` | 使用者已確認目前的草稿或排程會被改為「已捨棄」 |

```json
{ "workingCopyDocVersion": 7, "confirmOverwrite": true }
```

| 目前的編輯中版本 | 結果（在同一個交易中完成） |
|---|---|
| 沒有 | 新增一份 `DRAFT` |
| `DRAFT` | 原草稿改為 `DISCARDED`（內容保留）；新增一份 `DRAFT` |
| `SCHEDULED` | 原排程版本改為 `DISCARDED`（排程取消，`scheduledAt` 保留作為「原預約時間」紀錄）；新增一份 `DRAFT` |

新增的草稿：`version` = 最大值 + 1，`name`、`content` 取自來源版本，`basedOnVersion` = 來源版本，`changeNote` 空，`createdBy`、`updatedBy` 為操作者。被改為 `DISCARDED` 的版本，`updatedBy`、`updatedAt` 為操作者與現在時間，`createdBy` 保留原作者。

**有編輯中版本時的流程**

1. 前端送出套用（`confirmOverwrite` 為 false 或未帶）。
2. 後端發現已有編輯中版本，回 409 `AI_CONFIG_OVERWRITE_PENDING`：

   ```json
   {
     "code": "AI_CONFIG_OVERWRITE_PENDING",
     "message": "此設定已有編輯中的版本，套用後它會被標為已捨棄",
     "requestId": "a1b2c3d4",
     "details": [ { "status": "SCHEDULED", "scheduledAt": "2026-10-24T09:00:00+08:00", "updatedAt": "2026-10-20T14:32:00+08:00", "docVersion": 7 } ]
   }
   ```

3. 前端跳出確認視窗：
   - 草稿時：「目前有一份 {updatedAt} 儲存的草稿，套用後它會被標為已捨棄（仍可在版本歷程中找回）。確定要套用嗎？」
   - 已排程時：「目前有一份預約於 {scheduledAt} 發布的版本，套用後排程會取消，該版本會被標為已捨棄（仍可在版本歷程中找回）。確定要套用嗎？」
4. 使用者確認後，前端帶 `confirmOverwrite: true` 與 `details[0].docVersion` 重送。

**回應**

- **200**：回傳套用後的草稿（格式同 3.4），前端切到編輯分頁顯示它
- **404** `AI_CONFIG_NOT_FOUND`：來源版本不存在
- **409** `AI_CONFIG_OVERWRITE_PENDING`：已有編輯中版本但未確認
- **409** `AI_CONFIG_CONFLICT`：`workingCopyDocVersion` 不一致（有人剛修改，或排程剛到期而已發布）
- **409** `AI_CONFIG_INVALID_STATE`：來源版本是 `DRAFT` 或 `SCHEDULED`（只能套用已發布、已封存或已捨棄的版本）

### 3.11 `GET /api/ai-assistant/admin/configs/{configKey}/feedback-stats`

各版本的使用者回饋統計，供版本歷程顯示。

- **權限**：`AI_CONFIG_READ`

```json
[
  { "version": 3, "up": 12, "down": 3 },
  { "version": 2, "up": 40, "down": 15 }
]
```

- 依 `version` 由新到舊排列；沒有回饋的版本不出現。`version` 只供前端對應到各版本列，畫面仍以時間辨識版本。
- 聚合範圍是稽核紀錄中 `feedback` 有值、且 `promptVersions` 含該 `configKey` 的回答，所以只涵蓋稽核保存期限（180 天）內的回答，畫面以小字註明。
- **計入規則依設定類型**：`system.main`（`SYSTEM_PROMPT`）計入所有有回饋的回答；問題分類（`SYSTEM_INSTRUCTION`）只計入 `routeSource` 為 `ROUTER` 或 `CHIP` 的回答，`FALLBACK`（載入全部分類）與沒有 `routeSource` 的舊紀錄不計入。版本歷程的小字：問題分類為「回饋統計涵蓋 180 天內、且確定使用此分類的回答」，`system.main` 為「回饋統計涵蓋 180 天內的回答」。
- **400** `AI_INPUT_INVALID`：`configKey` 格式錯誤
- **404** `AI_CONFIG_NOT_FOUND`：`configKey` 不存在

### 3.12 `POST /api/ai-assistant/admin/configs/{configKey}/evaluations`

開始評測：以固定題目比較「目前已發布的全部設定」與「把這個 key 的編輯中版本換進去的同一組設定」。非同步執行，立即回 202。

- **權限**：`AI_CONFIG_EDIT`（`AI_CONFIG_READ` 不能開始評測）

Request：

```json
{ "docVersion": 5 }
```

| 欄位 | 型別 | 必填 | 規則 |
|---|---|---|---|
| `docVersion` | number | ✓ | 編輯中版本目前的 `docVersion` |

請求**不含**顧客或存取等級的欄位：評測的顧客與存取等級只來自資料集檔案，管理者無法藉評測查詢任意顧客。

Response 202：

```json
{ "runId": "6719f0c2a4b5e6f7a8b9c0d1", "status": "RUNNING", "totalCases": 17 }
```

同步檢查依序為：

| 順序 | HTTP | code | 情境 |
|---|---|---|---|
| 1 | 404 | `AI_CONFIG_NOT_FOUND` | `configKey` 不存在 |
| 2 | 409 | `AI_CONFIG_INVALID_STATE` | 沒有編輯中版本（`DRAFT` 或 `SCHEDULED`） |
| 3 | 409 | `AI_CONFIG_CONFLICT` | `docVersion` 不一致 |
| 4 | 422 | `AI_CONFIG_VALIDATION_FAILED` | 編輯中版本未通過發布前驗證；`details` 同 3.8 的 `errors` |
| 5 | 409 | `AI_EVAL_RUNNING` | 任何 key 已有執行中的評測；`details` 為 `[{ runId, configKey, triggeredBy, completedCases, totalCases }]` |

另有 400 `AI_INPUT_INVALID`（`configKey` 格式錯誤或缺少 `docVersion`）、401、403，以及評測任務無法交給執行緒時的 500 `AI_INTERNAL_ERROR`。

- 快照在回應前建立：已發布設定直接讀 MongoDB（不經 60 秒快取）加上編輯中版本的內容；執行期間有人修改、發布或捨棄草稿都不影響這次評測。
- 沒有被取消的 API；執行中的評測要等它結束，或超過 `run-timeout`（30 分鐘）。

### 3.13 `GET /api/ai-assistant/admin/configs/{configKey}/evaluations`

這個 key 最近 10 次評測（摘要，不含 `cases`），由新到舊。

- **權限**：`AI_CONFIG_READ`

```json
[
  {
    "runId": "6719f0c2a4b5e6f7a8b9c0d1",
    "status": "COMPLETED",
    "errorCode": null,
    "triggeredBy": "it.admin",
    "startedAt": "2026-10-24T15:02:11+08:00",
    "finishedAt": "2026-10-24T15:07:40+08:00",
    "candidateVersion": 6,
    "candidateUpdatedAt": "2026-10-24T14:58:03+08:00",
    "candidateDocVersion": 5,
    "datasetChanged": false,
    "totalCases": 17,
    "completedCases": 17,
    "summary": {
      "baseline":  { "correct": 14, "total": 17, "toolsOk": 16, "routeOk": 13, "routeCases": 13,
                     "refusedExpected": 6, "expectedRefusals": 6,
                     "refusedNormal": 0, "normalCases": 11, "avgTotalTokens": 7420, "avgInputTokens": 7080,
                     "avgOutputTokens": 340, "avgDurationMs": 6200 },
      "candidate": { "correct": 16, "total": 17, "toolsOk": 16, "routeOk": 12, "routeCases": 13,
                     "refusedExpected": 6, "expectedRefusals": 6,
                     "refusedNormal": 1, "normalCases": 11, "avgTotalTokens": 7610, "avgInputTokens": 7230,
                     "avgOutputTokens": 380, "avgDurationMs": 6500 },
      "judgeTokens": 48200
    }
  }
]
```

- `status`：`RUNNING`、`COMPLETED`、`FAILED`；`FAILED` 時 `errorCode` 為 `AI_EVAL_TIMEOUT`、`AI_EVAL_INTERRUPTED` 或 `AI_INTERNAL_ERROR`。`finishedAt` 只有執行中為 `null`（失敗時與 `FAILED` 一起寫入）；`summary` 在執行中或失敗時為 `null`。
- `datasetChanged`：這次的 `datasetHash` 與目前載入的資料集不同。
- `candidateVersion` 只供前端比對「目前編輯中版本是否已評測」，畫面不顯示版本號。`candidateDocVersion` 與 `candidateUpdatedAt` 用來判斷「評測後內容已修改」。
- 比率（正確率等）不在回應中，前端由原始計數計算。
- **400** `AI_INPUT_INVALID`：`configKey` 格式錯誤

### 3.14 `GET /api/ai-assistant/admin/evaluations/current`

目前執行中的評測（任何 key）。

- **權限**：`AI_CONFIG_READ`
- **200**：`{ "runId": "…", "configKey": "instruction.contact-prep", "triggeredBy": "it.admin", "startedAt": "2026-10-24T15:02:11+08:00", "totalCases": 17, "completedCases": 7 }`
- **204**：沒有執行中的評測。呼叫時會先處理陳舊紀錄：`RUNNING` 且 `heartbeatAt` 超過 10 分鐘者標為 `FAILED`（`AI_EVAL_INTERRUPTED`）。

### 3.15 `GET /api/ai-assistant/admin/evaluations/{runId}`

單次評測的進度與完整結果；執行中也可查詢，`cases` 只含已完成的題目。

- **權限**：`AI_CONFIG_READ`
- **`runId`**：24 碼十六進位，格式不符 → 400 `AI_INPUT_INVALID`；不存在或已過期 → 404 `AI_EVAL_NOT_FOUND`

Response 200：3.13 的欄位，加上 `configKey` 與 `cases`：

```json
{
  "runId": "6719f0c2a4b5e6f7a8b9c0d1",
  "configKey": "instruction.contact-prep",
  "status": "COMPLETED",
  "errorCode": null,
  "triggeredBy": "it.admin",
  "startedAt": "2026-10-24T15:02:11+08:00",
  "finishedAt": "2026-10-24T15:07:40+08:00",
  "candidateVersion": 6,
  "candidateUpdatedAt": "2026-10-24T14:58:03+08:00",
  "candidateDocVersion": 5,
  "datasetChanged": false,
  "totalCases": 17,
  "completedCases": 17,
  "summary": { "baseline": { "…": "…" }, "candidate": { "…": "…" }, "judgeTokens": 48200 },
  "cases": [
    {
      "caseId": "Q3-contact-prep",
      "group": "顧客經營",
      "question": "整理近期互動，幫我準備下次聯繫重點",
      "expectRefusal": false,
      "expectCategory": "instruction.contact-prep",
      "baseline": {
        "outcome": "ANSWERED",
        "errorCode": null,
        "answer": "### 近期互動\n\n- 10/02 電話：……",
        "toolCalls": [ { "toolName": "getRecentInteractions", "status": "SUCCESS" } ],
        "modelCalls": 2,
        "inputTokens": 7080,
        "outputTokens": 340,
        "totalTokens": 7420,
        "durationMs": 6200,
        "unverifiedNumbers": [],
        "toolsOk": true,
        "numbersOk": true,
        "routeOk": true,
        "routeSource": "ROUTER",
        "instructionKey": "instruction.contact-prep",
        "missingPhrases": [],
        "forbiddenPhrases": [],
        "judge": {
          "refused": false,
          "rubric": [ { "text": "列出近期互動的日期與管道", "met": true } ],
          "comment": "無",
          "error": null,
          "tokens": 1500
        },
        "passed": true
      },
      "candidate": { "…": "…" }
    }
  ]
}
```

- `answer` 是回應用的純 Markdown（不含引用標註），`question` 是去識別化後送給模型的提問；兩者與稽核紀錄同級敏感，所以只有 `AI_CONFIG_READ` 可讀。
- 作答失敗（`outcome: FAILED`）時 `answer` 與 `judge` 為 `null`，`errorCode` 為對話錯誤碼（05 第 2.2 節）或 `AI_CUSTOMER_NOT_FOUND`。
- 評審失敗時 `judge` 為 `{ "refused": null, "rubric": [], "comment": null, "error": "AI_EVAL_JUDGE_FAILED", "tokens": … }`（`rubric` 是空陣列，不是 `null`）。
- 路由欄位：`expectCategory` 是題目的 `expect.category`（未設定為 `null`）；`routeOk` 為 `true`／`false`，題目沒有預期分類時為 `null`；`routeSource` 為 `ROUTER` 或 `FALLBACK`（評測沒有快捷按鈕；路由前就失敗時為 `null`）；`instructionKey` 是實際載入的分類，`none` 與 `FALLBACK` 為 `null`。`summary` 的 `routeOk` 是通過題數、`routeCases` 是有預期分類的題數（舊紀錄沒有這兩個欄位）。
- 評審 system message 與路由 system message 只存在程式碼，任何 API 都不回傳。

### 3.16 `GET /api/ai-assistant/admin/evaluation-cases`

瀏覽評測資料集（唯讀）。

- **權限**：`AI_CONFIG_READ`

```json
{
  "datasetHash": "3f9a…（SHA-256，共 64 字元）",
  "cases": [
    {
      "id": "Q3-contact-prep",
      "group": "顧客經營",
      "accessLevel": "FULL",
      "customerId": "C001",
      "question": "整理近期互動，幫我準備下次聯繫重點",
      "expect": {
        "tools": ["getRecentInteractions", "getCampaignResults"],
        "forbiddenTools": [],
        "refusal": false,
        "mustContain": [],
        "mustNotContain": ["王小明", "A123456789", "0912-000-111"],
        "category": "instruction.contact-prep"
      },
      "rubric": ["列出近期互動的日期與管道", "提到待辦事項「約定 10 月下旬面談」", "提出 2 到 3 個具體的下次聯繫重點"]
    }
  ]
}
```

- 資料集來自 `classpath:eval/eval-cases.yml`，隨 git 版控，管理頁不提供編輯；共 17 題（初版 16 題，第二輪新增 X2-indirect；題目清單見 [06-conversation-scenarios.md](06-conversation-scenarios.md) 1.3）。
- `customerId` 是資料集指定的假測試顧客編號，不是真實顧客。
- `expect.category` 是預期的問題分類（`configKey` 或 `none`），題目不檢查分類時為 `null`。

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
