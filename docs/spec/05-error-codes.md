# 05 錯誤碼清單

> 版本：v1.0（POC）・2026-10-26

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
| `AI_ANSWER_FORMAT_INVALID` | 502 | 回答格式異常，請重試 | 模型的最終回答不是合法 JSON、不符合 schema、沒有任何非空白區塊；最終回答為 null 或空白（包含用完工具次數後的最後一次請求仍只回 tool call）；或模型拒絕回答（OpenAI 的 refusal 會以 LangChain4j 的 `ContentFilteredException` 到達，對應到這個錯誤碼）。不自動重試 |

> `AI_ANSWER_FORMAT_INVALID` 的稽核紀錄為 `outcome: FAILED`、`errorCode: AI_ANSWER_FORMAT_INVALID`，且 `answer` 為 null、`toolCalls[].citationId` 為 null（整題失敗時不配發引用編號）；已成功的工具呼叫仍照常記錄 `target`、`dataTime`、`resultCount`、`resultHash`。前端沿用既有的錯誤訊息與重試按鈕。我們自己的規則造成的拒答（例如拒絕透露系統提示）是正常的區塊回答，不屬於這個錯誤。

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

### 2.4 回饋與評測

| code | HTTP | 友善訊息 | 情境 |
|---|---|---|---|
| `AI_FEEDBACK_NOT_FOUND` | 404 | 這則回答已無法評分 | 找不到「自己的、成功的」對應回答：別人的 `requestId`、不存在的 `requestId`、失敗的提問、稽核寫入失敗的回答，一律同樣的 404，不透露紀錄是否存在 |
| `AI_EVAL_RUNNING` | 409 | 目前已有評測執行中，請稍後再試 | 任何 key 已有執行中的評測；`details` 為單一物件的陣列：`runId`、`configKey`、`triggeredBy`、`completedCases`、`totalCases`（兩個請求同時搶、對方又恰好結束時可能是空陣列） |
| `AI_EVAL_NOT_FOUND` | 404 | 找不到指定的評測紀錄 | `runId` 不存在或已過期（紀錄 180 天後自動刪除） |

開始評測（02 3.12）還會回既有的 `AI_CONFIG_NOT_FOUND`（404）、`AI_CONFIG_INVALID_STATE`（409，沒有編輯中版本）、`AI_CONFIG_CONFLICT`（409，`docVersion` 不一致）、`AI_CONFIG_VALIDATION_FAILED`（422，`details` 同 2.3）；評測任務無法交給執行緒時回 `AI_INTERNAL_ERROR`（500）。

**只寫入評測紀錄、不由 API 回傳**的代碼（字串常數，不在 `ErrorCode` enum）：

| 代碼 | 寫在哪裡 | 情境 |
|---|---|---|
| `AI_EVAL_TIMEOUT` | 評測紀錄 `errorCode`（`status: FAILED`） | 整次評測超過 `run-timeout`（預設 30 分鐘） |
| `AI_EVAL_INTERRUPTED` | 評測紀錄 `errorCode`（`status: FAILED`） | `RUNNING` 紀錄的 `heartbeatAt` 超過 `stale-after`（預設 10 分鐘）：後端重啟或執行緒異常中斷 |
| `AI_EVAL_JUDGE_FAILED` | `cases[].baseline／candidate.judge.error` | 評審 GPT 逾時、供應商錯誤、回傳不是合法 JSON，或 `rubric` 的 index 沒有剛好涵蓋 1..n；不重試，該題不算正確 |

- 評測紀錄的 `errorCode` 另可能是 `AI_INTERNAL_ERROR`（執行緒內的未預期例外，或任務無法交給執行緒）。
- 評測中單題作答失敗時，`cases[].*.errorCode` 沿用 2.2 的對話錯誤碼與 `AI_CUSTOMER_NOT_FOUND`（資料集的測試顧客不存在）；模型呼叫失敗與 `chat()` 同樣對應（例如 `AI_LLM_UNAVAILABLE`、`AI_LLM_TIMEOUT`），不會中止整次評測。

## 3. 工具錯誤碼

工具錯誤不會變成 HTTP 錯誤，對話仍回傳 200。

| errorCode | 摘要狀態 | 交給模型的訊息 | 情境 |
|---|---|---|---|
| `TOOL_NOT_ALLOWED` | DENIED | 此工具無法使用 | 模型呼叫了不存在或未提供的工具（例如去識別化身分的模型呼叫了互動紀錄工具） |
| `TOOL_ARGUMENT_INVALID` | DENIED | 參數格式不正確 | 參數驗證失敗，或含有 schema 以外的欄位（例如模型自行加上 `customerId`） |
| `CUSTOMER_DATA_UNAVAILABLE` | ERROR | 目前無法取得本顧客資料 | 綁定的顧客在查詢當下已無資料 |
| `STOCK_NOT_FOUND` | ERROR | 查無此上市股票代號 | 代號不存在或為上櫃股票 |
| `TOOL_CALL_LIMIT_REACHED` | DENIED | 已達本次查詢次數上限，未執行 | 單次提問的工具呼叫超過 5 次，超過的部分不執行 |
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
| 回饋 404 `AI_FEEDBACK_NOT_FOUND` | 全域 Toast 顯示友善訊息，回答的按鈕還原為伺服器最後確認的狀態 |
| 版本歷程的回饋統計失敗 | 靜默（不跳 Toast、不顯示統計）：`AI_FORBIDDEN`、`AI_CONFIG_NOT_FOUND`、網路錯誤、`AI_INTERNAL_ERROR`；`AI_UNAUTHENTICATED` 不靜默，交給 401 的登入逾時處理 |
| 開始評測 409 `AI_EVAL_RUNNING` | 不跳 Toast；改查 `evaluations/current`，顯示正在評測的人與進度並停用按鈕 |
| 評測輪詢失敗 | 單次失敗保留畫面並重試；連續 3 次失敗停止輪詢 |
| 評測題目作答失敗 | 在題目清單顯示該錯誤碼的友善訊息（前端 `caseErrorMessage`，未知代碼直接顯示代碼）；評測歷程的 `FAILED` 列依 `errorCode` 顯示「逾時」「已中斷」「系統錯誤」 |
| 未知錯誤碼 | Toast 顯示通用訊息，不顯示後端原始內容 |

## 5. Log 規範

| 等級 | 內容 |
|---|---|
| INFO | requestId、userId、耗時、模型往返次數、tool call 次數、token 數（總數，另分 `inputTokens`、`outputTokens`）、回答區塊數（`blocks`）、有引用的區塊數（`citedBlocks`，不含 heading）、單次回應的最大輸出 token 數（`peakOutputTokens`） |
| WARN | 工具失敗、限流、驗證失敗（只記錯誤碼） |
| ERROR | GPT 或 TWSE 錯誤類型、未預期例外（stack trace 只在後端 log，敏感字串遮罩） |

**評測與回饋的 log** 同樣只有計數與代碼（INFO 的 `eval run started／finished`、`eval case done`；WARN／ERROR 的 `eval run stopped／rejected／failed`、`eval judge failed`、`eval turn failed`，格式見 [prompt-evaluation-design](../superpowers/specs/2026-10-24-prompt-evaluation-design.md) 4.2）。評測作答時 MDC 的 `requestId` 為 `e` + runId 後 7 碼。評測不寫「chat answered／failed」兩行。稽核寫入的 `promptVersions` 超過 100 筆時截斷並記 WARN（只含 requestId 與筆數）。

**任何等級都不記錄**：使用者提問全文、模型回答全文、模型回傳的原始 JSON、工具參數、工具結果、評測題目與評審評語、API key。

> 提問與回答依需求 3-4 只寫入稽核 collection `COLL_ICRM_AI_ASSISTANT_AUDIT`（見 [04-mongodb-schema.md](04-mongodb-schema.md) 第 3 節），不進應用程式 log。
