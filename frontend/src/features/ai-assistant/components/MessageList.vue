<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { ChatMessage, FeedbackRating, SuggestionCategory } from '../types'
import { formatTaipei, formatTaipeiTime } from '../taipeiTime'
import WelcomeCard from './WelcomeCard.vue'
import UserMessage from './UserMessage.vue'
import AssistantMessage from './AssistantMessage.vue'
import PendingMessage from './PendingMessage.vue'
import ErrorMessage from './ErrorMessage.vue'

const props = defineProps<{
  messages: ChatMessage[]
  categories: SuggestionCategory[]
  unavailable: boolean
}>()
defineEmits<{ ask: [question: string]; cancel: []; retry: [id: string]; rate: [id: string, rating: FeedbackRating | null] }>()

const box = ref<HTMLElement | null>(null)
const firstTime = computed(() => {
  const first = props.messages[0]
  if (!first) return ''
  // compare calendar days in Asia/Taipei; formatTaipei is "YYYY/MM/DD HH:mm"
  const day = (iso: string) => formatTaipei(iso).slice(0, 10)
  return day(first.createdAt) === day(new Date().toISOString())
    ? `今天 ${formatTaipeiTime(first.createdAt)}`
    : formatTaipei(first.createdAt)
})

watch(
  () => [props.messages.length, props.messages.at(-1)?.state],
  async () => {
    await nextTick()
    if (box.value) box.value.scrollTop = box.value.scrollHeight
  },
)
</script>

<template>
  <div ref="box" class="ai-messages" role="log" aria-live="polite">
    <p v-if="unavailable" class="ai-unavailable" role="alert">查無此顧客或您沒有查看權限</p>
    <WelcomeCard v-else-if="messages.length === 0" :categories="categories" @ask="$emit('ask', $event)" />
    <div v-else class="ai-thread">
      <div class="ai-divider">
        <span>{{ firstTime }}</span>
      </div>
      <template v-for="m in messages" :key="m.id">
        <UserMessage v-if="m.role === 'user'" :message="m" />
        <PendingMessage v-else-if="m.state === 'pending'" @cancel="$emit('cancel')" />
        <ErrorMessage v-else-if="m.state === 'error'" :message="m" @retry="$emit('retry', m.id)" />
        <div v-else-if="m.state === 'cancelled'" class="ai-cancelled">已取消</div>
        <AssistantMessage v-else :message="m" @rate="$emit('rate', m.id, $event)" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.ai-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-4);
  background: var(--gray-25);
  scroll-behavior: smooth;
}
.ai-thread {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.ai-divider {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--text-xs);
  color: var(--color-text-subtle);
}
.ai-divider::before,
.ai-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--color-border);
}
.ai-cancelled {
  align-self: flex-start;
  margin-left: 38px;
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  background: var(--gray-50);
  color: var(--color-text-muted);
  font-size: var(--text-xs);
}
.ai-unavailable {
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--red-50);
  border: 1px solid var(--red-200);
  color: var(--red-700);
  font-size: var(--text-sm);
}
</style>
