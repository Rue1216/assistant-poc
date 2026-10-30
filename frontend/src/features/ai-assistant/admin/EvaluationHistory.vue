<script setup lang="ts">
import { formatTaipeiShort } from '../taipeiTime'
import type { EvalRunSummary } from '../types'
import { formatRate } from './evaluationFormat'

defineProps<{ runs: EvalRunSummary[]; selectedId: string | null }>()
defineEmits<{ select: [runId: string] }>()

const FAILURE: Record<string, string> = {
  AI_EVAL_TIMEOUT: '逾時',
  AI_EVAL_INTERRUPTED: '已中斷',
  AI_EVAL_LLM_UNAVAILABLE: '模型無法使用',
}

function outcome(r: EvalRunSummary): string {
  if (r.status === 'FAILED') return FAILURE[r.errorCode ?? ''] ?? '系統錯誤'
  if (r.status === 'RUNNING' || !r.summary) return `執行中 ${r.completedCases}/${r.totalCases}`
  const { baseline: b, candidate: c } = r.summary
  return `${formatRate(b.correct, b.total)} → ${formatRate(c.correct, c.total)}`
}
</script>

<template>
  <ul class="ev-hist">
    <li v-for="r in runs" :key="r.runId">
      <button
        class="ev-hist__row"
        type="button"
        :aria-current="r.runId === selectedId ? 'true' : undefined"
        @click="$emit('select', r.runId)"
      >
        <span class="ev-hist__time">{{ formatTaipeiShort(r.startedAt) }}</span>
        <span class="ev-hist__by">{{ r.triggeredBy }}</span>
        <span class="ev-hist__outcome" :class="{ 'is-failed': r.status === 'FAILED' }">{{ outcome(r) }}</span>
        <span v-if="r.datasetChanged" class="badge badge--scheduled">題目已變更</span>
      </button>
    </li>
  </ul>
</template>

<style scoped>
.ev-hist {
  list-style: none;
  margin: 0;
  padding: 0;
}
.ev-hist__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 14px;
  width: 100%;
  padding: 8px 12px;
  border: 0;
  border-bottom: 1px solid var(--color-border);
  background: transparent;
  font: inherit;
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
  color: var(--color-text);
}
.ev-hist__row:hover {
  background: var(--gray-25);
}
.ev-hist__row[aria-current='true'] {
  background: var(--color-primary-subtle);
  box-shadow: inset 3px 0 0 var(--color-primary);
}
.ev-hist__time {
  font-variant-numeric: tabular-nums;
  color: var(--color-text-muted);
}
.ev-hist__outcome.is-failed {
  color: var(--red-600);
}
</style>
