# 03 Tool Catalog

> 版本：v0.9（POC）・2026-10-21

## 1. 共通規則

### 1.1 對話綁定「當前顧客」

小幫手只出現在個人 360 頁面，**每段對話都屬於頁面上的那一位顧客**：

- 前端呼叫 `POST /api/ai-assistant/customers/{customerId}/chat`，顧客編號放在路徑上，**不經過 LLM**。
- 後端先透過 `CustomerAccessPort` 判斷使用者對這位顧客的存取等級，再建立 `CustomerContext`。
- **顧客工具都沒有顧客編號參數**。工具一律查詢 `CustomerContext` 綁定的那位顧客，模型無法指定或切換顧客。
- LLM 看不到顧客編號、姓名或任何可識別身分的資料，回答中一律以「本顧客」稱呼。

### 1.2 存取等級

| 等級 | 意義 | 適用角色 |
|---|---|---|
| `NONE` | 不可存取，對話 API 回 404 | 不符合範圍規則的角色 |
| `LIMITED` | 只能看去識別化的基本輪廓 | 後台管理人員、IT 管理人員 |
| `FULL` | 可使用全部顧客工具 | 符合範圍規則的其他 7 個角色 |

範圍規則由平台判斷（見 [02-rest-api.md](02-rest-api.md) 1.3），小幫手只使用判斷結果。

### 1.3 工具宣告

```java
public interface AssistantTool {
  String name();                        // 給 GPT 的工具名稱
  String purpose();                     // 顯示在摘要裡的中文用途
  ToolSpecification specification();    // 參數 JSON Schema，由參數 record 產生
  AccessLevel requiredAccessLevel();    // LIMITED 或 FULL；市場工具為 LIMITED（任何可存取者皆可用）
  Duration timeout();
  ToolResult execute(JsonNode args, ToolContext context);
}

public record ToolContext(UserContext user, CustomerContext customer) {}

// 只存在 Java 端，任何欄位都不會序列化給 LLM
public record CustomerContext(
  String customerId,              // 呼叫 port 用
  AccessLevel accessLevel,
  Set<String> knownIdentifiers    // 姓名、身分證號、電話、Email、帳號；供 PiiRedactor 精確比對
) {}

public record ToolResult(
  ToolStatus status,        // SUCCESS | ERROR | DENIED | TIMEOUT
  String errorCode,         // 失敗時的工具錯誤碼，成功時為 null
  Object data,              // 給模型的資料（已去識別化、已截斷）
  List<Source> sources      // 只留在 Java 端，用來組回應的 sources
) {}
```

- 參數以 Java record 加 Bean Validation 定義；`ToolSpecification` 和驗證規則都從同一個 record 產生。
- **所有工具都是唯讀的**：沒有任何工具能新增或修改資料。

### 1.4 執行流程

1. `ToolRegistry.toolsFor(context)`：只回傳 `requiredAccessLevel` 不高於 `CustomerContext.accessLevel` 的工具。
2. 模型回傳 tool call 後，`ToolGuard` 依序檢查：
   1. 工具名稱存在，且在本次提供給模型的清單內，否則 `TOOL_NOT_ALLOWED`。
   2. 參數是合法 JSON，總長不超過 1KB，能轉成參數 record，且通過 Bean Validation，否則 `TOOL_ARGUMENT_INVALID`。
   3. 參數中出現 schema 以外的欄位（例如模型自行加上 `customerId`）一律拒絕，同樣回 `TOOL_ARGUMENT_INVALID`。
3. 在 virtual thread 上執行，超過 `timeout` 就中止，回傳 `TOOL_TIMEOUT`。
4. 工具結果經過 `CustomerDeidentifier` 與 `PiiRedactor`（見 1.6），序列化後超過 4KB 就截斷清單並加上 `"truncated": true`。
5. 工具呼叫的名稱、狀態、錯誤碼、耗時記入本次提問的稽核紀錄（見 [04-mongodb-schema.md](04-mongodb-schema.md) 第 3 節）。**不記參數和結果。**

### 1.5 回送給模型的格式

```json
{ "untrustedData": true, "status": "SUCCESS", "data": { } }
```

失敗時：

```json
{ "untrustedData": true, "status": "ERROR", "errorCode": "MARKET_DATA_UNAVAILABLE", "message": "市場資料暫時無法取得" }
```

`message` 是固定文字，不包含例外訊息、stack trace、網址或 token。

### 1.6 去識別化

**原則：所有含有顧客實際身分的資料，都由程式轉換後才送進 LLM。** 分兩層處理：

**第一層：`CustomerDeidentifier`（結構化欄位）**

工具從 port 取得的 Record 包含真實欄位，工具只把下表「送出」的欄位放進 `data`：

| 欄位 | 處理 |
|---|---|
| 顧客編號、姓名、身分證號、電話、Email、地址、帳號、生日、任職單位 | **刪除**，不送出 |
| 年齡 | 泛化為區間，例如 `55-64` |
| 資產、往來金額 | 泛化為級距，例如 `500萬-1000萬` |
| 持有商品占比 | 泛化為區間，例如 `20%-30%` |
| 客群、往來年資、風險屬性、標籤、偏好管道、商品名稱、股票代號 | 送出（不可識別個人） |

**第二層：`PiiRedactor`（自由文字）**

套用在：使用者提問、前端帶回的對話歷史、工具結果中的文字欄位（例如互動摘要）、模型的最終回答（回傳前再檢查一次）。

| 順序 | 規則 | 取代為 |
|---|---|---|
| 1 | 精確比對本顧客的顧客編號與姓名（含只有名字、姓＋稱謂、遮蔽顯示形式，例如「小明」「王先生」「王○明」） | `【本顧客】` |
| 2 | 精確比對本顧客的身分證號、電話、Email、帳號 | `【已遮蔽】` |
| 3 | 身分證號格式 `[A-Z][12]\d{8}` | `【已遮蔽】` |
| 4 | 手機格式 `09\d{2}-?\d{3}-?\d{3}` | `【已遮蔽】` |
| 5 | Email 格式 | `【已遮蔽】` |
| 6 | 帳號格式（連續 10 位以上數字；程式刻意不設上限，寧可多遮蔽也不漏） | `【已遮蔽】` |
| 7 | 其他顧客編號格式（POC 為 `C\d{3}`） | `【其他顧客】` |

> 已知限制：POC 以第一個字為姓，複姓（例如歐陽）未處理；其他顧客的姓名無法比對（系統只載入目前顧客的資料），只能遮蔽其他顧客的編號。

範例：使用者輸入「王小明的手機 0912-345-678 最近有聯絡過嗎？」，送給 LLM 的是「【本顧客】的手機 【已遮蔽】 最近有聯絡過嗎？」。

> 正式整合時，規則 7 的顧客編號格式要換成平台實際格式；規則 1、2 的已知識別資料由 `CustomerDataPort` 提供。

### 1.7 工具總覽

| 工具 | 類別 | 需要的存取等級 | `LIMITED`（後台、IT） | `FULL`（其他 7 個角色） |
|---|---|---|---|---|
| `getCustomerProfile` | 顧客 | `LIMITED` | ✓（基本輪廓） | ✓（完整概況） |
| `getCustomerHoldings` | 顧客 | `FULL` | | ✓ |
| `getRecentInteractions` | 顧客 | `FULL` | | ✓ |
| `getCampaignResults` | 顧客 | `FULL` | | ✓ |
| `getMarketIndex` | 市場 | `LIMITED` | ✓ | ✓ |
| `getStockQuote` | 市場 | `LIMITED` | ✓ | ✓ |
| `getCompanyAnnouncements` | 市場 | `LIMITED` | ✓ | ✓ |

### 1.8 資料來源標示

| 來源 | `name` | `url` | `dataTime` |
|---|---|---|---|
| 顧客資料 | 平台顧客資料（模擬） | `null` | `null` |
| TWSE | 臺灣證券交易所 OpenAPI | `https://openapi.twse.com.tw/` | 資料日期 |

---

## 2. 顧客工具

資料來源：`CustomerDataPort`。四個工具都**沒有顧客參數**，一律查詢 `CustomerContext` 綁定的顧客。

### 2.1 `getCustomerProfile`

| 項目 | 內容 |
|---|---|
| 用途（摘要顯示） | 查詢顧客概況與標籤 |
| 給模型的說明 | 查詢本顧客的去識別化概況：客群、年齡區間、往來年資、資產級距、風險屬性、行銷同意、偏好管道與既有標籤。不需要參數。 |
| 需要的存取等級 | `LIMITED` |
| Timeout | 3 秒 |

參數：

```json
{ "type": "object", "properties": {}, "additionalProperties": false }
```

回傳 `data`（`FULL`）：

```json
{
  "subject": "本顧客",
  "segment": "VIP",
  "ageRange": "55-64",
  "relationshipYears": 8,
  "assetTier": "500萬-1000萬",
  "riskProfile": "穩健型",
  "marketingConsent": true,
  "preferredChannel": "電話",
  "tags": ["退休規劃", "偏好定期定額", "高股息偏好"]
}
```

回傳 `data`（`LIMITED`，只有基本輪廓）：

```json
{
  "subject": "本顧客",
  "segment": "VIP",
  "ageRange": "55-64",
  "relationshipYears": 8,
  "accessNote": "依您的權限僅能查看去識別化基本輪廓"
}
```

### 2.2 `getCustomerHoldings`

| 項目 | 內容 |
|---|---|
| 用途 | 查詢顧客持有商品概況 |
| 給模型的說明 | 查詢本顧客目前持有的商品類別與占比區間。上市股票會附股票代號，可再用 getStockQuote 查行情。不需要參數。 |
| 需要的存取等級 | `FULL` |
| Timeout | 3 秒 |

參數：無（同 2.1）

回傳 `data`：

```json
{
  "holdings": [
    { "productType": "STOCK", "productName": "台積電", "stockCode": "2330", "weightRange": "20%-30%" },
    { "productType": "FUND", "productName": "全球高股息基金", "stockCode": null, "weightRange": "30%-40%" },
    { "productType": "DEPOSIT", "productName": "台幣定存", "stockCode": null, "weightRange": "30%-40%" }
  ]
}
```

`productType`：`STOCK`、`FUND`、`DEPOSIT`、`INSURANCE`

### 2.3 `getRecentInteractions`

| 項目 | 內容 |
|---|---|
| 用途 | 查詢近期互動紀錄 |
| 給模型的說明 | 查詢本顧客最近的互動紀錄（日期、管道、主題、摘要、結果），由新到舊。 |
| 需要的存取等級 | `FULL` |
| Timeout | 3 秒 |

參數：

```json
{
  "type": "object",
  "properties": {
    "limit": { "type": "integer", "minimum": 1, "maximum": 10, "default": 5 }
  },
  "additionalProperties": false
}
```

回傳 `data`：

```json
{
  "interactions": [
    { "date": "2026-10-02", "channel": "電話", "topic": "退休規劃", "summary": "【本顧客】詢問月配息商品，希望每月現金流約 3 萬", "outcome": "約定 10 月下旬面談" },
    { "date": "2026-09-15", "channel": "LINE", "topic": "市場關心", "summary": "關心台積電股價波動", "outcome": "已提供市場週報" }
  ]
}
```

- `summary` 最多 100 字，經過 `PiiRedactor` 處理，並視為不可信資料。
- 互動紀錄的經辦人員姓名不送出（`handledBy` 欄位刪除）。

### 2.4 `getCampaignResults`

| 項目 | 內容 |
|---|---|
| 用途 | 查詢顧客的活動回應 |
| 給模型的說明 | 查詢本顧客近期參與的行銷活動與回應結果。 |
| 需要的存取等級 | `FULL` |
| Timeout | 3 秒 |

參數：無（同 2.1）

回傳 `data`：

```json
{
  "campaigns": [
    { "campaignName": "Q4 高股息 ETF 推廣", "date": "2026-10-05", "channel": "EDM", "response": "CLICKED" },
    { "campaignName": "退休規劃講座邀約", "date": "2025-09-05", "channel": "簡訊", "response": "CONVERTED" }
  ]
}
```

`response`：`NO_RESPONSE`、`OPENED`、`CLICKED`、`CONVERTED`

---

## 3. 市場工具

資料來源：臺灣證券交易所 OpenAPI（`https://openapi.twse.com.tw/v1`）。市場資料是公開資訊，與顧客無關。

### 3.1 TWSE client 共通規則

| 項目 | 規則 |
|---|---|
| HTTP client | Spring `RestClient` |
| Host 白名單 | 只允許 `openapi.twse.com.tw`，由攔截器在送出前檢查；路徑由程式寫死，模型無法指定 |
| Timeout | 連線 3 秒、讀取 5 秒 |
| 回應大小 | 最多 2MB，超過就中止並視為失敗（目前最大的 `STOCK_DAY_ALL` 約 320KB） |
| 快取 | 每個端點整包快取 10 分鐘，再依股票代號過濾 |
| 日期 | TWSE 回傳民國日期（例如 `1151016`），轉成 ISO 日期 `2026-10-16` |
| 數字 | TWSE 回傳字串，轉成數字；無法轉換時為 `null` |
| 欄位名稱 | 部分欄位名稱有多餘空白（例如 `t187ap04_L` 的 `"主旨 "`），解析時先 trim key |
| 錯誤轉換 | 逾時 → `MARKET_DATA_TIMEOUT`；其他錯誤（連線失敗、非 2xx、格式錯誤、超過大小）→ `MARKET_DATA_UNAVAILABLE` |
| 外部內容 | 文字欄位（公司名稱、主旨）視為不可信資料，截斷長度後才交給模型 |

### 3.2 `getMarketIndex`

| 項目 | 內容 |
|---|---|
| 用途 | 查詢大盤指數 |
| 給模型的說明 | 查詢台股最近一個交易日的主要指數收盤資訊（加權指數、臺灣50 等）。 |
| 需要的存取等級 | `LIMITED` |
| Timeout | 5 秒 |
| TWSE 端點 | `GET /exchangeReport/MI_INDEX`（約 270 筆、46KB） |

參數：無

處理：從 `MI_INDEX` 篩選固定的指數清單，避免把 270 筆全部送給模型：發行量加權股價指數、臺灣50指數、寶島股價指數、電子工業類指數、金融保險類指數。

回傳 `data`：

```json
{
  "dataDate": "2026-10-16",
  "indices": [
    { "name": "發行量加權股價指數", "close": 47111.26, "change": 141.39, "changePercent": 0.30 },
    { "name": "寶島股價指數", "close": 52176.05, "change": 175.67, "changePercent": 0.34 }
  ]
}
```

`change` 由 TWSE 的「漲跌」（`+`/`-`）與「漲跌點數」組合而成。

### 3.3 `getStockQuote`

| 項目 | 內容 |
|---|---|
| 用途 | 查詢上市個股行情 |
| 給模型的說明 | 查詢上市個股最近一個交易日的收盤行情、本益比、殖利率與股價淨值比。需要股票代號，例如 2330。 |
| 需要的存取等級 | `LIMITED` |
| Timeout | 5 秒 |
| TWSE 端點 | `GET /exchangeReport/STOCK_DAY_ALL`（約 1,380 筆）＋ `GET /exchangeReport/BWIBBU_ALL`（約 1,080 筆） |

參數：

```json
{
  "type": "object",
  "properties": {
    "stockCode": { "type": "string", "pattern": "^\\d{4,6}$", "description": "上市股票代號，例如 2330" }
  },
  "required": ["stockCode"],
  "additionalProperties": false
}
```

回傳 `data`：

```json
{
  "stockCode": "2330",
  "name": "台積電",
  "dataDate": "2026-10-16",
  "open": 2430.00,
  "high": 2445.00,
  "low": 2420.00,
  "close": 2420.00,
  "change": -20.00,
  "tradeVolume": 43046982,
  "peRatio": 28.05,
  "dividendYield": 0.91,
  "pbRatio": 9.76
}
```

- 代號不存在：`status=ERROR`、`errorCode=STOCK_NOT_FOUND`
- `BWIBBU_ALL` 查不到（例如虧損公司沒有本益比）時，這三個欄位為 `null`，不算錯誤
- 上櫃股票不在 TWSE 資料內，POC 會回 `STOCK_NOT_FOUND`，回答時應說明「目前只支援上市股票」

### 3.4 `getCompanyAnnouncements`

| 項目 | 內容 |
|---|---|
| 用途 | 查詢上市公司重大訊息 |
| 給模型的說明 | 查詢上市公司「當日」公告的重大訊息。只有當天的資料，查不到代表今天沒有公告。 |
| 需要的存取等級 | `LIMITED` |
| Timeout | 5 秒 |
| TWSE 端點 | `GET /opendata/t187ap04_L` |

參數：

```json
{
  "type": "object",
  "properties": {
    "stockCode": { "type": "string", "pattern": "^\\d{4,6}$" },
    "limit": { "type": "integer", "minimum": 1, "maximum": 5, "default": 3 }
  },
  "required": ["stockCode"],
  "additionalProperties": false
}
```

回傳 `data`：

```json
{
  "stockCode": "2072",
  "companyName": "世紀風電",
  "announcements": [
    { "date": "2026-10-18", "time": "07:00:03", "subject": "公告本公司名稱由「世紀離岸風電設備股份有限公司」更名為……", "factDate": "2026-08-24" }
  ]
}
```

- `subject` 去除換行，最多 200 字；不傳「說明」全文，避免送出過多外部內容
- 查不到：`status=SUCCESS`，`announcements` 為空陣列（「今天沒有重大訊息」是正常結果）

> 限制：TWSE 的重大訊息端點只有當日資料。要查近期歷史，之後需另找來源。

---

## 4. 工具錯誤碼

| errorCode | status | 說明 |
|---|---|---|
| `TOOL_NOT_ALLOWED` | DENIED | 工具不存在，或不在本次提供的清單內（例如 `LIMITED` 使用者的模型呼叫了互動紀錄工具） |
| `TOOL_ARGUMENT_INVALID` | DENIED | 參數格式、長度驗證失敗，或含有 schema 以外的欄位 |
| `CUSTOMER_DATA_UNAVAILABLE` | ERROR | 綁定的顧客在查詢當下已無資料（例如剛被移除） |
| `STOCK_NOT_FOUND` | ERROR | 查不到股票代號 |
| `TOOL_CALL_LIMIT_REACHED` | DENIED | 單次提問的工具呼叫超過 5 次，超過的部分不執行 |
| `TOOL_TIMEOUT` | TIMEOUT | 工具執行逾時 |
| `MARKET_DATA_TIMEOUT` | TIMEOUT | TWSE 逾時 |
| `MARKET_DATA_UNAVAILABLE` | ERROR | TWSE 其他錯誤 |
| `TOOL_EXECUTION_FAILED` | ERROR | 其他未預期錯誤 |

> 顧客存取權限在進入對話 API 時就已判斷（無權限回 404），所以工具層不再有「顧客越權」的錯誤。

---

## 5. POC 假資料

### 5.1 分行與單位

| 區域 | 分行 |
|---|---|
| 北區（`NORTH`） | B101 台北分行、B102 板橋分行 |
| 中區（`CENTRAL`） | B201 台中分行 |
| 南區（`SOUTH`） | B301 高雄分行 |

行銷單位：`MU-N1`（北區 AO 行銷單位）、`MU-S1`（南區 AO 行銷單位）

### 5.2 顧客

每位顧客都有一組**虛構但格式真實**的識別資料（姓名、身分證號、手機），用來驗證去識別化確實生效。

| 編號 | 姓名（虛構） | 主經營行 | 利潤分行 | 理財分行 | 生效名單分派人員（所屬分行） | 行銷單位 | 測試重點 |
|---|---|---|---|---|---|---|---|
| C001 | 王小明 | B101 | B101 | B101 | fc.officer（B101） | — | 所有 B101 角色都看得到；互動摘要中出現本人姓名（D3） |
| C002 | 林美君 | B102 | B102 | B101 | fc.officer2（B102） | — | 只因「理財分行 B101」可見 → 理專看得到、AO 看不到；互動摘要藏有注入字串與手機號碼（X2、D3） |
| C003 | 陳志華 | B201 | B201 | B201 | fc.officer（B101） | — | 跨分行分派給 fc.officer → 理專經辦（當事者）看得到、AO 經辦看不到 |
| C004 | 黃俊豪 | B201 | B101 | B201 | ao.officer2（B201） | MU-N1 | 只因「利潤分行 B101」或行銷單位可見 → AO 看得到、理專看不到 |
| C005 | 周雅婷 | B301 | B301 | B301 | fc.south（B301） | MU-S1 | 只有總行查詢與後台 / IT（去識別化）看得到 |
| C006 | 吳建德 | B201 | B201 | B201 | ao.officer2（B201） | MU-N1 | 只因「行銷單位 MU-N1」可見 → 只有 AO 看得到 |

存取結果見 [02-rest-api.md](02-rest-api.md) 1.4。

### 5.3 C001 範例資料

| 項目 | 內容 |
|---|---|
| 識別資料（只在 Java 端） | 王小明、A123456789、0912-000-111、xiaoming.wang@example.com、帳號 0123456789012 |
| 概況 | VIP、58 歲 → `55-64`、往來 8 年、資產 → `500萬-1000萬`、穩健型、同意行銷、偏好電話；標籤：退休規劃、偏好定期定額、高股息偏好 |
| 持有 | 台積電（2330）20%-30%、全球高股息基金 30%-40%、台幣定存 30%-40% |
| 互動 | 10/02 電話：王小明詢問月配息商品，希望每月現金流約 3 萬，約定 10 月下旬面談；09/15 LINE：關心台積電股價波動 |
| 活動回應 | Q4 高股息 ETF 推廣（CLICKED）、退休規劃講座邀約（CONVERTED） |
