<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useAuthStore } from '../../../platform-mock/useAuthStore'
import { usePromptAdmin } from '../composables/usePromptAdmin'
import { usePromptEvaluation } from '../composables/usePromptEvaluation'
import { formatTaipeiShort } from '../taipeiTime'
import type { ConfigType, OverwritePendingDetail, VersionHistoryItem } from '../types'
import ConfigEditor from './ConfigEditor.vue'
import ConfigHeader, { type PageTab } from './ConfigHeader.vue'
import ConfigPicker from './ConfigPicker.vue'
import ConfirmDialog from './ConfirmDialog.vue'
import EvaluationView from './EvaluationView.vue'
import NewInstructionDialog from './NewInstructionDialog.vue'
import VersionsTab from './VersionsTab.vue'
import VersionViewer from './VersionViewer.vue'
import { verdictMark } from './evaluationFormat'
import './prompt-admin.css'

const auth = useAuthStore()
const canEdit = computed(() => auth.hasPermission('AI_CONFIG_EDIT'))
// R31: publishing saves the form first (PUT draft needs AI_CONFIG_EDIT), so it needs EDIT as well as PUBLISH
const canPublish = computed(() => canEdit.value && auth.hasPermission('AI_CONFIG_PUBLISH'))

const tab = ref<PageTab>('content')
const selectedKey = ref('')

const admin = usePromptAdmin(selectedKey)
const state = admin.state

// Owned by the page, not by the 評測 tab: progress and the tab mark outlive tab switches.
const evaluation = usePromptEvaluation(selectedKey)
// The composable refreshes when the key changes; the key is only known once the config list has loaded,
// so this covers a key that is already there at mount.
onMounted(() => {
  if (selectedKey.value) void evaluation.refresh().catch(() => {})
})
const mark = computed(() =>
  verdictMark(evaluation.state.history, evaluation.state.current, selectedKey.value, state.workingCopy),
)

/** Save first; the evaluation starts only if the save went through (errors are shown by the save itself). */
async function onSaveAndRun() {
  const saved = await admin.save('KEEP_STATUS')
  if (!saved || saved.configKey !== selectedKey.value) return
  try {
    await evaluation.start(saved.docVersion)
  } catch {
    // already shown by the global handler
  }
}

const selectedSummary = computed(() => state.configs.find((c) => c.configKey === selectedKey.value) ?? null)
const configType = computed<ConfigType>(() => selectedSummary.value?.configType ?? 'SYSTEM_PROMPT')
const header = ref<InstanceType<typeof ConfigHeader> | null>(null)

function showContent() {
  tab.value = 'content'
}

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

const pendingApply = ref<{ source: VersionHistoryItem; detail: OverwritePendingDetail } | null>(null)

void admin.loadConfigs()

// as the list loads or changes: keep the selection, else the first system prompt, else the first question category
watch(
  () => state.configs,
  (configs) => {
    if (configs.some((c) => c.configKey === selectedKey.value)) return
    const first = configs.find((c) => c.configType === 'SYSTEM_PROMPT') ?? configs.find((c) => c.configType === 'SYSTEM_INSTRUCTION')
    selectedKey.value = first?.configKey ?? ''
  },
)

watch(selectedKey, showContent)

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

function selectConfig(key: string) {
  if (key !== selectedKey.value) guarded(() => (selectedKey.value = key))
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
  selectedKey.value = created.configKey
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
        v-if="canEdit"
        class="btn btn--primary"
        type="button"
        @click="openNewDialog"
      >
        <span aria-hidden="true">＋</span>新增問題分類
      </button>
    </div>

    <section v-if="selectedSummary" class="pm-main card">
      <ConfigHeader
        ref="header"
        v-model:tab="tab"
        :name="(state.workingCopy ?? state.published ?? selectedSummary).name"
        :ready="!!(state.workingCopy || state.published)"
        :configs="state.configs"
        :loading="!state.configsLoaded"
        :config-key="selectedKey"
        :working-copy="state.workingCopy"
        :published="state.published"
        :verdict-mark="mark"
        @select="selectConfig"
      />

      <section v-if="!state.workingCopy && !state.published" class="pm-empty">
        {{ state.loading ? '載入中…' : '請從上方選單選擇一個設定' }}
      </section>

      <template v-else-if="tab === 'content'">
        <ConfigEditor
          v-if="mainPane === 'editor'"
          :key="`${selectedKey}:${state.workingCopy!.version}`"
          :admin="admin"
          :config-type="configType"
          :published-instruction-count="publishedInstructionCount"
          :can-edit="canEdit"
          :can-publish="canPublish"
          @go-instructions="header?.openPicker()"
          @discard="onDiscard"
          @reload="onReload"
        />
        <VersionViewer
          v-else
          :version="state.published!"
          :versions="state.versions"
          action="edit"
          :can-edit="canEdit"
          :busy="state.busy"
          :conflict="state.conflict"
          @apply="onApply"
          @edit="onCreateDraft"
          @reload="onReload"
        />
      </template>

      <VersionsTab
        v-else-if="tab === 'versions'"
        :admin="admin"
        :config-type="configType"
        :can-edit="canEdit"
        @open-content="showContent"
        @apply="onApply"
        @reload="onReload"
      />

      <EvaluationView
        v-else
        :evaluation="evaluation"
        :config-key="selectedKey"
        :name="selectedSummary?.name ?? ''"
        :working-copy="state.workingCopy"
        :dirty="admin.dirty.value"
        :can-edit="canEdit"
        :busy="state.busy"
        :name-of="nameOfKey"
        @save-and-run="onSaveAndRun"
      />
    </section>
    <template v-else>
      <div class="pm-emptypick">
        <ConfigPicker
          :configs="state.configs"
          :selected-key="selectedKey"
          :loading="!state.configsLoaded"
          @select="selectConfig"
        />
      </div>
      <section class="pm-empty card">
        {{ !state.configsLoaded ? '載入中…' : '請從上方選單選擇一個設定' }}
      </section>
    </template>

    <ConfirmDialog
      :open="!!pendingApply"
      :title="pendingApply ? `以 ${formatTaipeiShort(pendingApply.source.updatedAt)} 的版本建立草稿？` : ''"
      confirm-label="建立草稿"
      :busy="state.busy"
      @confirm="onConfirmApply"
      @cancel="onCancelApply"
    >
      <p v-if="pendingApply" class="dialog__text">
        <template v-if="pendingApply.detail.status === 'SCHEDULED'">
          目前預約於 {{ formatTaipeiShort(pendingApply.detail.scheduledAt ?? '') }} 發布的版本會取消排程並改為已捨棄，可在版本列表找回。
        </template>
        <template v-else>
          目前 {{ formatTaipeiShort(pendingApply.detail.updatedAt) }} 儲存的草稿會改為已捨棄，可在版本列表找回。
        </template>
      </p>
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

/* stands in for the header's picker when there is no config to show */
.pm-emptypick {
  position: relative;
  margin-bottom: var(--space-3);
}

.pm-main {
  min-width: 0;
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
}
</style>
