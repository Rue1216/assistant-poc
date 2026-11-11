# 小幫手前端平移說明

把 AI 小幫手（個人 360 的對話視窗、後台的 Prompt 管理頁）從這個 POC 搬進 iCRM 前端時用的清單。

`frontend/` 已依 `frontend-instructions.md` §2、§3 的分層目錄與命名擺放。**第 1 節的檔案照原路徑複製即可，不必改 import**；第 2 節是 POC 用來頂替平台的替身，**不要複製**。

所有路徑相對於 `frontend/`。專案內 import 一律用 `@/`（指向 `src/`）。

## 1. 要複製的檔案

共 97 個：`src/` 56 個、`tests/` 41 個。

### 1.1 `src/`

| 目錄 | 檔案 |
| --- | --- |
| `src/beans/` | `ai-assistant-bean.ts`、`prompt-management-bean.ts` |
| `src/components/ai-assistant/` | `ai-assistant-api.ts`、`ai-assistant-constant.ts`、`answer-blocks.vue`、`assistant-header.vue`、`assistant-launcher.vue`、`assistant-message.vue`、`assistant-panel.vue`、`chat-composer.vue`、`citation-list.vue`、`error-message.vue`、`feedback-buttons.vue`、`message-list.vue`、`pending-message.vue`、`quick-questions.vue`、`thumb-icon.vue`、`user-message.vue`、`welcome-card.vue` |
| `src/composables/` | `use-assistant-chat.ts`、`use-suggestions.ts`、`use-prompt-admin.ts`、`use-prompt-evaluation.ts` |
| `src/stores/` | `use-assistant-store.ts` |
| `src/utils/` | `taipei-time.ts` |
| `src/utils/markdown/` | `parser.ts`、`safe-markdown.ts`、`line-diff.ts`、`example-questions.ts` |
| `src/views/maintenance/prompt-management/` | `prompt-management-page.vue`、`prompt-management-api.ts`、`prompt-management-constant.ts`、`prompt-management-schema.ts`、`prompt-labels.ts`、`diff-rows.ts`、`evaluation-format.ts` |
| `src/views/maintenance/prompt-management/components/` | `config-editor.vue`、`config-header.vue`、`config-picker.vue`、`confirm-dialog.vue`、`evaluation-case-list.vue`、`evaluation-cases-dialog.vue`、`evaluation-history.vue`、`evaluation-summary.vue`、`evaluation-verdict.vue`、`evaluation-view.vue`、`markdown-editor.vue`、`new-instruction-dialog.vue`、`publish-bar.vue`、`tool-picker.vue`、`validation-panel.vue`、`version-diff.vue`、`version-list.vue`、`version-viewer.vue`、`versions-tab.vue` |
| `src/assets/styles/` | `prompt-admin.css` |

### 1.2 `tests/`

測試路徑對應 `src/`：`src/<路徑>/<檔名>` 的測試是 `tests/unit/<路徑>/<檔名>.spec.ts`。

| 目錄 | 檔案 |
| --- | --- |
| `tests/unit/components/ai-assistant/` | `answer-blocks`、`assistant-launcher`、`assistant-message`、`assistant-panel`、`citation-list`、`feedback-buttons`、`message-list`、`quick-questions`（各 `.spec.ts`） |
| `tests/unit/composables/` | `use-assistant-chat.spec.ts`、`use-prompt-admin.spec.ts`、`use-prompt-evaluation.spec.ts` |
| `tests/unit/stores/` | `use-assistant-store.spec.ts` |
| `tests/unit/utils/` | `taipei-time.spec.ts` |
| `tests/unit/utils/markdown/` | `parser`、`safe-markdown`、`line-diff`、`example-questions`（各 `.spec.ts`） |
| `tests/unit/views/maintenance/prompt-management/` | `prompt-management-page.spec.ts`、`prompt-management-api.spec.ts`、`prompt-management-api-stats.spec.ts`、`prompt-management-schema.spec.ts`、`diff-rows.spec.ts`、`evaluation-format.spec.ts` |
| `tests/unit/views/maintenance/prompt-management/components/` | `config-editor`、`config-picker`、`evaluation-case-list`、`evaluation-cases-dialog`、`evaluation-history`、`evaluation-summary`、`evaluation-view`、`markdown-editor`、`publish-bar`、`tool-picker`、`validation-panel`、`version-diff`、`version-list`、`version-viewer`、`versions-tab`（各 `.spec.ts`） |
| `tests/unit/fixtures/` | `eval-fixtures.ts`、`test-customers.ts` |
| `tests/unit/helpers/` | `settle-validation.ts`（等 vee-validate 5 ms 的驗證 debounce） |

複製前先確認平台沒有同路徑的檔案，特別是 `src/utils/markdown/`、`src/utils/taipei-time.ts`、`src/composables/use-suggestions.ts`。有衝突時改小幫手這邊的檔名，並同步改 import。

## 2. 不要複製的替身

這些檔案只為了讓 POC 能獨立執行，平台已有正式版本。

| 檔案 | 對應平台的 |
| --- | --- |
| `src/utils/api-client.ts` | `utils/api-client.ts` |
| `src/utils/formatters.ts` | `utils/formatters.ts` |
| `src/stores/use-auth-store.ts` | `stores/use-auth-store.ts` |
| `src/composables/use-toast.ts` | 平台的訊息機制（`use-message-store`），名稱與介面不同 |
| `src/composables/use-theme.ts` | `use-ui-store` 的 `isDark` / `toggleDark()` |
| `src/router/index.ts` | `router/index.ts`、`guards/auth-guard.ts` |
| `src/beans/customer-bean.ts` | `beans/` 的顧客電文 |
| `src/components/layout/main-layout.vue`、`toast-host.vue` | 平台 layout 與訊息元件 |
| `src/components/charts/radar-chart.vue`、`ring-chart.vue` | 平台圖表元件 |
| `src/views/home-page.vue` | 平台首頁 |
| `src/views/dev/dev-login-page.vue` | `views/dev/dev-login-page` |
| `src/views/management/customer/` 整個資料夾（`customer-list-page.vue`、`customer-360-page.vue`、`member-rank.ts`、`components/` 6 個） | 平台顧客列表與個人 360 |
| `src/assets/styles/tokens.css`、`base.css` | 平台樣式（見 3.3） |
| `src/assets/styles/customer-360.css` | 平台個人 360 樣式 |
| `src/App.vue`、`src/main.ts`、`src/env.d.ts` | 平台的同名檔案 |

替身的測試也不複製：`tests/unit/utils/api-client.spec.ts`、`tests/unit/utils/formatters.spec.ts`、`tests/unit/router/`、`tests/unit/composables/use-theme.spec.ts`、`tests/unit/components/layout/`、`tests/unit/components/charts/`、`tests/unit/views/dev/`、`tests/unit/views/management/`、`tests/unit/fixtures/customer-360-fixtures.ts`。

`index.html`、`vite.config.ts`、`vitest.config.ts`、`tsconfig*.json`、`package.json` 是 POC 自己的設定，不複製。小幫手用到的 npm 套件 `dayjs`、`vee-validate`、`@vee-validate/zod`、`zod` 都是規範指定的套件；接入時版本以平台 `package.json` 為準，`@vee-validate/zod` 需搭配 zod 3（POC 用 `zod@3.25.76`）。

## 3. 接入點

### 3.1 路由

在平台的 `router/index.ts` 加一條，掛在後台管理群組下：

| 路徑 | 元件 | 權限 |
| --- | --- | --- |
| `maintenance/prompt-management` | `@/views/maintenance/prompt-management/prompt-management-page.vue` | `AI_CONFIG_READ` |

POC 用 `meta: { permission: 'AI_CONFIG_READ' }`，平台的寫法（`requiredModule` 等）以平台為準。頁面內另外用 `AI_CONFIG_EDIT`、`AI_CONFIG_PUBLISH` 控制按鈕。

### 3.2 個人 360 頁

在平台個人 360 頁放：

```vue
<script setup lang="ts">
import AssistantLauncher from '@/components/ai-assistant/assistant-launcher.vue'
</script>

<template>
  <assistant-launcher :cert-no="certNo" />
</template>
```

`certNo`（`string`）是頁面上顧客的證號。元件自己檢查 `AI_ASSISTANT_USE` 權限，沒有權限時不顯示。POC 的掛法見 `src/views/management/customer/customer-360-page.vue`。

### 3.3 樣式

小幫手的元件各自帶 scoped style，Prompt 管理頁的元件另外 import `@/assets/styles/prompt-admin.css`（已在第 1 節）。除此之外它依賴兩樣由替身提供的全域樣式，平台要提供同名的，或在接入時改掉。

**全域 class**（定義在替身的 `base.css`）：
`btn`、`btn--primary`、`btn--secondary`、`btn--ghost`、`btn--sm`、`field`、`field__label`、`field__hint`、`badge`、`badge--scheduled`、`card`、`breadcrumb`、`md`、`sr-only`。

**CSS 變數**（定義在替身的 `tokens.css`，深色模式由 `html.dark` 覆蓋）：

| 類別 | 變數 |
| --- | --- |
| 小幫手專用 | `--ai-gap`、`--ai-width`、`--gradient-ai`、`--gradient-soft`、`--ai-cat-4`、`--ai-cat-5` |
| 語意色 | `--color-border`、`--color-border-strong`、`--color-focus-ring`、`--color-primary`、`--color-primary-active`、`--color-primary-border`、`--color-primary-hover`、`--color-primary-subtle`、`--color-surface`、`--color-text`、`--color-text-muted`、`--color-text-on-primary`、`--color-text-strong`、`--color-text-subtle` |
| 色票 | `--amber-50/200/700`、`--gray-25/50/100/200/400/700/900`、`--green-50/100/200/300/400/500/600/700/800`、`--red-50/200/600/700`、`--teal-400` |
| 字體與字級 | `--font-sans`、`--font-mono`、`--text-xs/sm/md/lg/xl/2xl`、`--leading-tight` |
| 間距、圓角、陰影、動畫 | `--space-2/3/4/5/6/8`、`--radius-sm/md/lg/xl/pill`、`--shadow-sm/md/lg`、`--duration-fast`、`--duration-normal`、`--ease-out` |

### 3.4 小幫手對平台模組的依賴

| 模組 | 用到的匯出 | 用途 |
| --- | --- | --- |
| `@/utils/api-client` | `apiClient`（axios instance） | 兩支 API 檔呼叫後端 |
| `@/utils/api-client` | `ApiError`（`status`、`code`、`traceId`、`details`） | composable 與 store 依 `code` 分流錯誤 |
| `@/utils/api-client` | request config 的 `meta.silentCodes: string[]` | 指定哪些錯誤碼不跳全域提示，由畫面自己顯示 |
| `@/stores/use-auth-store` | `useAuthStore().hasPermission(code)` | `AI_ASSISTANT_USE`、`AI_CONFIG_EDIT`、`AI_CONFIG_PUBLISH` |
| `@/composables/use-toast` | `useToast().show(message)` | 操作結果提示 |

這三個模組的介面和平台不同，是接入時主要的改寫點，見第 4 節。

### 3.5 測試

測試以**相對路徑** import `tests/unit/fixtures/`（fixtures 在 `src/` 外，`@/` 指不到），所以 fixtures 要放在同樣的相對位置。測試裡的 `vi.mock('@/composables/use-toast')`、`vi.mock('@/utils/api-client')` 要跟著第 4 節的改寫一起調整。

## 4. 行為差異待辦

日期（dayjs）與表單驗證（VeeValidate + zod）已改完，見第 5 節。下列項目仍與 `frontend-instructions.md` 不同，接入時處理。

| 項目 | 現況 | 規範 | 備註 |
| --- | --- | --- | --- |
| API 錯誤契約 | 攔截器 reject `ApiError`；呼叫端讀 `error.code` | `ApiResponse<T>` 包裝、reject `HandledError`、呼叫端先 `isHandledError()`（§4、§4-1） | **要和後端一起改**：後端目前也還沒回 `ApiResponse<T>`。另需確認平台 `api-client` 有沒有等同 `meta.silentCodes` 的機制 |
| Modal 點遮罩關閉 | `confirm-dialog.vue`、`new-instruction-dialog.vue`、`evaluation-cases-dialog.vue` 的 `dialog__backdrop` 點擊會關閉 | 點遮罩不得關閉（§6） | 拿掉遮罩的 `@click`，並改對應測試 |
| 元件直接呼叫 API | `tool-picker.vue` 直接呼叫 `promptManagementApi.tools()` | 元件不直接呼叫 API（§6） | 移到 composable |
| 訊息提示 | `useToast().show()`，4 個檔案 | 平台的訊息機制 | |
| 權限判斷 | `useAuthStore().hasPermission(code)`，3 處 | 平台 store 的 `permissions`、`hasAnyRole()`（§5） | |
| ESLint | 未設定 | 零 error（§1） | template 標籤已是 kebab-case；import 順序未檢查 |
| JSDoc | 部分有 | composable、store action、公開 utility 必須有（§3） | |

## 5. 對規範的偏離

- **聊天 API 放在 `src/components/ai-assistant/ai-assistant-api.ts`**，不在 `views/{模組}/`。對話視窗沒有自己的頁面，放進顧客模組會把它綁死在個人 360。平台若有不同安排，搬這支檔案與 `ai-assistant-constant.ts`，再改 `stores/use-assistant-store.ts` 與 `composables/use-suggestions.ts` 的 import。
- **聊天元件沒有 `icrm-` 前綴**。它們是小幫手專用，不是 `components/shared/` 的通用元件。
- **日期時間用 `utils/taipei-time.ts`，不用平台的 `formatDate`**。它以 dayjs（`utc`、`customParseFormat` plugin 在檔內 extend）固定讀成 +08:00，顯示格式含時分（`YYYY/MM/DD HH:mm`、`MM/DD HH:mm` 等），也負責排程欄位與 ISO 的互轉；`formatDate` 只有 `YYYY-MM-DD`，涵蓋不到。若平台 `formatters.ts` 已有含時間、固定台北時區的格式，可改用並刪掉對應函式。
- **表單 schema 放在 `views/maintenance/prompt-management/prompt-management-schema.ts`**（zod），新增問題分類、編輯器名稱與修改說明、排程日期時間三組。名稱規則對齊後端 `SaveDraftRequest` / `CreateConfigRequest`。編輯器的欄位值仍由 `usePromptAdmin` 的 `state.form` 持有（未儲存判斷、重新載入都讀它），vee-validate 表單只鏡像兩個欄位做驗證。
- **Prompt 管理頁的子元件放在 `views/maintenance/prompt-management/components/`**，`prompt-labels.ts`、`diff-rows.ts`、`evaluation-format.ts` 放在模組資料夾內。規範只列出 `*-page.vue`、`*-api.ts`、`*-constant.ts` 三種檔案，沒有規定頁面專用的元件與輔助函式放哪。
