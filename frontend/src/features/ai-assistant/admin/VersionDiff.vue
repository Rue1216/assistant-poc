<script setup lang="ts">
import { computed, useId } from 'vue'
import type { DiffLine } from '../markdown/lineDiff'
import { formatTaipeiShort } from '../taipeiTime'
import type { VersionHistoryItem } from '../types'
import { diffRows, diffStats } from './diffRows'
import { STATUS_BADGE, STATUS_SHORT } from './promptLabels'
import './prompt-admin.css'

const props = defineProps<{
  configKey: string
  name: string
  /** the older version (left, "−") */
  before: VersionHistoryItem
  /** the newer version (right, "+") */
  after: VersionHistoryItem
  nameDiff: DiffLine[]
  contentDiff: DiffLine[]
}>()

const id = useId()
const nameRows = computed(() => diffRows(props.nameDiff))
const contentRows = computed(() => diffRows(props.contentDiff))
const nameStats = computed(() => diffStats(props.nameDiff))
const contentStats = computed(() => diffStats(props.contentDiff))
const nameChanged = computed(() => nameStats.value.added + nameStats.value.removed > 0)
const contentChanged = computed(() => contentStats.value.added + contentStats.value.removed > 0)

function sideText(v: VersionHistoryItem): string {
  return v.changeNote ? `${v.updatedBy}・${v.changeNote}` : v.updatedBy
}
</script>

<template>
  <section class="pm-viewer" :aria-labelledby="`${id}-title`">
    <header class="pm-editor__head">
      <div class="pm-editor__title-row">
        <h2 :id="`${id}-title`" class="pm-editor__title">{{ name }}</h2>
        <span class="badge badge--info badge--plain">版本比較</span>
      </div>
      <div class="pm-editor__meta">
        <code>{{ configKey }}</code>
      </div>
    </header>

    <div class="pm-editor__body">
      <div class="pm-compare">
        <div class="pm-compare__side pm-compare__side--del">
          <span class="pm-compare__sign">−</span>
          <div>
            <span class="badge" :class="STATUS_BADGE[before.status]">{{ STATUS_SHORT[before.status] }}</span>
            <strong>{{ formatTaipeiShort(before.updatedAt) }}</strong>
            <span>{{ sideText(before) }}</span>
          </div>
        </div>
        <span class="pm-compare__arrow" aria-hidden="true">→</span>
        <div class="pm-compare__side pm-compare__side--add">
          <span class="pm-compare__sign">+</span>
          <div>
            <span class="badge" :class="STATUS_BADGE[after.status]">{{ STATUS_SHORT[after.status] }}</span>
            <strong>{{ formatTaipeiShort(after.updatedAt) }}</strong>
            <span>{{ sideText(after) }}</span>
          </div>
        </div>
      </div>

      <div v-for="pane in [
        { title: '名稱', rows: nameRows, stats: nameStats, changed: nameChanged },
        { title: '內容', rows: contentRows, stats: contentStats, changed: contentChanged },
      ]" :key="pane.title" class="pm-pane pm-pane--auto">
        <div class="pm-pane__head">
          <span class="pm-pane__title">
            {{ pane.title }}
            <span v-if="pane.changed" class="pm-seg__stat">
              <span class="is-add">+{{ pane.stats.added }}</span> <span class="is-del">−{{ pane.stats.removed }}</span>
            </span>
          </span>
          <span v-if="pane.changed" class="pm-diff-legend">
            <span class="is-del">− 只在 {{ formatTaipeiShort(before.updatedAt) }} 的版本</span>
            <span class="is-add">+ 只在 {{ formatTaipeiShort(after.updatedAt) }} 的版本</span>
          </span>
          <span v-else class="pm-diff-legend">沒有差異</span>
        </div>
        <div class="pm-difflines">
          <template v-for="(row, i) in pane.rows" :key="i">
            <div v-if="row.kind === 'gap'" class="pm-dl pm-dl--gap">⋯ {{ row.count }} 行未變更</div>
            <div
              v-else
              class="pm-dl"
              :class="{ 'pm-dl--eq': row.kind === 'same', 'pm-dl--add': row.kind === 'added', 'pm-dl--del': row.kind === 'removed' }"
            >
              <span class="pm-dl__no">{{ row.oldNo ?? '' }}</span>
              <span class="pm-dl__no">{{ row.newNo ?? '' }}</span>
              <span class="pm-dl__sign">{{ row.kind === 'added' ? '+' : row.kind === 'removed' ? '−' : '' }}</span>
              <span class="pm-dl__text">{{ row.text || ' ' }}</span>
            </div>
          </template>
          <div v-if="!pane.rows.length" class="pm-dl pm-dl--gap">（兩個版本皆為空白）</div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.pm-viewer {
  min-width: 0;
}

.pm-seg__stat {
  margin-left: 6px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
}

.pm-seg__stat .is-add,
.pm-diff-legend .is-add { color: var(--green-700); }
.pm-seg__stat .is-del,
.pm-diff-legend .is-del { color: var(--red-600); }

.pm-diff-legend {
  display: flex;
  gap: 12px;
  font-size: var(--text-xs);
  font-weight: 600;
}

/* ---- 比較：兩個版本 ---- */

.pm-compare {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: stretch;
  gap: var(--space-3);
}

.pm-compare__side {
  display: flex;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
}

.pm-compare__side > div {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 8px;
  min-width: 0;
}

.pm-compare__side > div > span:last-child {
  flex-basis: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.pm-compare__side--del {
  background: var(--red-50);
  border-color: var(--red-200);
}

.pm-compare__side--add {
  background: var(--green-50);
  border-color: var(--green-200);
}

.pm-compare__sign {
  font-family: var(--font-mono);
  font-weight: 700;
}

.pm-compare__side--del .pm-compare__sign { color: var(--red-600); }
.pm-compare__side--add .pm-compare__sign { color: var(--green-700); }

.pm-compare__arrow {
  align-self: center;
  color: var(--color-text-subtle);
}

/* ---- 差異行 ---- */

.pm-difflines {
  max-height: 560px;
  overflow: auto;
  padding: 6px 0;
  font-family: var(--font-mono);
  font-size: 12.5px;
  line-height: 1.75;
}

.pm-dl {
  display: grid;
  grid-template-columns: 36px 36px 18px minmax(0, 1fr);
}

.pm-dl__no {
  padding-right: 8px;
  text-align: right;
  color: var(--gray-400);
  font-size: 11px;
  user-select: none;
}

.pm-dl__sign {
  font-weight: 700;
  user-select: none;
}

.pm-dl__text {
  padding-right: 14px;
  white-space: pre-wrap;
  color: var(--gray-700);
}

.pm-dl--add {
  background: var(--green-50);
}

.pm-dl--add .pm-dl__sign,
.pm-dl--add .pm-dl__text {
  color: var(--green-800);
}

.pm-dl--del {
  background: var(--red-50);
}

.pm-dl--del .pm-dl__sign,
.pm-dl--del .pm-dl__text {
  color: var(--red-700);
}

.pm-dl--gap {
  display: block;
  margin: 4px 0;
  padding: 2px 0;
  background: var(--gray-50);
  font-family: var(--font-sans);
  font-size: var(--text-xs);
  text-align: center;
  color: var(--color-text-subtle);
}

@media (max-width: 1100px) {
  .pm-compare {
    grid-template-columns: 1fr;
  }
  .pm-compare__arrow {
    display: none;
  }
}
</style>
