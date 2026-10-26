<script setup lang="ts">
import { computed, onMounted, ref, toRef } from 'vue'
import { usePromptEvaluation } from '../composables/usePromptEvaluation'
import { formatTaipeiShort } from '../taipeiTime'
import type { ConfigVersion } from '../types'
import EvaluationCaseList from './EvaluationCaseList.vue'
import EvaluationCasesDialog from './EvaluationCasesDialog.vue'
import EvaluationHistory from './EvaluationHistory.vue'
import EvaluationSummary from './EvaluationSummary.vue'
import { versionState } from './evaluationFormat'
import './prompt-admin.css'

const props = defineProps<{
  configKey: string
  name: string
  /** The saved working copy; the evaluation runs against it, not against the form. */
  workingCopy: ConfigVersion | null
  /** Unsaved edits in the form */
  dirty: boolean
  canEdit: boolean
  backLabel: string
  /** Display name of another key, for "who is evaluating what" */
  nameOf?: (configKey: string) => string
}>()
const emit = defineEmits<{ back: [] }>()

const evaluation = usePromptEvaluation(toRef(props, 'configKey'))
const state = evaluation.state

/** The composable has already shown API errors; this only keeps them from becoming unhandled rejections. */
const swallow = () => {}

onMounted(() => void evaluation.refresh().catch(swallow))

const casesOpen = ref(false)

const latestCompleted = computed(() => state.history.find((r) => r.status === 'COMPLETED'))
const vState = computed(() => versionState(latestCompleted.value, props.workingCopy))
const VERSION_TEXT = { none: '', 'not-evaluated': '尚未評測', evaluated: '已評測', modified: '評測後內容已修改' } as const

const runningHere = computed(() => !!state.current && state.current.configKey === props.configKey)

/** Why the run button is off, in the order the user can fix them. */
const blocked = computed(() => {
  if (!props.workingCopy) return '沒有編輯中的版本'
  if (props.dirty) return '請先儲存，評測使用已儲存的內容'
  const c = state.current
  if (c) {
    const name = c.configKey === props.configKey ? props.name : (props.nameOf?.(c.configKey) ?? c.configKey)
    return `${c.triggeredBy ? c.triggeredBy + ' ' : ''}正在評測「${name}」（${c.completedCases}/${c.totalCases}）`
  }
  return ''
})

const disabled = computed(() => !!blocked.value || state.starting)

async function onRun() {
  const wc = props.workingCopy
  if (!wc || disabled.value) return
  try {
    await evaluation.start(wc.docVersion)
  } catch {
    // already shown by the global handler
  }
}

async function onSelect(runId: string) {
  try {
    await evaluation.open(runId)
  } catch {
    // already shown by the global handler
  }
}

async function onOpenCases() {
  casesOpen.value = true
  try {
    await evaluation.loadCases()
  } catch {
    // already shown by the global handler
  }
}

const caseCount = computed(() => state.cases?.cases.length ?? state.run?.totalCases ?? state.current?.totalCases ?? null)

const run = computed(() => state.run)
const progress = computed(() => state.current)
</script>

<template>
  <section class="pm-eval card" aria-labelledby="ev-title">
    <header class="pm-editor__head">
      <div class="pm-editor__title-row">
        <h2 id="ev-title" class="pm-editor__title">評測：{{ name }}</h2>
        <button class="btn btn--secondary btn--sm" type="button" @click="emit('back')">{{ backLabel }}</button>
      </div>
    </header>

    <div class="pm-editor__body">
      <div class="ev-actions">
        <button v-if="canEdit" class="btn btn--primary" type="button" :disabled="disabled" @click="onRun">執行評測</button>
        <button class="btn btn--ghost" type="button" @click="onOpenCases">
          查看題目{{ caseCount === null ? '' : `（${caseCount}）` }}
        </button>
      </div>
      <p v-if="canEdit && blocked" class="ev-hint" role="status">{{ blocked }}</p>
      <p v-if="workingCopy" class="ev-state">
        編輯中版本（{{ formatTaipeiShort(workingCopy.updatedAt) }} 儲存）：{{ VERSION_TEXT[vState] }}
      </p>

      <div v-if="runningHere && progress" class="ev-progress">
        <span>執行中 {{ progress.completedCases }}/{{ progress.totalCases }}</span>
        <div
          class="ev-progress__bar"
          role="progressbar"
          aria-label="評測進度"
          aria-valuemin="0"
          :aria-valuemax="progress.totalCases"
          :aria-valuenow="progress.completedCases"
        >
          <div
            class="ev-progress__fill"
            :style="{ width: progress.totalCases ? `${(progress.completedCases / progress.totalCases) * 100}%` : '0%' }"
          ></div>
        </div>
      </div>

      <template v-if="run">
        <h3 class="ev-h">最近一次結果 {{ formatTaipeiShort(run.startedAt) }} · {{ run.triggeredBy }}</h3>
        <EvaluationSummary v-if="run.summary" :summary="run.summary" />
        <p v-else-if="run.status === 'FAILED'" class="ev-hint">這次評測沒有完成，已完成的題目仍列在下方。</p>
        <h3 class="ev-h">題目</h3>
        <EvaluationCaseList :key="run.runId" :cases="run.cases" />
      </template>
      <p v-else class="ev-hint">還沒有評測紀錄。</p>

      <template v-if="state.history.length">
        <h3 class="ev-h">評測歷程</h3>
        <EvaluationHistory :runs="state.history" :selected-id="run?.runId ?? null" @select="onSelect" />
      </template>
    </div>

    <EvaluationCasesDialog :open="casesOpen" :cases="state.cases" @close="casesOpen = false" />
  </section>
</template>

<style scoped>
.pm-eval {
  min-width: 0;
}
.ev-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}
.ev-hint {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}
.ev-state {
  font-size: var(--text-sm);
  color: var(--color-text);
}
.ev-h {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text-strong);
}
.ev-progress {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: var(--text-sm);
}
.ev-progress__bar {
  height: 8px;
  border-radius: 4px;
  background: var(--gray-100);
  overflow: hidden;
}
.ev-progress__fill {
  height: 100%;
  background: var(--color-primary);
  transition: width 0.3s;
}
</style>
