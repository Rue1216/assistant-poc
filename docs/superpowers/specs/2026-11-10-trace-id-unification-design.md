# 追蹤碼統一為 traceId 設計

- 日期：2026-11-10
- 版本：v0.2
- 狀態：待審
- v0.2（2026-11-10）：寫實作計畫時發現平台的 `TraceIdFilter` 目前**沒有被註冊**。`CopilotApplication` 只掃描 `com.example.copilot`，`com.example.filter` 不在範圍內，所以現在的請求沒有 `traceId`，也沒有 `X-Trace-Id` header。新增 2.1「註冊平台 filter」，第 7 節補對應測試，第 9 節第 1 階段補這一步。
- 起因：比對 [reference.md](../../../reference.md) 第 6 節與現行程式後發現兩個問題。(1) 專案同時有平台的 `traceId` 與自己的 8 碼 `requestId`，所有 log、錯誤回應、稽核都用後者，而平台規定 Kibana 與稽核以 `traceId` 關聯（6-2、6-6）。(2) MDC 沒有帶到工具執行緒、評測與排程，這些地方的 log 沒有任何追蹤碼。
- 前提：不修改平台檔案（`com.example.filter`、`com.example.aspect`、`com.example.logging` 等）；`ArchitectureRulesTest` 全綠。
- 相關規格：[02-rest-api.md](../../spec/02-rest-api.md)、[04-mongodb-schema.md](../../spec/04-mongodb-schema.md)、[05-error-codes.md](../../spec/05-error-codes.md)、[LLM 可觀測性設計](2026-11-10-llm-observability-design.md) 第 7 節「TraceId」

## 1. 目標與決策

目的：**全系統只留一個追蹤碼 `traceId`，移除 `requestId`；每一行 log 不論在哪個執行緒都帶得到它。**

| 項目 | 決策 | 節 |
| --- | --- | --- |
| 追蹤碼 | 只用平台 `TraceIdFilter` 產生的 `traceId`。`requestId` 這個名稱在程式、API、Mongo、前端都不再出現 | 2 |
| 回答識別碼 | 回饋 API 改用 `traceId` 找稽核文件。接受 `traceId` 可由呼叫端的 `X-B3-TraceId` 帶入、因此可能重複的特性 | 3.2 |
| 舊資料 | 不搬移。由開發者刪除稽核集合（`drop`，不是刪文件） | 4 |
| Outbound log 內層欄位 | `llm.request_id` 改名為 `llm.trace_id`，平台 `log4j2.xml` 上線後再刪 | 5 |
| MDC 傳遞 | 工具執行緒複製呼叫端的 MDC；評測與排程在進入點自行設定 | 6 |

不做的事：

- 不修改平台檔案。
- 不新增 `log4j2.xml`，不引入 `InboundLogAspect`，不調整例外處理器的 log（另案）。
- 不搬移 Mongo 舊資料。
- 不更新 `docs/superpowers` 下的舊設計與計畫，那些是歷史紀錄。

## 2. 追蹤碼來源

| 元件 | 變更 |
| --- | --- |
| `RequestIdFilter` | 刪除。`X-Request-Id` 回應 header 隨之消失，只剩平台的 `X-Trace-Id` |
| `RequestContext` | `MDC_KEY` 改為 `"traceId"`，另加 `SPAN_KEY = "spanId"`；`currentRequestId()` 改名為 `currentTraceId()`。新增 `newId()`，回傳 32 碼小寫十六進位（去掉連字號的 UUID，與 `TraceIdFilter` 相同） |
| `AssistantExceptionHandler`、`ApiError` | 原本呼叫 `RequestIdFilter.current()`，改為 `RequestContext.currentTraceId()` |
| `ToolExecutor` | 常數 `REQUEST_ID_KEY` 改為 `TRACE_ID_KEY = "traceId"` |

`RequestContext` 自己定義鍵名，不引用平台的 `TraceIdFilter.MDC_TRACE_ID`，維持 application 層不依賴平台 filter。兩邊的值必須相同，以一個測試固定（第 7 節）。

### 2.1 註冊平台 filter

`TraceIdFilter` 有 `@Component`，但 `CopilotApplication` 的元件掃描只涵蓋 `com.example.copilot`，所以它目前不是 bean。

- 新增 `com.example.copilot.mockplatform.PlatformFilterConfiguration`：`@Configuration` 加 `@Import(TraceIdFilter.class)`，不改平台檔案，也不擴大掃描範圍。
- 放在 `mockplatform` 是因為這個套件代表「平台會提供的東西」；平移到平台後由平台自己的掃描註冊，這個類別刪除。
- 只匯入 `TraceIdFilter`。`OutboundLogAspect` 等其他平台類別不在本次範圍。

`TraceIdFilter` 是 `HIGHEST_PRECEDENCE`，排在 Spring Security 之前，所以 401、403 的回應仍會帶追蹤碼。

## 3. 對外介面

### 3.1 回應欄位

| 位置 | 原名 | 新名 |
| --- | --- | --- |
| 聊天回應 `ChatResponseBody`、`ChatResult` | `requestId` | `traceId` |
| 錯誤回應 `ApiError` | `requestId` | `traceId` |
| 回應 header | `X-Request-Id` | 移除（平台已回 `X-Trace-Id`） |

值由 8 碼變為 32 碼。呼叫端有帶 `X-B3-TraceId` 時，值就是呼叫端給的字串。

### 3.2 回饋 API

- 路徑：`PUT /api/ai-assistant/chat/{traceId}/feedback`
- 格式檢查：`^[0-9a-f]{16,32}$`（B3 規範的 64 位元與 128 位元兩種長度；原為 `^[0-9a-f]{8}$`）。
- 同一 `traceId` 有多筆稽核文件時，更新最新一筆；這是現行行為，不變。
- 已知限制：上游若送了不符合上述格式的 `X-B3-TraceId`，該則回答無法評分（回 `AI_INPUT_INVALID`）；稽核仍會寫入，`traceId` 截到 64 字。

### 3.3 前端

| 檔案 | 變更 |
| --- | --- |
| `features/ai-assistant/types.ts` | 三個 `requestId` 欄位改名 |
| `features/ai-assistant/stores/useAssistantStore.ts` | 讀寫欄位改名 |
| `features/ai-assistant/api/aiAssistantApi.ts` | `putFeedback` 參數改名 |
| `features/ai-assistant/components/AssistantMessage.vue`、`ErrorMessage.vue` | 欄位改名；「追蹤碼」完整顯示 32 碼 |
| `platform-mock/apiClient.ts` | `ApiError.requestId` 改為 `traceId`，從回應的 `traceId` 取值 |

## 4. 稽核與 Mongo

| 位置 | 變更 |
| --- | --- |
| `AuditEntry`、`AuditRecord` | 元件 `requestId` 改名為 `traceId` |
| `AuditRecordMapper` | 文件欄位 `requestId` 改為 `traceId` |
| `AuditRepository.setFeedback`、`AuditRepositoryImpl` | 參數與查詢條件改為 `traceId` |
| `audit-validator.json` | `required` 與 `properties` 的 `requestId` 改為 `traceId`，長度上限維持 64 |
| `MongoSchemaInitializer` | 索引 `IX_ICRM_AI_AUDIT_4` 的鍵改為 `traceId`，名稱不變 |

舊資料處理：升級前刪除集合 `COLL_ICRM_AI_ASSISTANT_AUDIT`。必須是刪除集合，因為 `IX_ICRM_AI_AUDIT_4` 同名但鍵不同，舊索引還在時啟動會失敗。步驟寫進 [demo-checklist.md](../../demo-checklist.md)。

## 5. Log

- 所有 log 文字的 `requestId={}` 改為 `traceId={}`，取值改用 `RequestContext.currentTraceId()`。涉及 `ChatOrchestrator`、`InstructionRouter`、`HistorySummarizer`、`StepArgumentExtractor`、`WorkflowRunner`、`AuditLogger`、`ToolExecutor`、`AssistantExceptionHandler`。
- `FeedbackService` 的 `feedback set requestId={} answerRequestId={}`：兩個值原本不同（本次請求與被評分的回答），改為 `traceId={} answerTraceId={}`。
- `OutboundLlmLogListener`：內層 `llm.request_id` 改名為 `llm.trace_id`。保留的原因與原設計相同：本機 console 不印 MDC。

## 6. MDC 傳遞

### 6.1 工具執行緒

`ToolExecutor.run` 以新的 virtual thread 執行工具，MDC 不會繼承。

- 提交前在呼叫端執行緒取 `MDC.getCopyOfContextMap()`。
- 任務開頭：複本不是 null 時 `MDC.setContextMap(複本)`。
- 任務結尾（`finally`）：`MDC.clear()`。
- 複製整份 MDC（含 `spanId`、`httpMethod`、`requestUri`），不只 `traceId`，與同一請求的其他 log 一致。

### 6.2 評測

- 以評測紀錄的 id（24 碼十六進位的 Mongo ObjectId）當 `traceId`，另以 `RequestContext.newId()` 產生 `spanId`。從 log 的 `traceId` 可以直接查到評測紀錄。
- 範圍由「每一題的作答與評分」擴大為整次評測：在 `EvalRunner` 執行一次評測的進入點設定，`finally` 還原成進入前的值。`eval run finished` 等摘要 log 因此也帶得到。
- 原本的 `e` 加末 7 碼不再使用。評測流量的辨識方式改為：`traceId` 長度 24（一般請求是 32），或直接以 `traceId` 查評測紀錄。
- 評測不寫稽核，所以不會有稽核文件的 `traceId` 是 24 碼。

### 6.3 排程

`ScheduledPublishJob.run`：開頭以 `RequestContext.newId()` 設定 `traceId` 與 `spanId`，`finally` 移除這兩個鍵。每次執行一組新的值。

## 7. 測試

既有測試隨欄位與方法改名（後端約 20 個檔，前端 4 個檔）。新增或調整的斷言：

1. **鍵名一致**：`RequestContext.MDC_KEY` 等於 `TraceIdFilter.MDC_TRACE_ID`。
2. **回應**：每個回應都有 32 碼的 `X-Trace-Id` header（含未登入的 401）；聊天成功與錯誤回應的 `traceId` 等於該 header；帶 `X-B3-TraceId` 時等於帶入的值；回應沒有 `X-Request-Id`。
3. **回饋 API**：接受 16 碼與 32 碼；拒絕 8 碼、33 碼、大寫與非十六進位字元。
4. **工具執行緒**：工具執行期間 `MDC.get("traceId")` 等於呼叫端的值；呼叫端的 MDC 在工具執行後不變；呼叫端沒有 MDC 時不出錯。
5. **評測**：作答與評分期間 MDC 的 `traceId` 等於評測 id 且有 `spanId`；結束後還原為進入前的值。
6. **排程**：`publishDue` 執行期間 MDC 有 32 碼 `traceId`；結束後兩個鍵都不存在，包含 `publishDue` 丟例外的情況。
7. **稽核**：寫入的文件有 `traceId`、沒有 `requestId`；`MongoSchemaTest` 的 validator 與索引斷言改為 `traceId`。
8. **Outbound log**：`llm.trace_id` 取自 MDC 的 `traceId`；JSON 內沒有 `request_id`。
9. **殘留檢查**：`backend/src/main` 與 `frontend/src` 搜尋 `requestId`、`RequestId`、`request_id`、`X-Request-Id` 沒有結果。

## 8. 文件

| 文件 | 變更 |
| --- | --- |
| `docs/spec/01-architecture.md` | 元件清單移除 `RequestIdFilter`；追蹤碼說明改為 `traceId` |
| `docs/spec/02-rest-api.md` | 回應欄位、回饋 API 路徑與格式、header |
| `docs/spec/04-mongodb-schema.md` | 稽核欄位與索引 |
| `docs/spec/05-error-codes.md`、`06-conversation-scenarios.md` | 範例中的欄位名稱 |
| `docs/demo-checklist.md` | 升級前刪除稽核集合的步驟；手動驗證步驟的過濾欄位 |
| `docs/questions-for-manager.md`、`platform-setup-requests.md`、`ideas-backlog.md` | 提到 `requestId` 的句子改為現況 |
| `2026-11-10-llm-observability-design.md` | 第 7 節「TraceId」標記為已由本設計處理；`llm.request_id` 加註已改名 |

## 9. 實作順序

每一階段結束時後端或前端測試全綠。

1. **後端追蹤碼來源與 log**：註冊 `TraceIdFilter`（2.1）、`RequestContext` 改名、刪除 `RequestIdFilter`、log 文字、`OutboundLlmLogListener`。
2. **後端對外介面與稽核**：`ChatResult`、`ChatResponseBody`、`ApiError`、回饋 API、稽核紀錄、validator、索引。
3. **MDC 傳遞**：`ToolExecutor`、`EvalRunner`、`ScheduledPublishJob`。
4. **前端**。
5. **文件**與殘留檢查。

## 10. 驗收標準

- 第 7 節第 9 項的搜尋沒有結果。
- 一則回答的回應 `traceId`、`X-Trace-Id` header、稽核文件 `traceId`、該題所有 log（含工具內的 log 與 Outbound log 的 `llm.trace_id`）是同一個值。
- 以該 `traceId` 呼叫回饋 API 可以評分。
- 評測與排程產生的 log 都有 `traceId`。
- 後端與前端所有測試通過。
