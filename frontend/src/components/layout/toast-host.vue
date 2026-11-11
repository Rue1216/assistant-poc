<script setup lang="ts">
import { useToastItems } from '@/composables/use-toast'

const { toasts, dismiss } = useToastItems()
</script>

<template>
  <div class="toast-host" aria-live="polite">
    <div
      v-for="t in toasts"
      :key="t.id"
      class="toast"
      :class="`toast--${t.kind}`"
      role="status"
      @click="dismiss(t.id)"
    >
      {{ t.message }}
    </div>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: var(--space-4);
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  align-items: center;
}
.toast {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  font-size: var(--text-sm);
  cursor: pointer;
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  color: var(--color-text-strong);
}
.toast--error {
  background: var(--red-50);
  border-color: var(--red-200);
  color: var(--red-700);
}
.toast--info {
  background: var(--green-50);
  border-color: var(--green-200);
  color: var(--green-700);
}
</style>
