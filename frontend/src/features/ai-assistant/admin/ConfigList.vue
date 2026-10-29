<script setup lang="ts">
import { computed, ref } from 'vue'
import { formatTaipeiShort } from '../taipeiTime'
import type { ConfigSummary, ConfigType } from '../types'

const props = defineProps<{ configs: ConfigSummary[]; tab: ConfigType; selectedKey: string }>()
const emit = defineEmits<{ 'update:tab': [tab: ConfigType]; select: [configKey: string] }>()

const TABS: { type: ConfigType; label: string }[] = [
  { type: 'SYSTEM_PROMPT', label: 'System Prompt' },
  { type: 'SYSTEM_INSTRUCTION', label: '問題分類' },
]

const search = ref('')

const counts = computed(() => ({
  SYSTEM_PROMPT: props.configs.filter((c) => c.configType === 'SYSTEM_PROMPT').length,
  SYSTEM_INSTRUCTION: props.configs.filter((c) => c.configType === 'SYSTEM_INSTRUCTION').length,
}))

const items = computed(() => {
  const q = search.value.trim().toLowerCase()
  return props.configs.filter(
    (c) =>
      c.configType === props.tab &&
      (!q || c.name.toLowerCase().includes(q) || c.configKey.toLowerCase().includes(q)),
  )
})
</script>

<template>
  <aside class="pm-sidebar card" aria-label="設定清單">
    <div class="pm-tabs" role="tablist">
      <button
        v-for="t in TABS"
        :key="t.type"
        class="pm-tabs__tab"
        type="button"
        role="tab"
        :aria-selected="tab === t.type"
        @click="emit('update:tab', t.type)"
      >
        {{ t.label }} <span>{{ counts[t.type] }}</span>
      </button>
    </div>

    <div class="pm-search">
      <input v-model="search" class="input" type="search" placeholder="搜尋名稱或 key" aria-label="搜尋設定" />
    </div>

    <ul class="pm-list">
      <li v-for="c in items" :key="c.configKey">
        <div class="pm-item" :aria-current="c.configKey === selectedKey ? 'true' : undefined">
          <button class="pm-item__main" type="button" @click="emit('select', c.configKey)">
            <span class="pm-item__row">
              <span class="pm-item__name">{{ c.name }}</span>
              <span v-if="c.workingCopy?.status === 'SCHEDULED' && c.workingCopy.scheduledAt" class="badge badge--scheduled">
                已排程 {{ formatTaipeiShort(c.workingCopy.scheduledAt) }}
              </span>
              <span v-else-if="c.workingCopy" class="badge badge--draft">草稿</span>
            </span>
          </button>
        </div>
      </li>
      <li v-if="!items.length" class="pm-list__empty">{{ search ? '沒有符合的設定' : '尚無設定' }}</li>
    </ul>
  </aside>
</template>

<style scoped>
.pm-sidebar {
  position: sticky;
  top: var(--space-4);
  overflow: hidden;
}

.pm-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 4px;
  margin: var(--space-3) var(--space-3) 0;
  border-radius: var(--radius-md);
  background: var(--gray-50);
}

.pm-tabs__tab {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 6px 4px;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-muted);
  cursor: pointer;
  white-space: nowrap;
  transition: background var(--duration-fast), color var(--duration-fast);
}

.pm-tabs__tab[aria-selected='true'] {
  background: var(--color-surface);
  color: var(--green-700);
  box-shadow: var(--shadow-sm);
}

.pm-search {
  padding: var(--space-3);
  border-bottom: 1px solid var(--color-border);
}

.pm-list {
  list-style: none;
  padding: var(--space-2);
  max-height: calc(100vh - 260px);
  overflow-y: auto;
}

.pm-list__empty {
  padding: var(--space-4);
  text-align: center;
  font-size: var(--text-sm);
  color: var(--color-text-subtle);
}

.pm-item {
  position: relative;
  border-radius: var(--radius-md);
  transition: background var(--duration-fast);
}

.pm-item:hover {
  background: var(--gray-25);
}

.pm-item__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.pm-item[aria-current='true'] {
  background: var(--color-primary-subtle);
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.pm-item__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.pm-item__name {
  font-weight: 600;
  color: var(--color-text-strong);
}

@media (max-width: 1100px) {
  .pm-sidebar {
    position: static;
  }
  .pm-list {
    max-height: 320px;
  }
}
</style>
