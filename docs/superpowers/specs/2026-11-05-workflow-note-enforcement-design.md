# 工作流與註記強制設計

- 日期：2026-11-05
- 版本：v0.5
- 狀態：待審閱
- v0.5（2026-11-05）：選擇器改為可搜尋的單選下拉選單，選了就插入，不顯示提示。
- v0.4（2026-11-05）：重複的工具標記由程式合併，只調用一次；拿掉「本分類必查」清單。
- v0.3（2026-11-05）：選擇器插入的一律是必查標記，拿掉「程式必查」勾選。
- v0.2（2026-11-05）：工作流從固定的 `## 處理流程` 章節改為可放在文字任意位置的內嵌工具標記，並調整選擇器與相關規則。
- 起因：小幫手準備整併進實際平台。目前工作流只能寫成 instruction 的文字，由 GPT 自行決定是否照做；2026-10-29 實測也顯示，GPT 看得到註記卻沒有遵守（R1、F1、F2、F3，見 [demo-checklist](../../demo-checklist.md)、ideas-backlog 第 13 項）。
- 前提：本設計寫在 [Clean Architecture 分層](2026-11-02-clean-architecture-layering-design.md) 之後的結構上，必須維持 `ArchitectureRulesTest` 全綠。
- 相關規格：[01-architecture.md](../../spec/01-architecture.md) 3、4、[02-rest-api.md](../../spec/02-rest-api.md) 3、[03-tool-catalog.md](../../spec/03-tool-catalog.md) 1、2、[07-prompt-examples.md](../../spec/07-prompt-examples.md)、[prompt-evaluation](2026-10-24-prompt-evaluation-design.md)

## 1. 目標與決策

要達成的結果：

1. 註記規則由程式保證，不靠 GPT 自律，管理者也無法透過 prompt 或工作流關掉。
2. 管理者可以替問題分類設定必查資料與順序，由程式執行。
3. 管理者不必背 tool 名稱，用選擇器就能寫出正確的流程。
4. 評測驗得出流程有沒有真的觸發。

| 項目 | 決策 | 節 |
| --- | --- | --- |
| 強制範圍 | 註記規則全部強制，加上可設定的工作流 | 2、3 |
| 工作流表達能力 | 必查資料加順序（線性步驟）。資料格式保留擴充空間，這次不做條件分支 | 3.1 |
| 步驟執行者 | 程式依序執行，不經 GPT；GPT 之後仍可自行調用其他 tool | 3.3 |
| 需要參數的 tool | 可以當步驟。執行前先抽取參數，缺少時由程式請使用者提供 | 3.4 |
| 步驟失敗 | 繼續回答，由程式標示未完成的查詢 | 3.3、2.3 |
| 工作流存放 | instruction 內容裡的內嵌工具標記 `{{tool}}`，格式自由，不新增資料庫欄位 | 3.1 |
| 編輯方式 | 可搜尋的單選下拉選單，選了就在游標位置插入必查標記，不顯示提示；手打仍可使用 | 5.2 |
| 註記說明顯示時機 | 本題調用過顧客 tool 才顯示 | 2.3 |
| 讀不到註記 | 視為受限，扣住行銷相關資料 | 2.2 |
| 順序驗證 | 新增 `expect.toolOrder`，結果併入 `toolsOk` | 4 |

不做的事：

- 不做條件分支（「X 符合某條件才查 Y」）。
- 不支援用前一步的結果當下一步的參數；參數只來自步驟選項，或問題與近期對話。
- 標記不受文字條件影響：「如果…再查 `{{tool}}`」每次都會查。文字裡的整理、比較、判斷由 GPT 做，程式不解析。
- 不做表單式工作流編輯器與新的資料庫欄位。
- 不拆分「市場快訊」分類，也不替它設參數步驟（原因見 3.4）。
- 不支援一個參數步驟同時查多檔股票。
- 不改路由：工作流只在路由選中單一分類時生效（3.3）。
- 不判斷註記到期日（2.2）。

## 2. 註記強制

只作用於 `FULL`。`LIMITED` 本來就只拿得到去識別化的基本資料，也看不到註記，行為不變。

### 2.1 `CustomerRestrictions`

新增 `domain/customer/CustomerRestrictions`，由 `TurnCustomerData.realTime()` 算出本題限制，`CustomerNotes`、`GetCustomerTool` 與回答組裝共用同一份判斷，避免規則分散：

- `notesUnavailable`：`realTime()` 為空或拋出例外。
- `withholdMarketingData`：有 `FlagType.NO_MARKETING`，或 `notesUnavailable` 為真。
- `notices()`：要加在回答最前面的固定文字（2.3）。

`CustomerNotes` 已在組 prompt 時讀過即時註記，`TurnCustomerData` 會快取結果（含例外），所以這裡不會多打一次平台 API。

### 2.2 不能說的內容：扣住資料

`withholdMarketingData` 為真時，`getCustomer` 不回傳以下區塊：

| 區塊 | 原因 |
| --- | --- |
| `management` | 活動沒有推廣型或服務型欄位，無法分辨；推薦產品要扣；經營洞察是自由文字，可能含行銷內容 |
| `productIntent` | 商品機會的依據 |

其他區塊不受影響。服務類提醒來自 `getRealTimeCustomer` 的 `keyAlerts`，不會被扣。

回傳格式：

- `status` 仍為 `SUCCESS`，`data` 多一個固定 key `withheldSections`（被扣的區塊 key）與 `restrictionNote`（固定文字：「依本顧客註記，這些區塊不提供」）。
- 扣住不算失敗，所以工作流不會誤標未完成。
- 請求的區塊全部被扣時也回 `SUCCESS`，不走現有的 `CUSTOMER_DATA_UNAVAILABLE`。引用標題為「個人360・資料受註記限制」，`count` 為 0。

平台回傳的註記一律視為生效，即使到期日已過。目前沒有規格說明到期後如何處理，這點列入整併時要向主管或平台確認的項目（第 8 節）。

這層只保證 GPT 拿不到系統裡的推薦品項與活動。GPT 仍可能用其他資料自己寫出行銷話術，這部分靠 prompt 規則與評測持續檢查。

### 2.3 一定要說的內容：程式加在回答最前面

`AnswerAssembler.assemble` 新增參數 `List<String> programNotices`，每則組成一個 `PARAGRAPH` block，放在 GPT 內容之前。

| 情況 | 固定文字 | 顯示條件 |
| --- | --- | --- |
| 不宜行銷 | 本顧客有不宜行銷註記，以下不提供商品機會、推薦產品或行銷話術。 | 本題調用過顧客 tool |
| 客訴、應注意名單 | 注意事項：本顧客有{類別}註記（到期日 yyyy/MM/dd），請依規定程序處理。沒有到期日或格式不符時省略括號 | 本題調用過顧客 tool |
| 特殊客群 | 注意事項：本顧客為特殊客群，請依規定程序處理，並確認商品適合度。 | 本題調用過顧客 tool |
| 讀不到註記 | 無法取得註記，商品與行銷資料暫不提供，請先參考平台畫面上的註記。 | 本題調用過顧客 tool |
| 工作流步驟失敗 | 未能完成「{tool 的 purpose}」，以下回答可能不完整。 | 有步驟失敗就顯示 |

規則：

- 「調用過顧客 tool」指本題 `executed` 裡有實作 marker 介面 `CustomerDataTool` 的 tool（包含工作流步驟，失敗也算）。寫法比照現有的 `MarketTool`，`AbstractCustomerTool` 實作它。
- 順序固定：不宜行銷、其他註記、讀不到註記、步驟失敗。
- 這些 block 在 `limit` 之後加入，不會被截斷；不跑數字檢查（到期日不在 tool 資料裡）；沒有引用。
- 它們是回答的一部分，所以 `answer`、`annotatedAnswer`（稽核）、評測都看得到，前端不用改。
- 已知限制：GPT 沒查 tool 就回答顧客問題時不會顯示。由評測補上檢查（第 4 節）。

### 2.4 Prompt 調整

- `CustomerNotes` 的程式固定段落多一句：「程式會在回答最前面列出以上註記，回答中不要重複。」放在程式固定段落，才能對已發布的 `system.main` 立即生效。
- 種子 `system.main.md`「顧客註記」章節與各 instruction 的註記規則改寫：刪除「回答開頭說明」「在結論前先列注意事項」等由程式接手的要求，保留「改從關懷與服務的方向回答」這類只有 GPT 能做的要求。

## 3. 工作流引擎

### 3.1 定義：內嵌工具標記

管理者用任意文字撰寫，需要程式必查的資料就放一個標記，位置與章節不限：

```markdown
## 注意事項
聯繫前先看 {{getRealTimeCustomer}} 的提醒，再參考 {{getCustomer: management, behavior}}，
比對日期後，把最近到期的排在前面。
```

| 內容 | 誰處理 | 保證 |
| --- | --- | --- |
| `{{tool}}` 或 `{{tool: 選項, 選項}}` | 程式依出現順序先執行 | 一定會執行 |
| 其他文字，包含沒有大括號的 tool 名稱 | GPT 閱讀並照做 | 沒有，靠評測檢查 |

內容仍是 instruction 的 Markdown，版本紀錄、差異比對、預約發布、套用舊版與評測比較新舊版本都不用改，資料庫結構也不變。

解析（`domain/prompt/WorkflowParser`）：

- 掃描全文，每個 `{{…}}` 都是一個步驟；內容格式為 `tool 名稱` 或 `tool 名稱: 選項, 選項`，冒號與逗號可用半形或全形。
- 執行順序依標記在內容中出現的先後。
- 同一個 tool 出現多次時由程式合併成一個步驟，只調用一次：位置以第一次出現為準，選項取聯集。例如 `{{getCustomer: management}}` 和 `{{getCustomer: behavior}}` 只查一次，兩個區塊都會拿到。
- 沒有標記代表沒有工作流，行為和現在相同。
- 步驟的資料結構是 `WorkflowStep(toolName, options)`。之後要加條件分支時，在這裡加欄位，不影響既有內容。

### 3.2 發布前檢查

`ConfigValidator` 新增規則，對照 `ToolRegistry`。它既用於發布，也用於草稿驗證、排程到期重驗與評測前驗證，所以都會擋下。

| 情況 | 訊息 |
| --- | --- |
| `SYSTEM_PROMPT` 有標記 | System Prompt 不可放工具標記，流程要設定在問題分類 |
| 標記放在 `## 適用問題` 或 `## 範例問題` | 「{章節}」不可放工具標記（這段會交給路由或顯示成快捷按鈕） |
| 有 `{{` 卻沒有對應的 `}}`（同一行內） | 第 {n} 行的工具標記沒有結尾 |
| 標記內容格式不符 | 第 {n} 個工具標記格式不正確 |
| 超過 3 步（合併後） | 工具標記最多 3 個不同的工具，目前有 {n} 個 |
| tool 不存在 | 第 {n} 個工具標記：找不到工具 {name} |
| 有選項的 tool 沒選 | 第 {n} 個工具標記：{name} 至少要選一個選項 |
| 選項不存在 | 第 {n} 個工具標記：{name} 沒有選項 {option} |
| 沒有選項的 tool 寫了選項 | 第 {n} 個工具標記：{name} 不接受選項 |

規則代碼新增 `WORKFLOW_INVALID`。訊息裡的 tool 名稱與選項來自使用者輸入，最多顯示 32 字元，超過的部分截掉。

Tool 的新介面（`AssistantTool`，都有預設值，既有 tool 只改需要的）：

```java
/** Values an admin may fix in a workflow step; empty when the tool has none. */
default List<ToolOption> options() { return List.of(); }
/** The argument the fixed options fill, e.g. "sections"; null when there are no options. */
default String optionsArgument() { return null; }
/** Text asking the user for this tool's required inputs; used when they cannot be extracted. */
default String inputRequest() { return null; }
```

`getCustomer` 的 `options()` 來自 `CustomerSection`。需要從問題取得的必填參數（`requiredInputs`）由 `specification().parameters().required()` 扣掉 `optionsArgument()` 算出，不另外宣告。`getStockQuote`、`getCompanyAnnouncements` 的 `inputRequest()` 都是「請提供要查詢的股票代號（例如 2330）。」

### 3.3 執行：`WorkflowRunner`

新增 `application/chat/WorkflowRunner`，由 `ChatOrchestrator.Turn.answer` 在組好訊息之後、進入現有迴圈之前呼叫。邏輯放在共用路徑上，所以 `chat` 與 `evaluate` 跑的流程相同。

```
路由選中單一分類，且內容有工具標記
 ├─ ① 有參數步驟 → 抽取參數（3.4）
 │     缺參數 → 回傳請求文字，本題結束，不執行任何步驟
 ├─ ② 依序執行每個步驟（ToolExecutor.execute）
 │     失敗 → 記下該步驟，下一步照跑
 ├─ ③ 每步加入一組 AiMessage（tool request）與 ToolExecutionResultMessage
 └─ ④ 進入現有迴圈，再加上程式標示（2.3）
```

規則：

| 項目 | 做法 |
| --- | --- |
| 沿用的保護 | 走 `ToolExecutor`，權限檢查、逾時、去識別化、大小限制與引用編號都不變 |
| 工具請求 | id 為 `workflow-{n}`，參數由 `options` 或抽取結果組成 JSON |
| 上限 | 步驟加進 `executed`，算入整題 5 次調用；不算入 `toolRounds`，GPT 仍有 3 輪 |
| 期限與預算 | 每步前檢查 deadline；抽取的 token 計入預算，超過照現有規則失敗 |
| 權限不足 | tool 不在本題 `offered` 內時不抽參數，該步驟算失敗。標示用 `registry.find(name).purpose()`，因為 `ToolExecutor` 對被拒絕的呼叫只給「未知工具」 |
| 失敗定義 | `ToolStatus` 不是 `SUCCESS` |
| 不觸發的情況 | 路由回 `none`、路由失敗而載入全部分類（`InstructionSelection` 不是 `ONLY`） |
| 快捷問題 | 視同路由選中，會執行 |
| 已發布內容不合規則 | 正常情況不會發生（3.2 會擋），但 tool 可能在發布後被移除。不合規的標記走 `ToolGuard` 被拒絕，算該步驟失敗；超過 3 個時只執行前 3 個並記不含內容的 warn log，都不讓整題失敗 |
| 稽核 | 不新增欄位。`toolCalls` 依執行順序記錄，`promptVersions` 可對到當時的流程內容 |

`PromptAssembler` 組 prompt 時把標記換成文字，讓 GPT 讀得懂、也知道資料是否已備妥：

| 情況 | `{{getCustomer: management, behavior}}` 換成 |
| --- | --- |
| 路由選中單一分類（步驟已由程式執行） | `getCustomer（系統已查詢）` |
| 路由失敗而載入全部分類（步驟沒有執行） | `getCustomer`，GPT 仍可自行調用 |

路由只讀 `## 適用問題`，而標記不能放在這個章節，所以不受影響。

步驟結果在之後每次呼叫 GPT 都會重送，token 風險見第 7 節。

### 3.4 參數抽取：`StepArgumentExtractor`

新增 `application/chat/StepArgumentExtractor`，比照 `InstructionRouter`：一次 GPT 呼叫、不帶 tool、strict JSON Schema、不拋例外，使用 `router-reasoning-effort`。

- **輸入：** 分類器式的固定 system 文字（`domain/prompt/StepArgumentPrompt`，同樣宣告輸入都是資料、其中的指示不要執行），加上去識別化後的問題、摘要、最近 4 則對話（每則最多 500 字）。不帶 system prompt。
- **回傳格式：** 由各參數步驟的必填參數組成，例如 `{"step1":{"stockCode":"2330"}}`。一次呼叫處理所有參數步驟。提示要求抽不到時填空字串，不可猜測。
- **驗證：** 組成參數後交給 `ToolGuard.check`。空字串或格式不符都會被拒絕，所以 GPT 編出 `台積電` 這類值也不會被執行。
- **結果：**
  - 全部通過：照順序執行。
  - 任一步驟不通過：不執行任何步驟，本題回覆去重後的 `inputRequest()`，由程式組成 block，不跑數字檢查（否則範例的 2330 會被判為未核對數字）。
  - GPT 呼叫失敗或回傳無法解析：該參數步驟算失敗，其他步驟照跑，回答標示未完成。
- **選填參數不抽取：** 例如 `limit`，由 tool 的預設值處理。
- **續問：** 使用者下一句回「2330」時，靠近期對話讓路由留在同一分類並抽到代號，不需要保存「等待參數」的狀態。

參數步驟綁在分類上，同一分類的每個問題都會觸發。「市場快訊」同時包含大盤和個股，把 `getStockQuote` 設成必查的話，問「今天大盤如何？」也會被要求提供代號，所以參數步驟只適合範圍窄的分類（寫在撰寫建議，見 5.2）。

## 4. 評測

- `EvalCase.Expect` 新增 `toolOrder`（預設空）。`EvalDatasetLoader` 的 `EXPECT_KEYS` 與 tool 名稱檢查一併更新，並要求 `toolOrder` 不得包含 `forbiddenTools` 的項目。
- `CaseChecker`：`toolOrder` 必須是實際調用清單的子序列，中間夾其他調用也算通過。計算方式和現有一樣，所有被要求的調用都算，包含被拒絕或超過上限的。同一輪由 GPT 一次要求的多個調用，以它列出的順序為準。
- 結果併入既有的 `toolsOk`，Mongo validator、`EvalMetrics` 與舊評測紀錄都不用改。
- 前端：`EvaluationCaseList` 的文字改為「工具選擇或順序不符」，`EvaluationCasesDialog` 多一欄「預期順序」，對應 API 型別加上 `toolOrder`。

程式執行的步驟順序不會錯，所以這個檢查主要驗證**流程有沒有真的觸發**。路由選錯分類時，步驟不會執行，檢查就會失敗。

資料集調整（`eval-cases.yml`，目前 34 題，上限 40）：

- F1 補 `tools: [getCustomer]`；F2 補 `tools: [getRealTimeCustomer, getCustomer]`、`toolOrder: [getRealTimeCustomer, getCustomer]`。逐一確認其他註記題都有預期 tool，補上 2.3 的已知限制。
- 種子只替 `instruction.contact-prep` 加流程，在 `## 注意事項` 寫入 `{{getRealTimeCustomer}}` 與 `{{getCustomer: management, behavior}}`，其他分類不動。
- 參數步驟目前沒有種子分類使用，由自動化測試涵蓋。

## 5. 工具選擇器

### 5.1 API

`GET /api/ai-assistant/admin/tools`，權限 `AI_CONFIG_READ`，資料由 `ToolRegistry` 產生，順序和 registry 相同。安全規則與 tool 的完整 schema 不回傳。

```json
{
  "tools": [
    {
      "name": "getCustomer",
      "purpose": "查詢顧客 360 資料",
      "options": [
        { "key": "management", "name": "顧客經營", "description": "專屬活動、建議商品、經營洞察" }
      ]
    },
    {
      "name": "getStockQuote",
      "purpose": "查詢上市個股行情",
      "options": []
    }
  ]
}
```

### 5.2 前端

新增 `admin/ToolPicker.vue`，是可搜尋的下拉選單，沿用 `ConfigPicker.vue` 的 combobox 寫法（`role="combobox"`、`role="listbox"`、鍵盤操作）。`MarkdownEditor` 的編輯分頁標頭加「插入工具」按鈕。

操作流程：點按鈕 → 開啟下拉選單 → 輸入文字搜尋（可選） → 點選一項 → 在游標位置插入標記，選單關閉，游標移到插入內容之後。

選單項目（一次只選一項，選了就插入，不另外確認）：

| tool | 項目 | 顯示文字 | 插入內容 |
| --- | --- | --- | --- |
| 有選項（`getCustomer`） | 每個選項一項，不提供不帶選項的項目 | 顧客經營　getCustomer: management | `{{getCustomer: management}}` |
| 沒有選項 | 一項 | 查詢顧客即時註記與提醒　getRealTimeCustomer | `{{getRealTimeCustomer}}` |

要查多個區塊就選多次。相同 tool 由程式合併（3.1），所以只會調用一次。

搜尋比對 tool 名稱、選項 key、顯示名稱與選項說明。例如輸入「活動」能找到顧客經營。

其他行為：

- 選單不顯示提示，也不檢查游標位置或步驟數量。標記放錯章節或超過上限，由發布前檢查擋下並說明（3.2）。
- `SYSTEM_PROMPT`、唯讀使用者與預覽分頁都沒有此按鈕。
- 清單載入失敗時，選單內顯示錯誤與重試，不影響手打。
- 插入後走現有 `update:modelValue`，所以未儲存標記與驗證都照舊。
- 想讓 GPT 自行判斷時，直接在文字裡手打 tool 名稱，不加大括號。
- 標記的條件句限制、參數步驟只適合範圍窄的分類等注意事項，寫在 [07-prompt-examples.md](../../spec/07-prompt-examples.md) 的撰寫建議，介面上不提示。

## 6. 測試

| 層級 | 內容 |
| --- | --- |
| 單元測試 | `WorkflowParser`（含重複標記合併、選項聯集、位置以第一次為準）；`ConfigValidator` 第 3.2 節每條規則；`CustomerRestrictions`；`GetCustomerTool` 扣住資料（部分、全部、讀不到註記）；`AnswerAssembler` 前綴（順序、不截斷、不跑數字檢查）；`StepArgumentExtractor`（通過、缺參數、格式不符、呼叫失敗）；`CaseChecker` 與 `EvalDatasetLoader` 的 `toolOrder`；`PromptAssembler` 依情況替換標記 |
| 流程測試 | `ChatOrchestratorTest` 以 `ScriptedChatModel` 測：步驟依序執行並進入訊息、引用編號連續、步驟失敗後繼續並標示、權限不足、缺參數時不執行任何步驟、`none` 與 fallback 不觸發、調用上限與輪數計算、顯示條件（純市場問題不顯示註記） |
| API 測試 | `GET /admin/tools` 的權限與內容 |
| 前端測試 | `ToolPicker` 的項目展開、搜尋、鍵盤操作、載入失敗；`MarkdownEditor` 在游標位置插入標記與游標位置；`SYSTEM_PROMPT` 與唯讀時沒有按鈕 |
| 實測 | OpenAI 接受程式放入的 tool 紀錄與 `AnswerSchema` 的組合（比照 `OpenAiResponseFormatLiveTest`）；完整評測，比較 token 與 F1、F2、F3、R1；demo-checklist 手動走一次 |

## 7. 風險

| 風險 | 影響 | 處理 |
| --- | --- | --- |
| Token：步驟結果每次呼叫都重送 | 目前平均每題約 6,000，上限 16,000，3 步流程可能接近上限，超過時整題失敗 | 實測後再決定是否調整步驟上限或預算；目前只有一個種子流程使用 |
| 路由選錯分類 | 流程不會執行，分類相關的保證失效；註記規則不受影響 | 評測的分類正確率與 `toolOrder` 持續檢查 |
| 同一題問多檔股票 | 參數步驟只查一檔，另一檔由 GPT 自行調用 | 已知限制 |
| 公司名稱轉代號 | 靠 GPT 的知識，可能對應錯；回傳資料帶公司名稱，看得出來但程式不會擋 | 現有風險，這次不處理 |
| 每題都查完所有步驟 | 問題很窄時也會查，浪費 token | 撰寫建議提醒步驟要精簡 |
| 介面不提示注意事項 | 管理者可能把參數步驟放進範圍寬的分類，例如問大盤也被要求提供代號；放錯位置要到發布前檢查才發現 | 撰寫建議說明；發布前檢查與評測把關 |
| 註記讀不到 | 平台 API 故障期間，商品類問題答不完整 | 刻意的取捨，優先合規 |
| 條件句裡的標記 | 「如果…再查 `{{tool}}`」每次都會查，和文字的意思不同；發布前檢查分辨不出 | 撰寫建議說明；查詢唯讀，影響是多花 token，GPT 仍可依文字決定回答內容 |
| 標記分散在全文 | 沒有清單彙整，實際會查什麼、順序如何要讀完內容才知道 | 接受；可從評測的 `toolOrder` 與工具調用結果確認 |
| 管理者刪除標記 | 分類流程失效 | 權限與版本紀錄管理；不可繞過的規則只放在程式（第 2 節） |

## 8. 整併與上線

- 種子只在設定集合為空時載入（`SeedLoader`），已發布的 `system.main` 與 instruction 不會隨種子檔更新。有資料的環境要透過管理頁發新版，至少包含 `system.main`（2.4）與「聯繫準備」（第 4 節）。
- 要向主管或平台確認：
  - 註記到期後是否應該失效（2.2）。
  - 活動資料是否能提供推廣型或服務型欄位。有的話可以只扣推廣型，不必整區扣住。
- 文件同步：`docs/spec/01`、`02`、`03`、`05`、`07`、`06`（情境）、`demo-checklist`、`ideas-backlog`（第 13 項標為完成）。`docs/spec/` 目前有未 commit 的修改，同步時接在這些修改上，不可覆蓋。

## 9. 實作順序與落點

每階段結束由使用者 commit，確認後才進下一階段。時程不足時優先砍第 4 階段的選擇器（手打仍可用）；第 1 階段不可砍。

| 階段 | 內容 | 主要落點 |
| --- | --- | --- |
| 1. 註記強制 | 第 2 節 | `domain/customer/CustomerRestrictions`、`domain/tool/customer/*`、`domain/prompt/CustomerNotes`、`domain/answer/AnswerAssembler`、`application/chat/ChatOrchestrator`、種子 |
| 2. 工作流引擎 | 第 3 節 | `domain/prompt/WorkflowParser`、`WorkflowStep`、`StepArgumentPrompt`、`PromptAssembler`、`domain/config/ConfigValidator`、`domain/tool/AssistantTool`、`application/chat/WorkflowRunner`、`StepArgumentExtractor`、`ChatOrchestrator`、`infrastructure/config/DomainBeanConfiguration` |
| 3. 評測 | 第 4 節 | `domain/eval/EvalCase`、`EvalDatasetLoader`、`CaseChecker`、`eval-cases.yml`、前端評測元件、種子「聯繫準備」 |
| 4. 選擇器與文件 | 第 5、8 節 | `interfaces/rest/config/*`、`admin/ToolPicker.vue`、`admin/MarkdownEditor.vue`、`api/aiAssistantApi.ts`、`types.ts`、`docs/` |

## 10. 驗收標準

1. F1、F2、F3、R1 通過評測；對有不宜行銷註記的顧客，連續執行多次都不會出現系統的推薦產品與活動。
2. 設定流程的分類，評測的 `toolOrder` 通過；路由選錯分類時檢查失敗。
3. 步驟失敗時回答仍產出，且最前面有未完成標示。
4. 參數步驟：問題含代號時查到資料，不含時回覆請求文字且沒有執行任何步驟；下一句提供代號後能完成流程。
5. 打錯的標記無法發布，並顯示第 3.2 節的訊息。
6. 管理者不必手打標記，用選擇器就能插入通過驗證的流程；重複的標記只調用一次 tool，且不同選項都拿得到。
7. 標記之間的自由文字會交給 GPT，GPT 也能得知資料已由系統查詢。
8. 後端、前端測試與 `ArchitectureRulesTest` 全綠。
