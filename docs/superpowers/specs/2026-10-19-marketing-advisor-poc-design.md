# 行銷人員顧問小幫手 POC 設計

- 日期：2026-10-19
- 版本：v0.11（2026-10-29 版本註記，原文不改：個人 360 內容重寫後，後台、IT 管理人員的存取等級由 LIMITED 改為 FULL（可看全部顧客）；mock 帳號改為員工帳號（`ICRM00047`…、`EEE11111`、`EEE22222`），`roleKey` 改為業務角色碼（ANALYST、MANAGER、RM、AO、ADMIN），顧客範圍由 `scope` 決定；顧客編號改為身分證字號 `certNo`，取代 `customerId`；顧客工具整併為 `getCustomer(sections)` 與 `getRealTimeCustomer`（v0.2）。詳見 [個人 360 內容重寫設計](2026-10-28-customer360-content-rewrite-design.md)，本文件其餘段落維持 v0.10 的原貌）
- 前一版：v0.10（2026-10-27：平台替身的外框與個人 360 頁面照 prototype，名片以外為示意資料；支援深色模式）
- 更早：v0.9（2026-10-21：新增「已捨棄」狀態；套用一律新增草稿，原草稿或排程改為已捨棄；捨棄草稿不再刪除）
- 更早：v0.8（2026-10-20：Prompt 管理改為「任選兩版比較、套用為草稿」取代回復上一版；畫面以時間辨識版本、不顯示版本號；預覽改為分頁；平台安全規則不在管理頁顯示；前端以 `prototype/` 為視覺依據，個人 360 頁面簡易版）
- 更早：v0.7（移除 System Instruction 的 `relatedTools`；統一權限不足時的回答用語）
- 狀態：待審查
- 對應需求：[requirements.md](../../../requirements.md)
- 詳細規格：[docs/spec/](../../spec/)

## 1. 目標與範圍

### 1.1 目標

在 10/19–10/23 完成一個可以展示給主管的 POC，從頭到尾打通以下流程：

- 個人 360 頁面右下角的對話視窗
- LangChain4j tool calling 迴圈（Java 控制工具執行與權限）
- 以「當前顧客」為對象的顧客工具（假資料）與 TWSE 市場工具
- 全面去識別化：顧客編號、姓名與任何識別資料都不進 LLM
- Prompt 管理：System Prompt 與 System Instructions（問題分類與回答套版）的草稿、立即 / 預約發布、版本比較與套用

POC 程式碼之後要能整合進既有平台，所以小幫手模組和「平台替身」必須明確分開。

### 1.2 本週範圍

- 9 個角色與顧客範圍規則（見 [02-rest-api.md](../../spec/02-rest-api.md) 1.2–1.4）
- 7 個工具（見 [03-tool-catalog.md](../../spec/03-tool-catalog.md)）
- 去識別化元件 `CustomerDeidentifier`、`PiiRedactor`
- Prompt 管理後端與管理頁（含快捷問題）
- 範例 prompt 與初始資料（見 [07-prompt-examples.md](../../spec/07-prompt-examples.md)）
- 對答情境：K4、K5 以外的全部情境（見 [06-conversation-scenarios.md](../../spec/06-conversation-scenarios.md)）
- 需求 3-7 規定的開發前文件

### 1.3 不在本週範圍

- 情境 K4、K5（需新增月營收、除權息工具，10/26 之後）
- Prompt 評測集（加分項，10/26 之後）
- 複雜的前端文字編輯器（以 textarea 加「編輯 / 預覽」分頁取代）
- 個人 360 頁面的真實資料（平台替身：版面照 prototype，名片以外的區塊是寫死的示意資料，不接後端）
- 平台命名規範、分層慣例、既有 `api-client` 對齊（等主管回覆 [questions-for-manager.md](../../questions-for-manager.md) 之後再做）
- 串流回應、多語系

### 1.4 與需求文件的差異

| 需求 | 原文 | 本次調整 | 依據 |
|---|---|---|---|
| 3-1 | 所有已授權頁面顯示助理按鈕 | 只在個人 360 頁面顯示 | 使用者縮限範圍（2026-10-19） |
| 3-5 | 管理 System Prompt 與 Skill Markdown | 管理 System Prompt 與 System Instructions；Instruction 即「問題分類 + 回答套版」，對應原本的 Skill | 同上 |
| 3-5 | 回復前一版本 | 任選兩版比較，並可把任一已發布 / 已封存版本套用為草稿再發布（涵蓋回復前一版） | 使用者決定（2026-10-20） |
| 3-5 | 資料包含 `version`，以 `configKey + version` 建唯一索引 | 保留 `version` 作為內部流水號與唯一索引；畫面不顯示版本號，以時間辨識版本 | 同上 |

## 2. 已確定的決策

| 決策 | 內容 | 理由 |
|---|---|---|
| LangChain4j 使用方式 | 低階 `ChatModel`，tool calling 迴圈自己寫 | 驗證、限額、稽核、去識別化都在 Java 控制之下，程式碼和需求 2-2 的步驟一一對應 |
| LangChain4j 套件 | 只用核心套件 `langchain4j` 和 `langchain4j-open-ai`（1.22.0），不用 Spring Boot starter | starter 仍是 beta，不保證支援 Spring Boot 4.1 |
| 依賴取得方式 | 公司環境連不到 Maven repo：LangChain4j 和平台沒有的間接依賴，照 Maven 目錄結構放在 `backend/lib-repo/` 並 commit 進 repo；pom.xml 把它宣告成專案內的 file repository（見 [01-architecture.md](../../spec/01-architecture.md) 7.1） | 不連網也能建置；平移時整個資料夾一起帶走；保留 pom，Maven 自己處理間接依賴，不用 `system` scope |
| 換模型的彈性 | 程式碼只依賴 `ChatModel` 介面，供應商和模型由 yaml 決定 | 換供應商只需換依賴和一個 `@Configuration` |
| GPT key | 開發者為本專案另建的專用 key（限制權限、設花費上限、POC 結束後撤銷），存放在 `backend/.env`（已列入 `.gitignore`），只由後端讀取 | 不放全域環境變數；限縮 key 權限讓外洩時損失有上限 |
| 小幫手出現位置 | 只在個人 360 頁面；換顧客就清空對話 | 使用者縮限範圍；避免不同顧客的資訊混在同一段對話 |
| 對話綁定顧客 | 顧客編號放在 API 路徑，由後端判斷權限並綁定；顧客工具**沒有顧客參數** | 模型無法指定或切換顧客，跨顧客越權從工具層就不存在 |
| 去識別化 | 結構化欄位由 `CustomerDeidentifier` 刪除或泛化；自由文字（提問、歷史、工具文字、回答）由 `PiiRedactor` 精確比對加 regex 遮蔽 | 所有含顧客實際身分的資料都由程式轉換後才進 LLM |
| 稱呼顧客 | 回答一律稱「本顧客」，不還原姓名 | 使用者選擇；最單純，使用者本來就在該顧客頁面上 |
| 角色 | 9 個實際角色：總行查詢、區域查詢、分行主管、理專主管、理專經辦、AO 主管、AO 經辦、後台管理、IT 管理 | 對應平台實際組織 |
| 顧客範圍 | 依主經營行、利潤分行、理財分行、生效名單分派人員、行銷單位判斷，由平台端 `CustomerAccessPort` 實作 | 範圍規則本來就是平台邏輯；整合時直接沿用 |
| 角色看到的資料 | 除後台與 IT 外，所有角色可用的工具相同，差異在顧客範圍；後台與 IT 為 `LIMITED`，只看基本輪廓與市場資訊 | 使用者未要求理專與 AO 區分資料種類，先採最簡單的方案；之後可加 AO 專屬工具 |
| 小幫手的定位 | 唯讀：只查詢、整理資訊，不修改任何資料 | 降低風險 |
| Prompt 管理 | System Prompt 一份；System Instructions 多份，每份一個問題分類，含適用問題、回答套版、範例問題；範例問題顯示為快捷按鈕 | 讓 IT 人員預先設定問題分類與套版 |
| Instruction 載入 | 所有已發布的 Instruction 都載入，不依存取等級篩選；不設 `relatedTools`。由模型在同一個 tool calling 迴圈內判斷分類、從可用工具中挑選呼叫 | 管理者只需撰寫內文，不必理解工具名稱；工具增減或改名不會讓分類默默消失。代價是後台與 IT 會看到「聯繫準備」等用不了的快捷按鈕，由 `system.main` 規則回答「依您的權限無法查看這類資料」 |
| Prompt 管理權限 | 後台管理人員和 IT 管理人員都可以編輯、發布與預約發布，不另設審核關卡 | 以 `createdBy`、`updatedBy` 與對應時間追溯；不另設 `publishedBy`、`publishedAt` |
| 預約發布 | 發布可選「立即發布」或「預約發布」；新增 `SCHEDULED` 狀態與 `scheduledAt` 欄位；排程器每 30 秒發布到期版本，`updatedBy` 記預約者（排程中有人「儲存」則改記此人） | 讓 IT 人員能安排 prompt 在指定時間上線 |
| 編輯頁按鈕 | 「儲存」維持目前狀態（已排程時保留排程）；「存成草稿」一律退回草稿（已排程時即取消排程）；「發布」依發布方式立即或預約 | 使用者確認 |
| 版本比較與套用 | 任選兩版逐行比較（前端計算差異）；對任一已發布 / 已封存 / 已捨棄版本「套用」，**一律新增一份草稿**，再照一般流程立即或預約發布。已有草稿或排程時先確認，確認後原版本改為「已捨棄」。取代原本的「回復上一版」 | 套用也經過發布前驗證、也能預約，流程只有一套 |
| 已捨棄狀態 | 新增 `DISCARDED`：未上線就被放棄的版本（捨棄草稿、被套用取代的草稿或排程）。不刪除任何 document；已捨棄版本預設收合，可比較、可再套用 | 使用者決定（2026-10-21）：避免未發布內容永久遺失、避免「建立者」與內容對不上；與「已封存（曾上線）」分開，狀態即意義 |
| 版本辨識 | `version` 只作內部流水號；畫面以 `updatedAt`（已發布 / 已封存版本即生效時間）辨識版本；封存時不改動舊版的 `updatedBy`、`updatedAt` | 使用者不要版本號；封存時間可由下一版的生效時間推得，不需另存 |
| 管理頁編輯器 | textarea 與 `SafeMarkdown` 預覽以「編輯 / 預覽」分頁切換，一次只顯示一個；不提供工具勾選 | 使用者確認 |
| 平台安全規則 | 只存在後端程式，管理頁不顯示、不可調整 | 使用者確認：安全規則是 chatbot 一定遵循的規則，不需揭露 |
| 前端視覺 | 小幫手與 Prompt 管理頁照 `prototype/`（元件拆分與樣式），樣式複製進 `frontend/`；後台與 IT 的快捷按鈕照 spec 顯示全部分類 | 使用者確認 |
| 平台替身畫面 | 外框（頂部列、左側選單）與個人 360 的 A–M 版面照 prototype；名片接 mock API，其他區塊為寫死的示意資料並標示；支援深色模式（`html.dark`，見 [01-architecture.md](../../spec/01-architecture.md) 5.2） | 使用者決定（2026-10-27）：demo 畫面接近實際平台；360 是整合時刪除的替身，不投入後端資料 |
| Markdown 渲染 | 自己寫解析器，以 Vue VNode 渲染，不用 `v-html`，不引入套件 | 公司可能不支援外部套件；從結構上保證安全 |
| UI 框架 | 不使用 PrimeVue 或其他 UI 框架，用 scoped CSS | 使用者確認目前專案沒有實際使用 |
| 平台慣例 | 只採用 SP 計畫已指定的 MongoDB 前綴 `COLL_ICRM_`，其他命名規範暫不處理 | 以技術驗證優先 |
| 稽核內容 | 依 SP 計畫記錄使用者、顧客編號、工具、提問、LLM 回答、執行結果、耗時與時間；提問存去識別化後的版本 | SP 計畫的稽核要求；稽核在內部 DB，可追溯顧客，但不進 LLM |
| 市場資料來源 | TWSE OpenAPI | 免費、公開 |

## 3. 架構摘要

詳見 [01-architecture.md](../../spec/01-architecture.md)。

- **後端**：Spring Boot 4.1、Java 25、virtual threads、Maven Wrapper
  - `aiassistant` 套件：小幫手模組，之後要搬走
  - `mockplatform` 套件：假登入、角色、顧客範圍規則與假資料，整合時刪除
  - 兩者透過 `CurrentUserPort`、`CustomerAccessPort`、`CustomerDataPort` 介面溝通
- **前端**：Vue 3.5、TypeScript strict、Pinia、Vite
  - `features/ai-assistant/`：小幫手模組（對話視窗、Prompt 管理頁）
  - `platform-mock/`：宿主外殼（登入、顧客經營、個人 360、後台管理），整合時刪除
- **資料**：MongoDB（docker compose，單節點 replica set，以支援交易）

## 4. 主要元件

### 4.1 `ChatOrchestrator`

依序執行：判斷顧客存取等級 → 檢查輸入 → 去識別化提問與歷史 → 組裝 prompt 和可用工具 → tool calling 迴圈 → 去識別化檢查回答 → 組裝回應 → 寫入稽核。

迴圈中每個 tool call 都會經過 `ToolGuard` 驗證，再執行、去識別化結果，最後以 `role=tool` 加上對應的 tool call id 回送給模型。

### 4.2 工具與權限

- 每個工具實作 `AssistantTool`，宣告需要的存取等級（`LIMITED` 或 `FULL`）。
- `ToolRegistry` 依本次對話的存取等級篩選工具。
- `ToolGuard` 在執行前再次驗證工具名稱、參數格式與長度，並拒絕 schema 以外的欄位。
- `ToolContext`（使用者 + 綁定的顧客）以參數明確傳給工具，不依賴 ThreadLocal。

### 4.3 去識別化

- `CustomerContext` 只存在 Java 端，保存顧客編號與已知識別資料，供 port 查詢與 `PiiRedactor` 比對。
- 送進 `ChatModel` 的每一則訊息都必須先經過去識別化，由 `ChatOrchestrator` 統一把關，並以單元測試驗證。
- 規則見 [03-tool-catalog.md](../../spec/03-tool-catalog.md) 1.6。

### 4.4 Prompt 組裝

- 組合方式：已發布的 `system.main` + 全部已發布的 System Instructions + 寫死在 Java 的安全規則。
- 提供給模型的工具只依存取等級決定（`ToolRegistry.toolsFor`），和載入哪些分類無關。
- 快捷問題 API（`/suggestions`）回傳全部已發布分類的範例問題，不依存取等級篩選。
- `LIMITED` 使用者問到需要 `FULL` 工具的分類時，由 `system.main` 規則回答「依您的權限無法查看這類資料」，不套用回答套版；以 [06-conversation-scenarios.md](../../spec/06-conversation-scenarios.md) P5 驗證不會編造內容。

### 4.5 Prompt 管理

- 每個版本是一份獨立的 document，詳見 [04-mongodb-schema.md](../../spec/04-mongodb-schema.md)。
- 流程：草稿 →（預約）→ 發布 → 封存。任一歷史版本可「套用」為草稿，再照一般流程發布。
- 每個 key 同時只有一份編輯中版本（`DRAFT` 或 `SCHEDULED`）。
- `ScheduledPublishJob` 每 30 秒發布到期的預約版本，以條件更新確保多台機器只發布一次。
- 以 `docVersion`（`@Version`）做樂觀鎖。發布在交易中完成，並由 partial unique index 保證同一個 key 只有一個已發布版本。

## 5. 安全控制與需求對照

| 需求 | 控制方式 |
|---|---|
| 2-1 API key | 只在後端：`backend/.env` 經 `spring.config.import` 載入，yaml 以 `${OPENAI_API_KEY}` 引用；不寫入 log、錯誤訊息，不開放 Actuator `/env`；前端從不接觸 |
| 2-2 tool call 摘要不含敏感資訊 | 摘要只有工具名稱、用途、狀態、耗時 |
| 3-1 安全渲染 | `SafeMarkdown`：白名單語法、VNode 渲染、連結只允許 http/https |
| 3-2 依權限存取 | 對話 API 先以 `CustomerAccessPort` 判斷，`NONE` 回 404；顧客工具沒有顧客參數 |
| 3-3 外部 API | Host 白名單、連線與讀取 timeout、回應大小上限、錯誤轉換、快取 |
| 3-3 警語 | 用到市場工具時由 Java 附加，不靠模型 |
| 3-4 動態工具 | `ToolRegistry` 依存取等級篩選 |
| 3-4 執行前再驗證 | `ToolGuard`：名稱、參數格式與長度、拒絕多餘欄位 |
| 3-4 限額 | tool call 5 次、模型往返 3 輪、整體 60 秒、token 預算 16k |
| 3-4 錯誤不外洩 | 工具錯誤轉為固定錯誤碼的工具結果 |
| 3-4 稽核 | 每次提問寫入一筆：使用者、顧客編號、存取等級、去識別化後的提問、回答、各工具的狀態與耗時、時間；不記工具參數與結果；提問與回答只進稽核 collection，不進應用程式 log；保留 180 天 |
| 3-5 內容驗證 | 不可為空、64KB 上限、必要章節、範例問題格式、禁止內容 regex（含身分證號、手機、顧客編號） |
| 3-6 prompt injection | Java 寫死的安全規則；工具結果和外部內容標示為不可信資料；假資料中藏有注入字串做測試 |
| 3-6 越權指令 | 所有工具唯讀；權限無法由對話變更；模型無法指定顧客 |
| 3-6 輸入長度 | 前後端都限制單則 1000 字；後端限制歷史 10 輪、8000 字 |
| 3-6 韌性 | GPT 或 TWSE 失敗只影響 chat API；TWSE 失敗時仍會回答並告知 |
| 個資 | 識別資料不進 LLM：結構化欄位刪除或泛化、自由文字遮蔽、回答再檢查；後台與 IT 只看基本輪廓 |
| 資料存在性 | 顧客「不存在」和「無權限」回傳相同的 404 |

## 6. 限額與設定預設值

| 項目 | 預設值 |
|---|---|
| 單則訊息 | 1000 字 |
| 對話歷史 | 10 輪、8000 字 |
| tool call 次數 | 每次提問 5 次 |
| 模型往返 | 每次提問 3 輪 |
| 單次 GPT 呼叫 timeout | 30 秒 |
| 整體 timeout | 60 秒 |
| token 預算 | 每次提問 16,000 |
| 單一工具 timeout | 5 秒 |
| 工具結果大小 | 4KB |
| 限流 | 每位使用者每分鐘 10 次 |
| TWSE 連線 / 讀取 timeout | 3 秒 / 5 秒 |
| TWSE 回應大小上限 | 2MB |
| TWSE 快取 | 10 分鐘 |

所有數值都放在 yaml，可以調整。

## 7. 錯誤處理

詳見 [05-error-codes.md](../../spec/05-error-codes.md)。

- API 錯誤格式：`{ code, message, requestId, details? }`，`message` 一律是友善訊息。
- 工具層錯誤不會變成 HTTP 錯誤，而是寫進工具結果交給模型，並顯示在摘要的狀態欄。

## 8. 測試策略

- **後端**：JUnit 5、Mockito、Testcontainers MongoDB
  - `ChatOrchestrator`：用依劇本回應的假 `ChatModel`，涵蓋不呼叫工具、單一與多個工具、次數觸頂、token 超出預算、模型編造工具名稱、參數含多餘欄位、工具逾時
  - **去識別化**：檢查送給 `ChatModel` 的每一則訊息都不含本顧客的姓名、顧客編號、身分證號、電話、Email、帳號（D1–D5）
  - `MockCustomerAccessAdapter`：依 [02-rest-api.md](../../spec/02-rest-api.md) 1.4 的存取矩陣，每格一個測試
  - `ToolRegistry`：`LIMITED` 與 `FULL` 的工具清單
  - `ToolGuard`、TWSE client（`MockRestServiceServer`）
  - `ConfigService`：草稿、儲存與存成草稿、立即與預約發布、套用歷史版本（含覆蓋確認）、封存不改動舊版時間、衝突、驗證規則（含範例問題、預約時間範圍）
  - `ScheduledPublishJob`：到期發布、`updatedBy` 記預約者、兩個執行個體同時執行只發布一次（以可注入的 `Clock` 控制時間）
  - Controller 權限：對話 API 404、Prompt 管理 403
- **前端**：Vitest
  - `SafeMarkdown` 的 XSS 測試組
  - 輸入行為（Enter、Shift+Enter、輸入法選字）
  - store 的取消、清除，以及換顧客時清空
- **情境驗收**：依 [06-conversation-scenarios.md](../../spec/06-conversation-scenarios.md) 用真實 GPT 跑 demo 腳本

## 9. 交付與前置條件

- **前置條件**：開發者已依 [setup.md](../../setup.md) 安裝 JDK 25 和 Docker，並在 `backend/.env` 設定 `OPENAI_API_KEY` 和 `OPENAI_MODEL`。
- **本週交付**：`docs/spec/` 七份文件、可執行的 POC、demo 腳本。

## 10. 假設與待確認

以下是 POC 採用的假設，正式開發前需依 [questions-for-manager.md](../../questions-for-manager.md) 的回覆調整：

- 平台是單體，小幫手直接呼叫既有 service（S1）
- 顧客範圍規則中，理專經辦與 AO 經辦的「主經營行」條件依提供的規則照實作（見 [02-rest-api.md](../../spec/02-rest-api.md) 1.3 註記）
- 理專與 AO 看到的資料種類相同；若 AO 需要授信資料，再新增 `getCreditOverview`
- 錯誤回應格式、權限碼命名（S5、S7）
- 「回復前一版本」以「任選版本套用為草稿」實現（M22）
- 平台可以接受用專案內的 repository 引入 jar，而且公司 Maven 的 mirror 設定不會攔截它（S13）
