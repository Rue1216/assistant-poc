<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useAuthStore } from '../../../platform-mock/useAuthStore'
import { useAssistantStore } from '../stores/useAssistantStore'
import AssistantPanel from './AssistantPanel.vue'

defineProps<{ customerId: string }>()

const auth = useAuthStore()
const store = useAssistantStore()

const open = ref(false)
// the panel is created on first open so suggestions load lazily, then kept mounted
const everOpened = ref(false)

function show(): void {
  everOpened.value = true
  open.value = true
}

onBeforeUnmount(() => store.reset())
</script>

<template>
  <template v-if="auth.hasPermission('AI_ASSISTANT_USE')">
    <button v-show="!open" class="ai-fab" type="button" @click="show">
      <span class="ai-fab__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
          <path
            d="M12 2.5l1.9 5.6 5.6 1.9-5.6 1.9L12 17.5l-1.9-5.6L4.5 10l5.6-1.9L12 2.5zm6.5 11l.95 2.55L22 17l-2.55.95L18.5 20.5l-.95-2.55L15 17l2.55-.95.95-2.55z"
          />
        </svg>
      </span>
      <span class="ai-fab__label">顧問小幫手</span>
    </button>
    <AssistantPanel v-if="everOpened" v-show="open" :customer-id="customerId" @close="open = false" />
  </template>
</template>

<style scoped>
.ai-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 39;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 52px;
  padding: 0 20px 0 8px;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--gradient-ai);
  color: #fff;
  font-weight: 700;
  font-size: var(--text-md);
  box-shadow: 0 10px 28px rgba(14, 122, 85, 0.35);
  cursor: pointer;
  transition:
    transform var(--duration-fast),
    box-shadow var(--duration-fast);
}
.ai-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 32px rgba(14, 122, 85, 0.42);
}
.ai-fab:focus-visible {
  outline: 3px solid var(--color-focus-ring);
  outline-offset: 3px;
}
.ai-fab__icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
}
@media (max-width: 600px) {
  .ai-fab {
    right: 16px;
    bottom: 16px;
    padding: 0 7px;
  }
  .ai-fab__label {
    display: none;
  }
}
</style>
