# LLM 可觀測性設計

- 日期：2026-11-10
- 版本：v0.3
- 狀態：已實作，待手動驗證
- v0.3（2026-11-10）：實作與設計的差異：(1) 測試輔助 `LogCapture` 新增 `of(String)`、`of(String, Level)`，因為測試的預設 log 等級是 ERROR，擷取 INFO 需要指定等級；(2) 以 Mockito 模擬 `ChatModel` 的測試（`ChatCitationFlowTest`、`EvalRunnerTestBase`、`ChatControllerTest`）改為 stub／verify 兩個參數的 `chat(request, options)`，第 6 節第 6 項「不影響既有測試」的說法已更正；(3) 第 6 節第 7 項手動驗證尚未執行，步驟已列入 [demo-checklist.md](../../demo-checklist.md) 步驟 28
- v0.2（2026-11-10）：執行前複查並以程式實測：失敗時的模型名稱改取自請求（listener 不再依賴設定檔）；補上實測確認的行為（3.2）；用途測試改為各呼叫點各自驗證；架構規則不寫死呼叫點數量；待確認新增平台 JSON layout、內容過濾與評測 log 量。
- 起因：主管在 [M34](../../questions-for-manager.md) 回覆加分項「可觀測性」要做（[requirements.md](../../../requirements.md) 第 12 項）。現有稽核以「一題」為單位，看不到單次模型呼叫；平台把外部呼叫定義為 Outbound Log（[reference.md](../../../reference.md) 6-4），呼叫 OpenAI 目前沒有任何 Outbound log。
- 前提：維持 [Clean Architecture 分層](2026-11-02-clean-architecture-layering-design.md) 與 `ArchitectureRulesTest` 全綠。
- 相關規格：[01-architecture.md](../../spec/01-architecture.md) 1.2、5.1；[05-error-codes.md](../../spec/05-error-codes.md) 2.2；reference.md 6（日誌管理規範）

## 1. 目標與決策

目的：**以 Outbound Log 的形式記錄每一次模型呼叫，補上單次呼叫的耗時、各用途的 token 與錯誤分類，並讓它在 ALP 能用 traceId 串起來；整題層級的資訊仍以稽核為準。**

優先順序：查問題（哪一次呼叫慢或失敗）> 追成本（各用途的 token）> 展示（dev 的 Debug Console 看得到每次呼叫）。

| 項目 | 決策 | 節 |
| --- | --- | --- |
| 輸出管道 | 只寫 log，送 ALP（EFK）。不加 Micrometer：平台沒有 metrics 後端，官方 `langchain4j-micrometer-metrics` 也只記 token | 2 |
| 攔截方式 | 自訂 `ChatModelListener`，不用 `@OutboundLog` AOP。AOP 攔不到 `OpenAiChatModel`，包一層也拿不到 token、用途、真實狀態碼 | 3 |
| 格式 | 欄位名稱與型別照平台 Outbound log（ALP Table D），新增欄位放在獨立的 `llm` 物件 | 4 |
| 實作歸屬 | 自己實作在 `copilot` 套件內，不依賴平台的 `LogSummaryFormatter` 等類別，平移時整包搬走 | 3 |
| JSON 產生方式 | 用 Jackson `JsonMapper`，不拼字串，保證是合法的單行 JSON | 4 |
| 失敗的錯誤細節 | ERROR，附**去掉訊息**的 StackTrace（保留類型與呼叫位置）加上 HTTP 狀態碼。同時滿足 reference.md 516（ERROR 要有 StackTrace）與 524（不得含機敏資訊） | 5 |
| 用途標記 | 呼叫點以 `ChatRequestOptions.listenerAttributes` 帶入 | 3.2 |
| 成功的 log 等級 | INFO，寫入 `Outbound` logger，與平台 `OutboundLogAspect` 相同 | 4 |
| 現有 `log.info` | 一律不動（含 `chat answered`、`feedback set`），之後隨環境 profile 處理 | 7 |

不做的事：

- 不加 Micrometer、Prometheus、儀表板或告警。
- 不在 log 裡重複記錄整題層級的資訊，那是稽核的工作。
- 不記 prompt、回答、工具參數或任何回應內容。
- 不在 dev 本機另外寫 log 檔；看 log 用 Debug Console 的過濾框。
- 不修改平台檔案（`com.example.aspect`、`com.example.logging` 等）。

## 2. 現況與缺口

| 想知道的事 | 現有來源 | 是否足夠 |
| --- | --- | --- |
| 一題的總耗時、token、結果 | 稽核；`chat answered`／`chat failed` log | 足夠 |
| 每個工具的耗時 | 稽核 `toolCalls[].durationMs` | 足夠 |
| 模型呼叫失敗的原因 | 回答輪：`LLM call failed` ERROR；路由、摘要、步驟參數：各自一行 WARN（只有錯誤碼） | 有錯誤碼，沒有耗時，格式不一 |
| 一題慢時，慢在哪一次模型呼叫 | 無 | **缺** |
| 各用途的 token | 只有 `summaryTokens` 分開，路由與步驟參數的 token 混在總數 | **缺** |
| 維運在 Kibana 用 traceId 串起請求與 OpenAI 呼叫 | 稽核在 Mongo，維運看不到；沒有 Outbound log | **缺** |

稽核與可觀測性的分工：

| | 稽核 | 可觀測性（本設計） |
| --- | --- | --- |
| 回答的問題 | 誰在何時問了什麼、回答了什麼、用了哪些資料 | 哪裡慢、哪裡壞、花了多少 |
| 單位 | 一題 | 一次模型呼叫 |
| 內容 | 含遮罩後的問題與回答 | 不含任何內容 |
| 存放 | Mongo，保留 5 年，必須完整 | ALP，隨 log 輪替，漏一行可接受 |
| 讀者 | 稽核、法遵、管理者 | 開發、維運 |

## 3. 架構

### 3.1 元件

| 元件 | 位置 | 職責 |
| --- | --- | --- |
| `LlmCallPurpose`（enum） | `application/llm` | `ROUTE`、`SUMMARY`、`STEP_ARGUMENTS`、`ANSWER`、`JUDGE`；`options()` 回傳帶用途屬性的 `ChatRequestOptions` |
| `OutboundLlmLogListener` | `infrastructure/llm` | 實作 `ChatModelListener`，每次呼叫寫一行 Outbound log（第 4、5 節） |
| `MessageFreeThrowable` | `infrastructure/llm` | 複製例外鏈，保留類型與呼叫位置，清空訊息（5.2） |
| `LlmConfiguration` | 修改 | `OpenAiChatModel.builder().listeners(...)` 註冊 listener |
| 5 個呼叫點 | 修改 | 改呼叫 `chatModel.chat(request, LlmCallPurpose.X.options())` |
| `ChatOrchestrator` | 修改 | 移除與 listener 重複的 `LLM call failed` ERROR；錯誤碼對應不變 |

呼叫點與用途：

| 呼叫點 | 用途 |
| --- | --- |
| `InstructionRouter` | `ROUTE` |
| `HistorySummarizer` | `SUMMARY` |
| `StepArgumentExtractor` | `STEP_ARGUMENTS` |
| `ChatOrchestrator.callModel` | `ANSWER` |
| `EvalJudge` | `JUDGE` |

### 3.2 資料流

```text
呼叫點 ── chat(request, options{purpose}) ──► ChatModel（LangChain4j 預設實作）
                                               ├─ onRequest  : 在 attributes 記開始時間
                                               ├─ doChat     : OpenAI HTTP 呼叫
                                               └─ onResponse / onError
                                                    └─► Outbound logger：一次呼叫一行
例外照原樣往外拋 ──► 呼叫點現有的錯誤處理（不變）
```

已對照 LangChain4j 1.22.0 確認的行為：

- `chat(ChatRequest)` 本身就是 `chat(request, ChatRequestOptions.EMPTY)`，改用帶 options 的版本不改變請求內容。
- `listenerAttributes` 會複製成每次呼叫專用的 map，`onRequest`／`onResponse`／`onError` 拿到的是同一份。
- listener 與 `doChat` 在同一執行緒同步執行，可直接從 MDC 讀 `requestId`（`RequestContext.MDC_KEY`）。評測流量的 requestId 以 `e` 開頭，可藉此區分來源。
- `doChat` 丟出例外時，呼叫 `onError` 後原樣重拋。
- listener 丟出的例外會被 LangChain4j 攔下並記 WARN，不影響呼叫。

2026-11-10 以程式實測（LangChain4j 1.22.0、Log4j2 2.26.1）：

- listener 拿到的 `chatRequest()` 是合併預設參數後的請求，`modelName()` 有值；`onRequest`、`onError` 與呼叫端在同一執行緒。
- 預設的 `chat(request, options)` 合併參數後，`doChat` 收到的請求保留原本的參數型別（`OpenAiChatRequestParameters` 仍是該型別）與 `responseFormat`，測試替身改覆寫 `doChat()` 不影響既有斷言的前提。
- 連線失敗時例外鏈是 `RuntimeException` → `ConnectException`，沒有 `HttpException`，`status_code` 會是 `"500"`。
- Log4j2 的 PatternLayout 以 `toString()` 印例外：`MessageFreeThrowable` 印出來是原例外的類別名稱，`Caused by:` 也是，沒有訊息。

`ScriptedChatModel`（測試替身）目前覆寫 `chat(ChatRequest)`；改呼叫帶 options 的版本後請求會走 `doChat()`，所以替身要改為覆寫 `doChat()`。

## 4. Log 格式

寫入 logger `Outbound`。log 本文是一行 JSON，欄位名稱與型別照平台 `OutboundLogAspect`，平台的數值也是字串，我們跟著用字串，避免與 ALP 既有 index 的欄位型別衝突。新增欄位全部放在 `llm` 物件。

成功（INFO）：

```json
{"event":"external_call_completed","result":"SUCCESS",
 "request":{"target_system":"OpenAI","path":"OpenAiChatModel.chat"},
 "response":{"response_type":"ChatResponse","status_code":"200","result_code":"SUCCESS"},
 "execution_time":"842","duration_ms":"842",
 "llm":{"request_id":"a1b2c3","purpose":"ROUTE","model":"<供應商回報的模型名稱>",
        "input_tokens":"1830","output_tokens":"42","total_tokens":"1872"}}
```

失敗（ERROR，附去掉訊息的 StackTrace）：

```json
{"event":"external_call_failed","result":"FAIL",
 "request":{"target_system":"OpenAI","path":"OpenAiChatModel.chat"},
 "response":{"response_type":"","status_code":"429","result_code":"FAIL"},
 "execution_time":"311","duration_ms":"311",
 "error_type":"RateLimitException","root_cause_type":"HttpException",
 "llm":{"request_id":"a1b2c3","purpose":"ANSWER","model":"<請求的模型名稱>",
        "error_code":"AI_LLM_RATE_LIMITED"}}
```

欄位取值：

| 欄位 | 取值 |
| --- | --- |
| `request.target_system` | 固定 `"OpenAI"` |
| `request.path` | 固定 `"OpenAiChatModel.chat"`（平台慣例是 client 的 `類別.方法`） |
| `response.status_code` | 成功 `"200"`；失敗時取例外鏈中第一個 `HttpException.statusCode()`，沒有（例如逾時）則照平台填 `"500"` |
| `execution_time`、`duration_ms` | 同一個值，毫秒，從 `onRequest` 記下的開始時間算起；拿不到開始時間時填 `""` |
| `error_type`、`root_cause_type` | 例外與根因的簡單類別名稱；只在失敗時出現 |
| `error_message`、`root_cause_message` | **不輸出** |
| `llm.request_id` | MDC 的 `requestId`。平台的 JSON layout 會自動帶外層 `trace_id`，但本機 console 格式不印 MDC，所以內層放一份，方便 dev 過濾；平移後與外層重複但不衝突 |
| `llm.purpose` | 呼叫點帶入的用途；沒帶時 `"UNKNOWN"` |
| `llm.model` | 成功：`ChatResponse.modelName()`（供應商回報），null 時改用請求的模型名稱；失敗：請求的模型名稱。請求的模型名稱取自 listener context 的 `chatRequest().modelName()`，那是已合併預設參數的請求，所以 listener 不需要讀設定檔 |
| `llm.input_tokens`、`output_tokens`、`total_tokens` | 成功時取自 `ChatResponse.tokenUsage()`，個別為 null 時填 `""`；`tokenUsage()` 為 null 時三者皆 `""` |
| `llm.error_code` | 失敗時由現有 `LlmErrorMapper` 對應，與稽核、前端的錯誤碼一致 |

MDC `logMessage`：照平台的 key=value 摘要格式，例如 `event="external_call_completed" result="SUCCESS" target_system="OpenAI" operation="ROUTE" status_code="200" duration_ms="842"`；失敗時加上 `error_type`、`root_cause_type`，不含任何訊息。寫完 log 後還原 MDC 原本的值。

## 5. 錯誤處理

### 5.1 listener 本身

- `onResponse` 與 `onError` 整段包在 try/catch，出錯只記一行 WARN（只有例外類型），不交給 LangChain4j 的預設處理，因為它會連訊息一起印出。`onRequest` 只把開始時間寫進 attributes，不會丟例外，沒有另外包。
- listener 不保存狀態；每次呼叫的資料都放在該次呼叫的 attributes。
- 模型呼叫的例外照原樣往外拋，現有的錯誤碼對應、稽核與使用者看到的訊息都不變。

### 5.2 `MessageFreeThrowable`

- 自訂例外類型，`toString()` 回傳原例外的完整類別名稱，訊息一律為 null。
- 複製原例外的每一層呼叫位置；cause 鏈逐層照樣轉換，以 identity set 防止循環。
- 不複製 suppressed 例外。
- 印出的 StackTrace 會像 `dev.langchain4j.exception.RateLimitException` → `Caused by: dev.langchain4j.exception.HttpException`，加上各層呼叫位置，沒有任何訊息。

### 5.3 重複的 ERROR

`ChatOrchestrator.callModel` 的 `LLM call failed requestId={} code={} error={}` 與 Outbound log 重複，移除。路由、摘要、步驟參數與評測評分各自的 WARN 是業務層的降級紀錄（例如路由失敗改為載入全部分類），保留不動。

## 6. 測試

1. **`OutboundLlmLogListenerTest`**（以既有 `LogCapture` 擷取 `Outbound` logger）：
   - 成功：JSON 可解析，各欄位值與字串型別正確。
   - 失敗：`HttpException(429)` 得 `"429"`；逾時得 `"500"` 與 `AI_LLM_TIMEOUT`。
   - 防洩漏：例外訊息放一段標記字串，確認整個 log 輸出（含 StackTrace）都找不到。
   - 寫完 log 後 MDC `logMessage` 還原為原值（含原本沒有值的情況）。
   - `tokenUsage`、模型名稱為 null 時不出錯，欄位填 `""` 或請求的模型名稱。
   - 沒帶用途時記 `UNKNOWN`。
   - listener 內部出錯時不丟例外，只記 WARN。
2. **`MessageFreeThrowableTest`**：類型與呼叫位置保留、訊息全部清空、cause 鏈完整、循環的 cause 鏈不會無限迴圈。
3. **用途標記**：`ScriptedChatModel` 改覆寫 `doChat()`，並可掛一個記錄用途的 listener，確認：
   - 5 個呼叫點各自的測試確認帶出對應的用途（`ROUTE`、`SUMMARY`、`STEP_ARGUMENTS`、`ANSWER`、`JUDGE`）。
   - `ChatOrchestrator` 一題的所有模型呼叫都有用途（沒有 null）。
4. **架構規則**：`ArchitectureRulesTest` 新增一條，掃描 application 層原始碼，每個 `chatModel.chat(` 呼叫到結尾 `;` 之間都必須含 `LlmCallPurpose.`；至少要掃到一個呼叫（防止規則失效），不寫死數量。寫法沿用既有的原始碼掃描方式。
5. **設定**：`LlmConfiguration` 產生的 `ChatModel` 的 `listeners()` 含 `OutboundLlmLogListener`。
6. **既有測試**：目前沒有測試檢查 `LLM call failed` 這行 log，移除不影響既有斷言；`ScriptedChatModel` 改為覆寫 `doChat()` 後，既有測試應全數通過。以 Mockito 模擬 `ChatModel` 的測試（`ChatCitationFlowTest`、`EvalRunnerTestBase`、`ChatControllerTest`）因呼叫點改走兩個參數的 `chat(request, options)`，需改為 stub／verify 該方法。
7. **手動驗證（dev）**：問一題後，在 Debug Console 過濾 `external_call`，每次模型呼叫各有一行，用途、耗時、token 正確；以無效的 API key 觸發一次失敗，確認是 ERROR、有狀態碼、StackTrace 沒有訊息。

## 7. 範圍外與待確認

- **`log.info` 與 548**：`chat answered`（`ChatOrchestrator`）與 `feedback set`（`FeedbackService`）在寫完稽核後又記 INFO，牴觸 reference.md 548。這次不動。注意 reference.md 6-6 顯示平台在所有環境對自家程式碼都是 INFO，切換 profile 不會讓這些 INFO 消失，平移時需再處理。
- **Outbound log 是否強制**：平台是否要求所有外部呼叫都有 Outbound log，待向主管確認；本設計已依慣例產出。
- **TraceId**：平台以 `TraceIdFilter` 的 `traceId` 串 log，專案用自己的 `requestId`。平移時要確認兩者如何對應（可併入 S12「Log 規範」一起問）。
- **Kibana 權限**：開發團隊能否自建 Kibana 儀表板，待確認；不影響本設計。
- **平台 JSON layout 的例外欄位**：本機的 PatternLayout 以 `toString()` 印例外（已實測）。平台 SIT 以上的 JSON layout 若用 `getClass()` 取例外類別，外層欄位會顯示 `MessageFreeThrowable`；內層 JSON 的 `error_type`、`root_cause_type` 不受影響。平移時確認。
- **內容過濾**：OpenAI 拒答以 `ContentFilteredException` 丟出，會記成 `external_call_failed`、`status_code` `"500"`（實際 HTTP 是 200），以 `llm.error_code` = `AI_ANSWER_FORMAT_INVALID` 區分。這次接受。
- **評測的 log 量**：一次評測（35 題、兩個版本）會產生數百行 Outbound INFO，以 `llm.request_id` 的 `e` 開頭區分。這次接受。

## 8. 實作順序與落點

1. `MessageFreeThrowable` 與測試。
2. `LlmCallPurpose`。
3. `OutboundLlmLogListener` 與測試；`LlmConfiguration` 註冊與設定測試。
4. `ScriptedChatModel` 改覆寫 `doChat()`，確認既有測試全綠。
5. 5 個呼叫點改帶用途；用途標記測試；`ArchitectureRulesTest` 新規則。
6. 移除 `LLM call failed` ERROR。
7. 文件同步：01-architecture.md 1.2 元件明細與 5.1 目錄結構；ideas-backlog 或 demo-checklist 補上手動驗證步驟。

## 9. 驗收標準

- 每一次模型呼叫（含評測）各產生一行 `Outbound` log，格式符合第 4 節。
- 失敗時為 ERROR，附去掉訊息的 StackTrace，log 輸出中找不到任何例外訊息。
- 新增的呼叫點若未帶用途，`ArchitectureRulesTest` 會失敗。
- 現有的稽核內容、錯誤碼與回答行為不變；後端所有測試通過。
