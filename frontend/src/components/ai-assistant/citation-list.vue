<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import type { Citation } from '@/beans/ai-assistant-bean'
import { formatTaipei, formatTaipeiTime } from '@/utils/taipei-time'

const props = defineProps<{ citations: Citation[] }>()

const FLASH_MS = 1500

const cited = computed(() => props.citations.filter((c) => c.cited))
const other = computed(() => props.citations.filter((c) => !c.cited))

const root = ref<HTMLElement | null>(null)
const timers = new Map<HTMLElement, ReturnType<typeof setTimeout>>()

function isHttp(url: string | null): url is string {
  return url !== null && /^https?:\/\//i.test(url)
}

/** dataTime is either a plain date (e.g. a closing day) or a full timestamp. */
function dataTime(value: string): string {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : formatTaipei(value)
}

/** Imperative on purpose: the item must be open, scrolled to and highlighted in the same tick. */
function reveal(id: number): void {
  const el = root.value?.querySelector<HTMLDetailsElement>(`[data-citation-id="${id}"]`)
  if (!el) return
  el.open = true
  el.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' })
  el.classList.add('ai-citation--flash')
  const prev = timers.get(el)
  if (prev) clearTimeout(prev)
  timers.set(
    el,
    setTimeout(() => {
      el.classList.remove('ai-citation--flash')
      timers.delete(el)
    }, FLASH_MS),
  )
}

onBeforeUnmount(() => timers.forEach((t) => clearTimeout(t)))

defineExpose({ reveal })
</script>

<template>
  <div ref="root" class="ai-citations">
    <span class="ai-citations__label">引用（{{ citations.length }}）</span>
    <template v-for="group in [{ key: 'cited', items: cited }, { key: 'other', items: other }]" :key="group.key">
      <template v-if="group.items.length > 0">
        <span v-if="group.key === 'other'" class="ai-citations__other-label">其他查詢資料</span>
        <ul :class="group.key === 'cited' ? 'ai-citations__cited' : 'ai-citations__other'" class="ai-citations__list">
          <li v-for="c in group.items" :key="c.id">
            <details
              class="ai-citation"
              :data-citation-id="c.id"
            >
              <summary class="ai-citation__summary">[{{ c.id }}] {{ c.title }}<template v-if="c.target"> · {{ c.target }}</template></summary>
              <p v-if="c.excerpt ?? c.description" class="ai-citation__body">{{ c.excerpt ?? c.description }}</p>
              <div class="ai-citation__meta">
                <span v-if="c.dataTime" class="ai-citation__time">資料 {{ dataTime(c.dataTime) }}</span>
                <span class="ai-citation__time">查詢 {{ formatTaipeiTime(c.queriedAt) }}</span>
                <template v-if="c.sourceName">
                  <a v-if="isHttp(c.url)" class="ai-citation__link" :href="c.url" target="_blank" rel="noopener noreferrer">{{ c.sourceName }} ↗</a>
                  <span v-else class="ai-citation__source">{{ c.sourceName }}</span>
                </template>
              </div>
            </details>
          </li>
        </ul>
      </template>
    </template>
  </div>
</template>

<style scoped>
.ai-citations {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 7px 10px;
}
.ai-citations__label,
.ai-citations__other-label {
  color: var(--color-text-muted);
}
.ai-citations__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.ai-citation {
  min-width: 0;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: var(--gray-50);
  color: var(--color-text);
  transition: background var(--duration-normal);
}
.ai-citation__summary {
  font-weight: 600;
  overflow-wrap: anywhere;
  cursor: pointer;
}
.ai-citation__body {
  margin: 4px 0 0;
  color: var(--color-text-muted);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.ai-citation__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 12px;
  margin-top: 2px;
}
.ai-citation__time {
  color: var(--color-text-subtle);
  white-space: nowrap;
}
.ai-citation__source {
  color: var(--color-text-subtle);
  overflow-wrap: anywhere;
}
.ai-citation__link {
  color: var(--green-700);
  text-decoration: none;
  overflow-wrap: anywhere;
}
.ai-citation__link:hover {
  background: var(--green-100);
}
.ai-citation--flash {
  background: var(--green-100);
}
</style>
