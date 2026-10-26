<script setup lang="ts">
import { computed } from 'vue'
import type { EvalSummary } from '../types'
import { formatRate, rateDirection, tokenChange, tokenDirection, type Direction } from './evaluationFormat'

const props = defineProps<{ summary: EvalSummary }>()

interface Row {
  label: string
  base: string
  cand: string
  direction: Direction
  /** token row only */
  change?: string
}

const count = (n: number, d: number) => (d === 0 ? '—' : `${formatRate(n, d)}（${n}/${d}）`)

const rows = computed<Row[]>(() => {
  const b = props.summary.baseline
  const c = props.summary.candidate
  const tokens = (n: number) => Math.round(n).toLocaleString('en-US')
  return [
    {
      label: '正確率',
      base: count(b.correct, b.total),
      cand: count(c.correct, c.total),
      direction: rateDirection(b.correct, c.correct, b.total, true),
    },
    {
      label: '工具選擇正確率',
      base: count(b.toolsOk, b.total),
      cand: count(c.toolsOk, c.total),
      direction: rateDirection(b.toolsOk, c.toolsOk, b.total, true),
    },
    {
      label: '拒答率（應拒答題）',
      base: count(b.refusedExpected, b.expectedRefusals),
      cand: count(c.refusedExpected, c.expectedRefusals),
      direction: rateDirection(b.refusedExpected, c.refusedExpected, b.expectedRefusals, true),
    },
    {
      label: '拒答率（一般題）',
      base: count(b.refusedNormal, b.normalCases),
      cand: count(c.refusedNormal, c.normalCases),
      direction: rateDirection(b.refusedNormal, c.refusedNormal, b.normalCases, false),
    },
    {
      label: '平均 Token',
      base: tokens(b.avgTotalTokens),
      cand: tokens(c.avgTotalTokens),
      direction: tokenDirection(b.avgTotalTokens, c.avgTotalTokens),
      change: tokenChange(b.avgTotalTokens, c.avgTotalTokens),
    },
  ]
})

const MARK: Record<Direction, string> = { better: '▲', worse: '▼', same: '' }
const LABEL: Record<Direction, string> = { better: '變好', worse: '變差', same: '' }
</script>

<template>
  <div class="ev-summary">
    <table class="ev-table">
      <thead>
        <tr>
          <th scope="col"><span class="sr-only">指標</span></th>
          <th scope="col">已發布版本</th>
          <th scope="col">編輯中版本</th>
          <th scope="col"><span class="sr-only">差異</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.label">
          <th scope="row">{{ r.label }}</th>
          <td>{{ r.base }}</td>
          <td>{{ r.cand }}</td>
          <td>
            <span
              v-if="r.direction !== 'same'"
              class="ev-dir"
              :class="`is-${r.direction}`"
              role="img"
              :aria-label="LABEL[r.direction]"
              >{{ MARK[r.direction] }}</span
            >
            <span v-if="r.change" class="ev-change">{{ r.change }}</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p class="ev-note">差異在 1 題以內可能是模型隨機性</p>
  </div>
</template>

<style scoped>
.ev-table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-sm);
}
.ev-table th,
.ev-table td {
  padding: 8px 12px;
  text-align: left;
  border-bottom: 1px solid var(--color-border);
}
.ev-table thead th {
  font-weight: 600;
  color: var(--color-text-muted);
}
.ev-table tbody th {
  font-weight: 500;
  color: var(--color-text-strong);
}
.ev-dir {
  font-weight: 700;
}
.ev-dir.is-better {
  color: var(--green-700);
}
.ev-dir.is-worse {
  color: var(--red-600);
}
.ev-change {
  margin-left: 6px;
  color: var(--color-text-muted);
}
.ev-note {
  margin-top: 8px;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
</style>
