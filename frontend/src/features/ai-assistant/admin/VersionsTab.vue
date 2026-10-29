<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ApiError } from '../../../platform-mock/apiClient'
import { useToast } from '../../../platform-mock/useToast'
import { GENERIC_ERROR, type PromptAdmin } from '../composables/usePromptAdmin'
import { DiffTooLargeError, type DiffLine } from '../markdown/lineDiff'
import type { ConfigType, ConfigVersion, VersionHistoryItem } from '../types'
import VersionDiff from './VersionDiff.vue'
import VersionList from './VersionList.vue'
import VersionViewer from './VersionViewer.vue'
import './prompt-admin.css'

const props = defineProps<{ admin: PromptAdmin; configType: ConfigType; canEdit: boolean }>()
const emit = defineEmits<{ 'open-content': []; apply: [version: number]; reload: [] }>()

type Pane =
  | { kind: 'loading' }
  | { kind: 'hint' }
  | { kind: 'version'; version: ConfigVersion }
  | { kind: 'diff'; before: VersionHistoryItem; after: VersionHistoryItem; nameDiff: DiffLine[]; contentDiff: DiffLine[] }

const state = props.admin.state
const toast = useToast()

const pane = ref<Pane>({ kind: 'loading' })
const checked = ref<number[]>([])
/** Bumped by every request so a response that arrives late is dropped. */
let seq = 0

const itemOf = (version: number) => state.versions.find((v) => v.version === version)

/** ApiErrors were already toasted by apiClient; anything else is surfaced here. */
function surface(e: unknown) {
  if (e instanceof ApiError) return
  toast.show(e instanceof DiffTooLargeError ? e.message : GENERIC_ERROR)
}

/** The live version, or the hint when the config was never published. */
function showPublished() {
  pane.value = state.published ? { kind: 'version', version: state.published } : { kind: 'hint' }
}

/** Runs a comparison. 'failed' was already surfaced; 'stale' means a newer request took over. */
async function showDiff(a: number, b: number): Promise<'ok' | 'failed' | 'stale'> {
  const mine = ++seq // bump first so even the early return below supersedes any request in flight
  const [older, newer] = a <= b ? [a, b] : [b, a]
  const before = itemOf(older)
  const after = itemOf(newer)
  if (!before || !after) return 'failed'
  pane.value = { kind: 'loading' }
  try {
    const diff = await props.admin.compare(older, newer)
    if (mine !== seq) return 'stale'
    pane.value = { kind: 'diff', before, after, ...diff }
    return 'ok'
  } catch (e) {
    if (mine !== seq) return 'stale'
    surface(e)
    return 'failed'
  }
}

/** Spec 4.2: draft (or scheduled) vs live when both exist; else the live version; else a hint. */
async function applyDefault() {
  const working = state.workingCopy
  const published = state.published
  checked.value = []
  if (working && published) {
    const result = await showDiff(published.version, working.version)
    if (result === 'ok') checked.value = [published.version, working.version]
    else if (result === 'failed') showPublished()
    return
  }
  seq++
  showPublished()
}

// Recomputes the default pane whenever the versions array is replaced; today that only happens through
// reloads this tab must follow (Review Focus 2), and an in-tab list refresh would reset a manual selection.
// The default follows the versions: publishing, creating a draft or discarding reloads them.
watch(
  [() => state.versions, () => state.workingCopy?.version, () => state.published?.version],
  () => void applyDefault(),
  { immediate: true },
)

const selected = computed(() => {
  const p = pane.value
  if (p.kind === 'version') return [p.version.version]
  if (p.kind === 'diff') return [p.before.version, p.after.version]
  return []
})

async function onView(version: number) {
  const item = itemOf(version)
  if (!item) return
  if (item.status === 'DRAFT' || item.status === 'SCHEDULED') {
    emit('open-content')
    return
  }
  const mine = ++seq
  pane.value = { kind: 'loading' }
  try {
    const loaded = await props.admin.loadVersion(version)
    if (mine === seq) pane.value = { kind: 'version', version: loaded }
  } catch (e) {
    if (mine !== seq) return
    surface(e)
    showPublished()
  }
}

async function onCompare() {
  if (checked.value.length !== 2) return
  const [a, b] = checked.value as [number, number]
  if ((await showDiff(a, b)) === 'failed') showPublished()
}
</script>

<template>
  <div class="pm-versions-tab">
    <div class="pm-versions-tab__list">
      <VersionList
        :versions="state.versions"
        :selected="selected"
        :checked="checked"
        :feedback="state.feedback"
        :config-type="configType"
        @view="onView"
        @update:checked="checked = $event"
        @compare="onCompare"
      />
    </div>
    <div class="pm-versions-tab__pane">
      <VersionViewer
        v-if="pane.kind === 'version'"
        :version="pane.version"
        :versions="state.versions"
        action="apply"
        :can-edit="canEdit"
        :busy="state.busy"
        :conflict="state.conflict"
        @apply="emit('apply', $event)"
        @reload="emit('reload')"
      />
      <VersionDiff
        v-else-if="pane.kind === 'diff'"
        :before="pane.before"
        :after="pane.after"
        :name-diff="pane.nameDiff"
        :content-diff="pane.contentDiff"
      />
      <section v-else-if="pane.kind === 'loading'" class="pm-versions-tab__empty">載入中…</section>
      <section v-else class="pm-versions-tab__empty">尚未發布過，草稿請在內容分頁編輯</section>
    </div>
  </div>
</template>

<style scoped>
.pm-versions-tab {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  align-items: start;
}

.pm-versions-tab__list {
  padding: var(--space-3);
  border-right: 1px solid var(--color-border);
}

.pm-versions-tab__pane {
  min-width: 0;
}

.pm-versions-tab__empty {
  padding: var(--space-8);
  text-align: center;
  color: var(--color-text-muted);
}

@media (max-width: 1100px) {
  .pm-versions-tab {
    grid-template-columns: 1fr;
  }
  .pm-versions-tab__list {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }
}
</style>
