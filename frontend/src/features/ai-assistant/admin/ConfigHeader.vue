<script lang="ts">
export type PageTab = 'content' | 'versions' | 'evaluation'
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { formatTaipeiShort } from '../taipeiTime'
import type { ConfigVersion } from '../types'
import './prompt-admin.css'

const props = defineProps<{
  name: string
  configKey: string
  workingCopy: ConfigVersion | null
  published: ConfigVersion | null
  tab: PageTab
  /** appended to the 評測 tab label; empty shows nothing */
  verdictMark: string
}>()
const emit = defineEmits<{ 'update:tab': [tab: PageTab] }>()

const TABS: { key: PageTab; label: string }[] = [
  { key: 'content', label: '內容' },
  { key: 'versions', label: '版本' },
  { key: 'evaluation', label: '評測' },
]

const status = computed(() => {
  const wc = props.workingCopy
  if (!wc) return { text: '目前生效', badge: 'badge--published' }
  if (wc.status === 'SCHEDULED' && wc.scheduledAt) {
    return { text: `預約 ${formatTaipeiShort(wc.scheduledAt)} 發布`, badge: 'badge--scheduled' }
  }
  return { text: '草稿', badge: 'badge--draft' }
})

const meta = computed(() => {
  const p = props.published
  return p
    ? `・目前生效：${formatTaipeiShort(p.updatedAt)}（${p.updatedBy} 發布）`
    : `・尚未發布`
})
</script>

<template>
  <div>
    <header class="pm-confighead">
      <div class="pm-editor__title-row">
        <h2 class="pm-editor__title">{{ name }}</h2>
        <span class="badge" :class="status.badge">{{ status.text }}</span>
      </div>
      <p class="pm-confighead__meta">
        <code>{{ configKey }}</code>{{ meta }}
      </p>
    </header>
    <div class="pm-pagetabs" role="tablist" aria-label="設定內容分頁">
      <button
        v-for="t in TABS"
        :key="t.key"
        class="pm-pagetabs__tab"
        type="button"
        role="tab"
        :aria-selected="tab === t.key"
        @click="emit('update:tab', t.key)"
      >
        {{ t.label }}<template v-if="t.key === 'evaluation' && verdictMark"> {{ verdictMark }}</template>
      </button>
    </div>
  </div>
</template>

<style scoped>
.pm-confighead {
  padding: var(--space-5) var(--space-6) var(--space-4);
  border-bottom: 1px solid var(--color-border);
}

.pm-confighead__meta {
  margin: 6px 0 0;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.pm-confighead__meta code {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
}

.pm-pagetabs {
  display: flex;
  gap: var(--space-5);
  padding: 0 var(--space-6);
  border-bottom: 1px solid var(--color-border);
}

.pm-pagetabs__tab {
  padding: 12px 2px 10px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font: inherit;
  font-weight: 600;
  color: var(--color-text-muted);
  cursor: pointer;
}

.pm-pagetabs__tab[aria-selected='true'] {
  border-bottom-color: var(--color-primary);
  color: var(--green-700);
}
</style>
