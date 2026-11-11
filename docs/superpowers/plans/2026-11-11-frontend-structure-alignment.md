# Frontend Structure Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `frontend/` 改成平台的分層目錄與 kebab-case 命名，讓小幫手的檔案可以照目錄複製進 iCRM 前端，行為完全不變。

**Architecture:** 先把所有相對 import 原地改成 `@/` 絕對路徑，之後每次搬移就只是「`git mv` + 把 `@/舊路徑` 換成 `@/新路徑`」，由兩支一次性腳本完成。唯一非機械的改動是把 API 檔與型別檔各拆成聊天與後台兩支，獨立成一個 task。每個 task 結束都與基準比對。

**Tech Stack:** Vue 3.5、TypeScript 6、Vite 7、Vitest 4、vue-tsc、Node 24、Git Bash。

**Spec:** [docs/superpowers/specs/2026-11-11-frontend-structure-alignment-design.md](../specs/2026-11-11-frontend-structure-alignment-design.md)

## Global Constraints

- **基準**（HEAD `eec6bc7`）：`npx vitest run` → `Test Files 45 passed (45)`、`Tests 561 passed (561)`；`npm run build` 通過。每個 task 結束時這三項都必須相同。
- 所有指令在 `frontend/` 下執行（Git Bash）。
- 檔案內容只允許四種改動：import 路徑、template 元件標籤大小寫、API 物件名稱（拆出 `promptManagementApi`）、因拆檔而搬動的宣告。**不改**函式本體、樣式、路由路徑、測試斷言、匯出的識別字名稱。
- 搬移一律 `git mv`（腳本代為執行）。
- 測試失敗時先查 import／mock 路徑；**不得**為了讓測試通過而修改斷言或刪測試。查不出原因就停下來回報。
- **不要 commit**。每個 task 最後一步是把 `git add` 與 commit 訊息回報給使用者，由使用者自己提交。
- 不新增 npm 套件；`package.json` 不動。
- `tests/unit/fixtures/` 在 `src/` 之外，`@/` 指不到；測試以**相對路徑** import fixtures，這是「一律 `@/`」的唯一例外。
- 一次性腳本放 `frontend/.restructure/`，不加入 git，Task 7 刪除。

## Review Focus

以下是測試不會擋下、最可能出事的地方，各由指定的步驟把關：

1. **`vi.mock` 沒 mock 到實際被 import 的模組**（拆 API 後最容易發生）：測試會改打真的 `apiClient`。把關：Task 5 Step 6 逐檔核對表，加上測試案例數必須仍是 561。
2. **css 載入順序改變使畫面走樣**：測試不看樣式。把關：`main.ts` 保持 `tokens.css` 先於 `base.css`；元件內的 css import 留在原檔原位置；Task 7 Step 7 手動檢查。
3. **kebab-case 標籤沒對到元件**：寫錯時 Vue 只在執行期警告「Failed to resolve component」，build 不會失敗。把關：Task 6 Step 4 檢查測試輸出沒有這句警告。
4. **依名稱設定的測試 stub 失效**（`customer-360-page.spec.ts` 的 `stubs: { AssistantLauncher: true }`）：失效時測試可能仍通過但實際渲染了真元件。把關：Task 6 Step 4。
5. **lazy route 路徑錯誤**：`router/index.ts` 的 `import('@/...')`。把關：`npm run build` 的 `vue-tsc` 會解析動態 import 路徑；Task 7 Step 7 手動開四個路由。

---

### Task 1: 工具鏈與一次性腳本

**Files:**
- Modify: `frontend/vite.config.ts`
- Create: `frontend/vitest.config.ts`
- Modify: `frontend/tsconfig.app.json`、`frontend/tsconfig.node.json`
- Create（不進 git）: `frontend/.restructure/to-alias.mjs`、`move.mjs`、`kebab-tags.mjs`

**Interfaces:**
- Produces: `@/*` → `src/*` alias（Vite、Vitest、TypeScript 三處都認得）；三支腳本，用法見各 step。

- [ ] **Step 1: 確認基準**

Run: `npx vitest run 2>&1 | tail -6 && npm run build 2>&1 | tail -3`
Expected: `Test Files 45 passed (45)`、`Tests 561 passed (561)`、`✓ built in ...`。不符就停下回報。

- [ ] **Step 2: `vite.config.ts` 加 alias，移除測試設定**

移除第一行 `/// <reference types="vitest/config" />` 與整個 `test` 區塊；加入：

```ts
import { fileURLToPath, URL } from 'node:url'
// defineConfig 內：
resolve: {
  alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
},
```

`plugins`、`server` 不動。

- [ ] **Step 3: 建立 `vitest.config.ts`**

```ts
import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      // 'src/**' is transitional: removed in the last task once every spec lives in tests/unit
      include: ['src/**/*.spec.ts', 'tests/unit/**/*.spec.ts'],
    },
  }),
)
```

- [ ] **Step 4: tsconfig**

`tsconfig.app.json`：`compilerOptions` 加 `"paths": { "@/*": ["./src/*"] }`；`include` 改為 `["src/**/*.ts", "src/**/*.vue", "tests/**/*.ts"]`。
`tsconfig.node.json`：`include` 改為 `["vite.config.ts", "vitest.config.ts"]`。

- [ ] **Step 5: 建立 `.restructure/to-alias.mjs`**

把 `src/` 下所有相對 import 原地改成 `@/`。

```js
import { readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join, relative, resolve, sep } from 'node:path'

const SRC = resolve('src')
const RE = /(\bfrom\s*|\bimport\s*\(?\s*|vi\.mock\(\s*)(['"])(\.{1,2}\/[^'"]*)\2/g

function walk(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)],
  )
}

let count = 0
for (const file of walk(SRC)) {
  if (!/\.(ts|vue)$/.test(file)) continue
  const text = readFileSync(file, 'utf8')
  const out = text.replace(RE, (_m, head, quote, spec) => {
    const rel = relative(SRC, resolve(dirname(file), spec)).split(sep).join('/')
    if (rel.startsWith('..')) throw new Error(`${file}: ${spec} leaves src/`)
    count += 1
    return `${head}${quote}@/${rel}${quote}`
  })
  if (out !== text) writeFileSync(file, out)
}
console.log(`rewrote ${count} specifiers`)
```

- [ ] **Step 6: 建立 `.restructure/move.mjs`**

讀一份對應表（每行 `舊路徑 新路徑`，相對於 `frontend/`，`#` 開頭為註解），先全部 `git mv`，再把 `src/`、`tests/` 內所有 `@/舊` 換成新的 specifier。目標在 `src/` 外（fixtures）時，改寫成從 importer 最終位置算出的相對路徑。

```js
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { posix } from 'node:path'

const pairs = readFileSync(process.argv[2], 'utf8')
  .split(/\r?\n/)
  .map((l) => l.trim())
  .filter((l) => l && !l.startsWith('#'))
  .map((l) => l.split(/\s+/))

for (const [from, to] of pairs) {
  if (!existsSync(from)) throw new Error(`missing source: ${from}`)
  if (existsSync(to)) throw new Error(`target exists: ${to}`)
}
for (const [from, to] of pairs) {
  mkdirSync(posix.dirname(to), { recursive: true })
  execFileSync('git', ['mv', from, to], { stdio: 'inherit' })
}

const noTsExt = (p) => p.replace(/\.ts$/, '')
const moved = new Map(
  pairs.filter(([from]) => from.startsWith('src/')).map(([from, to]) => [`@/${noTsExt(from.slice(4))}`, to]),
)

function walk(dir) {
  if (!existsSync(dir)) return []
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? walk(posix.join(dir, e.name)) : [posix.join(dir, e.name)],
  )
}

let count = 0
for (const file of [...walk('src'), ...walk('tests')]) {
  if (!/\.(ts|vue)$/.test(file)) continue
  const text = readFileSync(file, 'utf8')
  const out = text.replace(/(['"])(@\/[^'"]+)\1/g, (m, quote, spec) => {
    const to = moved.get(spec)
    if (!to) return m
    count += 1
    if (to.startsWith('src/')) return `${quote}@/${noTsExt(to.slice(4))}${quote}`
    const rel = posix.relative(posix.dirname(file), noTsExt(to))
    return `${quote}${rel.startsWith('.') ? rel : `./${rel}`}${quote}`
  })
  if (out !== text) writeFileSync(file, out)
}
console.log(`moved ${pairs.length} files, rewrote ${count} specifiers`)
```

限制：一份 fixture 必須和它**所有** importer 在同一批搬移（相對路徑是依 importer 當下位置算的）。本計畫的兩份對應表都符合。

- [ ] **Step 7: 建立 `.restructure/kebab-tags.mjs`**

把 `.vue` 的 template 內、對應到「script 中以 PascalCase default import 進來的識別字」的標籤改成 kebab-case。沒有 import 的標籤（`RouterView`、`RouterLink`、`Teleport`、`Transition` 等）不會被動到。

```js
import { readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

function walk(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)],
  )
}
const kebab = (name) => name.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()

let count = 0
for (const file of walk('src')) {
  if (!file.endsWith('.vue')) continue
  const text = readFileSync(file, 'utf8')
  const start = text.indexOf('<template')
  const end = text.lastIndexOf('</template>')
  if (start < 0 || end < 0) continue
  const names = [...text.matchAll(/^import\s+([A-Z][A-Za-z0-9]*)\s+from\s+['"]@\/[^'"]+['"]/gm)].map((m) => m[1])
  let template = text.slice(start, end)
  for (const name of names) {
    template = template.replace(new RegExp(`<(/?)${name}(?=[^A-Za-z0-9_-])`, 'g'), (_m, slash) => {
      count += 1
      return `<${slash}${kebab(name)}`
    })
  }
  const out = text.slice(0, start) + template + text.slice(end)
  if (out !== text) writeFileSync(file, out)
}
console.log(`renamed ${count} tags`)
```

- [ ] **Step 8: 驗證基準不變**

Run: `npx vitest run 2>&1 | tail -6 && npm run build 2>&1 | tail -3 && git status --short .`
Expected: 45 / 561 / built；`git status` 只列出 `vite.config.ts`、`tsconfig.app.json`、`tsconfig.node.json`（M）與 `vitest.config.ts`、`.restructure/`（??）。

- [ ] **Step 9: 回報 commit 指令**

```bash
git add frontend/vite.config.ts frontend/vitest.config.ts frontend/tsconfig.app.json frontend/tsconfig.node.json
git commit -m "build(frontend): add the @ alias and move the test config to vitest.config.ts"
```

---

### Task 2: 相對 import 原地改成 `@/`

**Files:**
- Modify: `frontend/src/` 下所有含相對 import 的 `.ts`、`.vue`（約 120 個檔案、337 個 specifier）

**Interfaces:**
- Consumes: Task 1 的 alias 與 `to-alias.mjs`。
- Produces: `src/` 內每個專案內 import 都是 `@/<相對於 src 的路徑>`（`.ts` 不帶副檔名，`.vue`、`.css` 帶）。Task 3、4 的 `move.mjs` 依賴這個形式。

- [ ] **Step 1: 執行**

Run: `node .restructure/to-alias.mjs`
Expected: `rewrote 337 specifiers`（`from` 287、`import '…'` 15、`import(…)` 11、`vi.mock(…)` 24）。數字不同就檢查差在哪一類再繼續。

- [ ] **Step 2: 確認沒有殘留**

Run: `grep -rnE "(from|import|vi\.mock\()\s*\(?\s*['\"]\.\.?/" src`
Expected: 無輸出。

- [ ] **Step 3: 驗證**

Run: `npx vitest run 2>&1 | tail -6 && npm run build 2>&1 | tail -3`
Expected: 45 / 561 / built。

- [ ] **Step 4: 回報 commit 指令**

```bash
git add frontend/src
git commit -m "refactor(frontend): import project modules through the @ alias"
```

---

### Task 3: 搬移平台替身

**Files:**
- Create: `frontend/.restructure/map-mock.tsv`
- Delete: `frontend/src/platform-mock/PromptManagementRoute.vue`
- Move: `src/platform-mock/**`、`src/styles/**`（共 35 個檔案）
- Modify: `frontend/index.html`（註解）

**Interfaces:**
- Consumes: Task 2 的 `@/` 形式、`move.mjs`。
- Produces（之後的 task 與小幫手都用這些路徑）：`@/utils/api-client`（`apiClient`、`ApiError`、`setUnauthorizedHandler`）、`@/utils/formatters`、`@/stores/use-auth-store`（`useAuthStore`）、`@/composables/use-toast`（`useToast`、`useToastItems`）、`@/composables/use-theme`、`@/router/index`（`createAppRouter`）、`@/beans/customer-bean`、`@/assets/styles/{tokens,base,customer-360}.css`。

- [ ] **Step 1: 移除轉接元件**

`src/platform-mock/router.ts` 中把 `import("@/platform-mock/PromptManagementRoute.vue")` 改為 `import("@/features/ai-assistant/admin/PromptManagementPage.vue")`，然後 `git rm src/platform-mock/PromptManagementRoute.vue`。

- [ ] **Step 2: 建立 `.restructure/map-mock.tsv`**

```
src/platform-mock/apiClient.ts src/utils/api-client.ts
src/platform-mock/format.ts src/utils/formatters.ts
src/platform-mock/useAuthStore.ts src/stores/use-auth-store.ts
src/platform-mock/useToast.ts src/composables/use-toast.ts
src/platform-mock/useTheme.ts src/composables/use-theme.ts
src/platform-mock/router.ts src/router/index.ts
src/platform-mock/memberRank.ts src/views/management/customer/member-rank.ts
src/platform-mock/customer360Types.ts src/beans/customer-bean.ts
src/platform-mock/MainLayout.vue src/components/layout/main-layout.vue
src/platform-mock/ToastHost.vue src/components/layout/toast-host.vue
src/platform-mock/pages/customer360/RadarChart.vue src/components/charts/radar-chart.vue
src/platform-mock/pages/customer360/RingChart.vue src/components/charts/ring-chart.vue
src/platform-mock/pages/LoginPage.vue src/views/dev/dev-login-page.vue
src/platform-mock/pages/HomePage.vue src/views/home-page.vue
src/platform-mock/pages/CustomerListPage.vue src/views/management/customer/customer-list-page.vue
src/platform-mock/pages/Customer360Page.vue src/views/management/customer/customer-360-page.vue
src/platform-mock/pages/customer360/AssetCards.vue src/views/management/customer/components/asset-cards.vue
src/platform-mock/pages/customer360/BehaviorCards.vue src/views/management/customer/components/behavior-cards.vue
src/platform-mock/pages/customer360/IntentCards.vue src/views/management/customer/components/intent-cards.vue
src/platform-mock/pages/customer360/OpsCard.vue src/views/management/customer/components/ops-card.vue
src/platform-mock/pages/customer360/PairBars.vue src/views/management/customer/components/pair-bars.vue
src/platform-mock/pages/customer360/ProfileCards.vue src/views/management/customer/components/profile-cards.vue
src/platform-mock/styles/customer-360.css src/assets/styles/customer-360.css
src/styles/tokens.css src/assets/styles/tokens.css
src/styles/base.css src/assets/styles/base.css
# tests
src/platform-mock/__tests__/apiClient.spec.ts tests/unit/utils/api-client.spec.ts
src/platform-mock/__tests__/format.spec.ts tests/unit/utils/formatters.spec.ts
src/platform-mock/__tests__/router.spec.ts tests/unit/router/index.spec.ts
src/platform-mock/__tests__/useTheme.spec.ts tests/unit/composables/use-theme.spec.ts
src/platform-mock/__tests__/MainLayout.spec.ts tests/unit/components/layout/main-layout.spec.ts
src/platform-mock/__tests__/charts.spec.ts tests/unit/components/charts/charts.spec.ts
src/platform-mock/__tests__/LoginPage.spec.ts tests/unit/views/dev/dev-login-page.spec.ts
src/platform-mock/__tests__/Customer360Page.spec.ts tests/unit/views/management/customer/customer-360-page.spec.ts
src/platform-mock/__tests__/memberRank.spec.ts tests/unit/views/management/customer/member-rank.spec.ts
src/platform-mock/__tests__/customer360Fixtures.ts tests/unit/fixtures/customer-360-fixtures.ts
```

- [ ] **Step 3: 執行搬移**

Run: `node .restructure/move.mjs .restructure/map-mock.tsv && find src -type d -empty -delete`
Expected: `moved 35 files, rewrote N specifiers`（N > 0）；`src/platform-mock`、`src/styles` 不再存在。

- [ ] **Step 4: `index.html` 註解**

把註解中的 `useTheme.ts` 改為 `use-theme.ts`。其餘不動。

- [ ] **Step 5: 確認沒有殘留舊路徑**

Run: `grep -rnE "platform-mock|@/styles/" src tests index.html`
Expected: 無輸出。

- [ ] **Step 6: 驗證**

Run: `npx vitest run 2>&1 | tail -6 && npm run build 2>&1 | tail -3`
Expected: 45 / 561 / built。

- [ ] **Step 7: 回報 commit 指令**

```bash
git add -A frontend/src frontend/tests frontend/index.html
git commit -m "refactor(frontend): move the platform stand-ins to the platform directory layout"
```

---

### Task 4: 搬移小幫手

**Files:**
- Create: `frontend/.restructure/map-assistant.tsv`
- Move: `src/features/ai-assistant/**`、`src/testing/testCustomers.ts`（共 89 個檔案）

**Interfaces:**
- Consumes: Task 3 產出的平台模組路徑、`move.mjs`。
- Produces: 小幫手所有檔案的最終位置。**此時 API 與型別尚未拆**：`@/components/ai-assistant/ai-assistant-api`（匯出 `aiAssistantApi` 全部 20 個函式與 6 個常數）、`@/beans/ai-assistant-bean`（全部型別）。兩支後台 API 測試已在最終檔名，但仍 import `aiAssistantApi`。Task 5 接手拆分。

- [ ] **Step 1: 建立 `.restructure/map-assistant.tsv`**

以下 `A` 代表 `src/features/ai-assistant`，`PM` 代表 `src/views/maintenance/prompt-management`，`TPM` 代表 `tests/unit/views/maintenance/prompt-management`。**寫入檔案時要展開成完整路徑**（腳本不認縮寫）。

```
A/types.ts src/beans/ai-assistant-bean.ts
A/api/aiAssistantApi.ts src/components/ai-assistant/ai-assistant-api.ts
A/taipeiTime.ts src/utils/taipei-time.ts
A/markdown/parser.ts src/utils/markdown/parser.ts
A/markdown/SafeMarkdown.ts src/utils/markdown/safe-markdown.ts
A/markdown/lineDiff.ts src/utils/markdown/line-diff.ts
A/markdown/exampleQuestions.ts src/utils/markdown/example-questions.ts
A/composables/useAssistantChat.ts src/composables/use-assistant-chat.ts
A/composables/useSuggestions.ts src/composables/use-suggestions.ts
A/composables/usePromptAdmin.ts src/composables/use-prompt-admin.ts
A/composables/usePromptEvaluation.ts src/composables/use-prompt-evaluation.ts
A/stores/useAssistantStore.ts src/stores/use-assistant-store.ts
# chat widget
A/components/AnswerBlocks.ts src/components/ai-assistant/answer-blocks.ts
A/components/AssistantHeader.vue src/components/ai-assistant/assistant-header.vue
A/components/AssistantLauncher.vue src/components/ai-assistant/assistant-launcher.vue
A/components/AssistantMessage.vue src/components/ai-assistant/assistant-message.vue
A/components/AssistantPanel.vue src/components/ai-assistant/assistant-panel.vue
A/components/ChatComposer.vue src/components/ai-assistant/chat-composer.vue
A/components/CitationList.vue src/components/ai-assistant/citation-list.vue
A/components/ErrorMessage.vue src/components/ai-assistant/error-message.vue
A/components/FeedbackButtons.vue src/components/ai-assistant/feedback-buttons.vue
A/components/MessageList.vue src/components/ai-assistant/message-list.vue
A/components/PendingMessage.vue src/components/ai-assistant/pending-message.vue
A/components/QuickQuestions.vue src/components/ai-assistant/quick-questions.vue
A/components/ThumbIcon.vue src/components/ai-assistant/thumb-icon.vue
A/components/UserMessage.vue src/components/ai-assistant/user-message.vue
A/components/WelcomeCard.vue src/components/ai-assistant/welcome-card.vue
# prompt management
A/admin/PromptManagementPage.vue PM/prompt-management-page.vue
A/admin/promptLabels.ts PM/prompt-labels.ts
A/admin/diffRows.ts PM/diff-rows.ts
A/admin/evaluationFormat.ts PM/evaluation-format.ts
A/admin/prompt-admin.css src/assets/styles/prompt-admin.css
A/admin/ConfigEditor.vue PM/components/config-editor.vue
A/admin/ConfigHeader.vue PM/components/config-header.vue
A/admin/ConfigPicker.vue PM/components/config-picker.vue
A/admin/ConfirmDialog.vue PM/components/confirm-dialog.vue
A/admin/EvaluationCaseList.vue PM/components/evaluation-case-list.vue
A/admin/EvaluationCasesDialog.vue PM/components/evaluation-cases-dialog.vue
A/admin/EvaluationHistory.vue PM/components/evaluation-history.vue
A/admin/EvaluationSummary.vue PM/components/evaluation-summary.vue
A/admin/EvaluationVerdict.vue PM/components/evaluation-verdict.vue
A/admin/EvaluationView.vue PM/components/evaluation-view.vue
A/admin/MarkdownEditor.vue PM/components/markdown-editor.vue
A/admin/NewInstructionDialog.vue PM/components/new-instruction-dialog.vue
A/admin/PublishBar.vue PM/components/publish-bar.vue
A/admin/ToolPicker.vue PM/components/tool-picker.vue
A/admin/ValidationPanel.vue PM/components/validation-panel.vue
A/admin/VersionDiff.vue PM/components/version-diff.vue
A/admin/VersionList.vue PM/components/version-list.vue
A/admin/VersionViewer.vue PM/components/version-viewer.vue
A/admin/VersionsTab.vue PM/components/versions-tab.vue
# tests: chat widget
A/components/__tests__/AnswerBlocks.spec.ts tests/unit/components/ai-assistant/answer-blocks.spec.ts
A/components/__tests__/AssistantLauncher.spec.ts tests/unit/components/ai-assistant/assistant-launcher.spec.ts
A/components/__tests__/AssistantMessage.spec.ts tests/unit/components/ai-assistant/assistant-message.spec.ts
A/components/__tests__/AssistantPanel.spec.ts tests/unit/components/ai-assistant/assistant-panel.spec.ts
A/components/__tests__/CitationList.spec.ts tests/unit/components/ai-assistant/citation-list.spec.ts
A/components/__tests__/FeedbackButtons.spec.ts tests/unit/components/ai-assistant/feedback-buttons.spec.ts
A/components/__tests__/MessageList.spec.ts tests/unit/components/ai-assistant/message-list.spec.ts
A/components/__tests__/QuickQuestions.spec.ts tests/unit/components/ai-assistant/quick-questions.spec.ts
# tests: prompt management
A/admin/__tests__/PromptManagementPage.spec.ts TPM/prompt-management-page.spec.ts
A/admin/__tests__/diffRows.spec.ts TPM/diff-rows.spec.ts
A/admin/__tests__/evaluationFormat.spec.ts TPM/evaluation-format.spec.ts
A/api/__tests__/aiAssistantApi.admin.spec.ts TPM/prompt-management-api.spec.ts
A/api/__tests__/aiAssistantApi.stats.spec.ts TPM/prompt-management-api.stats.spec.ts
A/admin/__tests__/ConfigEditor.spec.ts TPM/components/config-editor.spec.ts
A/admin/__tests__/ConfigPicker.spec.ts TPM/components/config-picker.spec.ts
A/admin/__tests__/EvaluationCaseList.spec.ts TPM/components/evaluation-case-list.spec.ts
A/admin/__tests__/EvaluationCasesDialog.spec.ts TPM/components/evaluation-cases-dialog.spec.ts
A/admin/__tests__/EvaluationHistory.spec.ts TPM/components/evaluation-history.spec.ts
A/admin/__tests__/EvaluationSummary.spec.ts TPM/components/evaluation-summary.spec.ts
A/admin/__tests__/EvaluationView.spec.ts TPM/components/evaluation-view.spec.ts
A/admin/__tests__/MarkdownEditor.spec.ts TPM/components/markdown-editor.spec.ts
A/admin/__tests__/PublishBar.spec.ts TPM/components/publish-bar.spec.ts
A/admin/__tests__/ToolPicker.spec.ts TPM/components/tool-picker.spec.ts
A/admin/__tests__/ValidationPanel.spec.ts TPM/components/validation-panel.spec.ts
A/admin/__tests__/VersionDiff.spec.ts TPM/components/version-diff.spec.ts
A/admin/__tests__/VersionList.spec.ts TPM/components/version-list.spec.ts
A/admin/__tests__/VersionViewer.spec.ts TPM/components/version-viewer.spec.ts
A/admin/__tests__/VersionsTab.spec.ts TPM/components/versions-tab.spec.ts
# tests: the rest
A/composables/__tests__/usePromptAdmin.spec.ts tests/unit/composables/use-prompt-admin.spec.ts
A/composables/__tests__/usePromptEvaluation.spec.ts tests/unit/composables/use-prompt-evaluation.spec.ts
A/stores/__tests__/useAssistantStore.spec.ts tests/unit/stores/use-assistant-store.spec.ts
A/markdown/__tests__/parser.spec.ts tests/unit/utils/markdown/parser.spec.ts
A/markdown/__tests__/SafeMarkdown.spec.ts tests/unit/utils/markdown/safe-markdown.spec.ts
A/markdown/__tests__/lineDiff.spec.ts tests/unit/utils/markdown/line-diff.spec.ts
A/markdown/__tests__/exampleQuestions.spec.ts tests/unit/utils/markdown/example-questions.spec.ts
A/__tests__/taipeiTime.spec.ts tests/unit/utils/taipei-time.spec.ts
# fixtures
A/admin/__tests__/evalFixtures.ts tests/unit/fixtures/eval-fixtures.ts
src/testing/testCustomers.ts tests/unit/fixtures/test-customers.ts
```

- [ ] **Step 2: 執行搬移**

Run: `node .restructure/move.mjs .restructure/map-assistant.tsv && find src -type d -empty -delete`
Expected: `moved 89 files, rewrote N specifiers`；`src/features`、`src/testing` 不再存在。

- [ ] **Step 3: 確認結構**

Run: `grep -rnE "@/features|@/testing|__tests__" src tests; find src tests -name "__tests__"; ls src`
Expected: 前兩個指令無輸出；`ls src` 為 `App.vue assets beans components composables env.d.ts main.ts router stores utils views`。

- [ ] **Step 4: 驗證**

Run: `npx vitest run 2>&1 | tail -6 && npm run build 2>&1 | tail -3`
Expected: 45 / 561 / built。

- [ ] **Step 5: 回報 commit 指令**

```bash
git add -A frontend/src frontend/tests
git commit -m "refactor(frontend): move the assistant into the platform directory layout"
```

---

### Task 5: 拆分 API 與型別

**Files:**
- Modify: `src/components/ai-assistant/ai-assistant-api.ts`、`src/beans/ai-assistant-bean.ts`
- Create: `src/components/ai-assistant/ai-assistant-constant.ts`
- Create: `src/views/maintenance/prompt-management/prompt-management-api.ts`、`prompt-management-constant.ts`
- Create: `src/beans/prompt-management-bean.ts`
- Modify: 這些檔案的所有 importer（`src/` 與 `tests/`）

**Interfaces:**
- Consumes: Task 4 的未拆分檔案。
- Produces:
  - `@/components/ai-assistant/ai-assistant-api` → `aiAssistantApi: { chat, suggestions, putFeedback }`
  - `@/components/ai-assistant/ai-assistant-constant` → `CHAT_TIMEOUT_MS`
  - `@/views/maintenance/prompt-management/prompt-management-api` → `promptManagementApi: { listConfigs, createConfig, listVersions, feedbackStats, getVersion, createDraft, saveDraft, discardDraft, validateDraft, publish, applyVersion, startEvaluation, listEvaluations, currentEvaluation, getEvaluation, evaluationCases, tools }`
  - `@/views/maintenance/prompt-management/prompt-management-constant` → `CONFLICT`、`VALIDATION_FAILED`、`OVERWRITE_PENDING`、`INPUT_INVALID`、`EVAL_RUNNING`
  - `@/beans/ai-assistant-bean` → 聊天型別；`@/beans/prompt-management-bean` → 後台型別

函式簽章、函式本體、常數值、型別定義、JSDoc 註解全部原樣搬移。

- [ ] **Step 1: 拆型別**

把 `ai-assistant-bean.ts` 中從 `// ---- Prompt management (02 section 3) ----` 這行起到檔尾的內容剪到新檔 `prompt-management-bean.ts`。任一邊用到另一邊的型別時加 `import type { … } from '@/beans/…'`（已確認後台段不引用聊天型別；反方向由 `vue-tsc` 確認）。

- [ ] **Step 2: 拆常數**

`CHAT_TIMEOUT_MS`（連同它的 JSDoc）→ `ai-assistant-constant.ts`。五個錯誤碼（連同它們上方的 JSDoc）→ `prompt-management-constant.ts`。

- [ ] **Step 3: 拆 API**

`prompt-management-api.ts` 取得：`ADMIN`、`EVALUATIONS`、`configPath()`、`STATS_SILENT`、以及 `// ---- Prompt management` 註解起的 17 個函式，包成 `export const promptManagementApi = { … }`；型別從 `@/beans/prompt-management-bean`、常數從 `./` 對應的 `@/views/maintenance/prompt-management/prompt-management-constant` import。

`STATS_SILENT` 改為不引用 `SILENT`，值不變：

```ts
const STATS_SILENT = {
  silentCodes: ['AI_CUSTOMER_NOT_FOUND', 'AI_NETWORK_ERROR', 'AI_INTERNAL_ERROR', 'AI_FORBIDDEN', 'AI_CONFIG_NOT_FOUND'],
}
```

（它上方的 JSDoc 一併搬過去。）

`ai-assistant-api.ts` 留下：`SILENT`、`base()`、`aiAssistantApi = { chat, suggestions, putFeedback }`；`CHAT_TIMEOUT_MS` 改由 `@/components/ai-assistant/ai-assistant-constant` import。兩支 API 檔互不 import。

- [ ] **Step 4: 修 `src/` 的 importer**

Run: `npx vue-tsc -b 2>&1 | head -60`

依錯誤逐檔修正 import。預期的對應：

| 檔案 | 改為使用 |
| --- | --- |
| `composables/use-prompt-admin.ts`、`use-prompt-evaluation.ts`、`views/maintenance/prompt-management/components/tool-picker.vue` | `promptManagementApi`；錯誤碼常數來自 `prompt-management-constant` |
| `stores/use-assistant-store.ts`、`composables/use-suggestions.ts` | `aiAssistantApi`（不變） |
| 所有 import 型別的檔案 | 依型別所屬拆成一或兩行 `import type` |

呼叫端只把 `aiAssistantApi.` 前綴換成 `promptManagementApi.`，引數與後續邏輯不動。

- [ ] **Step 5: 修兩支 API 測試**

`tests/unit/views/maintenance/prompt-management/prompt-management-api.spec.ts` 與 `prompt-management-api.stats.spec.ts`：import 與呼叫改為 `promptManagementApi`（來自 `@/views/maintenance/prompt-management/prompt-management-api`）。斷言不動。

- [ ] **Step 6: 修其餘測試的 `vi.mock`**

逐檔核對，mock 的模組必須是受測程式實際 import 的那一支：

| 測試檔（相對於 `tests/unit/`） | `vi.mock` 路徑 | mock 的物件鍵 |
| --- | --- | --- |
| `views/maintenance/prompt-management/components/config-editor.spec.ts` | `@/views/maintenance/prompt-management/prompt-management-api` | `promptManagementApi` |
| `…/components/evaluation-view.spec.ts` | 同上 | `promptManagementApi` |
| `…/components/tool-picker.spec.ts` | 同上 | `promptManagementApi` |
| `…/prompt-management-page.spec.ts` | 同上 | `promptManagementApi` |
| `composables/use-prompt-admin.spec.ts` | 同上 | `promptManagementApi` |
| `composables/use-prompt-evaluation.spec.ts` | 同上 | `promptManagementApi` |
| `components/ai-assistant/assistant-launcher.spec.ts` | `@/components/ai-assistant/ai-assistant-api` | `aiAssistantApi`（不變） |
| `components/ai-assistant/assistant-panel.spec.ts` | 同上 | `aiAssistantApi`（不變） |
| `stores/use-assistant-store.spec.ts` | 同上 | `aiAssistantApi`（不變） |

使用 `importOriginal<typeof import('…')>()` 的五支，`typeof import` 的路徑與 `vi.mock` 的路徑一起改，展開寫法保留。測試內若 import 錯誤碼常數，改由 `prompt-management-constant` import。測試檔內引用 `aiAssistantApi.xxx` 作為 spy／斷言對象之處，同樣換成 `promptManagementApi.xxx`。

- [ ] **Step 7: 確認拆分乾淨**

Run:
```bash
grep -rn "aiAssistantApi" src tests | grep -vE "ai-assistant-api|use-assistant-store|use-suggestions|assistant-launcher\.spec|assistant-panel\.spec"
grep -n "import" src/components/ai-assistant/ai-assistant-api.ts src/views/maintenance/prompt-management/prompt-management-api.ts
```
Expected: 第一個指令無輸出；第二個顯示兩支 API 檔都只 import `@/utils/api-client`、各自的 bean、各自的 constant。

- [ ] **Step 8: 驗證**

Run: `npx vitest run 2>&1 | tail -6 && npm run build 2>&1 | tail -3`
Expected: 45 / 561 / built。案例數少於 561 代表有測試檔載入失敗；測試逾時或出現網路錯誤代表 mock 沒對到。

- [ ] **Step 9: 回報 commit 指令**

```bash
git add -A frontend/src frontend/tests
git commit -m "refactor(frontend): split the assistant API, constants and beans into chat and prompt management"
```

---

### Task 6: template 元件標籤改 kebab-case

**Files:**
- Modify: `src/**/*.vue` 中使用自訂元件的 template（含 `App.vue`）

**Interfaces:**
- Consumes: `kebab-tags.mjs`；Task 2 之後所有元件 import 都是 `import Xxx from '@/…'` 的形式。
- Produces: template 內自訂元件一律 kebab-case；script 內的 import 識別字維持 PascalCase（`<script setup>` 會把 `<config-editor>` 對到 `ConfigEditor`）。

- [ ] **Step 1: 執行**

Run: `node .restructure/kebab-tags.mjs`
Expected: `renamed N tags`（N > 0）。

- [ ] **Step 2: 確認殘留的 PascalCase 標籤只有內建與路由元件**

Run: `grep -rnoE "</?[A-Z][A-Za-z0-9]*" src --include=*.vue | grep -vE "<script|</script" | awk -F: '{print $3}' | sort | uniq -c`
Expected: 只出現 `RouterView`、`RouterLink`、`Teleport`、`Transition`、`TransitionGroup`、`KeepAlive`、`Suspense` 這類（以及 `<script>` 內 TypeScript 泛型造成的誤判，例如 `<T`、`<Record`，逐一確認位於 script 區塊即可）。若有自訂元件殘留（例如以具名 import 或動態元件使用），手動改。

- [ ] **Step 3: 確認 script 沒被動到**

Run: `git diff --stat -- src | tail -1 && git diff -- src | grep -E "^[+-]" | grep -vE "^(\+\+\+|---)" | grep -vE "^[+-]\s*</?[a-z]" | head`
Expected: 第二個指令無輸出（所有變更行都是以小寫標籤開頭的 template 行）。有輸出時逐行確認是多行標籤的屬性行以外的東西才算問題。

- [ ] **Step 4: 驗證，並檢查元件解析警告與 stub**

Run: `npx vitest run 2>&1 | tee .restructure/kebab-run.log | tail -6; grep -c "Failed to resolve component" .restructure/kebab-run.log; npm run build 2>&1 | tail -3`
Expected: 45 / 561；`Failed to resolve component` 計數為 `0`；built。

另外確認 `tests/unit/views/management/customer/customer-360-page.spec.ts` 的 `stubs: { AssistantLauncher: true }` 仍生效：在該測試暫時加一行 `console.log(wrapper.html())` 跑單檔，輸出應含 `<assistant-launcher-stub`，確認後移除該行。若 stub 沒生效，停下回報（不要改斷言）。

- [ ] **Step 5: 回報 commit 指令**

```bash
git add frontend/src
git commit -m "style(frontend): use kebab-case component tags in templates"
```

---

### Task 7: 收尾：測試設定、PORTING.md、文件同步、完成檢查

**Files:**
- Modify: `frontend/vitest.config.ts`
- Create: `frontend/PORTING.md`
- Modify: `docs/spec/01-architecture.md`、`docs/platform-setup-requests.md`、`docs/questions-for-manager.md`、`docs/setup.md`（若有引用舊路徑）
- Delete: `frontend/.restructure/`

**Interfaces:**
- Consumes: 前六個 task 的最終結構。

- [ ] **Step 1: 收緊測試範圍**

`vitest.config.ts` 的 `include` 改為 `['tests/unit/**/*.spec.ts']`，並刪掉 transitional 註解。

- [ ] **Step 2: 寫 `frontend/PORTING.md`**

讀者是把小幫手搬進 iCRM 前端的工程師，沒看過這個 repo。五節，內容依 spec §9：

1. **要複製的檔案**：依目錄分組列出。用 `git ls-files src tests` 產生清單，扣掉第 2 節的檔案。
2. **不要複製的替身**：spec §6 的表（本專案路徑、對應的平台檔案），加上它們的測試與 `tests/unit/fixtures/customer-360-fixtures.ts`，以及 `App.vue`、`main.ts`、`env.d.ts`、`index.html`、設定檔。
3. **接入點**：
   - 路由：`maintenance/prompt-management` → `@/views/maintenance/prompt-management/prompt-management-page.vue`，權限 `AI_CONFIG_READ`。
   - 個人 360 頁掛 `<assistant-launcher>`：從 `src/views/management/customer/customer-360-page.vue` 抄出實際的 props 與掛載寫法。
   - 樣式：`@/assets/styles/prompt-admin.css` 由元件自行 import；列出小幫手用到的 CSS 變數。用 `grep -rhoE "var\(--[a-z0-9-]+" src/components/ai-assistant src/views/maintenance/prompt-management src/assets/styles/prompt-admin.css | sort -u` 取得，並註明這些變數由替身的 `tokens.css` 定義，平台需提供同名變數或對照表。
   - 對平台模組的依賴：用 `grep -rhoE "from '@/(utils/api-client|stores/use-auth-store|composables/use-toast)'" …` 與實際 import 的名稱整理成表（模組、用到的匯出、用途）。
   - 測試 fixtures 以相對路徑 import，`tests/unit/fixtures/` 要一起複製。
4. **行為差異待辦**：spec §2 的表，`ApiResponse<T>` 一項註明要與後端一起改。
5. **對規範的偏離與注意事項**：聊天 API 位於 `components/ai-assistant/`；複製前先比對平台是否已有同名檔案（特別是 `utils/markdown/`、`utils/taipei-time.ts`、`composables/use-suggestions.ts`）。

- [ ] **Step 3: 同步文件**

Run: `grep -rnE "features/ai-assistant|platform-mock|src/styles|aiAssistantApi|types\.ts|__tests__|PascalCase 檔名" ../docs/spec ../docs/setup.md ../docs/platform-setup-requests.md ../docs/demo-checklist.md`

- `docs/spec/01-architecture.md`：約 18 處（mermaid 圖節點、§5 的目錄樹、整合步驟）。目錄樹換成 spec §4 的結構；「複製 `features/ai-assistant` 資料夾」改為「依 `frontend/PORTING.md` 照目錄複製」；`platform-mock` 的敘述改為「平台替身（清單見 `frontend/PORTING.md`）」；`<AssistantLauncher>` 改 `<assistant-launcher>`。
- `docs/platform-setup-requests.md` 第 102 行附近：同樣改寫。
- `docs/questions-for-manager.md`：M35 預設假設的前端部分改為「依平台分層目錄放置，替身檔案不複製，附 `frontend/PORTING.md`」；S6、S7、S8 的「回覆」填入 `frontend-instructions.md` §4、§4-1、§2 已能回答的內容，並標明來源；無法回答的部分（`AbortSignal` 是否支援、個人 360 元件實際位置）保留為待確認。
- `docs/superpowers/` 下的歷史 spec／plan 不改。

- [ ] **Step 4: 刪除一次性腳本**

Run: `rm -rf .restructure`

- [ ] **Step 5: 完成檢查**

Run:
```bash
npx vitest run 2>&1 | tail -6
npm run build 2>&1 | tail -3
ls src
find src tests -name "__tests__"
git ls-files src tests | grep -E "[A-Z]" | grep -v "src/App.vue"
grep -rnE "(from|import|vi\.mock\()\s*\(?\s*['\"]\.\.?/" src
grep -rnE "(from|import|vi\.mock\()\s*\(?\s*['\"]\.\.?/" tests | grep -v "fixtures/"
git ls-files src | grep -c "spec.ts"
```
Expected：45 / 561 / built；`ls src` 為 `App.vue assets beans components composables env.d.ts main.ts router stores utils views`；其後四個指令皆無輸出；最後一個為 `0`。

- [ ] **Step 6: 比對內容改動沒有越界**

Run: `git diff -M --stat eec6bc7 -- src tests | tail -3`，再對三個邏輯最多的檔案看 rename 相似度：
`git diff -M --summary eec6bc7 -- src | grep -E "use-prompt-admin|use-assistant-store|prompt-management-page"`
Expected: 相似度 ≥ 90%（只改了 import 與標籤）。明顯偏低就打開 diff 檢查是否夾帶了行為改動。

- [ ] **Step 7: 手動檢查（請使用者執行或協助）**

啟動後端與 `npm run dev`，依序：登入 → 首頁 → 顧客列表 → 個人 360（版面、圖表、深色模式切換）→ 開小幫手問一題（回答、引用、回饋按鈕）→ Prompt 管理頁（列表、編輯器、版本差異、評測分頁）。畫面與搬移前相同，瀏覽器 console 沒有 `Failed to resolve component` 或 404。

- [ ] **Step 8: 回報 commit 指令**

```bash
git add -A frontend docs/spec/01-architecture.md docs/platform-setup-requests.md docs/questions-for-manager.md
git commit -m "docs(frontend): add the porting guide and sync the docs with the new layout"
```
