<script setup lang="ts">
import { watch } from 'vue'
import { useAssistantStore } from '../stores/useAssistantStore'
import { useAssistantChat } from '../composables/useAssistantChat'
import { useSuggestions } from '../composables/useSuggestions'
import AssistantHeader from './AssistantHeader.vue'
import MessageList from './MessageList.vue'
import ChatComposer from './ChatComposer.vue'

const props = defineProps<{ customerId: string }>()
defineEmits<{ close: [] }>()

const store = useAssistantStore()
const chat = useAssistantChat()
const suggestions = useSuggestions()

// bind first, then load: suggestions are dropped if the customer changes meanwhile
watch(
  () => props.customerId,
  (id) => {
    store.bindCustomer(id)
    chat.draft.value = ''
    void suggestions.load(id)
  },
  { immediate: true },
)

function startNewChat(): void {
  chat.clear()
  chat.draft.value = ''
}
</script>

<template>
  <section class="ai-panel" aria-label="顧問小幫手">
    <AssistantHeader
      :customer-id="customerId"
      :has-messages="chat.messages.value.length > 0"
      @new-chat="startNewChat"
      @delete="chat.clear"
      @close="$emit('close')"
    />
    <div class="ai-notice">
      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <rect x="4" y="11" width="16" height="10" rx="2" />
        <path d="M8 11V7a4 4 0 0 1 8 0v4" />
      </svg>
      <span>對話只針對此顧客，切換顧客會清空；姓名等識別資料不會傳給 AI。</span>
    </div>
    <MessageList
      :messages="chat.messages.value"
      :categories="suggestions.categories.value"
      :unavailable="chat.unavailable.value"
      @ask="chat.ask"
      @cancel="chat.cancel"
      @retry="chat.retry"
    />
    <ChatComposer
      :model-value="chat.draft.value"
      :count="chat.count.value"
      :can-send="chat.canSend.value"
      :pending="chat.pending.value"
      :disabled="chat.unavailable.value"
      :quick-questions="chat.messages.value.length > 0 ? suggestions.categories.value.flatMap((c) => c.questions) : []"
      @update:model-value="chat.setDraft"
      @send="chat.submit"
      @ask="chat.ask"
    />
  </section>
</template>

<style scoped>
.ai-panel {
  --ai-width: 420px;
  --ai-gap: 24px;

  position: fixed;
  right: var(--ai-gap);
  bottom: var(--ai-gap);
  z-index: 40;
  display: flex;
  flex-direction: column;
  width: var(--ai-width);
  height: min(720px, calc(100vh - 56px - var(--ai-gap) * 2));
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--color-border);
  overflow: hidden;
}
.ai-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 16px;
  background: var(--green-50);
  border-bottom: 1px solid var(--green-100);
  color: var(--green-700);
  font-size: var(--text-xs);
  line-height: var(--leading-tight);
}
.ai-notice svg {
  flex: none;
}
@media (max-width: 600px) {
  .ai-panel {
    inset: 0;
    width: auto;
    height: auto;
    border-radius: 0;
    border: 0;
  }
}
</style>
