<script setup lang="ts">
defineEmits<{ cancel: [] }>()
</script>

<template>
  <div class="ai-msg">
    <div class="ai-msg__avatar" aria-hidden="true">✦</div>
    <div class="ai-msg__content">
      <div class="ai-msg__bubble">
        <div class="ai-typing" aria-label="查詢中"><span></span><span></span><span></span></div>
        <p class="ai-pending__text">正在查詢資料並整理回答…</p>
        <button class="ai-mini-btn" type="button" @click="$emit('cancel')">停止</button>
      </div>
    </div>
  </div>
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
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 4px 16px 16px 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}
.ai-pending__text {
  flex: 1;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}
.ai-mini-btn {
  display: inline-flex;
  align-items: center;
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
.ai-typing {
  display: flex;
  gap: 4px;
}
.ai-typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--green-400);
  animation: ai-bounce 1.2s infinite ease-in-out;
}
.ai-typing span:nth-child(2) { animation-delay: 0.15s; }
.ai-typing span:nth-child(3) { animation-delay: 0.3s; }
@keyframes ai-bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.45; }
  40% { transform: translateY(-4px); opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .ai-typing span { animation: none; opacity: 0.8; }
}
</style>
