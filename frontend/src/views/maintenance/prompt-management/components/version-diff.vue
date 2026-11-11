<script setup lang="ts">
import { computed } from 'vue'
import type { DiffLine } from '@/utils/markdown/line-diff'
import { formatTaipeiShort } from '@/utils/taipei-time'
import type { VersionHistoryItem } from '@/beans/prompt-management-bean'
import { diffRows, diffStats } from '@/views/maintenance/prompt-management/diff-rows'
import { LIST_STATUS } from '@/views/maintenance/prompt-management/prompt-labels'
import '@/assets/styles/prompt-admin.css'

const props = defineProps<{
  /** the older version (left, "−") */
  before: VersionHistoryItem
  /** the newer version (right, "+") */
  after: VersionHistoryItem
  nameDiff: DiffLine[]
  contentDiff: DiffLine[]
}>()

const nameRows = computed(() => diffRows(props.nameDiff))
const contentRows = computed(() => diffRows(props.contentDiff))
const nameStats = computed(() => diffStats(props.nameDiff))
const contentStats = computed(() => diffStats(props.contentDiff))
const nameChanged = computed(() => nameStats.value.added + nameStats.value.removed > 0)
const totalAdded = computed(() => nameStats.value.added + contentStats.value.added)
const totalRemoved = computed(() => nameStats.value.removed + contentStats.value.removed)

function label(v: VersionHistoryItem): string {
  return `${LIST_STATUS[v.status]} ${formatTaipeiShort(v.updatedAt)}`
}
</script>

<template>
  <section class="pm-diff">
    <header class="pm-diff__head">
      <span class="is-del">− {{ label(before) }}</span>
      <span class="is-add">＋ {{ label(after) }}</span>
      <span class="pm-diff__stat">＋{{ totalAdded }} −{{ totalRemoved }} 行</span>
    </header>

    <div v-if="nameChanged" class="pm-diff__name">
      <div class="pm-diff__label">名稱</div>
      <div class="pm-difflines">
        <template v-for="(row, i) in nameRows" :key="i">
          <div v-if="row.kind === 'gap'" class="pm-dl pm-dl--gap">⋯ {{ row.count }} 行未變更</div>
          <div
            v-else
            class="pm-dl"
            :class="{ 'pm-dl--eq': row.kind === 'same', 'pm-dl--add': row.kind === 'added', 'pm-dl--del': row.kind === 'removed' }"
          >
            <span class="pm-dl__sign">{{ row.kind === 'added' ? '+' : row.kind === 'removed' ? '−' : '' }}</span>
            <span class="pm-dl__text">{{ row.text || ' ' }}</span>
          </div>
        </template>
      </div>
    </div>
    <p v-else class="pm-diff__same">名稱相同</p>

    <div class="pm-difflines">
      <template v-for="(row, i) in contentRows" :key="i">
        <div v-if="row.kind === 'gap'" class="pm-dl pm-dl--gap">⋯ {{ row.count }} 行未變更</div>
        <div
          v-else
          class="pm-dl"
          :class="{ 'pm-dl--eq': row.kind === 'same', 'pm-dl--add': row.kind === 'added', 'pm-dl--del': row.kind === 'removed' }"
        >
          <span class="pm-dl__no">{{ row.oldNo ?? '' }}</span>
          <span class="pm-dl__no">{{ row.newNo ?? '' }}</span>
          <span class="pm-dl__sign">{{ row.kind === 'added' ? '+' : row.kind === 'removed' ? '−' : '' }}</span>
          <span class="pm-dl__text">
            <template v-if="row.segments">
              <template v-for="(seg, j) in row.segments" :key="j">
                <template v-if="!seg.changed">{{ seg.text }}</template>
                <ins v-else-if="row.kind === 'added'">{{ seg.text }}</ins>
                <del v-else>{{ seg.text }}</del>
              </template>
            </template>
            <template v-else>{{ row.text || ' ' }}</template>
          </span>
        </div>
      </template>
      <div v-if="!contentRows.length" class="pm-dl pm-dl--gap">（兩個版本皆為空白）</div>
    </div>
  </section>
</template>

<style scoped>
.pm-diff {
  min-width: 0;
}

.pm-diff__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px var(--space-3);
  padding: 8px 14px;
  font-size: var(--text-sm);
  font-weight: 600;
}

.pm-diff__head .is-del { color: var(--red-600); }
.pm-diff__head .is-add { color: var(--green-700); }

.pm-diff__stat {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.pm-diff__same {
  margin: 0;
  padding: 4px 14px;
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.pm-diff__label {
  padding: 4px 14px;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-muted);
}

.pm-dl__text del,
.pm-dl__text ins {
  text-decoration: none;
  border-radius: 2px;
}

.pm-dl__text del { background: var(--red-200); }
.pm-dl__text ins { background: var(--green-200); }

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

.pm-diff__name .pm-dl {
  grid-template-columns: 18px minmax(0, 1fr);
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
