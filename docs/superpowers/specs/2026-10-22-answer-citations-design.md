# 回答引用設計（加分挑戰 1）

- 日期：2026-10-22
- 版本：v0.1
- 狀態：待審查
- 對應需求：[requirements.md](../../../requirements.md) 第 4 節「1. 回答引用」
- 相關規格：[02-rest-api.md](../../spec/02-rest-api.md)、[03-tool-catalog.md](../../spec/03-tool-catalog.md)、[04-mongodb-schema.md](../../spec/04-mongodb-schema.md)、[05-error-codes.md](../../spec/05-error-codes.md)

## 1. 目標與範圍

### 1.1 目標

需求原文：「回答可展開查看使用的資料來源、工具名稱及資料時間，但不得顯示敏感參數或原始個資。」

引用同時服務兩種用途：

| 使用者 | 用途 | 需要的能力 |
|---|---|---|
| 理專、AO、業務 | 對顧客說之前，快速確認某句話的根據和資料時間 | 回答中的每個重點都標出來源，點開就能看到依據 |
| 稽核 | 事後追溯回答來自哪些工具結果、有沒有捏造 | 稽核紀錄保存「標註 → 工具呼叫」對照、資料描述、結果雜湊，以及數字核對結果 |

### 1.2 現況與差距

現在的回答下方有「使用 N 個工具」摘要和「來源」清單，已經滿足需求的基本要求，但有三個缺口：

1. 來源沒有對應到回答中的句子，看不出哪一句來自哪個來源。
2. 同一個來源名稱可能重複出現（例如行情和重大訊息都顯示「臺灣證券交易所 OpenAPI」），看不出各自查了什麼。
3. 顧客資料沒有資料時間，也沒有說明查了什麼。

### 1.3 已確定的決策

| 項目 | 決策 |
|---|---|
| 引用粒度 | 每個段落或條列項目標註來源，以工具呼叫為單位（一次行情查詢是一個引用） |
| 標註方式 | 模型以結構化 JSON 輸出回答（區塊陣列），每個區塊各自帶來源 |
| 稽核內容 | 保存對照、資料描述與結果雜湊；工具結果原文以設定開關控制，預設關閉，待 M36 法遵答覆 |
| 展開內容 | 外部資料顯示原文摘錄與連結；內部顧客資料只顯示查詢項目、資料時間與筆數 |
| 未標註的內容 | 後端核對回答中的數字，找不到依據的只記在稽核紀錄，前端不顯示 |

### 1.4 不在範圍

- Prompt 評測集（加分挑戰 2，另一份規格）
- 串流回應與「思考步驟」呈現
- 使用者回饋（like / dislike）
- 實際開啟工具結果原文的保存（只實作開關）

## 2. 後端流程

```
使用者提問
  │
  ▼
模型回合 1..n（帶工具定義）── tool call ──► ToolExecutor
  │                                         │ 成功時配發引用編號 S1、S2…
  │                                         │ 回送模型的結果帶 "citationId": "S1"
  ◄─────────────────────────────────────────┘
  │
  ▼
最後一回合：ChatRequest 帶 strict JSON Schema（ResponseFormat）
  │  模型回 { "blocks": [ { type, text, sources[] } ] }
  ▼
AnswerAssembler（新增）
  1. 解析並驗證 JSON
  2. 每個區塊的 text 經過 PiiRedactor 與「本顧客」替換
  3. sources 只保留本次成功工具呼叫的編號，無效的移除
  4. 依首次出現順序把 S 編號換成引用編號 1、2、3…
  5. 數字核對
  6. 移除只有警語的結尾區塊
  ▼
ChatResult：blocks + citations + toolCalls + disclaimer → 前端與稽核
```

### 2.1 引用編號

- `ToolExecutor` 為每次**成功**的工具呼叫依序配發 `S1`、`S2`…；失敗、無權限、逾時、超過上限的呼叫不配發。
- 回送模型的格式（03 第 1.5 節）在成功時多一個欄位：

```json
{ "untrustedData": true, "status": "SUCCESS", "citationId": "S1", "data": { } }
```

### 2.2 最後一回合的 JSON 輸出

- 呼叫工具的回合不變，tool call 上限、逾時、權限檢查都照舊。
- 每一回合的 `ChatRequest` 都帶 `ResponseFormat`（JSON Schema，strict）。模型決定呼叫工具時回 tool call；決定回答時回符合 schema 的 JSON。
- JSON Schema：

```json
{
  "type": "object",
  "properties": {
    "blocks": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "type": { "type": "string", "enum": ["heading", "paragraph", "bullet", "numbered"] },
          "text": { "type": "string" },
          "sources": { "type": "array", "items": { "type": "string", "enum": ["S1", "S2"] } }
        },
        "required": ["type", "text", "sources"],
        "additionalProperties": false
      }
    }
  },
  "required": ["blocks"],
  "additionalProperties": false
}
```

- 事先無法知道哪一回合是最後一回合，所以每一回合都帶 schema，`sources` 的 `enum` 依當下已配發的編號重建。還沒有成功的工具呼叫時，改用不含 `enum` 的字串陣列，由後端驗證時全部移除。
- 實作第一步要用真實 OpenAI 呼叫驗證兩件事：strict mode 接受這個 schema（含動態 enum）；同一個請求同時帶 tool 定義和 `ResponseFormat` 時，模型仍會正常呼叫工具。任一項不成立時，`enum` 改為不限定，只靠 2.3 的後端驗證。
- 輸出 JSON 比純 Markdown 多用 token，`max-output-tokens` 由 1000 調為 1500；最終值依第 6 節實測結果決定。

### 2.3 AnswerAssembler

新元件，負責把模型的 JSON 轉成回應與稽核內容。

| 步驟 | 規則 |
|---|---|
| 解析 | 不是合法 JSON、不符合 schema、或模型回 refusal → `AI_ANSWER_FORMAT_INVALID`，不自動重試 |
| 數量上限 | 最多 40 個區塊；所有區塊 `text` 合計最多 8000 字。超過時保留前面的區塊並截斷，設 `answerTruncated`，不視為失敗 |
| 空白區塊 | `text` 去除前後空白後為空的區塊直接移除；全部移除後沒有區塊時 → `AI_ANSWER_FORMAT_INVALID` |
| 去識別化 | 每個區塊的 `text` 個別經過 `PiiRedactor`，再把 `【本顧客】` 換成「本顧客」（與現行回答相同） |
| 來源驗證 | `sources` 只保留本次成功工具呼叫的編號，重複的合併；`heading` 區塊的 `sources` 一律清空 |
| 重新編號 | 依區塊順序，S 編號首次出現時給引用編號 1、2、3…；沒有被任何區塊引用的成功呼叫，依呼叫順序接在後面，標為未引用 |
| 警語 | 最後一個區塊的內容只有投資警語時移除（取代現行 `stripTrailingDisclaimer`） |
| 扁平化 | 由區塊產生 Markdown：`heading` → `### `、`bullet` → `- `、`numbered` → `1. `、`paragraph` 前後空行。回應的 `answer` 不含標註；稽核的 `answer` 在每個區塊後加 ` [n]` |

### 2.4 數字核對

目的是找出回答中「本次工具結果裡找不到」的數字，供稽核與之後的評測集統計，不影響回答。

- 從每個非 `heading` 區塊的 `text` 擷取數字：整數、小數、千分位（`2,420`）、百分比（`0.91%`）、日期（`2026-10-21`、`10/21`）。
- 正規化後比對：移除千分位；百分比只比數值；小數去掉末尾的 0；日期轉成月、日數字比對。
- 比對對象：本次所有成功工具結果送給模型的 `data`（去識別化後）中出現的數字，日期欄位拆成年、月、日。
- 略過：1～10 的整數（常是筆數或序號）。
- 找不到的數字記入稽核的 `unverifiedNumbers`（去重，最多 20 個）。模型自行換算的數字（例如「約占 3 成」）也會被記入，這是預期行為。

### 2.5 各工具的引用資訊

每個工具在 `ToolResult` 旁提供 `CitationDetail`，內容由工具決定，不得包含顧客識別資料或工具參數中的敏感值。

| 工具 | title | target | excerpt（外部）／ description（內部） | dataTime |
|---|---|---|---|---|
| `getCustomerProfile` | 顧客概況 | — | 「N 個欄位」 | — |
| `getCustomerHoldings` | 顧客持有商品 | — | 「N 筆」 | — |
| `getRecentInteractions` | 近期互動紀錄 | — | 「N 筆，最近一筆 MM/DD」 | — |
| `getCampaignResults` | 行銷活動反應 | — | 「N 筆」 | — |
| `getStockQuote` | 上市個股行情 | 公司名稱與代號 | 「收盤 X、漲跌 Y、本益比 Z、殖利率 W%」 | 資料日期 |
| `getMarketIndex` | 大盤指數 | — | 「加權指數 X（漲跌 Y）」等，最多 3 項 | 資料日期 |
| `getCompanyAnnouncements` | 上市公司重大訊息 | 公司名稱與代號 | 最多 3 則主旨（截斷 60 字）；沒有資料時為「查無重大訊息」 | 有公告時為資料日期，沒有公告時為 null |

- 內部工具的 `target`、`excerpt` 一律為 `null`，只有 `description`。
- 外部資料的主旨是不可信內容（03 第 3 節），前端只以純文字顯示。
- `sourceName`、`url` 沿用現行 03 第 1.8 節的來源標示。

## 3. API 與稽核資料

### 3.1 對話 API 回應

`POST /api/ai-assistant/customers/{customerId}/chat` 回應 200（只列異動）：

```jsonc
{
  "requestId": "aa9e6000",
  "blocks": [
    { "type": "heading", "text": "持有標的近況", "citations": [] },
    { "type": "bullet", "text": "台積電(2330)：收盤 2420 元，上漲 15 元", "citations": [1] },
    { "type": "bullet", "text": "今日查無重大訊息", "citations": [2] }
  ],
  "answer": "### 持有標的近況\n\n- 台積電(2330)：收盤 2420 元，上漲 15 元\n- 今日查無重大訊息",
  "citations": [
    { "id": 1, "toolName": "getStockQuote", "title": "上市個股行情", "target": "台積電 2330",
      "excerpt": "收盤 2420、漲跌 +15、本益比 28.05、殖利率 0.91%", "description": null,
      "sourceName": "臺灣證券交易所 OpenAPI", "url": "https://openapi.twse.com.tw/",
      "dataTime": "2026-10-21", "queriedAt": "2026-10-22T14:20:11+08:00", "cited": true },
    { "id": 2, "toolName": "getCompanyAnnouncements", "title": "上市公司重大訊息", "target": "台積電 2330",
      "excerpt": "查無重大訊息", "description": null,
      "sourceName": "臺灣證券交易所 OpenAPI", "url": "https://openapi.twse.com.tw/",
      "dataTime": null, "queriedAt": "2026-10-22T14:20:11+08:00", "cited": true },
    { "id": 3, "toolName": "getCustomerHoldings", "title": "顧客持有商品", "target": null,
      "excerpt": null, "description": "3 筆",
      "sourceName": "平台顧客資料（模擬）", "url": null,
      "dataTime": null, "queriedAt": "2026-10-22T14:20:09+08:00", "cited": false }
  ],
  "toolCalls": [ ],
  "disclaimer": "資訊僅供內部經營參考，不構成投資建議",
  "queriedAt": "2026-10-22T14:20:13+08:00"
}
```

- `blocks[].citations` 是引用編號（1 起算），對應 `citations[].id`。
- 每次成功的工具呼叫都有一筆 `citations`；`cited: false` 表示回答沒有引用它。模型完全沒有標註時，來源仍會列出。
- `answer` 保留：前端用它當對話歷史（模型下一輪看到的是不含編號的 Markdown）和複製內容。
- 移除原本的 `sources` 欄位。

### 3.2 稽核紀錄

`COLL_ICRM_AI_ASSISTANT_AUDIT` 新增或調整的欄位：

| 欄位 | 型別 | 說明 |
|---|---|---|
| `answer` | string \| null | 改為**含** ` [n]` 標註的 Markdown，稽核時可對照每段的依據 |
| `toolCalls[].citationId` | int \| null | 對應的引用編號；未配發時為 null |
| `toolCalls[].target` | string \| null | 工具宣告的非敏感查詢對象（2.5 節）；顧客工具一律為 null |
| `toolCalls[].dataTime` | string \| null | 資料日期 |
| `toolCalls[].resultCount` | int \| null | 結果筆數 |
| `toolCalls[].resultHash` | string \| null | 送給模型的去識別化結果（`payloadForModel`）的 SHA-256，十六進位 |
| `toolCalls[].result` | object \| null | 只有 `ai-assistant.audit.store-tool-results: true` 時寫入，內容即送給模型的 `data`，最多 4 KB（同 `tool-result-max-bytes`） |
| `unverifiedNumbers` | string[] | 數字核對找不到依據的數字，最多 20 個 |

- 04 第 3.1 節「不記錄：工具參數、工具結果」改為：「不記錄工具參數，只記錄工具自行宣告的非敏感查詢對象（`target`）；工具結果只在開關開啟時記錄」。
- 新增設定 `ai-assistant.audit.store-tool-results`，預設 `false`。開啟前需 M36 法遵同意。
- 開關關閉時，每筆稽核紀錄約增加 0.3～0.5 KB，不影響 04 第 5 節的容量估算；開啟時每筆可能增加到 10～20 KB。
- JSON Schema Validation（04 第 3.3 節）同步加入新欄位。

### 3.3 錯誤碼

05 新增：

| code | HTTP | 友善訊息 | 情境 |
|---|---|---|---|
| `AI_ANSWER_FORMAT_INVALID` | 502 | 回答格式異常，請重試 | 模型的最終回答不是合法 JSON、不符合 schema、沒有任何非空白區塊，或模型拒絕回答（refusal） |

- 稽核記 `outcome: FAILED`、`errorCode: AI_ANSWER_FORMAT_INVALID`。
- 前端沿用現有錯誤訊息與重試按鈕。
- 我們自己的規則造成的拒答（例如拒絕透露系統提示）是正常的區塊回答，不受影響。

## 4. 前端

### 4.1 回答呈現

- 新增 `AnswerBlocks` 元件，取代 `AssistantMessage` 裡的 `<SafeMarkdown :source>`：
  - `heading` → 標題；`paragraph` → 段落；連續的 `bullet` 合併成一個無序清單，連續的 `numbered` 合併成一個有序清單。
  - 區塊文字內的行內格式（粗體、連結）沿用自寫 parser 的 `parseInline`（改為 export），一樣只用 `h()` 渲染、不使用 `innerHTML`，連結只允許 http(s)。
- 有引用的區塊在文字後加上標按鈕 ¹ ²：
  - `aria-label="引用 1：上市個股行情"`，可用鍵盤操作。
  - 點擊後展開引用清單中的對應項目，捲動到可見範圍並短暫高亮。

### 4.2 引用清單

新增 `CitationList` 元件，取代 `SourceList`：

```
── 引用（3）──────────────────────────────
▸ [1] 上市個股行情 · 台積電 2330
      資料 2026-10-21 · 查詢 14:20 · 臺灣證券交易所 OpenAPI ↗
      （展開）收盤 2420、漲跌 +15、本益比 28.05、殖利率 0.91%
▸ [2] 上市公司重大訊息 · 台積電 2330
      查詢 14:20 · 臺灣證券交易所 OpenAPI ↗
      （展開）查無重大訊息
   其他查詢資料
▸ [3] 顧客持有商品 · 平台顧客資料（模擬）
      查詢 14:20
      （展開）3 筆
```

- `cited: true` 的項目依編號列出；`cited: false` 的放在「其他查詢資料」分組。
- 展開內容：外部資料顯示 `excerpt`，內部資料顯示 `description`，一律以純文字呈現。
- 「使用 N 個工具」摘要維持不變。

### 4.3 狀態與歷史

- `ChatMessage` 型別新增 `blocks`、`citations`，移除 `sources`。
- 對話歷史與複製按鈕都使用 `answer`，歷史組裝邏輯不變。
- Prompt 管理頁不受影響：回答套版仍以 Markdown 撰寫，由模型轉成區塊。

## 5. Prompt

在平台安全規則（`SafetyRules`）之後新增「回答格式規則」。和安全規則一樣只存在程式碼，管理者無法修改，任何 API 都不會回傳：

```
# 回答格式規則
- 最終回答必須符合指定的 JSON 格式，由 blocks 陣列組成
- 問題分類套版中的 ### 小節對應 heading，條列對應 bullet 或 numbered，其他文字對應 paragraph
- 每個 paragraph、bullet、numbered 的 sources 填入支持該段內容的工具結果 citationId
- 沒有工具依據的內容（例如一般說明、建議話術）sources 留空
- 只能使用本次工具結果中出現的 citationId，不得自行編造
```

- 07-prompt-examples.md 的套版內容不需要改寫；文件補充說明套版會被轉成區塊。
- `PromptAssembler` 組裝順序：安全規則 → 回答格式規則 → System Prompt → 問題分類。

## 6. 測試與驗收

### 6.1 後端

- `AnswerAssembler` 單元測試：
  - JSON 解析失敗、不符 schema、refusal、全部空白 → `AI_ANSWER_FORMAT_INVALID`
  - 每個區塊個別去識別化，`【本顧客】` 替換
  - 無效、重複的 `sources` 處理；`heading` 的 `sources` 清空
  - 重新編號：首次出現順序、未引用的接在後面
  - 數字核對：千分位、小數末尾 0、百分比、日期、略過 1～10、去重與 20 個上限
  - 結尾警語區塊移除；40 區塊與 8000 字上限截斷
  - 扁平化：回應的 `answer` 不含標註，稽核的 `answer` 含標註
- 7 個工具的 `CitationDetail`：內容符合 2.5 節，顧客工具不含 `target`、`excerpt`。
- JSON Schema 產生：動態 enum；沒有成功工具時不含 enum。
- 整合測試：`ScriptedChatModel` 在最後一回合回 JSON，走完整流程；檢查回應、稽核新欄位、JSON Schema Validation，以及 `store-tool-results` 開與關。
- 既有測試由 `sources` 改為 `citations`。

### 6.2 前端

- `AnswerBlocks`：清單合併、行內格式、XSS 測試字串（`<script>`、`javascript:` 連結）、引用按鈕的 aria-label。
- `CitationList`：已引用與未引用分組、展開內容、外部與內部的顯示差異。
- 點擊引用按鈕後，對應項目展開並高亮。

### 6.3 真實模型驗證

以 demo-checklist 的 Q1–Q4、D1、X1、X3 實際呼叫 OpenAI，記錄：

| 指標 | 用途 |
|---|---|
| JSON 失敗次數 | 確認 strict schema 的穩定度 |
| 有來源的區塊比例（不含 heading） | 確認模型配合標註的程度 |
| `unverifiedNumbers` 的實例 | 判斷數字核對的誤判情形 |
| 輸出 token 數 | 決定 `max-output-tokens` 最終值 |

demo-checklist 新增引用相關的檢查步驟。

## 7. 文件異動

| 文件 | 異動 |
|---|---|
| 02-rest-api.md | 2.1 Response 200 改為 `blocks`、`citations`，移除 `sources` |
| 03-tool-catalog.md | 1.5 成功時加 `citationId`；新增「引用資訊」小節（2.5 節的表格）；1.8 改為引用於各工具的說明 |
| 04-mongodb-schema.md | 3.1 新增欄位與「不記錄」規則修改；3.3 Validation |
| 05-error-codes.md | 新增 `AI_ANSWER_FORMAT_INVALID` |
| 06-conversation-scenarios.md | 各情境的預期結果加上引用 |
| 07-prompt-examples.md | 說明套版會被轉成區塊、回答格式規則的存在（不列出內容） |
| 01-architecture.md | 元件清單加入 `AnswerAssembler`、`CitationDetail` |
| demo-checklist.md | 新增引用檢查步驟 |
