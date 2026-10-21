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
    <template v-for="(s, i) in sources" :key="i">
      <a v-if="isHttp(s.url)" class="ai-source ai-source--link" :href="s.url" target="_blank" rel="noopener noreferrer">
        {{ s.name }}
        <span v-if="s.dataTime" class="ai-source__time">資料 {{ dataTime(s.dataTime) }}</span>
        <span class="ai-source__time">查詢 {{ formatTaipeiTime(s.queriedAt) }}</span>
      </a>
      <span v-else class="ai-source">
        {{ s.name }}
        <span v-if="s.dataTime" class="ai-source__time">資料 {{ dataTime(s.dataTime) }}</span>
        <span class="ai-source__time">查詢 {{ formatTaipeiTime(s.queriedAt) }}</span>
      </span>
    </template>
  </div>
</template>

<style scoped>
.ai-sources {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
}
.ai-sources__label {
  color: var(--color-text-muted);
}
.ai-source {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--gray-50);
  color: var(--color-text);
  text-decoration: none;
}
.ai-source__time {
  color: var(--color-text-subtle);
}
.ai-source--link {
  background: var(--green-50);
  color: var(--green-700);
}
.ai-source--link::after {
  content: '↗';
  font-size: 10px;
}
.ai-source--link:hover {
  background: var(--green-100);
}
</style>
