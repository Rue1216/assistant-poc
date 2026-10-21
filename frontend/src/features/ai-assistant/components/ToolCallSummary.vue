<script setup lang="ts">
import { computed } from 'vue'
import type { ToolCallStatus, ToolCallSummary as ToolCall } from '../types'

const props = defineProps<{ toolCalls: ToolCall[] }>()

const STATUS: Record<ToolCallStatus, { label: string; cls: string }> = {
  SUCCESS: { label: '成功', cls: 'ok' },
  ERROR: { label: '失敗', cls: 'error' },
  DENIED: { label: '無權限', cls: 'denied' },
  TIMEOUT: { label: '逾時', cls: 'timeout' },
}

function duration(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)} s` : `${ms} ms`
}

const total = computed(() => duration(props.toolCalls.reduce((sum, t) => sum + t.durationMs, 0)))
</script>

<template>
  <details class="ai-tools">
    <summary>
      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.4-.6-.6-2.4 2.5-2.5z" />
      </svg>
      使用 {{ toolCalls.length }} 個工具 · {{ total }}
    </summary>
    <ul class="ai-tools__list">
      <li v-for="(t, i) in toolCalls" :key="i">
        <span class="ai-tools__purpose">{{ t.purpose }}</span>
        <code>{{ t.name }}</code>
        <span class="ai-status" :class="`ai-status--${STATUS[t.status]?.cls ?? 'error'}`">{{ STATUS[t.status]?.label ?? t.status }}</span>
        <span class="ai-tools__ms">{{ duration(t.durationMs) }}</span>
      </li>
    </ul>
  </details>
</template>

<style scoped>
.ai-tools summary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  color: var(--color-text-muted);
  cursor: pointer;
  list-style: none;
  user-select: none;
}
.ai-tools summary::-webkit-details-marker {
  display: none;
}
.ai-tools summary::after {
  content: '';
  width: 6px;
  height: 6px;
  margin-left: auto;
  border-right: 1.5px solid currentColor;
  border-bottom: 1.5px solid currentColor;
  transform: rotate(45deg) translateY(-2px);
  transition: transform var(--duration-fast);
}
.ai-tools[open] summary::after {
  transform: rotate(-135deg) translateY(-1px);
}
.ai-tools summary:hover {
  color: var(--color-text);
}
.ai-tools__list {
  list-style: none;
  padding: 0 10px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ai-tools__list li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  grid-template-areas:
    'purpose status ms'
    'code    code   code';
  align-items: center;
  gap: 0 8px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  background: var(--gray-25);
}
.ai-tools__purpose {
  grid-area: purpose;
  color: var(--color-text);
}
.ai-tools__list code {
  grid-area: code;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--color-text-subtle);
}
.ai-tools__ms {
  grid-area: ms;
  min-width: 48px;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-subtle);
}
.ai-status {
  grid-area: status;
  padding: 1px 7px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 600;
}
.ai-status--ok { background: var(--green-50); color: var(--green-700); }
.ai-status--error { background: var(--red-50); color: var(--red-700); }
.ai-status--denied,
.ai-status--timeout { background: var(--amber-50); color: var(--amber-700); }
</style>
