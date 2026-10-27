<script setup lang="ts">
import { MAX_MESSAGE_LENGTH } from "../composables/useAssistantChat";

defineProps<{
  modelValue: string;
  count: number;
  canSend: boolean;
  pending: boolean;
  disabled: boolean;
  quickQuestions: string[];
}>();
const emit = defineEmits<{
  "update:modelValue": [value: string];
  send: [];
  ask: [question: string];
}>();

function onKeydown(e: KeyboardEvent): void {
  if (e.key !== "Enter" || e.shiftKey) return;
  // Enter that confirms an IME candidate must not send (keyCode 229 for older engines)
  if (e.isComposing || e.keyCode === 229) return;
  e.preventDefault();
  emit("send");
}

function onInput(e: Event): void {
  const el = e.target as HTMLTextAreaElement;
  emit("update:modelValue", el.value);
  // parent may have truncated; keep the element in sync with the model
  if (el.value.length > MAX_MESSAGE_LENGTH)
    el.value = el.value.slice(0, MAX_MESSAGE_LENGTH);
}
</script>

<template>
  <footer class="ai-composer-wrap">
    <details v-if="quickQuestions.length" class="ai-quick-pop">
      <summary>
        <svg
          viewBox="0 0 24 24"
          width="14"
          height="14"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path
            d="M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.4 1 1.1 1 1.8V16h5v-.3c0-.7.4-1.4 1-1.8A6 6 0 0 0 12 3z"
          />
        </svg>
        快捷問題
      </summary>
      <div class="ai-quick-pop__list">
        <button
          v-for="q in quickQuestions"
          :key="q"
          class="chip"
          type="button"
          :disabled="pending || disabled"
          @click="emit('ask', q)"
        >
          {{ q }}
        </button>
      </div>
    </details>

    <div class="ai-composer">
      <label class="sr-only" for="ai-input">輸入問題</label>
      <textarea
        id="ai-input"
        class="ai-composer__input"
        rows="1"
        :maxlength="MAX_MESSAGE_LENGTH"
        placeholder="詢問本顧客或市場資訊…"
        :value="modelValue"
        :disabled="disabled"
        @input="onInput"
        @keydown="onKeydown"
      ></textarea>
      <button
        class="ai-send"
        type="button"
        aria-label="送出"
        :disabled="!canSend"
        @click="emit('send')"
      >
        <svg
          viewBox="0 0 24 24"
          width="18"
          height="18"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M5 12h14M13 6l6 6-6 6" />
        </svg>
      </button>
    </div>
    <div class="ai-composer__foot">
      <span>Enter 送出 · Shift + Enter 換行</span>
      <span class="ai-composer__count"
        >{{ count }} / {{ MAX_MESSAGE_LENGTH }}</span
      >
    </div>
  </footer>
</template>

<style scoped>
.ai-composer-wrap {
  padding: 10px 14px;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
}
.ai-quick-pop {
  margin-bottom: 8px;
}
.ai-quick-pop summary {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  background: var(--green-50);
  color: var(--green-700);
  font-size: var(--text-xs);
  font-weight: 600;
  cursor: pointer;
  list-style: none;
  user-select: none;
}
.ai-quick-pop summary::-webkit-details-marker {
  display: none;
}
.ai-quick-pop summary:hover {
  background: var(--green-100);
}
.ai-quick-pop[open] summary {
  background: var(--color-primary);
  color: #fff;
}
.ai-quick-pop__list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  max-height: 120px;
  overflow-y: auto;
}
.ai-composer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 6px 6px 6px 14px;
  border: 1px solid var(--color-border-strong);
  border-radius: 14px;
  background: var(--color-surface);
  transition:
    border-color var(--duration-fast),
    box-shadow var(--duration-fast);
}
.ai-composer:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}
.ai-composer__input {
  flex: 1;
  min-height: 34px;
  max-height: 140px;
  padding: 6px 0;
  border: 0;
  resize: none;
  background: transparent;
  line-height: 1.55;
  field-sizing: content;
}
.ai-composer__input:focus {
  outline: none;
}
.ai-composer__input::placeholder {
  color: var(--color-text-subtle);
}
.ai-send {
  display: grid;
  place-items: center;
  flex: none;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 10px;
  background: var(--color-primary);
  color: #fff;
  cursor: pointer;
  transition: background var(--duration-fast);
}
.ai-send:hover:not(:disabled) {
  background: var(--color-primary-hover);
}
.ai-send:disabled {
  background: var(--gray-200);
  cursor: not-allowed;
}
.ai-composer__foot {
  display: flex;
  justify-content: space-between;
  margin-top: 6px;
  padding: 0 4px;
  font-size: 11px;
  color: var(--color-text-subtle);
}
.ai-composer__count {
  font-variant-numeric: tabular-nums;
}
</style>
