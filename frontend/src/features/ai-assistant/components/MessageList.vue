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
  /** The current round; earlier rounds stay on screen but cannot be retried. */
  round: number
  categories: SuggestionCategory[]
  unavailable: boolean
}>()
defineEmits<{ ask: [question: string, categoryKey: string]; cancel: []; retry: [id: string]; rate: [id: string, rating: FeedbackRating | null] }>()

const box = ref<HTMLElement | null>(null)
function timeLabel(iso: string): string {
  // compare calendar days in Asia/Taipei; formatTaipei is "YYYY/MM/DD HH:mm"
  const day = (t: string) => formatTaipei(t).slice(0, 10)
  return day(iso) === day(new Date().toISOString()) ? `今天 ${formatTaipeiTime(iso)}` : formatTaipei(iso)
}

/** Divider text before each message that opens a round (null otherwise); later rounds are marked 新對話. */
const dividers = computed(() =>
  props.messages.map((m, i) => {
    if (i > 0 && props.messages[i - 1]!.round === m.round) return null
    return i === 0 ? timeLabel(m.createdAt) : `新對話 · ${timeLabel(m.createdAt)}`
  }),
)

/** A new round was started and has no messages yet. */
const awaitingRound = computed(() => props.messages.length > 0 && props.messages.at(-1)!.round !== props.round)

watch(
  () => [props.messages.length, props.messages.at(-1)?.state, props.round],
  async () => {
    await nextTick()
    if (box.value) box.value.scrollTop = box.value.scrollHeight
  },
)
</script>

<template>
  <div ref="box" class="ai-messages" role="log" aria-live="polite">
    <p v-if="unavailable" class="ai-unavailable" role="alert">查無此顧客或您沒有查看權限</p>
    <WelcomeCard v-else-if="messages.length === 0" :categories="categories" @ask="(q, k) => $emit('ask', q, k)" />
    <div v-else class="ai-thread">
      <template v-for="(m, i) in messages" :key="m.id">
        <div v-if="dividers[i]" class="ai-divider">
          <span>{{ dividers[i] }}</span>
        </div>
        <UserMessage v-if="m.role === 'user'" :message="m" />
        <PendingMessage v-else-if="m.state === 'pending'" @cancel="$emit('cancel')" />
        <ErrorMessage v-else-if="m.state === 'error'" :message="m" :retryable="m.round === round" @retry="$emit('retry', m.id)" />
        <div v-else-if="m.state === 'cancelled'" class="ai-cancelled">已取消</div>
        <AssistantMessage v-else :message="m" @rate="$emit('rate', m.id, $event)" />
      </template>
      <div v-if="awaitingRound" class="ai-divider">
        <span>新對話</span>
      </div>
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
