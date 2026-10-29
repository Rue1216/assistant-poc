<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ApiError } from '../../../platform-mock/apiClient'
import { useAuthStore } from '../../../platform-mock/useAuthStore'
import { useToast } from '../../../platform-mock/useToast'
import { GENERIC_ERROR, usePromptAdmin } from '../composables/usePromptAdmin'
import { DiffTooLargeError, type DiffLine } from '../markdown/lineDiff'
import { formatTaipei, formatTaipeiShort } from '../taipeiTime'
import type { ConfigType, ConfigVersion, OverwritePendingDetail, VersionHistoryItem } from '../types'
import ConfigEditor from './ConfigEditor.vue'
import ConfigHeader, { type PageTab } from './ConfigHeader.vue'
import ConfigList from './ConfigList.vue'
import ConfirmDialog from './ConfirmDialog.vue'
import EvaluationView from './EvaluationView.vue'
import NewInstructionDialog from './NewInstructionDialog.vue'
import { STATUS_SHORT } from './promptLabels'
import VersionDiff from './VersionDiff.vue'
import VersionHistory from './VersionHistory.vue'
import VersionViewer from './VersionViewer.vue'
import './prompt-admin.css'

const auth = useAuthStore()
const canEdit = computed(() => auth.hasPermission('AI_CONFIG_EDIT'))
// R31: publishing saves the form first (PUT draft needs AI_CONFIG_EDIT), so it needs EDIT as well as PUBLISH
const canPublish = computed(() => canEdit.value && auth.hasPermission('AI_CONFIG_PUBLISH'))

const configType = ref<ConfigType>('SYSTEM_PROMPT')
const tab = ref<PageTab>('content')
const selected = reactive<Record<ConfigType, string>>({ SYSTEM_PROMPT: '', SYSTEM_INSTRUCTION: '' })
const selectedKey = computed(() => selected[configType.value])

const admin = usePromptAdmin(selectedKey)
const state = admin.state

/** Temporary right-hand pane of the 版本 tab (replaced by VersionsTab later). */
const versionPane = ref<
  | null
  | { kind: 'version'; version: ConfigVersion }
  | {
      kind: 'diff'
      before: VersionHistoryItem
      after: VersionHistoryItem
      nameDiff: DiffLine[]
      contentDiff: DiffLine[]
    }
>(null)
let viewSeq = 0
function resetVersionPane() {
  viewSeq++
  versionPane.value = null
}
function showContent() {
  resetVersionPane()
  tab.value = 'content'
}

const toast = useToast()
const newDialogOpen = ref(false)
/** R29: an action that would throw away unsaved edits, waiting for the user to confirm */
const discardEdits = ref<(() => void) | null>(null)

function guarded(action: () => void) {
  if (admin.dirty.value) discardEdits.value = action
  else action()
}

function onConfirmDiscardEdits() {
  const action = discardEdits.value
  discardEdits.value = null
  action?.()
}

/** Settles the pending route navigation, if the open dialog came from one. */
let settleLeave: ((leave: boolean) => void) | null = null

function onCancelDiscardEdits() {
  discardEdits.value = null
  settleLeave?.(false)
  settleLeave = null
}

// Leaving through the router (menu, logout, back button) asks the same question as switching config.
onBeforeRouteLeave(() => {
  if (!admin.dirty.value) return true
  settleLeave?.(false) // a second navigation replaces the first one's dialog
  return new Promise<boolean>((resolve) => {
    settleLeave = resolve
    discardEdits.value = () => {
      settleLeave = null
      resolve(true)
    }
  })
})

// Closing or reloading the tab is not a route change: let the browser show its own prompt.
function warnBeforeUnload(event: BeforeUnloadEvent) {
  event.preventDefault()
  event.returnValue = ''
}

watch(
  () => admin.dirty.value,
  (dirty) => {
    if (dirty) window.addEventListener('beforeunload', warnBeforeUnload)
    else window.removeEventListener('beforeunload', warnBeforeUnload)
  },
  { immediate: true },
)
onBeforeUnmount(() => window.removeEventListener('beforeunload', warnBeforeUnload))

/** ApiErrors were already toasted by apiClient; anything else is surfaced here. */
function surface(e: unknown) {
  if (e instanceof ApiError) return
  toast.show(e instanceof DiffTooLargeError ? e.message : GENERIC_ERROR)
}
const pendingApply = ref<{ source: VersionHistoryItem; detail: OverwritePendingDetail } | null>(null)

void admin.loadConfigs()

// keep a valid selection per tab as the list loads or changes
watch(
  () => state.configs,
  (configs) => {
    for (const type of ['SYSTEM_PROMPT', 'SYSTEM_INSTRUCTION'] as const) {
      const ofType = configs.filter((c) => c.configType === type)
      if (!ofType.some((c) => c.configKey === selected[type])) selected[type] = ofType[0]?.configKey ?? ''
    }
  },
)

watch(selectedKey, showContent)

const selectedSummary = computed(() => state.configs.find((c) => c.configKey === selectedKey.value) ?? null)
const publishedInstructionCount = computed(
  () => state.configs.filter((c) => c.configType === 'SYSTEM_INSTRUCTION' && c.published).length,
)
const itemOf = (version: number) => state.versions.find((v) => v.version === version)

/** R27: without a working copy the published version is shown read-only. */
const mainPane = computed<'editor' | 'published' | 'none'>(() => {
  if (state.workingCopy) return 'editor'
  if (state.published) return 'published'
  return 'none'
})

const nameOfKey = (key: string) => state.configs.find((c) => c.configKey === key)?.name ?? key

const activeVersions = computed(() => {
  const v = versionPane.value
  if (v?.kind === 'version') return [v.version.version]
  if (v?.kind === 'diff') return [v.before.version, v.after.version]
  const current = state.workingCopy ?? state.published
  return current ? [current.version] : []
})

function selectConfig(key: string) {
  if (key !== selectedKey.value) guarded(() => (selected[configType.value] = key))
}

function selectConfigType(next: ConfigType) {
  if (next !== configType.value) guarded(() => (configType.value = next))
}

async function onView(version: number) {
  const item = itemOf(version)
  if (!item || item.status === 'DRAFT' || item.status === 'SCHEDULED') return showContent()
  const seq = ++viewSeq
  try {
    const loaded = await admin.loadVersion(version)
    if (seq === viewSeq) versionPane.value = { kind: 'version', version: loaded }
  } catch (e) {
    surface(e)
  }
}

async function onCompare(before: number, after: number) {
  const a = itemOf(before)
  const b = itemOf(after)
  if (!a || !b) return
  const seq = ++viewSeq
  try {
    const diff = await admin.compare(before, after)
    if (seq === viewSeq) versionPane.value = { kind: 'diff', before: a, after: b, ...diff }
  } catch (e) {
    surface(e)
  }
}

function onApply(version: number) {
  guarded(() => void applyVersion(version))
}

async function applyVersion(version: number) {
  const source = itemOf(version)
  const result = await admin.apply(version)
  if (!result) return
  if (result.needsConfirm) {
    if (source) pendingApply.value = { source, detail: result }
    return
  }
  showContent()
}

async function onConfirmApply() {
  const draft = await admin.confirmApply()
  pendingApply.value = null
  if (draft) showContent()
}

function onCancelApply() {
  admin.cancelApply()
  pendingApply.value = null
}

async function onCreateDraft() {
  if (await admin.createDraft()) showContent()
}

function onReload() {
  guarded(() => void reloadNow())
}

function onDiscard() {
  guarded(() => void admin.discard())
}

function openNewDialog() {
  guarded(() => (newDialogOpen.value = true))
}

async function reloadNow() {
  showContent()
  await admin.reload()
}

async function onCreateInstruction(keySuffix: string, name: string) {
  const created = await admin.createInstruction(keySuffix, name)
  if (!created) return
  newDialogOpen.value = false
  configType.value = 'SYSTEM_INSTRUCTION'
  selected.SYSTEM_INSTRUCTION = created.configKey
}
</script>

<template>
  <div class="pm">
    <div class="pm-pagehead">
      <div>
        <nav class="breadcrumb" aria-label="麵包屑">
          <span>系統管理</span><span aria-hidden="true">/</span><span>Prompt 管理</span>
        </nav>
        <h1 class="pm-pagehead__title">Prompt 管理</h1>
        <p class="pm-pagehead__desc">
          管理小幫手的 System Prompt 與問題分類，發布後立即套用於所有對話。
        </p>
      </div>
      <button
        v-if="configType === 'SYSTEM_INSTRUCTION' && canEdit"
        class="btn btn--primary"
        type="button"
        @click="openNewDialog"
      >
        <span aria-hidden="true">＋</span>新增問題分類
      </button>
    </div>

    <div class="pm-layout">
      <ConfigList
        :tab="configType"
        :configs="state.configs"
        :selected-key="selectedKey"
        @update:tab="selectConfigType"
        @select="selectConfig"
      />

      <section v-if="state.workingCopy || state.published" class="pm-main card">
        <ConfigHeader
          v-model:tab="tab"
          :name="(state.workingCopy ?? state.published)!.name"
          :config-key="selectedKey"
          :working-copy="state.workingCopy"
          :published="state.published"
          verdict-mark=""
        />

        <template v-if="tab === 'content'">
          <ConfigEditor
            v-if="mainPane === 'editor'"
            :key="`${selectedKey}:${state.workingCopy!.version}`"
            :admin="admin"
            :config-type="configType"
            :published-instruction-count="publishedInstructionCount"
            :can-edit="canEdit"
            :can-publish="canPublish"
            @go-instructions="selectConfigType('SYSTEM_INSTRUCTION')"
            @discard="onDiscard"
            @reload="onReload"
          />
          <VersionViewer
            v-else
            :version="state.published!"
            :versions="state.versions"
            :working-copy="null"
            :can-edit="canEdit"
            :busy="state.busy"
            :conflict="state.conflict"
            @apply="onApply"
            @create-draft="onCreateDraft"
            @reload="onReload"
          />
        </template>

        <div v-else-if="tab === 'versions'" class="pm-versions-tab">
          <div class="pm-versions-tab__list">
            <VersionHistory
              v-if="state.versions.length"
              :key="selectedKey"
              :versions="state.versions"
              :active-versions="activeVersions"
              :can-edit="canEdit"
              :busy="state.busy"
              :feedback="state.feedback"
              :config-type="configType"
              @view="onView"
              @compare="onCompare"
              @apply="onApply"
            />
          </div>
          <VersionViewer
            v-if="versionPane?.kind === 'version'"
            :version="versionPane.version"
            :versions="state.versions"
            :working-copy="state.workingCopy"
            :can-edit="canEdit"
            :busy="state.busy"
            :conflict="state.conflict"
            @apply="onApply"
            @create-draft="onCreateDraft"
            @reload="onReload"
          />
          <VersionDiff
            v-else-if="versionPane?.kind === 'diff'"
            :config-key="selectedKey"
            :name="selectedSummary?.name ?? ''"
            :before="versionPane.before"
            :after="versionPane.after"
            :name-diff="versionPane.nameDiff"
            :content-diff="versionPane.contentDiff"
          />
          <section v-else class="pm-empty">請從左側選擇一個版本</section>
        </div>

        <EvaluationView
          v-else
          :config-key="selectedKey"
          :name="selectedSummary?.name ?? ''"
          :working-copy="state.workingCopy"
          :dirty="admin.dirty.value"
          :can-edit="canEdit"
          :name-of="nameOfKey"
        />
      </section>
      <section v-else class="pm-empty card">
        {{ state.loading || !state.configsLoaded ? '載入中…' : '請從左側選擇一個設定' }}
      </section>
    </div>

    <ConfirmDialog
      :open="!!pendingApply"
      :title="pendingApply ? `套用 ${formatTaipei(pendingApply.source.updatedAt)} 的版本？` : ''"
      confirm-label="確定套用"
      :busy="state.busy"
      @confirm="onConfirmApply"
      @cancel="onCancelApply"
    >
      <template v-if="pendingApply">
        <p v-if="pendingApply.detail.status === 'SCHEDULED'" class="dialog__text">
          目前有一份預約於 <strong>{{ formatTaipei(pendingApply.detail.scheduledAt ?? '') }}</strong> 發布的版本，套用後排程會取消，該版本會被標為已捨棄（仍可在版本歷程中找回）。確定要套用嗎？
        </p>
        <p v-else class="dialog__text">
          目前有一份 <strong>{{ formatTaipei(pendingApply.detail.updatedAt) }}</strong> 儲存的草稿，套用後它會被標為已捨棄（仍可在版本歷程中找回）。確定要套用嗎？
        </p>
        <dl class="dialog__summary">
          <div>
            <dt>來源</dt>
            <dd>
              {{ STATUS_SHORT[pendingApply.source.status] }}・{{ formatTaipeiShort(pendingApply.source.updatedAt) }}・{{ pendingApply.source.updatedBy }}
            </dd>
          </div>
          <div v-if="pendingApply.detail.status === 'SCHEDULED'">
            <dt>目前排程</dt>
            <dd>預約 {{ formatTaipeiShort(pendingApply.detail.scheduledAt ?? '') }} → 取消，標為已捨棄</dd>
          </div>
          <div v-else>
            <dt>目前草稿</dt>
            <dd>{{ formatTaipeiShort(pendingApply.detail.updatedAt) }} 儲存 → 標為已捨棄</dd>
          </div>
          <div><dt>套用後</dt><dd>新增一份草稿，需再發布才會生效</dd></div>
        </dl>
      </template>
    </ConfirmDialog>

    <ConfirmDialog
      :open="!!discardEdits"
      title="放棄尚未儲存的修改？"
      confirm-label="放棄修改"
      @confirm="onConfirmDiscardEdits"
      @cancel="onCancelDiscardEdits"
    >
      <p class="dialog__text">有尚未儲存的修改，確定要放棄嗎？</p>
    </ConfirmDialog>

    <NewInstructionDialog
      :open="newDialogOpen"
      :busy="state.busy"
      @create="onCreateInstruction"
      @cancel="newDialogOpen = false"
    />
  </div>
</template>

<style scoped>
.pm {
  width: 100%;
  padding-bottom: var(--space-6);
}

.pm-pagehead {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.pm-pagehead__title {
  font-size: var(--text-2xl);
  font-weight: 700;
  color: var(--color-text-strong);
  line-height: var(--leading-tight);
}

.pm-pagehead__desc {
  margin-top: 4px;
  color: var(--color-text-muted);
}

.pm-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: var(--space-5);
  align-items: start;
}

.pm-main {
  min-width: 0;
}

.pm-versions-tab {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  align-items: start;
}

.pm-versions-tab__list {
  padding: var(--space-3);
  border-right: 1px solid var(--color-border);
}

/* the history was built to sit under a list row; here it stands alone */
.pm-versions-tab__list :deep(.pm-item__history-btn) {
  position: static;
  margin: var(--space-2);
  width: fit-content;
}

.pm-empty {
  padding: var(--space-8);
  text-align: center;
  color: var(--color-text-muted);
}

@media (max-width: 1100px) {
  .pm-pagehead {
    flex-direction: column;
    align-items: stretch;
  }
  .pm-layout,
  .pm-versions-tab {
    grid-template-columns: 1fr;
  }
  .pm-versions-tab__list {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }
}
</style>
