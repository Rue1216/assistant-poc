# 排查 `AI_LLM_UNAVAILABLE`（問問題後回 llm_unavailable）

這份文件給在平台專案內排查問題的 agent 使用。照順序做，每一步都有「看到什麼代表什麼」。

## 1. 問題描述

- AI 小幫手後端（Spring Boot + LangChain4j 1.22.0 的 `OpenAiChatModel`）已搬進平台專案。
- OpenAI API key 設定在平台專案的 `application.yaml`。
- 專案可以啟動，但實際問問題時，API 回錯誤 `AI_LLM_UNAVAILABLE`（使用者回報的 HTTP 狀態是 500）。
- 目標：找出模型呼叫失敗的真正原因並修正。

## 2. 先理解這個錯誤碼的意思

`AI_LLM_UNAVAILABLE` 是預設錯誤碼，不是具體原因。`LlmErrorMapper.map()` 沿著例外的 cause chain 判斷：

| 例外型別 | 對應錯誤碼 |
|---|---|
| LangChain4j `TimeoutException`、`HttpTimeoutException`、`SocketTimeoutException` | `AI_LLM_TIMEOUT` |
| `ContentFilteredException`（模型拒答） | `AI_ANSWER_FORMAT_INVALID` |
| `RateLimitException`（HTTP 429，包含額度用完） | `AI_LLM_RATE_LIMITED` |
| 其他全部 | `AI_LLM_UNAVAILABLE` |

所以 key 錯、模型不存在、參數被拒、連線被拒、DNS 解析失敗、SSL 憑證問題，全部都會顯示成 `AI_LLM_UNAVAILABLE`。

反過來說，下列情況**不會**是這個錯誤碼：

- 防火牆直接丟棄封包（連線逾時，預設 15 秒）→ `AI_LLM_TIMEOUT`；
- OpenAI 額度用完或限流（429）→ `AI_LLM_RATE_LIMITED`。

另外有一個不經過例外的來源：`ChatUseCase.callModel()` 在模型回應沒有 `aiMessage` 時也會丟這個錯誤碼。這種情況很少見。

## 3. 相關程式（用類別名稱搜尋，平台專案的套件路徑可能不同）

| 類別 | 作用 |
|---|---|
| `LlmConfiguration` | 唯一建立 `ChatModel` bean 的地方，把 `apiKey`、`baseUrl`、`modelName` 等傳給 `OpenAiChatModel.builder()` |
| `AiAssistantProperties` | `@ConfigurationProperties(prefix = "ai-assistant")`，`llm` 是其中的 record |
| `LlmErrorMapper` | 把例外對應到錯誤碼（第 2 節） |
| `OutboundLlmLogListener` | 每次模型呼叫寫一行 log，logger 名稱是 `Outbound` |
| `MessageFreeThrowable` | 把例外的訊息全部去掉，只留型別和 stack trace，再交給 logger |
| `ChatUseCase` | 回答流程；失敗時寫 `chat failed traceId=... code=...` |
| `InstructionRouter`、`StepArgumentExtractor`、`HistorySummarizer` | 其他會呼叫模型的地方 |
| `AssistantExceptionHandler` | POC 把 `AssistantException` 轉成 HTTP 回應的地方；平移後可能已改成平台的 `GlobalExceptionHandler` 與 `ApiResponse` |

POC 的設定長這樣（`application.yml`）：

```yaml
ai-assistant:
  llm:
    provider: openai
    api-key: ${OPENAI_API_KEY}
    base-url:                 # 留空＝OpenAI 公開端點
    model-name: gpt-6-luna
    temperature:              # 留空＝不傳
    reasoning-effort: none
    timeout: 30s
    max-output-tokens: 1500
```

`LlmConfiguration` 另外固定設了 `strictJsonSchema(true)`、`maxRetries(0)`、`logRequests(false)`、`logResponses(false)`。底層 HTTP 是 JDK 的 `java.net.http.HttpClient`（`langchain4j-http-client-jdk`），不是平台的 `RestClient`。

## 4. 排查步驟

### 步驟 1：確認錯誤碼的來源

重現一次問題，記下回應的 HTTP 狀態、完整回應內容、traceId。

- POC 的 `AI_LLM_UNAVAILABLE` 是 HTTP **503**，`AI_INTERNAL_ERROR` 才是 500。
- 平移時錯誤回應格式可能已改成平台的 `ApiResponse`（HTTP 狀態和 `serviceCode` 的對應由平台決定）。找出平台專案內處理 `AssistantException` 的地方，確認 500 是怎麼產生的。
- 如果實際的錯誤碼是 `AI_INTERNAL_ERROR`，或例外根本不是 `AssistantException`，代表不是模型呼叫失敗。這時改查未預期的例外，本文件後面的步驟不適用。
- 在 log 找 `chat failed traceId=<traceId>`，確認 `code=AI_LLM_UNAVAILABLE`。

### 步驟 2：找 Outbound log

用 traceId 在後端 log 裡找 logger `Outbound` 的 `external_call_failed`。它是 JSON，後面附一段沒有訊息的 stack trace（`Caused by:` 會列出整條 cause chain 的型別）。要看的欄位：

- `response.status_code`
- `error_type`（最外層例外的類別名稱）
- `root_cause_type`（最底層 cause 的類別名稱）
- `llm.purpose`：`ROUTE`（問題分類）、`ANSWER`（回答）、`SUMMARY`、`STEP_ARGUMENTS`、`JUDGE`
- `llm.model`

問題分類失敗時程式會改用預設分類繼續，所以一次提問通常會看到兩行失敗：先 `ROUTE`，再 `ANSWER`。兩行的原因通常相同。

對照表（依 LangChain4j 1.22.0 的 `ExceptionMapper` 與 `JdkHttpClient`）：

| `status_code` | `error_type` | `root_cause_type` | 原因 | 下一步 |
|---|---|---|---|---|
| 401 | `AuthenticationException` | `HttpException` | key 無效，或實際送出的不是你以為的那把 key | 步驟 4 |
| 403 | `AuthenticationException` | `HttpException` | key 所屬的專案沒有權限、地區不支援，或被公司的 proxy、防火牆擋下 | 步驟 3 看回應內容是 OpenAI 的 JSON 還是 proxy 的頁面 |
| 404 | `ModelNotFoundException` | `HttpException` | 這把 key 不能用設定的 `model-name`，或 `base-url` 路徑錯（常見是少了 `/v1`） | 步驟 4、步驟 5 |
| 400、407、其他 4xx | `InvalidRequestException` | `HttpException` | 400：模型或 gateway 不接受某個參數（`reasoning-effort`、`temperature`、strict JSON schema、`max_completion_tokens`）。407：proxy 要求認證 | 步驟 3；407 見步驟 6 |
| 5xx | `InternalServerException` | `HttpException` | OpenAI 或 gateway 本身出錯 | 步驟 3 看內容；稍後重試或查 gateway |
| 3xx | `HttpException` | `HttpException` | 被 proxy 或 gateway 轉址（例如轉到登入頁） | 步驟 6 |
| 500 | `UnresolvedModelServerException` | `UnresolvedAddressException` | DNS 解析不到主機：`base-url` 打錯，或這台機器解析不到外部網域 | 步驟 4、步驟 6 |
| 500 | `RuntimeException` | `ConnectException`、`ClosedChannelException` 等 | 連線被拒或被中斷：防火牆沒開，或需要 proxy | 步驟 6 |
| 500 | `RuntimeException` | `SSLHandshakeException`、`SunCertPathBuilderException`、其他 `SSL*` | 公司的 SSL 攔截憑證不在 JDK truststore | 步驟 6 |

注意：`status_code` 是 `"500"` 不代表對方回了 500。cause chain 裡沒有 `HttpException` 時，listener 固定填 500，這時以 `root_cause_type` 為準。

如果找不到 `Outbound` 這一行：

1. 檢查平台的 logback 設定有沒有輸出名為 `Outbound` 的 logger，以及它寫到哪個 appender 或檔案（平台的 Outbound log 是獨立的 RollingFile）。
2. 找 `outbound log failed error=`，這代表 listener 自己寫 log 時失敗。
3. 確認 `OutboundLlmLogListener` 有被註冊（`LlmConfiguration` 的 `.listeners(...)`）。
4. 都沒有的話，直接做步驟 3，用暫時的 log 取得例外。

### 步驟 3：取得錯誤回應的內容

log 刻意不記例外訊息（可能含客戶資料），所以 Outbound log 看不到對方回的內容。要看內容，在 `OutboundLlmLogListener.onError()` 的開頭**暫時**加一段，把整條 cause chain 的型別和訊息印出來（這個類別已有 `@Slf4j` 的 `log`）：

```java
for (Throwable t = context.error(); t != null; t = t.getCause() == t ? null : t.getCause()) {
    log.warn("TEMP-DEBUG llm error type={} message={}", t.getClass().getName(), t.getMessage());
}
```

重現問題後讀這幾行。`HttpException` 的訊息就是對方回應的 body：

- OpenAI 的錯誤是 JSON，直接寫明原因，例如 `Incorrect API key provided`、`The model ... does not exist or you do not have access to it`、`Unsupported parameter: ...`。
- 如果內容是 HTML 或公司 proxy 的攔截頁面，代表請求沒有到 OpenAI，改看步驟 6。

限制：

- 只在本機或開發環境做，用測試問題重現，不要用真實客戶資料。
- 找到原因後**一定要移除**這段程式，不可提交。

### 步驟 4：確認設定有生效

在 `LlmConfiguration.chatModel()` 內**暫時**印出實際綁到的值。這個類別沒有 logger，要自己取一個。不可印出完整的 key：

```java
String key = llm.apiKey();
org.slf4j.LoggerFactory.getLogger(LlmConfiguration.class).warn(
        "TEMP-DEBUG llm config keyLength={} keyTail={} keyLooksEncrypted={} keyHasWhitespace={} "
                + "baseUrl=[{}] model=[{}] reasoningEffort=[{}] temperature={}",
        key == null ? -1 : key.length(),
        key == null || key.length() < 4 ? "" : key.substring(key.length() - 4),
        key != null && key.startsWith("ENC("),
        key != null && !key.equals(key.strip()),
        llm.baseUrl(), llm.modelName(), llm.reasoningEffort(), llm.temperature());
```

逐項比對：

- **key 是 null 或長度不對**：屬性沒綁到。檢查 yaml 的路徑是否正好是 `ai-assistant.llm.api-key`，縮排錯了不會報錯。
- **`keyLooksEncrypted=true`**：yaml 寫的是 Jasypt 的 `ENC(...)`，但沒有被解密，送給 OpenAI 的是密文。確認平台的 Jasypt 設定有套用到這個屬性，以及解密金鑰有注入。
- **`keyHasWhitespace=true` 或長度比預期多**：yaml 值帶了空白、換行或多餘的引號。
- **key 尾碼和預期的不同**：被別的來源蓋掉。Spring 的優先順序是：命令列參數 > 環境變數 > profile 專屬的 yaml > `application.yaml`。檢查：
  - yaml 若寫 `${OPENAI_API_KEY}`，實際值來自環境變數 `OPENAI_API_KEY`，機器上可能留著舊的 key；
  - 環境變數 `AIASSISTANT_LLM_APIKEY`（Spring 把屬性名稱去掉 `-`、`.` 換成 `_` 後的形式）會直接蓋掉 yaml；
  - `application-{profile}.yaml` 和目前啟用的 profile；
  - 平台的設定中心或 secret 注入；
  - POC 的 `spring.config.import: optional:file:./.env[.properties]` 有沒有一起搬過來，以及工作目錄下有沒有舊的 `.env`。
- **`baseUrl` 有值**：請求會送到那個位址而不是 OpenAI。確認位址正確、結尾含 `/v1`、而且該 gateway 接受 Bearer API key。
- **`model` 不是預期的值**：同樣是被別的設定蓋掉。

做完後移除這段程式。

### 步驟 5：用 curl 排除程式以外的因素

在執行後端的同一台機器（或同一個容器）上，用同一把 key 直接呼叫。

1. 把 key 放進目前 shell 的環境變數 `OPENAI_API_KEY`（只在這個 shell 有效）。不要把 key 寫進指令、腳本或檔案，也不要印出來。key 只存在 yaml 裡的話，請使用者自己設定這個環境變數。
2. 把請求內容寫成檔案（放在專案目錄以外的暫存目錄），避免 shell 的引號跳脫問題。`model` 填步驟 4 看到的實際值：
   ```json
   {"model":"gpt-6-luna","messages":[{"role":"user","content":"hi"}]}
   ```
3. 呼叫（有設 `base-url` 的話，網址換成 `{base-url}/chat/completions`）：
   ```powershell
   curl.exe -sS -o - -w "`nHTTP %{http_code}`n" https://api.openai.com/v1/chat/completions `
     -H "Authorization: Bearer $env:OPENAI_API_KEY" `
     -H "Content-Type: application/json" `
     --data-binary "@C:\path\to\body.json"
   ```
   ```bash
   curl -sS -w '\nHTTP %{http_code}\n' https://api.openai.com/v1/chat/completions \
     -H "Authorization: Bearer $OPENAI_API_KEY" \
     -H "Content-Type: application/json" \
     --data-binary @/path/to/body.json
   ```

| curl 結果 | 結論 |
|---|---|
| 回 200 | key、模型、網路都正常，問題在後端：設定沒生效（步驟 4），或 JVM 的 proxy、憑證（步驟 6），或請求參數（步驟 3） |
| 回 401 / 403 / 404 | key 或模型權限的問題，和程式無關。回應內容會寫明原因 |
| 連線失敗或逾時 | 網路不通，見步驟 6 |
| SSL 憑證錯誤 | 公司有 SSL 攔截，見步驟 6 |

curl 會讀環境變數 `HTTPS_PROXY`，JVM 不會。所以「curl 可以、後端不行」時先查 proxy（步驟 6）。

curl 回 200 但後端回 400 時，原因多半是後端多帶的參數。在 `body.json` 逐一加上 `"reasoning_effort":"none"`、`"max_completion_tokens":1500`、`response_format`（`json_schema`、`strict: true`）來找出是哪一個。

### 步驟 6：網路、proxy、憑證

先看平台既有的外部 client 怎麼連外：找平台 `client` 套件下的 `*ClientConfig`（建立 `RestClient` bean 的地方），看它有沒有設定 proxy、自訂的 SSL 或 truststore、額外的 header。LangChain4j 用的是另一個 HTTP client，不會沿用這些設定；平台 client 有設的，LLM 這條連線也要有。

- **防火牆**：後端需要連到 `api.openai.com:443`（或 `base-url` 指定的 gateway）。
- **proxy**：JDK 的 `HttpClient` 不讀系統或瀏覽器的 proxy 設定，也不讀 `HTTPS_PROXY` 環境變數，只讀 JVM 系統屬性。啟動參數加上：
  ```
  -Dhttps.proxyHost=<host> -Dhttps.proxyPort=<port>
  ```
  需要帳號密碼的 proxy（回 407）光靠 JVM 參數不夠，要在 `LlmConfiguration` 用 `JdkHttpClientBuilder.httpClientBuilder(java.net.http.HttpClient.newBuilder().proxy(...).authenticator(...))` 設定，再傳給 `OpenAiChatModel.builder().httpClientBuilder(...)`。這屬於程式修改，先回報使用者再做。
- **SSL 憑證**：公司有 SSL 攔截時，把公司的根憑證匯入後端使用的 JDK truststore（`keytool -importcert ... -cacerts`），或用 `-Djavax.net.ssl.trustStore` 指向含該憑證的 truststore。不要用關閉憑證驗證的方式處理。
- **gateway 要求額外 header**：用 `OpenAiChatModel.builder().customHeaders(...)`，只改 `LlmConfiguration`。

### 步驟 7：修正並驗證

1. 依找到的原因修正設定或環境。
2. 移除步驟 3、步驟 4 加的所有 `TEMP-DEBUG` 程式，用搜尋確認沒有殘留。刪除步驟 5 的 `body.json`。
3. 重新啟動，問一個問題，確認：
   - API 回成功；
   - log 有 `Outbound` 的 `external_call_completed`，`status_code` 是 `200`，`llm.model` 是預期的模型。

## 5. 和平台外部 client 規範的關係

平台規範（`reference.md` §23）要求外部 HTTP 呼叫放在 `client` 套件，走 Port → Adapter → ApiClient → `RestClient`，並標 `@OutboundLog`。LLM 呼叫沒有照這個做，這是平移時記錄在案的偏離（`PORTING.md` §3.3 第 4 點、§9）：tool calling、JSON schema、token 計算都依賴 LangChain4j，Outbound log 由 `OutboundLlmLogListener` 輸出同樣的格式。

排查時：

- **不要**為了修這個錯誤把 LLM 呼叫改寫成平台的 `RestClient` client。沒有走平台 client 本身不會造成 `AI_LLM_UNAVAILABLE`；會造成問題的是平台 client 有、而 LangChain4j 沒有的連線設定（步驟 6）。
- 如果根本原因是連線設定，修正範圍限於 `LlmConfiguration` 和啟動參數。
- 如果你判斷必須改成走平台的 `RestClient` 才能解決，先停下來回報使用者，不要自行改寫。

## 6. 排查時的限制

- 不可把 API key 印到 log、終端輸出、檔案或 commit 裡。只能印長度和末 4 碼。
- 不可把明文的 key 寫進會進版控的設定檔。平台規範要求機敏設定用 Jasypt `ENC(...)` 或由環境變數／K8s Secret 注入。如果目前 `application.yaml` 裡是明文的 key，回報使用者。
- 不要把 `logRequests` / `logResponses` 改成 `true` 後提交，請求和回應內容含 prompt 和工具結果。
- 暫時加的診斷程式只留在本機，結束前全部移除。
- 不要 commit；完成後列出修改的檔案給使用者。

## 7. 完成後要回報的內容

- 步驟 1 確認的錯誤碼，以及 HTTP 500 是怎麼產生的；
- Outbound log 的 `status_code`、`error_type`、`root_cause_type`、`llm.purpose`；
- 步驟 3 看到的錯誤訊息（遮掉 key 和客戶資料）；
- 根本原因是哪一類：key、設定被覆蓋或未解密、模型權限、請求參數、網路或 proxy、憑證；
- 做了什麼修正，以及步驟 7 的驗證結果；
- `application.yaml` 裡的 key 是不是明文。
