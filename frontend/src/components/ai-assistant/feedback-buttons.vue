<script setup lang="ts">
import type { FeedbackRating } from '@/beans/ai-assistant-bean'
import ThumbIcon from '@/components/ai-assistant/thumb-icon.vue'

const props = defineProps<{ rating: FeedbackRating | null }>()
const emit = defineEmits<{ rate: [rating: FeedbackRating | null] }>()

/** Clicking the selected button again withdraws the rating. */
function toggle(value: FeedbackRating): void {
  emit('rate', props.rating === value ? null : value)
}
</script>

<template>
  <span class="ai-feedback">
    <button class="ai-fb-btn" type="button" aria-label="有幫助" :aria-pressed="rating === 'UP'" @click="toggle('UP')">
      <thumb-icon direction="up" :filled="rating === 'UP'" />
    </button>
    <button class="ai-fb-btn" type="button" aria-label="沒有幫助" :aria-pressed="rating === 'DOWN'" @click="toggle('DOWN')">
      <thumb-icon direction="down" :filled="rating === 'DOWN'" />
    </button>
  </span>
</template>

<style scoped>
.ai-feedback {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.ai-fb-btn {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
}
.ai-fb-btn:hover {
  background: var(--gray-100);
  color: var(--color-text);
}
.ai-fb-btn[aria-pressed='true'] {
  background: var(--color-primary);
  color: var(--color-text-on-primary);
}
.ai-fb-btn[aria-pressed='true']:hover {
  background: var(--color-primary-hover);
  color: var(--color-text-on-primary);
}
.ai-fb-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}
.ai-fb-btn[aria-pressed='true']:focus-visible {
  outline-color: var(--color-primary-active);
  outline-offset: 2px;
}
</style>
