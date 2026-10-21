<script setup lang="ts">
import type { SuggestionCategory } from '../types'

defineProps<{ categories: SuggestionCategory[] }>()
defineEmits<{ ask: [question: string] }>()
</script>

<template>
  <div v-if="categories.length" class="ai-quick-groups">
    <div v-for="(c, i) in categories" :key="c.configKey" class="ai-quick-group">
      <h4 class="ai-quick-group__title">
        <span class="ai-cat" :class="`ai-cat--${(i % 5) + 1}`" aria-hidden="true"></span>{{ c.name }}
      </h4>
      <div class="ai-quick-group__chips">
        <button v-for="q in c.questions" :key="q" class="chip" type="button" @click="$emit('ask', q)">{{ q }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-quick-groups {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-top: var(--space-4);
}
.ai-quick-group {
  padding: 12px;
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
}
.ai-quick-group__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--color-text-muted);
  letter-spacing: 0.02em;
}
.ai-quick-group__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ai-cat {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}
.ai-cat--1 { background: var(--green-500); }
.ai-cat--2 { background: var(--teal-400); }
.ai-cat--3 { background: var(--green-300); }
.ai-cat--4 { background: #6aa84f; }
.ai-cat--5 { background: #3d8fb3; }
</style>
