<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useAuthStore } from '@/stores/use-auth-store'
import { useAssistantStore } from '@/stores/use-assistant-store'
import AssistantPanel from '@/components/ai-assistant/assistant-panel.vue'

defineProps<{ certNo: string }>()

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
    <button v-show="!open" class="ai-fab" type="button" title="顧問小幫手" aria-label="開啟顧問小幫手" @click="show">
      <svg viewBox="0 0 24 24" width="26" height="26" fill="currentColor" aria-hidden="true">
        <path
          d="M12 2.5l1.9 5.6 5.6 1.9-5.6 1.9L12 17.5l-1.9-5.6L4.5 10l5.6-1.9L12 2.5zm6.5 11l.95 2.55L22 17l-2.55.95L18.5 20.5l-.95-2.55L15 17l2.55-.95.95-2.55z"
        />
      </svg>
    </button>
    <assistant-panel v-if="everOpened" v-show="open" :cert-no="certNo" @close="open = false" />
  </template>
</template>

<style scoped>
.ai-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 39;
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--gradient-ai);
  color: var(--color-text-on-primary);
  box-shadow: 0 10px 28px color-mix(in srgb, var(--green-600) 35%, transparent);
  cursor: pointer;
  transition:
    transform var(--duration-fast),
    box-shadow var(--duration-fast);
}
.ai-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 32px color-mix(in srgb, var(--green-600) 42%, transparent);
}
.ai-fab:focus-visible {
  outline: 3px solid var(--color-focus-ring);
  outline-offset: 3px;
}
@media (max-width: 600px) {
  .ai-fab {
    right: 16px;
    bottom: 16px;
  }
}
</style>
