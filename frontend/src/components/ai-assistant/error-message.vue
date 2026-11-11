<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage } from '@/beans/ai-assistant-bean'

const props = defineProps<{ message: ChatMessage; retryable: boolean }>()
defineEmits<{ retry: [] }>()

const reference = computed(() => {
  const e = props.message.error
  if (!e) return ''
  return e.traceId ? `${e.code} · 追蹤碼 ${e.traceId}` : e.code
})
</script>

<template>
  <div class="ai-error" role="alert">
    <div class="ai-error__icon" aria-hidden="true">!</div>
    <div class="ai-error__body">
      <strong>{{ message.error?.message }}</strong>
      <p class="ai-error__ref">{{ reference }}</p>
    </div>
    <button v-if="retryable" class="btn btn--secondary btn--sm" type="button" @click="$emit('retry')">重試</button>
  </div>
</template>

<style scoped>
.ai-error {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border-radius: var(--radius-lg);
  background: var(--red-50);
  border: 1px solid var(--red-200);
  font-size: var(--text-sm);
  color: var(--red-700);
}
.ai-error__icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--red-600);
  color: var(--color-text-on-primary);
  font-size: 12px;
  font-weight: 700;
}
.ai-error__body {
  flex: 1;
}
.ai-error__ref {
  margin-top: 2px;
  font-family: var(--font-mono);
  font-size: 11px;
  opacity: 0.75;
}
</style>
