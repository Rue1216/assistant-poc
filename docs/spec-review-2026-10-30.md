# 規格文件對照 requirements.md 的分析

> 2026-10-30 整理，供 11/3（週二）講解規格文件使用。對照對象：`docs/spec/01–07` 與 `requirements.md`；輔以 `questions-for-manager.md`、`platform-setup-requests.md`、`ideas-backlog.md`。
>
> **2026-10-30 已對齊**：第 3、4、5 節列出的文件矛盾與缺口都已修正（修改清單見第 8 節）。與需求字面不同的設計決策集中寫在 [01-architecture.md](spec/01-architecture.md) 第 9 節，講解時以那一節為準。

## 1. 需求章節與規格文件的對照總表

| 需求章節 | 主要對應文件 | 狀態 | 備註 |
| --- | --- | --- | --- |
| 開頭：右下角助理、依權限調用 API、可複製貼上平移 | 01 §2（模組邊界、port）、§8（整合指引）、platform-setup-requests.md | 偏離（已寫明） | 助理只在個人 360 頁面出現，不是「所有已授權頁面」（01 §9.1） |
| 1-3 Prompt 與 Skill 管理 | 02 §3、04 §2、07 | 改名（已寫明） | 「Skill」改稱 System Instruction（問題分類）；「回復」改為「套用歷史版本為草稿再發布」（01 §9.4） |
| 1-4 docs-as-code | 01–07 | 符合 | 需求只要求 4 份，實際有 7 份 |
| 2 前端 stack（vue 3.5／TS strict／pinia／**primevue 4 unstyled**／api-client） | 01 §2.3、§5.2、§7 | 偏離（已寫明） | 平台雖有 PrimeVue 依賴但現有頁面未使用，小幫手一致採 scoped CSS（01 §7） |
| 2 後端 stack（java 25／boot 4.1／virtual threads／同步 REST／**Clean Architecture**／Session RBAC） | 01 §5.1、§6、§7 | 符合（已補） | 01 §5.1 新增套件與 Clean Architecture 各層的對應表；資料夾命名整合時依平台（S2） |
| 2 AI Orchestration（LangChain4j、Java 控制工具、前端不碰 GPT） | 01 §1、§3 | 符合 | |
| 2 資料層（`COLL_ICRM_` 前綴） | 04 §1 | 符合 | index 另用 `UK_ICRM_`、`IX_ICRM_` |
| 2 內部資料來源（Allowlist API、模型不得產生 URL／SQL／Query） | 03 §1.1、§1.4、§2 | 符合 | 工具對應平台兩支 API；Allowlist 本身待 M10 確認 |
| 2 外部資料來源（公開市場 API、附來源與查詢時間） | 03 §3、02 §2.1 `citations` | 符合 | TWSE OpenAPI；新聞 API 未做（M16 列選做） |
| 2-1 GPT API key | 01 §6.1 | 符合 | `.env` 只由後端讀取；正式環境改 secret 機制 |
| 2-2 tool calling 流程步驟 1–5 | 01 §3、§3.1 | 符合 | 多了一次「路由」模型呼叫（01 §9.8） |
| 2-2 步驟 6：對話視窗以可展開區塊顯示工具名稱、用途、狀態、耗時 | 02 §2.1、05 §1、06 W11 | **偏離（已寫明）** | 以引用清單取代，理由在 01 §9.2；API 的 `toolCalls` 仍完整回傳，需要時前端可加回 |
| 2-2 注意事項（摘要不含參數、個資、URL） | 02 §2.1、04 §3.1 | 符合 | |
| 3-1 對話視窗：送出／多輪／Loading／取消／錯誤／清除 | 06 §7 W1–W13、05 §4 | 符合 | 「清除本次對話」實作為「開新對話」（01 §9.9） |
| 3-1 Enter 送出、Shift+Enter 換行、防重複提交 | 06 W12 | 符合（已補） | 程式：`ChatComposer.onKeydown` 處理 Enter 與輸入法；`canSend` 在等待期間為 false |
| 3-1 Markdown 安全渲染 | 06 X6、01 §5.2 `SafeMarkdown` | 符合 | 自行實作，不引入套件 |
| 3-1 對話不保存 | 02 §2.1、04 §1 | 符合 | 只有稽核 collection |
| 3-2 顧客概況 | 07 §3.1、06 Q1 | 符合 | |
| 3-2 經營機會 | 07 §3.4、06 Q4–Q5 | 符合 | |
| 3-2 互動摘要（近期互動紀錄或活動結果） | 06 §1 對應表、01 §9.3 | **偏離（已寫明）** | 以「聯繫準備」對應；互動紀錄在 360 的下一層頁面、需通過該頁權限審核，小幫手不繞過，列下一階段 |
| 3-2 其他 | 06 Q9（電話開場白）、資產負債、會員等級、往來與通路、市場快訊 | 符合 | |
| 3-2 僅能使用 SIT 測資 | 03 §5 | POC 替代 | POC 用 mock 假顧客；SIT 帳號與測資待 M13 |
| 3-3 市場資訊（來源、timeout、大小上限、allowlist host、警語） | 03 §3.1、02 §2.1 `disclaimer` | 符合 | |
| 3-4 Tool Registry 宣告、動態提供 | 03 §1.3、§1.4、§1.7 | 部分（已寫明） | 依存取等級而非角色篩選；9 個 mock 帳號全是 FULL（01 §9.5） |
| 3-4 執行前再驗證、次數／深度／timeout／token 上限 | 03 §1.4、01 §3.1、§6 | 符合 | 5 次 tool call、3 輪、60 秒、16,000 token |
| 3-4 工具錯誤轉為可控 Tool Result | 03 §4、05 §3 | 符合 | |
| 3-4 稽核（使用者、工具、問題、回答、執行結果、耗時、時間） | 04 §3 | 部分（已寫明） | 執行結果預設只存 SHA-256 與筆數（01 §9.6） |
| 3-5 編輯、預覽、草稿、發布、版本、回復 | 02 §3.0–3.10、04 §2.4 | 符合 | 另加預約發布、兩版比較、已捨棄版本 |
| 3-5 欄位、collection、每版一份 document、`UK_ICRM_AI_CFG_1`、JSON Schema、`@Version` | 04 §2.1–2.3 | 符合 | |
| 3-5 sharding 設計須在開發前審查 | 04 §5 | 待確認 | 稽核 collection 的 shard 建議已寫，待 S18、M36 |
| 3-5 一般使用者只用已發布版本；管理權限才可編輯 | 02 §1.2、§3 | 符合 | |
| 3-5 發布前驗證、預覽 Sanitization | 04 §2.5 | 部分（已寫明） | System Prompt 無必要章節；「可繞過權限的指令」列為待辦（01 §9.7、04 §2.5） |
| 3-6 System Prompt 拒絕洩漏、不可信資料、輸入長度、錯誤碼、Toast、GPT 掛掉不影響平台 | 01 §4、05、06 X1–X6 | 符合 | 安全規則寫死在程式碼並放在 prompt 最後 |
| 3-7 四份文件 | 02、03、04、06 | 符合 | |
| 4 加分：回答引用、Prompt 評測集 | 02 §2.1 `citations`、§3.12–3.16、04 §6 | 符合 | 兩項都做了，另有 👍／👎 回饋與版本統計 |
| 5 評分項 1「響應式操作」 | 06 W13 | 符合（已補） | 600px 以下全螢幕 |
| 5 評分項 10、11 | questions M34 | 待確認 | SP 計畫原文缺漏 |

## 2. 講解時必須主動說明的設計決策

全部已寫進 [01-architecture.md](spec/01-architecture.md) 第 9 節，講解時直接用那一節。依被問到的可能性排序：

1. **9.2 對話視窗不顯示工具呼叫摘要**（需求 2-2 步驟 6 明文要求，評分項 4）。講法：使用者在意「這段話根據什麼」，引用直接對到區塊；工具名稱與耗時對行銷人員沒意義，也不暴露內部工具名稱；工具失敗由回答說明；API 與稽核仍有完整資料，要加回不需改 API。要有心理準備對方堅持需求字面，屆時加回前端只是一個元件。
2. **9.3 互動摘要以聯繫準備對應**。講法：互動紀錄在 360 下一層頁面、需通過該頁權限審核，小幫手綁 360 頁的權限不能繞過；等平台提供該頁 API 與權限規則再加工具。
3. **9.1 助理只在個人 360 頁面**。講法：對話綁定顧客、顧客編號不經 LLM，是去識別化與「工具沒有顧客參數」的前提。
4. **9.4 Skill 改稱 System Instruction、回復改為套用為草稿**：先講對應，再講多做的（預約發布、比較、已捨棄可找回、路由）。
5. **9.5 Tool Registry 依存取等級**、**9.6 稽核結果只存雜湊**、**9.7 驗證範圍**、**9.8 路由呼叫**、**9.9 其他（開新對話、PrimeVue、無新聞 API）**。

## 3. 文件內部不一致（已修正）

| # | 位置 | 問題 | 處理 |
| --- | --- | --- | --- |
| 3.1 | 01 §3 vs 02 §2.1、05 §1、06 W11 | 工具呼叫摘要顯示與否 | 01 §3 註解改為不顯示並連到 9.2；02、05、06 補理由連結 |
| 3.2 | 04 §2.5 顧客編號 `\bC\d{3}\b` | 程式 `ConfigValidator` 實際用 `PiiRedactor.CERT_NO_REGEX` | 04 §2.5 改為 certNo 三種格式 |
| 3.3 | 評測題數（30／34／17；上限 30／40） | 程式：`eval-cases.yml` 34 題、`STORED_CASES_MAX` 40 | 02 標題與 3.12／3.13／3.14 範例改 34；platform-setup-requests §8.2、questions M38 改 34 題、上限 40 |
| 3.4 | 03 §1.4 vs §2.2 `sections` 檢查分工 | 程式：ToolGuard 以 Bean Validation 檢查 1–6 個；enum 由 `GetCustomerTool` 檢查 | 03 §1.4 改寫 |
| 3.5 | platform-setup-requests §8.1 | `customerId`、C001／C005、持股、互動紀錄 | 改為 `certNo`、6 位假顧客、360 區塊與即時註記 |
| 3.6 | 05 §1 | 同 3.1 | 同 3.1 |

## 4. 附屬文件過期的地方（已修正）

questions-for-manager.md 的 M7（token 上限）、M14（其他類方向）、M19（路由）、M21（五個狀態）、M24（驗證規則現況）、M38（題數與 token）已改為現況。`demo-checklist.md` 與 `ideas-backlog.md` 提到的 30 題、17 題是歷史脈絡，保留。

## 5. 需求有、規格沒寫到的小缺口（已補）

1. 3-1 Enter／Shift+Enter／輸入法／防重複提交 → 06 W12。
2. 評分項 1 響應式 → 06 W13。
3. 3-5「可繞過權限的指令」→ 04 §2.5 註明未納入與理由，列待辦。
4. 3-2 三類問答的對應 → 06 §1 對應表。
5. Clean Architecture → 01 §5.1 對應表。
6. PrimeVue → 01 §7。

仍未做、只能口頭說明：市場新聞來源（M16 選做）；評分項 10、11 原文缺漏（M34）。

## 6. 講解時可順勢提問的待確認事項

- M1、M2：顧客資料去識別化後能否送外部 GPT；欄位分級表。
- M5、M6：OpenAI 直連或 Azure OpenAI；key、預算、rate limit。
- M10：Allowlist 的平台 API 清單；目前對應 `getCustomer`、`getRealTimeCustomer` 兩支。
- M15、M18：TWSE 是否核准；對外連線開通。
- M4、M36、S18：稽核保存期限、是否存工具結果、是否 shard。
- M43、M48、M49：LIMITED 角色是否存在；`certNo` 完整格式；路徑含 `certNo` 的 access log。
- S13：公司 Maven mirror 設定；`lib-repo` 做法是否可接受。
- M34：評分項 10、11 的完整內容；加分項是否必做。
- M24：是否要求加入「越權指令」的禁止樣式。

## 7. 建議的講解順序

1. requirements.md 的目標與限制（2 分鐘）。
2. 01 §1 系統元件圖、§2 模組邊界與三個 port、§5.1 分層對應：強調「平移時刪 mockplatform、平台實作 port」。
3. 01 §3 一次提問的時序：路由 → tool calling 迴圈 → AnswerAssembler；§3.1 的上限與 §4 的 prompt 組裝（安全規則在最後）。
4. 03 Tool Catalog：工具沒有顧客參數、去識別化兩層（§1.6）、TWSE client 規則（§3.1）。
5. 02 REST API：對話 API 的 request／response（`citations`、`disclaimer`）、Prompt 管理的狀態與操作（§3.0）。
6. 04 MongoDB：三個 collection、版本策略狀態圖（§2.4）、partial unique index、sharding 估算（§5）。
7. 05 錯誤碼：兩層錯誤、前端處理表。
8. 06 情境（§1 需求對應表、demo 腳本 §9）、07 範例 prompt。
9. 最後以 01 §9 統一講設計決策，再帶第 6 節的待確認事項。

可以強調的亮點：去識別化由程式保證而非 prompt；安全規則與回答格式寫死在 Java 並放在可編輯內容之後；回答區塊帶引用與數字核對（`unverifiedNumbers`）；評測集可量化比較發布前後；樂觀鎖、partial unique index 與 transaction 保證多人編輯與多 pod 下的一致性；`lib-repo` 解決離線建置。

## 8. 2026-10-30 修改清單

| 檔案 | 修改 |
| --- | --- |
| spec/01-architecture.md | v1.2：§3 時序圖註解；§5.1 Clean Architecture 對應表；§7 UI 框架說明；新增 §9 與 requirements.md 的差異（9.1–9.9） |
| spec/02-rest-api.md | v1.2：標題 34 題；§2.1 `toolCalls` 理由連結；§3.12、3.13、3.14 範例 34 題 |
| spec/03-tool-catalog.md | v0.11：§1.4 `sections` 檢查分工 |
| spec/04-mongodb-schema.md | v1.2：§2.5 顧客編號規則改 certNo 格式、名稱也檢查、越權指令待辦 |
| spec/05-error-codes.md | v1.0.1：§1 連結 |
| spec/06-conversation-scenarios.md | v1.2：§1 需求 3-2 對應表；§1.2 總覽 W1–W13；§7 W11 連結、新增 W12、W13 |
| platform-setup-requests.md | §8.1 假顧客需求改 certNo 與 360 資料；§8.2 34 題、token 推估、上限 40 |
| questions-for-manager.md | M7、M14、M19、M21、M24、M38 預設假設改為現況 |

程式碼沒有修改。
