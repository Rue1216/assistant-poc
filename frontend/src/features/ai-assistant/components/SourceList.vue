<script setup lang="ts">
import type { SourceView } from '../types'
import { formatTaipei, formatTaipeiTime } from '../taipeiTime'

defineProps<{ sources: SourceView[] }>()

function isHttp(url: string | null): url is string {
  return url !== null && /^https?:\/\//i.test(url)
}

/** dataTime is either a plain date (e.g. a closing day) or a full timestamp. */
function dataTime(value: string): string {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : formatTaipei(value)
}
</script>

<template>
  <div class="ai-sources">
    <span class="ai-sources__label">來源</span>
    <ul class="ai-sources__list">
      <!-- name on its own line, times below: long names wrap without splitting the time labels -->
      <li v-for="(s, i) in sources" :key="i">
        <component
          :is="isHttp(s.url) ? 'a' : 'span'"
          class="ai-source"
          :class="{ 'ai-source--link': isHttp(s.url) }"
          v-bind="isHttp(s.url) ? { href: s.url, target: '_blank', rel: 'noopener noreferrer' } : {}"
        >
          <span class="ai-source__name">{{ s.name }}</span>
          <span class="ai-source__meta">
            <span v-if="s.dataTime" class="ai-source__time">資料 {{ dataTime(s.dataTime) }}</span>
            <span class="ai-source__time">查詢 {{ formatTaipeiTime(s.queriedAt) }}</span>
          </span>
        </component>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.ai-sources {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 7px 10px;
}
.ai-sources__label {
  color: var(--color-text-muted);
}
.ai-sources__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.ai-source {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  background: var(--gray-50);
  color: var(--color-text);
  text-decoration: none;
}
.ai-source__name {
  font-weight: 600;
  overflow-wrap: anywhere;
}
.ai-source__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 12px;
}
.ai-source__time {
  white-space: nowrap;
  color: var(--color-text-subtle);
}
.ai-source--link {
  background: var(--green-50);
  color: var(--green-700);
}
.ai-source--link .ai-source__name::after {
  content: ' ↗';
  font-size: 10px;
}
.ai-source--link:hover {
  background: var(--green-100);
}
</style>
