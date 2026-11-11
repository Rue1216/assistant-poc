# 前端目錄結構對齊設計

> 版本 v0.1・2026-11-11。依平台 `frontend-instructions.md`（不進版控）§2、§3 把 `frontend/` 從「功能一包」改為平台的分層目錄與 kebab-case 命名，讓小幫手的檔案可以照目錄複製進 iCRM 前端。回答 [questions-for-manager.md](../../questions-for-manager.md) M35 的前端部分。

## 1. 目的與成功條件

**目的**：小幫手每個檔案在本專案的路徑，等於它在 iCRM 前端的路徑。交付時照目錄複製，不必改 import。

**成功條件**

1. `frontend/src/` 只剩平台規範的分層目錄；`src/features/`、`src/platform-mock/`、`src/styles/`、`src/testing/` 不存在。
2. 所有檔案與目錄為 kebab-case（`App.vue`、`main.ts`、`env.d.ts` 依規範保留原名）。
3. 測試全部在 `tests/unit/`，路徑對應 `src/`。
4. 專案內 import 一律用 `@/`。
5. 測試檔數量與測試案例數量與動手前的基準相同，全數通過；`npm run build`（含 `vue-tsc`）通過。
6. `frontend/PORTING.md` 列出要複製的檔案、不要複製的替身、接入點、行為差異待辦。

## 2. 範圍

**做**：搬移與改名、import 路徑、template 元件標籤改 kebab-case、API 與型別檔各拆成聊天與後台兩支、常數移到 `*-constant.ts`、`@` alias、`vitest.config.ts`、文件同步。

**不做（列入 `PORTING.md` 待辦）**

| 項目 | 現況 | 規範 | 為何不在本次 |
| --- | --- | --- | --- |
| API 錯誤契約 | reject `ApiError`（`status`、`code`、`traceId`、`details`） | `ApiResponse<T>` 包裝、reject `HandledError`、`isHandledError()` | 後端也還沒改用 `ApiResponse<T>`（分層設計列為範圍外），要前後端一起動；且需對照平台真正的 `api-client.ts` |
| 元件直接呼叫 API | `tool-picker.vue` 直接呼叫 `promptManagementApi.tools()` | 元件不直接呼叫 API | 行為重構 |
| Store 外部賦值 | router 內 `useAuthStore().user = null` | state 只能由 action 修改 | 在替身檔案內，整合時不帶走 |
| 日期 | `utils/taipei-time.ts` 自製 | `dayjs` + `formatDate` | 行為重構，需新增套件 |
| 表單驗證 | 手寫 | VeeValidate + zod | 同上 |
| 訊息提示 | 替身 `use-toast` | 平台 `use-message-store` | 需對照平台實作 |
| ESLint | 無 | 零 error | 需平台的 `eslint.config.js` |
| JSDoc | 部分有 | composable、store action、公開 utility 必須有 | 與搬移無關 |

**原則**：檔案內容只允許四種改動：import 路徑、template 元件標籤大小寫、API 物件名稱（`aiAssistantApi` 拆出 `promptManagementApi`）、因拆檔而搬動的宣告。函式本體、樣式、路由路徑、測試斷言都不改。

## 3. 已確認的決定

| 項目 | 決定 |
| --- | --- |
| 平台替身 | 搬到規範路徑（`utils/api-client.ts` 等），使小幫手的 import 在兩邊相同。繼續進版控（本專案 demo 需要），由 `PORTING.md` 標示不可複製。 |
| 聊天元件 | `components/ai-assistant/`，不加 `icrm-` 前綴（該前綴屬於 `components/shared/` 通用元件）。 |
| Prompt 管理頁 | `views/maintenance/prompt-management/`，頁面專用子元件放其下 `components/`。 |
| API | 拆成聊天與後台兩支。 |
| 聊天 API 位置 | `components/ai-assistant/ai-assistant-api.ts`。規範要 API 放 `views/{模組}/`，但聊天小工具沒有自己的 view；放顧客模組會把它綁死在個人 360。**這是對規範的一處偏離**，記入 `PORTING.md`。 |
| 型別 | `types.ts` 依檔內既有分界（`// ---- Prompt management` 註解）拆成兩支 bean。 |
| API 匯出形式 | 維持物件：`aiAssistantApi`（聊天）、`promptManagementApi`（後台）。測試的 `vi.mock` 只需換路徑與名稱。 |
| 元件標籤 | template 內改 kebab-case（規範 §3，ESLint 強制）。 |
| 轉接元件 | `PromptManagementRoute.vue` 刪除，router 直接載入頁面。 |

## 4. 目標結構

```
frontend/
├─ index.html
├─ PORTING.md
├─ package.json / package-lock.json
├─ vite.config.ts / vitest.config.ts
├─ tsconfig.json / tsconfig.app.json / tsconfig.node.json
├─ src/
│  ├─ main.ts / App.vue / env.d.ts
│  ├─ assets/styles/
│  ├─ beans/
│  ├─ components/
│  │  ├─ ai-assistant/
│  │  ├─ charts/
│  │  └─ layout/
│  ├─ composables/
│  ├─ router/
│  ├─ stores/
│  ├─ utils/
│  │  └─ markdown/
│  └─ views/
│     ├─ home-page.vue
│     ├─ dev/
│     ├─ maintenance/prompt-management/
│     │  └─ components/
│     └─ management/customer/
│        └─ components/
└─ tests/unit/          # 對應 src/；另有 fixtures/
```

## 5. 檔案對應：小幫手（交付）

來源路徑相對於 `src/features/ai-assistant/`，目標相對於 `src/`。

### 5.1 型別與 API

| 來源 | 目標 | 內容 |
| --- | --- | --- |
| `types.ts` 第 1 行至 `// ---- Prompt management` 之前 | `beans/ai-assistant-bean.ts` | `ToolCallStatus` … `ChatMessage` |
| `types.ts` 其餘 | `beans/prompt-management-bean.ts` | `ConfigType` … `ToolCatalogItem` |
| `api/aiAssistantApi.ts`：`chat`、`suggestions`、`putFeedback`、`base()`、`SILENT` | `components/ai-assistant/ai-assistant-api.ts` | 匯出 `aiAssistantApi` |
| `api/aiAssistantApi.ts`：`CHAT_TIMEOUT_MS` | `components/ai-assistant/ai-assistant-constant.ts` | |
| `api/aiAssistantApi.ts`：其餘 17 個函式、`ADMIN`、`EVALUATIONS`、`configPath()`、`STATS_SILENT` | `views/maintenance/prompt-management/prompt-management-api.ts` | 匯出 `promptManagementApi` |
| `api/aiAssistantApi.ts`：`CONFLICT`、`VALIDATION_FAILED`、`OVERWRITE_PENDING`、`INPUT_INVALID`、`EVAL_RUNNING` | `views/maintenance/prompt-management/prompt-management-constant.ts` | |

`STATS_SILENT` 原本展開 `SILENT.silentCodes`（`['AI_CUSTOMER_NOT_FOUND']`）。拆檔後在後台 API 檔內直接寫出完整陣列，值不變；兩支 API 檔互不 import。

若後台 bean 需要聊天 bean 的型別（或反之），以 `import type` 引用，不複製宣告。

### 5.2 聊天小工具 → `components/ai-assistant/`

| 來源（`components/`） | 目標 |
| --- | --- |
| `AssistantLauncher.vue` | `assistant-launcher.vue` |
| `AssistantPanel.vue` | `assistant-panel.vue` |
| `AssistantHeader.vue` | `assistant-header.vue` |
| `AssistantMessage.vue` | `assistant-message.vue` |
| `UserMessage.vue` | `user-message.vue` |
| `PendingMessage.vue` | `pending-message.vue` |
| `ErrorMessage.vue` | `error-message.vue` |
| `MessageList.vue` | `message-list.vue` |
| `ChatComposer.vue` | `chat-composer.vue` |
| `CitationList.vue` | `citation-list.vue` |
| `FeedbackButtons.vue` | `feedback-buttons.vue` |
| `QuickQuestions.vue` | `quick-questions.vue` |
| `WelcomeCard.vue` | `welcome-card.vue` |
| `ThumbIcon.vue` | `thumb-icon.vue` |
| `AnswerBlocks.ts` | `answer-blocks.ts` |

### 5.3 Prompt 管理 → `views/maintenance/prompt-management/`

| 來源（`admin/`） | 目標 |
| --- | --- |
| `PromptManagementPage.vue` | `prompt-management-page.vue` |
| `promptLabels.ts` | `prompt-labels.ts` |
| `diffRows.ts` | `diff-rows.ts` |
| `evaluationFormat.ts` | `evaluation-format.ts` |
| `ConfigEditor.vue`、`ConfigHeader.vue`、`ConfigPicker.vue`、`ConfirmDialog.vue` | `components/config-editor.vue`、`config-header.vue`、`config-picker.vue`、`confirm-dialog.vue` |
| `EvaluationCaseList.vue`、`EvaluationCasesDialog.vue`、`EvaluationHistory.vue`、`EvaluationSummary.vue`、`EvaluationVerdict.vue`、`EvaluationView.vue` | `components/evaluation-case-list.vue`、`evaluation-cases-dialog.vue`、`evaluation-history.vue`、`evaluation-summary.vue`、`evaluation-verdict.vue`、`evaluation-view.vue` |
| `MarkdownEditor.vue`、`NewInstructionDialog.vue`、`PublishBar.vue`、`ToolPicker.vue`、`ValidationPanel.vue` | `components/markdown-editor.vue`、`new-instruction-dialog.vue`、`publish-bar.vue`、`tool-picker.vue`、`validation-panel.vue` |
| `VersionDiff.vue`、`VersionList.vue`、`VersionViewer.vue`、`VersionsTab.vue` | `components/version-diff.vue`、`version-list.vue`、`version-viewer.vue`、`versions-tab.vue` |
| `prompt-admin.css` | `src/assets/styles/prompt-admin.css` |

### 5.4 其餘

| 來源 | 目標 |
| --- | --- |
| `composables/useAssistantChat.ts` | `composables/use-assistant-chat.ts` |
| `composables/useSuggestions.ts` | `composables/use-suggestions.ts` |
| `composables/usePromptAdmin.ts` | `composables/use-prompt-admin.ts` |
| `composables/usePromptEvaluation.ts` | `composables/use-prompt-evaluation.ts` |
| `stores/useAssistantStore.ts` | `stores/use-assistant-store.ts` |
| `taipeiTime.ts` | `utils/taipei-time.ts` |
| `markdown/parser.ts` | `utils/markdown/parser.ts` |
| `markdown/SafeMarkdown.ts` | `utils/markdown/safe-markdown.ts` |
| `markdown/lineDiff.ts` | `utils/markdown/line-diff.ts` |
| `markdown/exampleQuestions.ts` | `utils/markdown/example-questions.ts` |

匯出的識別字（`useAssistantStore`、`SafeMarkdown`、`formatTaipeiShort` 等）不改名，只改檔名。

## 6. 檔案對應：平台替身（不交付）

來源相對於 `src/platform-mock/`，目標相對於 `src/`。

| 來源 | 目標 | 對應平台的 |
| --- | --- | --- |
| `apiClient.ts` | `utils/api-client.ts` | `utils/api-client.ts` |
| `format.ts` | `utils/formatters.ts` | `utils/formatters.ts` |
| `useAuthStore.ts` | `stores/use-auth-store.ts` | `stores/use-auth-store.ts` |
| `useToast.ts` | `composables/use-toast.ts` | `stores/use-message-store`（名稱與介面不同） |
| `useTheme.ts` | `composables/use-theme.ts` | `stores/use-ui-store` 的 `isDark` / `toggleDark()` |
| `router.ts` | `router/index.ts` | `router/index.ts`、`guards/auth-guard.ts` |
| `MainLayout.vue` | `components/layout/main-layout.vue` | 平台 layout |
| `ToastHost.vue` | `components/layout/toast-host.vue` | 平台訊息元件 |
| `pages/customer360/RadarChart.vue` | `components/charts/radar-chart.vue` | 平台圖表元件 |
| `pages/customer360/RingChart.vue` | `components/charts/ring-chart.vue` | 同上 |
| `pages/LoginPage.vue` | `views/dev/dev-login-page.vue` | `views/dev/dev-login-page` |
| `pages/HomePage.vue` | `views/home-page.vue` | 平台首頁 |
| `pages/CustomerListPage.vue` | `views/management/customer/customer-list-page.vue` | 平台顧客列表 |
| `pages/Customer360Page.vue` | `views/management/customer/customer-360-page.vue` | 平台個人 360 |
| `pages/customer360/AssetCards.vue`、`BehaviorCards.vue`、`IntentCards.vue`、`ProfileCards.vue`、`OpsCard.vue`、`PairBars.vue` | `views/management/customer/components/asset-cards.vue`、`behavior-cards.vue`、`intent-cards.vue`、`profile-cards.vue`、`ops-card.vue`、`pair-bars.vue` | 平台個人 360 區塊 |
| `memberRank.ts` | `views/management/customer/member-rank.ts` | 同上 |
| `customer360Types.ts` | `beans/customer-bean.ts` | `beans/` 的顧客電文 |
| `styles/customer-360.css` | `assets/styles/customer-360.css` | 平台樣式 |
| `PromptManagementRoute.vue` | 刪除 | |
| `src/styles/tokens.css`、`base.css` | `assets/styles/tokens.css`、`base.css` | 平台樣式（小幫手依賴其中的 CSS 變數，見 §9） |

路由路徑（`/login`、`/customer`、`/customer/:certNo`、`/maintenance/prompt-management`）不變。`index.html` 內提到 `useTheme.ts` 的註解改為 `use-theme.ts`。

## 7. 測試

規則：`src/<路徑>/<檔名>` 的測試為 `tests/unit/<路徑>/<檔名>.spec.ts`；`__tests__/` 目錄全部移除。

| 來源 | 目標（相對於 `tests/unit/`） |
| --- | --- |
| `components/__tests__/*.spec.ts`（8 支） | `components/ai-assistant/<kebab>.spec.ts` |
| `admin/__tests__/<元件>.spec.ts`（15 支） | `views/maintenance/prompt-management/components/<kebab>.spec.ts` |
| `admin/__tests__/PromptManagementPage.spec.ts` | `views/maintenance/prompt-management/prompt-management-page.spec.ts` |
| `admin/__tests__/diffRows.spec.ts`、`evaluationFormat.spec.ts` | `views/maintenance/prompt-management/diff-rows.spec.ts`、`evaluation-format.spec.ts` |
| `api/__tests__/aiAssistantApi.admin.spec.ts` | `views/maintenance/prompt-management/prompt-management-api.spec.ts` |
| `api/__tests__/aiAssistantApi.stats.spec.ts` | `views/maintenance/prompt-management/prompt-management-api.stats.spec.ts` |
| `composables/__tests__/usePromptAdmin.spec.ts`、`usePromptEvaluation.spec.ts` | `composables/use-prompt-admin.spec.ts`、`use-prompt-evaluation.spec.ts` |
| `stores/__tests__/useAssistantStore.spec.ts` | `stores/use-assistant-store.spec.ts` |
| `markdown/__tests__/*.spec.ts`（4 支） | `utils/markdown/<kebab>.spec.ts` |
| `__tests__/taipeiTime.spec.ts` | `utils/taipei-time.spec.ts` |
| 替身：`apiClient.spec.ts`、`format.spec.ts` | `utils/api-client.spec.ts`、`utils/formatters.spec.ts` |
| 替身：`router.spec.ts`、`useTheme.spec.ts` | `router/index.spec.ts`、`composables/use-theme.spec.ts` |
| 替身：`MainLayout.spec.ts`、`charts.spec.ts` | `components/layout/main-layout.spec.ts`、`components/charts/charts.spec.ts` |
| 替身：`LoginPage.spec.ts`、`Customer360Page.spec.ts`、`memberRank.spec.ts` | `views/dev/dev-login-page.spec.ts`、`views/management/customer/customer-360-page.spec.ts`、`views/management/customer/member-rank.spec.ts` |
| `admin/__tests__/evalFixtures.ts` | `fixtures/eval-fixtures.ts` |
| `platform-mock/__tests__/customer360Fixtures.ts` | `fixtures/customer-360-fixtures.ts` |
| `src/testing/testCustomers.ts` | `fixtures/test-customers.ts` |

共 45 支 spec。聊天 API（`ai-assistant-api.ts`）目前沒有專屬測試（由 store 與元件測試間接涵蓋），本次不新增。

測試內的 `vi.mock(...)` 與 `importOriginal<typeof import(...)>` 路徑一律改 `@/`。原本以 `importOriginal` 保留常數的 mock，改為 mock `prompt-management-api`（或 `ai-assistant-api`）；常數已在 `*-constant.ts`，不受 mock 影響，`importOriginal` 展開仍保留以免改變測試行為。

## 8. 工具鏈

- `vite.config.ts`：加 `resolve.alias`（`@` → `src`）；移除 `test` 區塊與 `/// <reference types="vitest/config" />`。
- `vitest.config.ts`（新增）：以 `mergeConfig` 沿用 `vite.config.ts` 的 plugin 與 alias；`environment: 'jsdom'`、`include: ['tests/unit/**/*.spec.ts']`。
- `tsconfig.app.json`：加 `paths`（`@/*` → `./src/*`）；`include` 加 `tests/**/*.ts`，讓 `vue-tsc -b` 繼續檢查測試檔的型別（現況測試在 `src/` 下，本來就會被檢查）。
- `tsconfig.node.json`：`include` 加 `vitest.config.ts`。
- `package.json` 不變（不新增套件；scripts 不變）。

## 9. `frontend/PORTING.md`

1. **要複製的檔案**：§5 與對應測試的完整清單，依目錄分組。
2. **不要複製的替身**：§6 全部，各列對應的平台檔案。
3. **接入點**
   - `router/index.ts`：新增 `maintenance/prompt-management` 路由，權限 `AI_CONFIG_READ`（平台的 `requiredModule` 寫法以平台為準）。
   - 個人 360 頁：掛載 `<assistant-launcher>`，傳入顧客證號（現況見 `customer-360-page.vue`）。
   - 樣式：引入 `assets/styles/prompt-admin.css`；列出小幫手用到、需由平台 `tokens.css` 提供的 CSS 變數名稱。
   - 小幫手對平台模組的依賴：`@/utils/api-client` 的 `apiClient`、`ApiError`、`meta.silentCodes`；`@/stores/use-auth-store` 的 `user`、`hasPermission()`；`@/composables/use-toast` 的 `show()`。
4. **行為差異待辦**：§2 的表。
5. **對規範的偏離**：聊天 API 位置（§3）。

## 10. 文件同步

- [01-architecture.md](../../spec/01-architecture.md)、[platform-setup-requests.md](../../platform-setup-requests.md)：把 `features/ai-assistant`、`platform-mock` 等舊路徑改為新路徑。
- [questions-for-manager.md](../../questions-for-manager.md)：M35 預設假設的前端部分改為「依平台分層目錄放置，替身檔案不複製，附 `frontend/PORTING.md`」；S6、S7、S8 補上 `frontend-instructions.md` 已能回答的部分（`apiClient` 行為、錯誤處理表、元件位置）。
- 既有的 `docs/superpowers/specs/`、`plans/` 是歷史紀錄，不改。

## 11. 執行與驗證

1. **基準**：在目前 HEAD 跑 `npm test` 與 `npm run build`，記下測試檔數、測試案例數、結果。
2. **搬移**：用 `git mv`，保留歷史。來源與目標檔名僅大小寫不同時（Windows 檔案系統不分大小寫）分兩步改名；本次對應表中沒有這種情況（`parser.ts` 檔名不變，只換目錄）。
3. **順序**：先工具鏈（alias、`vitest.config.ts`）並確認基準不變 → 替身 → 小幫手（含 API 與型別拆檔）→ 測試 → `PORTING.md` 與文件。每個階段結束都跑測試與 build。
4. **完成檢查**
   - 測試檔數與案例數等於基準，全數通過；`npm run build` 通過。
   - `src/features`、`src/platform-mock`、`src/styles`、`src/testing`、任何 `__tests__` 目錄不存在。
   - `src/` 與 `tests/` 內沒有相對 import（同目錄 `./` 也改 `@/`）。唯一例外：測試 import `tests/unit/fixtures/` 用相對路徑，因為 fixtures 在 `src/` 之外，`@/` 指不到，且不想為此要求平台多設一個 alias。
   - 除 `App.vue` 外沒有含大寫字母的檔名。
   - `.vue` 的 template 內沒有 PascalCase 的自訂元件標籤（`RouterView`、`RouterLink`、`Teleport`、`Transition` 等內建或路由元件除外，維持現況）。
   - 手動：`npm run dev` 後登入、開個人 360、問一題、開 Prompt 管理頁，畫面與樣式和搬移前相同。

## 12. 風險

| 風險 | 處置 |
| --- | --- |
| 搬移時夾帶行為改動（後端分層重構後曾出現回歸，見 `660eea2`） | §2 的四種允許改動；每階段比對基準 |
| css 的載入順序改變導致樣式不同 | `main.ts` 維持 `tokens.css` → `base.css` 的順序；元件內的 css import 維持在原檔案、原位置 |
| 拆 API 後 `vi.mock` 沒 mock 到正確模組，測試打到真的 `apiClient` | 測試案例數等於基準，且 mock 的物件名稱（`aiAssistantApi` / `promptManagementApi`）逐檔核對呼叫端實際 import 的那一支 |
| 平台已有同名檔案（例如 `utils/markdown/parser.ts`） | 無法在本專案驗證；`PORTING.md` 提醒複製前先比對，衝突時整個 `utils/markdown/` 改名 |
