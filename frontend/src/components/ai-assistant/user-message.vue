<script setup lang="ts">
import type { ChatMessage } from '@/beans/ai-assistant-bean'

defineProps<{ message: ChatMessage }>()
</script>

<template>
  <div class="ai-msg ai-msg--user">
    <div class="ai-msg__bubble">{{ message.content }}</div>
  </div>
</template>

<style scoped>
.ai-msg--user {
  display: flex;
  justify-content: flex-end;
}
.ai-msg__bubble {
  max-width: 82%;
  padding: 9px 14px;
  border-radius: 16px 16px 4px 16px;
  background: var(--color-primary);
  color: var(--color-text-on-primary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  box-shadow: 0 2px 6px color-mix(in srgb, var(--green-600) 18%, transparent);
}
</style>
