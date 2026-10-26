<script setup lang="ts">
import type { FeedbackRating } from '../types'

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
      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M7 11v9H4v-9h3zM7 11l4-7a2 2 0 0 1 2 2v4h6a2 2 0 0 1 2 2.3l-1 6A2 2 0 0 1 18 20H7" />
      </svg>
    </button>
    <button class="ai-fb-btn" type="button" aria-label="沒有幫助" :aria-pressed="rating === 'DOWN'" @click="toggle('DOWN')">
      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M17 13V4h3v9h-3zM17 13l-4 7a2 2 0 0 1-2-2v-4H5a2 2 0 0 1-2-2.3l1-6A2 2 0 0 1 6 4h11" />
      </svg>
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
  background: var(--color-primary-subtle);
  color: var(--color-primary-active);
}
.ai-fb-btn:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}
</style>
