<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { formatTaipeiShort, formatTaipeiShortSeconds } from '../taipeiTime'
import type { ConfigStatus, VersionFeedback, VersionHistoryItem } from '../types'
import { APPLICABLE, STATUS_LABEL, STATUS_VERB } from './promptLabels'

const props = defineProps<{
  versions: VersionHistoryItem[]
  /** Rows shown on the right right now (highlighted). */
  activeVersions: number[]
  canEdit: boolean
  busy: boolean
  /** Thumbs counts by version; shown on published and archived rows only. */
  feedback: Record<number, VersionFeedback>
}>()
const emit = defineEmits<{
  view: [version: number]
  /** older first */
  compare: [before: number, after: number]
  apply: [version: number]
}>()

const open = ref(true)
const showDiscarded = ref(false)
const checked = ref<number[]>([])

const sorted = computed(() => [...props.versions].sort((a, b) => b.version - a.version))
const discardedCount = computed(() => sorted.value.filter((v) => v.status === 'DISCARDED').length)
const rows = computed(() => sorted.value.filter((v) => showDiscarded.value || v.status !== 'DISCARDED'))

// drop selections that disappeared (list refreshed) or were hidden with the discarded rows
watch(rows, (visible) => {
  checked.value = checked.value.filter((v) => visible.some((r) => r.version === v))
})

/** Rows (visible ones) whose minute is shared with another row show seconds as well. */
const crowdedMinutes = computed(() => {
  const seen = new Set<string>()
  const repeated = new Set<string>()
  for (const row of rows.value) {
    const minute = formatTaipeiShort(row.updatedAt)
    if (seen.has(minute)) repeated.add(minute)
    seen.add(minute)
  }
  return repeated
})

function timeOf(row: VersionHistoryItem): string {
  return crowdedMinutes.value.has(formatTaipeiShort(row.updatedAt))
    ? formatTaipeiShortSeconds(row.updatedAt)
    : formatTaipeiShort(row.updatedAt)
}

/** `basedOnVersion` is shown by the time of that version, never by its number. */
function appliedFrom(row: VersionHistoryItem): string | null {
  if (row.basedOnVersion == null) return null
  const source = props.versions.find((v) => v.version === row.basedOnVersion)
  return source ? `套用自 ${formatTaipeiShort(source.updatedAt)} 的版本` : '套用自先前的版本'
}

function label(row: VersionHistoryItem): string {
  return `${STATUS_LABEL[row.status]}，${timeOf(row)}`
}

const FEEDBACK_STATUSES: ConfigStatus[] = ['PUBLISHED', 'ARCHIVED']

/** 「👍 12・👎 3（80%）」 for published and archived rows that have feedback. */
function feedbackText(row: VersionHistoryItem): string | null {
  const f = props.feedback[row.version]
  if (!f || !FEEDBACK_STATUSES.includes(row.status)) return null
  const total = f.up + f.down
  if (total === 0) return null
  return `👍 ${f.up}・👎 ${f.down}（${Math.round((f.up / total) * 100)}%）`
}

const showFeedbackNote = computed(() => rows.value.some((row) => feedbackText(row) !== null))

/** Not v-model with :value, so the internal version number never lands in a DOM attribute. */
function toggle(version: number, on: boolean) {
  checked.value = on ? [...checked.value, version] : checked.value.filter((v) => v !== version)
}

function onCompare() {
  if (checked.value.length !== 2) return
  const [a, b] = [...checked.value].sort((x, y) => x - y) as [number, number]
  emit('compare', a, b)
}
</script>

<template>
  <details class="pm-item__history" :open="open" @toggle="open = ($event.target as HTMLDetailsElement).open">
    <summary class="pm-item__history-btn" title="版本歷程" :aria-label="`版本歷程（${versions.length} 個版本）`">
      <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 12a9 9 0 1 0 3-6.7" /><path d="M3 4v5h5" /><path d="M12 7v5l3 2" /></svg>
      <span>{{ versions.length }}</span>
    </summary>
    <ol class="pm-versions">
      <li
        v-for="row in rows"
        :key="row.version"
        class="pm-ver"
        :class="[`is-${row.status.toLowerCase()}`, { 'is-current': activeVersions.includes(row.version) }]"
      >
        <input
          class="pm-ver__check"
          type="checkbox"
          :checked="checked.includes(row.version)"
          :aria-label="`選取「${label(row)}」以比較`"
          @change="toggle(row.version, ($event.target as HTMLInputElement).checked)"
        />
        <button class="pm-ver__main" type="button" @click="emit('view', row.version)">
          <span class="pm-ver__top">
            <span class="pm-ver__status">{{ STATUS_LABEL[row.status] }}</span>
            <span class="pm-ver__time">{{ timeOf(row) }}</span>
          </span>
          <span class="pm-ver__by">{{ row.updatedBy }} {{ STATUS_VERB[row.status] }}</span>
          <span v-if="row.status === 'SCHEDULED' && row.scheduledAt" class="pm-ver__extra">
            預約 {{ formatTaipeiShort(row.scheduledAt) }} 發布
          </span>
          <template v-if="row.status === 'DISCARDED'">
            <span class="pm-ver__extra">原作者 {{ row.createdBy }}</span>
            <span v-if="row.scheduledAt" class="pm-ver__extra">原預約 {{ formatTaipeiShort(row.scheduledAt) }}</span>
          </template>
          <span v-if="feedbackText(row)" class="pm-ver__feedback">{{ feedbackText(row) }}</span>
          <span v-if="row.changeNote" class="pm-ver__note">{{ row.changeNote }}</span>
          <span v-if="appliedFrom(row)" class="pm-ver__from">{{ appliedFrom(row) }}</span>
        </button>
        <button
          v-if="canEdit && APPLICABLE.includes(row.status)"
          class="pm-ver__apply"
          type="button"
          :disabled="busy"
          @click="emit('apply', row.version)"
        >
          套用
        </button>
      </li>
    </ol>
    <p v-if="showFeedbackNote" class="pm-versions__note">回饋統計涵蓋 180 天內的回答</p>
    <div v-if="versions.length > 1" class="pm-versions__foot">
      <label v-if="discardedCount" class="pm-versions__toggle">
        <input v-model="showDiscarded" type="checkbox" /> 顯示已捨棄的版本（{{ discardedCount }}）
      </label>
      <button
        class="btn btn--secondary btn--sm pm-versions__compare"
        type="button"
        :disabled="checked.length !== 2"
        @click="onCompare"
      >
        比較所選的兩個版本
      </button>
      <span class="pm-versions__hint">勾選任兩個版本後比較</span>
    </div>
  </details>
</template>

<style scoped>
.pm-item__history-btn {
  position: absolute;
  top: 54px;
  right: 8px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  height: 24px;
  padding: 0 7px;
  border-radius: var(--radius-sm);
  color: var(--color-text-muted);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  list-style: none;
  user-select: none;
  transition: background var(--duration-fast), color var(--duration-fast);
}

.pm-item__history-btn::-webkit-details-marker {
  display: none;
}

.pm-item__history-btn:hover {
  background: var(--gray-100);
  color: var(--color-text);
}

.pm-item__history[open] .pm-item__history-btn {
  background: var(--green-100);
  color: var(--green-700);
}

.pm-versions {
  list-style: none;
  margin: 0 8px;
  padding: 6px 0 4px;
  border-top: 1px dashed var(--color-border-strong);
}

/* 每列：勾選（比較用） ｜ 狀態・時間・操作者・修改說明 ｜ 套用 */
.pm-ver {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr) auto;
  align-items: start;
  gap: 8px;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  color: var(--color-text);
}

.pm-ver:hover {
  background: var(--color-surface);
}

.pm-ver.is-current {
  background: var(--color-surface);
  box-shadow: inset 0 0 0 1px var(--green-300), var(--shadow-sm);
}

.pm-ver__check {
  width: 14px;
  height: 14px;
  margin: 2px 0 0;
  accent-color: var(--color-primary);
  cursor: pointer;
}

.pm-ver__main {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  font-size: inherit;
  text-align: left;
  cursor: pointer;
}

.pm-ver__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}

.pm-ver__status {
  font-weight: 600;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.pm-ver.is-published .pm-ver__status { color: var(--green-700); }
.pm-ver.is-scheduled .pm-ver__status { color: var(--amber-700); }
.pm-ver.is-draft .pm-ver__status { color: var(--gray-700); }
.pm-ver.is-discarded .pm-ver__status { color: var(--gray-400); }

.pm-ver__time {
  color: var(--color-text-subtle);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.pm-ver__by,
.pm-ver__extra,
.pm-ver__note,
.pm-ver__from {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pm-ver__by,
.pm-ver__extra {
  color: var(--color-text-muted);
}

.pm-ver__note {
  color: var(--color-text-subtle);
}

.pm-ver__feedback {
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.pm-versions__note {
  margin: 0 8px;
  padding: 0 8px 4px;
  font-size: 11px;
  color: var(--color-text-subtle);
}

.pm-ver__from {
  color: var(--blue-700);
}

.pm-ver.is-discarded .pm-ver__main {
  opacity: 0.85;
}

.pm-ver__apply {
  align-self: center;
  padding: 1px 8px;
  border: 1px solid var(--green-200);
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  color: var(--green-700);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
}

.pm-ver__apply:hover {
  background: var(--green-50);
}

.pm-ver__apply:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pm-versions__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  margin: 0 8px;
  padding: 6px 8px 10px;
  font-size: 11px;
  color: var(--color-text-subtle);
}

.pm-versions__toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-basis: 100%;
  color: var(--color-text-muted);
  cursor: pointer;
}

.pm-versions__toggle input {
  width: 13px;
  height: 13px;
  margin: 0;
  accent-color: var(--color-primary);
}

.pm-versions__hint {
  flex: 1;
  min-width: 0;
}
</style>
