<script setup lang="ts">
import { computed } from 'vue'
import type { EvalSummary } from '@/beans/prompt-management-bean'
import { changeText, formatRate, metricRows, tokenRow, type Direction } from '@/views/maintenance/prompt-management/evaluation-format'

const props = defineProps<{ summary: EvalSummary }>()

const count = (n: number, d: number) => (d === 0 ? '—' : `${formatRate(n, d)}（${n}/${d}）`)
const tokens = (n: number) => Math.round(n).toLocaleString('en-US')
const colour = (d: Direction) => (d === 'same' ? '' : `is-${d}`)

const rows = computed(() =>
  metricRows(props.summary).map((r) => ({
    label: r.label,
    base: count(r.base, r.d),
    cand: count(r.cand, r.d),
    change: changeText(r),
    direction: r.direction,
    lowerIsBetter: r.lowerIsBetter,
  })),
)

const token = computed(() => {
  const t = tokenRow(props.summary)
  let change = '持平'
  if (t.direction !== 'same' && t.percent !== null) {
    change = t.percent > 0 ? `多 ${t.percent}%` : `少 ${Math.abs(t.percent)}%`
  }
  return { base: tokens(t.base), cand: tokens(t.cand), change, direction: t.direction }
})
</script>

<template>
  <div class="ev-summary">
    <table class="ev-table">
      <thead>
        <tr>
          <th scope="col">指標</th>
          <th scope="col">目前生效</th>
          <th scope="col">草稿</th>
          <th scope="col">變化</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.label">
          <th scope="row">
            {{ r.label }}<small v-if="r.lowerIsBetter" class="ev-hint">越低越好</small>
          </th>
          <td>{{ r.base }}</td>
          <td>{{ r.cand }}</td>
          <td>
            <span :class="colour(r.direction)">{{ r.change }}</span>
          </td>
        </tr>
        <tr>
          <th scope="row">平均 Token<small class="ev-hint">越低越好</small></th>
          <td>{{ token.base }}</td>
          <td>{{ token.cand }}</td>
          <td>
            <span :class="colour(token.direction)">{{ token.change }}</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p class="ev-note">差 1 題以內標為持平，可能只是模型隨機性。</p>
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
.is-better {
  font-weight: 600;
  color: var(--green-700);
}
.is-worse {
  font-weight: 600;
  color: var(--red-600);
}
.ev-hint {
  margin-left: 6px;
  font-size: var(--text-xs);
  font-weight: 400;
  color: var(--color-text-muted);
}
.ev-note {
  margin-top: 8px;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}
</style>
