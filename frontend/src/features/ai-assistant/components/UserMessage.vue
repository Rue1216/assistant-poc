<script setup lang="ts">
import type { ChatMessage } from '../types'

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
  color: #fff;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  box-shadow: 0 2px 6px rgba(14, 122, 85, 0.18);
}
</style>
