<script setup lang="ts">
import { computed, watch } from "vue";
import { useAssistantStore } from "@/stores/use-assistant-store";
import { useAssistantChat } from "@/composables/use-assistant-chat";
import { useSuggestions } from "@/composables/use-suggestions";
import AssistantHeader from "@/components/ai-assistant/assistant-header.vue";
import MessageList from "@/components/ai-assistant/message-list.vue";
import ChatComposer from "@/components/ai-assistant/chat-composer.vue";

const props = defineProps<{ certNo: string }>();
defineEmits<{ close: [] }>();

const store = useAssistantStore();
const chat = useAssistantChat();
const suggestions = useSuggestions();

// bind first, then load: suggestions are dropped if the customer changes meanwhile
watch(
  () => props.certNo,
  (id) => {
    store.bindCustomer(id);
    chat.draft.value = "";
    void suggestions.load(id);
  },
  { immediate: true },
);

const canStartNew = computed(() =>
  chat.messages.value.some((m) => m.round === chat.round.value),
);

function startNewChat(): void {
  chat.startNewRound();
  chat.draft.value = "";
}
</script>

<template>
  <section class="ai-panel" aria-label="顧問小幫手">
    <assistant-header
      :can-start-new="canStartNew"
      @new-chat="startNewChat"
      @close="$emit('close')"
    />
    <message-list
      :messages="chat.messages.value"
      :round="chat.round.value"
      :categories="suggestions.categories.value"
      :unavailable="chat.unavailable.value"
      @ask="chat.ask"
      @retry="chat.retry"
      @rate="chat.rate"
    />
    <chat-composer
      :model-value="chat.draft.value"
      :count="chat.count.value"
      :can-send="chat.canSend.value"
      :pending="chat.pending.value"
      :disabled="chat.unavailable.value"
      :categories="
        chat.messages.value.length > 0 ? suggestions.categories.value : []
      "
      @update:model-value="chat.setDraft"
      @send="chat.submit"
      @cancel="chat.cancel"
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
