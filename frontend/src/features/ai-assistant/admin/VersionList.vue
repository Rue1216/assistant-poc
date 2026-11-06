<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { formatTaipeiShort, formatTaipeiShortSeconds } from '../taipeiTime'
import type { ConfigStatus, ConfigType, VersionFeedback, VersionHistoryItem } from '../types'
import ThumbIcon from '../components/ThumbIcon.vue'
import { LIST_STATUS } from './promptLabels'

const props = defineProps<{
  versions: VersionHistoryItem[]
  /** Rows shown on the right right now (highlighted). */
  selected: number[]
  /** Rows ticked for comparison. */
  checked: number[]
  /** Thumbs counts by version; shown on published and archived rows only. */
  feedback: Record<number, VersionFeedback>
  /** Decides the wording of the feedback footnote. */
  configType: ConfigType
}>()
const emit = defineEmits<{
  view: [version: number]
  'update:checked': [versions: number[]]
  compare: []
}>()

const showDiscarded = ref(false)
const tipId = useId()
const tipOpen = ref(false)

const sorted = computed(() => [...props.versions].sort((a, b) => b.version - a.version))
const discardedCount = computed(() => sorted.value.filter((v) => v.status === 'DISCARDED').length)
const rows = computed(() => sorted.value.filter((v) => showDiscarded.value || v.status !== 'DISCARDED'))

// drop selections that disappeared (list refreshed) or were hidden with the discarded rows
watch(rows, (visible) => {
  const kept = props.checked.filter((v) => visible.some((r) => r.version === v))
  if (kept.length !== props.checked.length) emit('update:checked', kept)
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

function label(row: VersionHistoryItem): string {
  return `${LIST_STATUS[row.status]}，${timeOf(row)}`
}

/** Second line: operator, then the change note when there is one. */
function byLine(row: VersionHistoryItem): string {
  return row.changeNote ? `${row.updatedBy}・${row.changeNote}` : row.updatedBy
}

const FEEDBACK_STATUSES: ConfigStatus[] = ['PUBLISHED', 'ARCHIVED']

/** Counts and the like percentage for published and archived rows that have feedback. */
function feedbackOf(row: VersionHistoryItem): { up: number; down: number; percent: number } | null {
  const f = props.feedback[row.version]
  if (!f || !FEEDBACK_STATUSES.includes(row.status)) return null
  const total = f.up + f.down
  if (total === 0) return null
  return { up: f.up, down: f.down, percent: Math.round((f.up / total) * 100) }
}

const showFeedbackNote = computed(() => rows.value.some((row) => feedbackOf(row) !== null))

/** Not v-model with :value, so the internal version number never lands in a DOM attribute. */
function toggle(version: number, on: boolean) {
  emit('update:checked', on ? [...props.checked, version] : props.checked.filter((v) => v !== version))
}

const compareReady = computed(() => props.checked.length === 2)

/** aria-disabled does not stop clicks, so a disabled button is ignored here. */
function onCompare() {
  if (!compareReady.value) return
  emit('compare')
}
</script>

<template>
  <div class="pm-versions-list">
    <ol class="pm-versions">
      <li
        v-for="row in rows"
        :key="row.version"
        class="pm-ver"
        :class="[`is-${row.status.toLowerCase()}`, { 'is-current': selected.includes(row.version) }]"
        :aria-current="selected.includes(row.version) ? 'true' : undefined"
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
            <span class="pm-ver__status">{{ LIST_STATUS[row.status] }}</span>
            <span class="pm-ver__time">{{ timeOf(row) }}</span>
          </span>
          <span class="pm-ver__by">{{ byLine(row) }}</span>
          <span
            v-if="feedbackOf(row)"
            class="pm-ver__feedback"
            role="img"
            :aria-label="`讚 ${feedbackOf(row)!.up}、倒讚 ${feedbackOf(row)!.down}（${feedbackOf(row)!.percent}%）`"
          >
            <ThumbIcon direction="up" :size="12" />{{ feedbackOf(row)!.up }}・<ThumbIcon direction="down" :size="12" />{{ feedbackOf(row)!.down }}（{{ feedbackOf(row)!.percent }}%）
          </span>
        </button>
      </li>
    </ol>
    <p v-if="showFeedbackNote" class="pm-versions__note">
      {{ configType === 'SYSTEM_INSTRUCTION' ? '回饋統計涵蓋 5 年內、且確定使用此分類的回答' : '回饋統計涵蓋 5 年內的回答' }}
    </p>
    <div class="pm-versions__foot">
      <label v-if="discardedCount" class="pm-versions__toggle">
        <input v-model="showDiscarded" type="checkbox" /> 顯示已捨棄（{{ discardedCount }}）
      </label>
      <span class="pm-versions__compare-wrap">
        <button
          class="btn btn--secondary btn--sm pm-versions__compare"
          :class="{ 'is-disabled': !compareReady }"
          type="button"
          :aria-disabled="compareReady ? 'false' : 'true'"
          :aria-describedby="compareReady ? undefined : tipId"
          @click="onCompare"
          @mouseenter="tipOpen = true"
          @mouseleave="tipOpen = false"
          @focus="tipOpen = true"
          @blur="tipOpen = false"
          @keydown.esc="tipOpen = false"
        >
          比較勾選的兩版
        </button>
        <span :id="tipId" v-show="!compareReady && tipOpen" class="pm-versions__tip" role="tooltip">勾選任兩個版本後比較</span>
      </span>
    </div>
  </div>
</template>

<style scoped>
.pm-versions {
  list-style: none;
  margin: 0;
  padding: 0;
}

/* 每列：勾選（比較用） ｜ 狀態・時間／操作者・修改說明 */
.pm-ver {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
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

.pm-ver__by {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-muted);
}

.pm-ver__feedback {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.pm-ver.is-discarded .pm-ver__main {
  opacity: 0.85;
}

.pm-versions__note {
  margin: 0;
  padding: 4px 8px;
  font-size: 11px;
  color: var(--color-text-subtle);
}

.pm-versions__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
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

.pm-versions__compare-wrap {
  position: relative;
  display: inline-flex;
}

/* looks disabled but stays focusable and hoverable, so the tooltip can show */
.pm-versions__compare.is-disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pm-versions__tip {
  position: absolute;
  left: 0;
  bottom: calc(100% + 6px);
  z-index: 10;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  background: var(--gray-900);
  color: var(--color-surface);
  font-size: 11px;
  white-space: nowrap;
  box-shadow: var(--shadow-sm);
  pointer-events: none;
}
</style>
