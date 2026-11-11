<script setup lang="ts">
import { computed, ref } from 'vue'
import type { PromptEvaluation } from '@/composables/use-prompt-evaluation'
import { formatTaipeiShort } from '@/utils/taipei-time'
import type { ConfigVersion } from '@/beans/prompt-management-bean'
import EvaluationCaseList from '@/views/maintenance/prompt-management/components/evaluation-case-list.vue'
import EvaluationCasesDialog from '@/views/maintenance/prompt-management/components/evaluation-cases-dialog.vue'
import EvaluationHistory from '@/views/maintenance/prompt-management/components/evaluation-history.vue'
import EvaluationSummary from '@/views/maintenance/prompt-management/components/evaluation-summary.vue'
import EvaluationVerdict, { type VerdictTone } from '@/views/maintenance/prompt-management/components/evaluation-verdict.vue'
import { overallVerdict, verdictSubtitle, versionState } from '@/views/maintenance/prompt-management/evaluation-format'
import '@/assets/styles/prompt-admin.css'

const props = defineProps<{
  /** Lives in the page, so progress survives tab switches. */
  evaluation: PromptEvaluation
  configKey: string
  name: string
  /** The saved working copy; the evaluation runs against it, not against the form. */
  workingCopy: ConfigVersion | null
  /** Unsaved edits in the form */
  dirty: boolean
  canEdit: boolean
  /** The page is saving or publishing */
  busy: boolean
  /** Display name of another key, for "who is evaluating what" */
  nameOf?: (configKey: string) => string
}>()
const emit = defineEmits<{ 'save-and-run': [] }>()

const state = props.evaluation.state

/** The composable has already shown API errors; this only keeps them from becoming unhandled rejections. */
async function quietly(action: () => Promise<unknown>) {
  try {
    await action()
  } catch {
    // already shown by the global handler
  }
}

const historyOpen = ref(false)
const casesOpen = ref(false)

const nameOfKey = (key: string) => (key === props.configKey ? props.name : (props.nameOf?.(key) ?? key))

const latestCompleted = computed(() => state.history.find((r) => r.status === 'COMPLETED'))
const vState = computed(() => versionState(latestCompleted.value, props.workingCopy))
const run = computed(() => state.run)
const caseCount = computed(() => state.cases?.cases.length ?? state.run?.totalCases ?? state.current?.totalCases ?? null)

/** This config's own running run is the latest one: its case list follows it, so there is nothing to go back to. */
const viewingOlder = computed(
  () =>
    !!run.value && state.history.length > 0 && state.current?.configKey !== props.configKey && run.value.runId !== state.history[0]!.runId,
)

const VERDICT_TITLE = {
  better: '草稿比目前生效的版本好',
  worse: '草稿有指標變差',
  same: '草稿和目前生效的版本差不多',
} as const

type Action = 'run' | 'save-and-run'
interface Card {
  tone: VerdictTone
  title: string
  subtitle: string
  progress?: { completed: number; total: number }
  button?: { label: string; action: Action; primary?: boolean; disabled?: boolean }
}

/** The run on screen failed on the saved content as it is now; an older failure does not count. */
const failedOnSavedContent = computed(() => {
  const r = run.value
  const wc = props.workingCopy
  return !!r && !!wc && r.status === 'FAILED' && r.candidateVersion === wc.version && r.candidateDocVersion === wc.docVersion
})

/** First matching state wins (design 5.1). */
const card = computed<Card>(() => {
  const cur = state.current
  if (cur && cur.configKey === props.configKey) {
    return {
      tone: 'neutral',
      title: `評測中 ${cur.completedCases}/${cur.totalCases}`,
      subtitle: '',
      progress: { completed: cur.completedCases, total: cur.totalCases },
    }
  }
  if (cur) {
    const prefix = cur.triggeredBy ? `${cur.triggeredBy} ` : ''
    return {
      tone: 'neutral',
      title: `${prefix}正在評測「${nameOfKey(cur.configKey)}」（${cur.completedCases}/${cur.totalCases}），完成後才能執行`,
      subtitle: '',
    }
  }
  if (!props.workingCopy) {
    const s = latestCompleted.value?.summary
    const verdict = s ? overallVerdict(s) : null
    return {
      tone: verdict ?? 'neutral',
      title: verdict ? VERDICT_TITLE[verdict] : '還沒有評測紀錄',
      subtitle: '評測會比較草稿和目前生效的版本，請先在內容分頁按「編輯」',
    }
  }
  if (props.dirty) {
    return {
      tone: 'neutral',
      title: '有未儲存的修改，評測只會用已儲存的內容',
      subtitle: '',
      button: { label: '儲存並評測', action: 'save-and-run', disabled: props.busy },
    }
  }
  if (failedOnSavedContent.value) {
    return {
      tone: 'neutral',
      title: '這次評測沒有完成，已完成的題目仍列在下方',
      subtitle: '',
      button: { label: '重新評測', action: 'run', disabled: state.starting },
    }
  }
  if (vState.value === 'not-evaluated') {
    const n = caseCount.value === null ? '固定題目' : ` ${caseCount.value} 題`
    return {
      tone: 'neutral',
      title: '這份草稿還沒評測',
      subtitle: `會用${n}分別問目前生效的版本和草稿，比較兩者的回答，約需 2–3 分鐘。`,
      button: { label: '執行評測', action: 'run', primary: true, disabled: state.starting },
    }
  }
  const rerun = { label: '重新評測', action: 'run' as const, disabled: state.starting }
  const s = latestCompleted.value?.summary
  if (vState.value === 'modified') {
    return { tone: 'neutral', title: '評測後內容已修改，結果可能不準', subtitle: '', button: rerun }
  }
  if (!s) return { tone: 'neutral', title: '這次評測沒有可比較的結果', subtitle: '', button: rerun }
  const verdict = overallVerdict(s)
  return { tone: verdict, title: VERDICT_TITLE[verdict], subtitle: verdictSubtitle(s), button: rerun }
})

function onButton(action: Action) {
  if (action === 'save-and-run') {
    emit('save-and-run')
    return
  }
  const wc = props.workingCopy
  if (wc) void quietly(() => props.evaluation.start(wc.docVersion))
}

function onOpenCases() {
  casesOpen.value = true
  void quietly(() => props.evaluation.loadCases())
}

const backToLatest = () => quietly(() => props.evaluation.open(state.history[0]!.runId))
</script>

<template>
  <section class="pm-eval">
    <div class="pm-editor__body">
      <p v-if="viewingOlder && run" class="ev-viewing">
        正在看 {{ formatTaipeiShort(run.startedAt) }} 的結果・<button class="btn btn--ghost" type="button" @click="backToLatest">回到最新</button>
      </p>

      <evaluation-verdict :tone="card.tone" :title="card.title" :subtitle="card.subtitle" :progress="card.progress">
        <button
          v-if="canEdit && card.button"
          class="btn"
          :class="card.button.primary ? 'btn--primary' : 'btn--secondary'"
          type="button"
          :disabled="card.button.disabled"
          @click="onButton(card.button.action)"
        >
          {{ card.button.label }}
        </button>
      </evaluation-verdict>

      <template v-if="run">
        <details v-if="run.summary" class="ev-metrics">
          <summary>全部指標</summary>
          <evaluation-summary :summary="run.summary" />
        </details>
        <h3 class="ev-h">題目</h3>
        <evaluation-case-list :key="run.runId" :cases="run.cases" :name-of="nameOfKey" />
      </template>

      <div class="ev-links">
        <button class="btn btn--ghost" type="button" :aria-expanded="historyOpen" @click="historyOpen = !historyOpen">
          評測歷程（{{ state.history.length }}）
        </button>
        <button class="btn btn--ghost" type="button" @click="onOpenCases">
          題庫{{ caseCount === null ? '' : `（${caseCount} 題）` }}
        </button>
      </div>

      <evaluation-history
        v-if="historyOpen && state.history.length"
        :runs="state.history"
        :selected-id="run?.runId ?? null"
        @select="(id: string) => quietly(() => evaluation.open(id))"
      />
    </div>

    <evaluation-cases-dialog :open="casesOpen" :cases="state.cases" :name-of="nameOfKey" @close="casesOpen = false" />
  </section>
</template>

<style scoped>
.pm-eval {
  min-width: 0;
}
.ev-viewing {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}
.ev-metrics > summary {
  cursor: pointer;
  font-weight: 700;
  color: var(--color-text-strong);
}
.ev-h {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text-strong);
}
.ev-links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}
</style>
