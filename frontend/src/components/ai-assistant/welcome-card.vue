<script setup lang="ts">
import type { SuggestionCategory } from '@/beans/ai-assistant-bean'
import QuickQuestions from '@/components/ai-assistant/quick-questions.vue'

defineProps<{ categories: SuggestionCategory[] }>()
defineEmits<{ ask: [question: string, categoryKey: string] }>()
</script>

<template>
  <div class="ai-welcome">
    <div class="ai-welcome__hero">
      <div class="ai-welcome__mark" aria-hidden="true">✦</div>
      <h3 class="ai-welcome__title">您好，我可以協助您掌握本顧客</h3>
      <p class="ai-welcome__desc">整理顧客概況、互動與經營機會，或查詢市場資訊。選一個問題開始，或直接輸入。</p>
    </div>
    <quick-questions :categories="categories" @ask="(q, k) => $emit('ask', q, k)" />
  </div>
</template>

<style scoped>
.ai-welcome__hero {
  padding: var(--space-4) var(--space-2) var(--space-2);
  text-align: center;
}
.ai-welcome__mark {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin: 0 auto var(--space-3);
  border-radius: 14px;
  background: var(--gradient-ai);
  color: #fff;
  font-size: 22px;
  box-shadow: 0 8px 20px rgba(14, 122, 85, 0.25);
}
.ai-welcome__title {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text-strong);
}
.ai-welcome__desc {
  margin-top: 4px;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}
</style>
