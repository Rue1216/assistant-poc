<script setup lang="ts">
import { computed, ref } from "vue";
import type { ChatMessage, FeedbackRating } from "../types";
import { formatTaipeiTime } from "../taipeiTime";
import { useToast } from "../../../platform-mock/useToast";
import AnswerBlocks from "./AnswerBlocks";
import CitationList from "./CitationList.vue";
import FeedbackButtons from "./FeedbackButtons.vue";

const props = defineProps<{ message: ChatMessage }>();

const emit = defineEmits<{ rate: [rating: FeedbackRating | null] }>();

const toast = useToast();
const time = computed(() =>
  formatTaipeiTime(props.message.queriedAt ?? props.message.createdAt),
);
const hasCitations = computed(() => (props.message.citations?.length ?? 0) > 0);
const citationList = ref<InstanceType<typeof CitationList> | null>(null);

function onCite(id: number): void {
  citationList.value?.reveal(id);
}

async function copy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(props.message.content);
    toast.show("已複製", "info");
  } catch {
    toast.show("無法複製，請手動選取文字");
  }
}
</script>

<template>
  <article class="ai-msg">
    <div class="ai-msg__avatar" aria-hidden="true">✦</div>
    <div class="ai-msg__content">
      <div class="ai-msg__bubble md">
        <AnswerBlocks
          :blocks="message.blocks ?? []"
          :citations="message.citations ?? []"
          @cite="onCite"
        />
      </div>

      <p v-if="message.disclaimer" class="ai-disclaimer">
        <svg
          viewBox="0 0 24 24"
          width="14"
          height="14"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="9" />
          <path d="M12 8v5M12 16h.01" />
        </svg>
        {{ message.disclaimer }}
      </p>

      <div v-if="hasCitations" class="ai-meta">
        <CitationList ref="citationList" :citations="message.citations!" />
      </div>

      <div class="ai-msg__actions">
        <button class="ai-mini-btn" type="button" @click="copy">
          <svg
            viewBox="0 0 24 24"
            width="14"
            height="14"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <rect x="9" y="9" width="11" height="11" rx="2" />
            <path d="M5 15V6a2 2 0 0 1 2-2h9" />
          </svg>
          複製
        </button>
        <FeedbackButtons
          v-if="message.state === 'done' && message.traceId"
          :rating="message.feedback ?? null"
          @rate="emit('rate', $event)"
        />
        <span class="ai-msg__time">{{ time }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.ai-msg {
  display: flex;
  gap: 10px;
}
.ai-msg__avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--gradient-ai);
  color: #fff;
  font-size: 13px;
}
.ai-msg__content {
  flex: 1;
  min-width: 0;
}
.ai-msg__bubble {
  padding: 12px 14px;
  border-radius: 4px 16px 16px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
  font-size: var(--text-sm);
  overflow-wrap: anywhere;
}
.ai-msg__bubble :deep(.safe-markdown > * + *) {
  margin-top: 8px;
}
.ai-msg__bubble :deep(h3) {
  font-size: var(--text-sm);
}
.ai-msg__bubble :deep(h3:not(:first-child)) {
  margin-top: 12px;
}
.ai-msg__bubble :deep(.ai-cite) {
  min-width: 16px;
  height: 16px;
  margin-left: 2px;
  padding: 0 4px;
  border: 1px solid var(--color-primary-border);
  border-radius: 4px;
  background: var(--color-primary-subtle);
  color: var(--color-primary-active);
  font-size: 11px;
  line-height: 14px;
  cursor: pointer;
}
.ai-msg__bubble :deep(.ai-cite:hover) {
  background: var(--color-primary);
  color: var(--color-text-on-primary);
}
.ai-msg__bubble :deep(.ai-cite:focus-visible) {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}
.ai-disclaimer {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: var(--radius-md);
  background: var(--amber-50);
  border: 1px solid var(--amber-200);
  color: var(--amber-700);
  font-size: var(--text-xs);
}
.ai-disclaimer svg {
  flex: none;
}
.ai-meta {
  margin-top: 8px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  font-size: var(--text-xs);
}
.ai-msg__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: 4px;
}
.ai-mini-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text-muted);
  font-size: var(--text-xs);
  cursor: pointer;
}
.ai-mini-btn:hover {
  background: var(--gray-100);
  color: var(--color-text);
}
.ai-msg__time {
  margin-left: auto;
  font-size: 11px;
  color: var(--color-text-subtle);
}
</style>
