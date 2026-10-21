# 05 錯誤碼清單

> 版本：v0.9（POC）・2026-10-21

## 1. 原則

- 錯誤分兩層：
  - **API 錯誤**：回傳 HTTP 錯誤，前端以 Toast 或對話視窗內的提示顯示。
  - **工具錯誤**：不中斷對話，寫進工具結果交給模型說明，並顯示在 tool call 摘要的狀態欄。
- `message` 一律是固定的友善訊息。供應商原始錯誤、stack trace、內部網址、token 只會出現在後端 log（並遮罩），不會回傳給前端或送給模型。
- 每個錯誤回應都帶 `requestId`，方便對照後端 log。

錯誤回應格式：

```json
{ "code": "AI_LLM_TIMEOUT", "message": "小幫手回應逾時，請稍後再試", "requestId": "a1b2c3d4", "details": [] }
```

## 2. API 錯誤碼

### 2.1 共通

| code | HTTP | 友善訊息 | 情境 |
|---|---|---|---|
| `AI_INPUT_INVALID` | 400 | 輸入內容不符合格式，請確認後再試 | 欄位格式錯誤、超過長度；`details` 列出欄位 |
| `AI_UNAUTHENTICATED` | 401 | 登入已逾時，請重新登入 | 沒有 session |
| `AI_FORBIDDEN` | 403 | 您沒有使用此功能的權限 | 缺少必要權限 |
| `AI_RATE_LIMITED` | 429 | 提問太頻繁，請稍候一分鐘再試 | 超過每人每分鐘 10 次 |
| `AI_INTERNAL_ERROR` | 500 | 系統發生錯誤，請稍後再試 | 未預期錯誤 |

### 2.2 對話

| code | HTTP | 友善訊息 | 情境 |
|---|---|---|---|
| `AI_CUSTOMER_NOT_FOUND` | 404 | 查無此顧客或您沒有查看權限 | 顧客不存在，或不在使用者的範圍內（兩者回應相同，不透露顧客是否存在） |
| `AI_TOKEN_BUDGET_EXCEEDED` | 422 | 問題涉及的資料太多，請縮小範圍後再問 | 累計 token 超過 16,000 |
| `AI_LLM_TIMEOUT` | 504 | 小幫手回應逾時，請稍後再試 | 單次 GPT 呼叫超過 30 秒 |
| `AI_REQUEST_TIMEOUT` | 504 | 處理時間過長，請簡化問題後再試 | 整體超過 60 秒 |
| `AI_LLM_RATE_LIMITED` | 503 | 小幫手目前使用量較高，請稍後再試 | GPT 供應商回傳 429 |
| `AI_LLM_UNAVAILABLE` | 503 | 小幫手暫時無法使用，其他功能不受影響 | GPT 連線失敗、5xx、key 無效（401）等 |

### 2.3 Prompt 管理

| code | HTTP | 友善訊息 | 情境 |
|---|---|---|---|
| `AI_CONFIG_NOT_FOUND` | 404 | 找不到指定的設定或版本 | key、版本或草稿不存在 |
| `AI_CONFIG_CONFLICT` | 409 | 內容已被他人更新，請重新載入後再編輯 | `docVersion` 不一致；同時發布造成 unique index 衝突；套用時編輯中版本已變更 |
| `AI_CONFIG_INVALID_STATE` | 409 | 目前狀態無法執行此操作 | 已有編輯中版本卻要建立草稿；捨棄已排程的版本；套用的來源是草稿或已排程版本；key 已存在；要新增第二份 System Prompt |
| `AI_CONFIG_OVERWRITE_PENDING` | 409 | 此設定已有編輯中的版本，套用後它會被標為已捨棄 | 套用歷史版本時已有草稿或排程，但未帶 `confirmOverwrite`；`details` 含編輯中版本的狀態、預約時間、最後儲存時間、`docVersion` |
| `AI_CONFIG_VALIDATION_FAILED` | 422 | 內容未通過發布檢查，請依提示修正 | 發布前驗證失敗；`details` 列出每條規則 |

`AI_CONFIG_VALIDATION_FAILED` 的 `details`：

```json
[
  { "rule": "REQUIRED_SECTION", "message": "缺少必要章節：## 安全規則" },
  { "rule": "FORBIDDEN_CONTENT", "message": "第 12 行疑似包含網址" }
]
```

`rule` 的值：`NOT_EMPTY`、`MAX_SIZE`、`REQUIRED_SECTION`、`EXAMPLE_QUESTIONS`、`FORBIDDEN_CONTENT`

## 3. 工具錯誤碼

工具錯誤不會變成 HTTP 錯誤，對話仍回傳 200。

| errorCode | 摘要狀態 | 交給模型的訊息 | 情境 |
|---|---|---|---|
| `TOOL_NOT_ALLOWED` | DENIED | 此工具無法使用 | 模型呼叫了不存在或未提供的工具（例如去識別化身分的模型呼叫了互動紀錄工具） |
| `TOOL_ARGUMENT_INVALID` | DENIED | 參數格式不正確 | 參數驗證失敗，或含有 schema 以外的欄位（例如模型自行加上 `customerId`） |
| `CUSTOMER_DATA_UNAVAILABLE` | ERROR | 目前無法取得本顧客資料 | 綁定的顧客在查詢當下已無資料 |
| `STOCK_NOT_FOUND` | ERROR | 查無此上市股票代號 | 代號不存在或為上櫃股票 |
| `TOOL_TIMEOUT` | TIMEOUT | 查詢逾時 | 工具執行超過 timeout |
| `MARKET_DATA_TIMEOUT` | TIMEOUT | 市場資料查詢逾時 | TWSE 逾時 |
| `MARKET_DATA_UNAVAILABLE` | ERROR | 市場資料暫時無法取得 | TWSE 連線失敗、非 2xx、格式錯誤、超過大小上限 |
| `TOOL_EXECUTION_FAILED` | ERROR | 查詢失敗 | 其他未預期錯誤 |

> 顧客存取權限在進入對話 API 時就已判斷（無權限回 404 `AI_CUSTOMER_NOT_FOUND`），顧客工具也沒有顧客參數，所以工具層不會出現「顧客越權」錯誤。

## 4. 前端處理

| 情況 | 顯示方式 |
|---|---|
| 對話 API 錯誤 | 對話視窗內顯示錯誤提示，並同時跳 Toast；保留使用者的輸入以便重送 |
| 401 | Toast 後導向登入頁 |
| 404 `AI_CUSTOMER_NOT_FOUND` | 對話視窗內顯示「查無此顧客或您沒有查看權限」，停用輸入框 |
| 管理 API 409 衝突 | Toast，提供「重新載入」按鈕 |
| 管理 API 409 `AI_CONFIG_OVERWRITE_PENDING` | 不跳 Toast，改跳確認視窗（草稿：會被標為已捨棄；已排程：排程取消並標為已捨棄；兩者都說明仍可在版本歷程找回）；確認後帶 `confirmOverwrite: true` 與 `details[0].docVersion` 重送 |
| 管理 API 400（預約時間） | 在預約時間欄位下方顯示「預約時間需在 1 分鐘後、90 天內」 |
| 管理 API 422 驗證失敗 | 在編輯器下方列出每條 `details` |
| 使用者按「取消」 | 不視為錯誤，訊息標示「已取消」，不跳 Toast |
| 網路中斷、無回應 | Toast「網路連線異常，請稍後再試」 |
| 未知錯誤碼 | Toast 顯示通用訊息，不顯示後端原始內容 |

## 5. Log 規範

| 等級 | 內容 |
|---|---|
| INFO | requestId、userId、耗時、模型往返次數、tool call 次數、token 數 |
| WARN | 工具失敗、限流、驗證失敗（只記錯誤碼） |
| ERROR | GPT 或 TWSE 錯誤類型、未預期例外（stack trace 只在後端 log，敏感字串遮罩） |

**任何等級都不記錄**：使用者提問全文、模型回答全文、工具參數、工具結果、API key。

> 提問與回答依需求 3-4 只寫入稽核 collection `COLL_ICRM_AI_ASSISTANT_AUDIT`（見 [04-mongodb-schema.md](04-mongodb-schema.md) 第 3 節），不進應用程式 log。
