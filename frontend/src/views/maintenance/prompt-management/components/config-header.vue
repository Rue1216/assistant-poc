<script lang="ts">
export type PageTab = 'content' | 'versions' | 'evaluation'

/** Ids shared with the page, which renders the tab panel the tabs control. */
export const pageTabId = (prefix: string, tab: PageTab) => `${prefix}-tab-${tab}`
export const pageTabPanelId = (prefix: string) => `${prefix}-tabpanel`
</script>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { formatTaipeiShort } from '@/utils/taipei-time'
import type { ConfigSummary, ConfigVersion } from '@/beans/prompt-management-bean'
import ConfigPicker from '@/views/maintenance/prompt-management/components/config-picker.vue'
import '@/assets/styles/prompt-admin.css'

const props = defineProps<{
  /** the current config's name, kept as the page's (visually hidden) heading */
  name: string
  configs: ConfigSummary[]
  loading: boolean
  /** false while the selected config's versions are loading: no status badge or meta line yet */
  ready: boolean
  configKey: string
  workingCopy: ConfigVersion | null
  published: ConfigVersion | null
  tab: PageTab
  /** appended to the 評測 tab label; empty shows nothing */
  verdictMark: string
  /** prefix of the tab and tab panel ids (pageTabId, pageTabPanelId) */
  idPrefix: string
}>()
const emit = defineEmits<{ 'update:tab': [tab: PageTab]; select: [configKey: string] }>()

const picker = ref<InstanceType<typeof ConfigPicker> | null>(null)
defineExpose({ openPicker: () => picker.value?.open() })

const TABS: { key: PageTab; label: string }[] = [
  { key: 'content', label: '內容' },
  { key: 'versions', label: '版本' },
  { key: 'evaluation', label: '評測' },
]

/** Tab pattern keys: ←/→ move and select (wrapping), Home/End go to the first/last tab. */
function onTabKeydown(event: KeyboardEvent) {
  const at = TABS.findIndex((t) => t.key === props.tab)
  const last = TABS.length - 1
  const targets: Record<string, number> = { ArrowRight: at === last ? 0 : at + 1, ArrowLeft: at === 0 ? last : at - 1, Home: 0, End: last }
  const next = targets[event.key]
  if (next === undefined) return
  event.preventDefault()
  const key = TABS[next]!.key
  emit('update:tab', key)
  void nextTick(() => document.getElementById(pageTabId(props.idPrefix, key))?.focus())
}

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
        <h2 class="sr-only">{{ name }}</h2>
        <config-picker
          ref="picker"
          :configs="configs"
          :selected-key="configKey"
          :loading="loading"
          @select="emit('select', $event)"
        />
        <span v-if="ready" class="badge" :class="status.badge">{{ status.text }}</span>
      </div>
      <p v-if="ready" class="pm-confighead__meta">
        <code>{{ configKey }}</code>{{ meta }}
      </p>
    </header>
    <div class="pm-pagetabs" role="tablist" aria-label="設定內容分頁">
      <button
        v-for="t in TABS"
        :id="pageTabId(idPrefix, t.key)"
        :key="t.key"
        class="pm-pagetabs__tab"
        type="button"
        role="tab"
        :aria-selected="tab === t.key"
        :aria-controls="pageTabPanelId(idPrefix)"
        :tabindex="tab === t.key ? 0 : -1"
        @click="emit('update:tab', t.key)"
        @keydown="onTabKeydown"
      >
        {{ t.key === 'evaluation' && verdictMark ? `${t.label} ${verdictMark}` : t.label }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.pm-confighead {
  position: relative; /* the picker panel spans the card below 1100px */
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

@media (max-width: 1100px) {
  .pm-confighead,
  .pm-pagetabs {
    padding-left: var(--space-4);
    padding-right: var(--space-4);
  }
}
</style>
