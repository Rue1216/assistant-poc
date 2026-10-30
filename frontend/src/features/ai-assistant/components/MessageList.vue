<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import type { ChatMessage, FeedbackRating, SuggestionCategory } from "../types";
import { formatTaipei, formatTaipeiTime } from "../taipeiTime";
import WelcomeCard from "./WelcomeCard.vue";
import UserMessage from "./UserMessage.vue";
import AssistantMessage from "./AssistantMessage.vue";
import PendingMessage from "./PendingMessage.vue";
import ErrorMessage from "./ErrorMessage.vue";

const props = defineProps<{
  messages: ChatMessage[];
  /** The current round; earlier rounds stay on screen but cannot be retried. */
  round: number;
  categories: SuggestionCategory[];
  unavailable: boolean;
}>();
defineEmits<{
  ask: [question: string, categoryKey: string];
  retry: [id: string];
  rate: [id: string, rating: FeedbackRating | null];
}>();

/** Earlier rounds stay on screen but are not sent as history; say so under each 新對話 divider. */
const NEW_ROUND_HINT = "以下為新對話，不會參考上方的對話內容";

/** Gap kept above a question scrolled to the top, same as the list's padding. */
const QUESTION_TOP_GAP = 16;

const box = ref<HTMLElement | null>(null);
/** Space below the thread so the newest question can reach the top while its answer is still short. */
const tailHeight = ref(0);
function timeLabel(iso: string): string {
  // compare calendar days in Asia/Taipei; formatTaipei is "YYYY/MM/DD HH:mm"
  const day = (t: string) => formatTaipei(t).slice(0, 10);
  return day(iso) === day(new Date().toISOString())
    ? `今天 ${formatTaipeiTime(iso)}`
    : formatTaipei(iso);
}

/** Divider before each message that opens a round (null otherwise); later rounds are marked 新對話. */
const dividers = computed(() =>
  props.messages.map((m, i) => {
    if (i > 0 && props.messages[i - 1]!.round === m.round) return null;
    return { label: timeLabel(m.createdAt), newRound: i !== 0 };
  }),
);

/** Message index -> how many of its round's messages were folded into the summary; set on the last such message only. */
const summaryHints = computed(() => {
  const counts = new Map<number, number>();
  const lastIndex = new Map<number, number>();
  props.messages.forEach((m, i) => {
    if (!m.summarized) return;
    counts.set(m.round, (counts.get(m.round) ?? 0) + 1);
    lastIndex.set(m.round, i);
  });
  const byIndex = new Map<number, number>();
  for (const [round, i] of lastIndex) byIndex.set(i, counts.get(round)!);
  return byIndex;
});

function summaryHint(n: number): string {
  return `以上 ${n} 則對話已整理成摘要，小幫手會參考摘要繼續回答`;
}

/** A new round was started and has no messages yet. */
const awaitingRound = computed(
  () =>
    props.messages.length > 0 && props.messages.at(-1)!.round !== props.round,
);

watch(
  () =>
    [
      props.messages.length,
      props.messages.at(-1)?.state,
      props.round,
      props.messages.at(-1)?.id,
    ] as const,
  async ([, , , lastId], [, , , previousId]) => {
    await nextTick();
    const el = box.value;
    if (!el) return;
    // A newly sent question goes to the top. Later state changes (answer, error, cancel) leave the scroll alone and
    // drop the bottom space: it is only needed while the answer is pending, and whenever it was non-zero the whole
    // exchange fits in the view, so removing it only shifts the view down to the end of the answer.
    if (lastId === previousId && !awaitingRound.value) {
      if (props.messages.at(-1)?.state !== "pending") tailHeight.value = 0;
      return;
    }
    const question = awaitingRound.value
      ? null
      : [...props.messages].reverse().find((m) => m.role === "user");
    const target = question
      ? el.querySelector(`[data-message-id="${question.id}"]`)
      : null;
    if (!target) {
      tailHeight.value = 0;
      await nextTick();
      el.scrollTop = el.scrollHeight;
      return;
    }
    const top =
      el.scrollTop +
      target.getBoundingClientRect().top -
      el.getBoundingClientRect().top -
      QUESTION_TOP_GAP;
    const content = el.scrollHeight - tailHeight.value;
    tailHeight.value = Math.max(0, top + el.clientHeight - content);
    await nextTick();
    el.scrollTop = top;
  },
);
</script>

<template>
  <div ref="box" class="ai-messages" role="log" aria-live="polite">
    <p v-if="unavailable" class="ai-unavailable" role="alert">
      查無此顧客或您沒有查看權限
    </p>
    <WelcomeCard
      v-else-if="messages.length === 0"
      :categories="categories"
      @ask="(q, k) => $emit('ask', q, k)"
    />
    <div v-else class="ai-thread">
      <template v-for="(m, i) in messages" :key="m.id">
        <div v-if="dividers[i]" class="ai-round-break">
          <div class="ai-divider">
            <span>{{ dividers[i]!.label }}</span>
          </div>
          <p v-if="dividers[i]!.newRound" class="ai-divider__hint">
            {{ NEW_ROUND_HINT }}
          </p>
        </div>
        <UserMessage
          v-if="m.role === 'user'"
          :data-message-id="m.id"
          :message="m"
        />
        <PendingMessage v-else-if="m.state === 'pending'" />
        <ErrorMessage
          v-else-if="m.state === 'error'"
          :message="m"
          :retryable="m.round === round"
          @retry="$emit('retry', m.id)"
        />
        <div v-else-if="m.state === 'cancelled'" class="ai-cancelled">
          已取消
        </div>
        <AssistantMessage
          v-else
          :data-message-id="m.id"
          :message="m"
          @rate="$emit('rate', m.id, $event)"
        />
        <div v-if="summaryHints.has(i)" class="ai-round-break">
          <div class="ai-divider">
            <span>{{ summaryHint(summaryHints.get(i)!) }}</span>
          </div>
        </div>
      </template>
      <div v-if="awaitingRound" class="ai-round-break">
        <div class="ai-divider">
          <span>{{ NEW_ROUND_HINT }}</span>
        </div>
      </div>
    </div>
    <div
      v-if="!unavailable && messages.length > 0"
      class="ai-tail"
      :style="{ height: `${tailHeight}px` }"
      aria-hidden="true"
    />
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
  overflowscroll-behavior: contain;
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
  content: "";
  flex: 1;
  height: 1px;
  background: var(--color-border);
}
.ai-divider__hint {
  margin-top: 4px;
  font-size: var(--text-xs);
  color: var(--color-text-subtle);
  text-align: center;
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
