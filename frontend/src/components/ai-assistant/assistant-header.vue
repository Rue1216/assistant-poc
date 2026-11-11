<script setup lang="ts">
defineProps<{ canStartNew: boolean }>()
defineEmits<{ newChat: []; close: [] }>()
</script>

<template>
  <header class="ai-header">
    <div class="ai-header__mark" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
        <path
          d="M12 2.5l1.9 5.6 5.6 1.9-5.6 1.9L12 17.5l-1.9-5.6L4.5 10l5.6-1.9L12 2.5zm6.5 11l.95 2.55L22 17l-2.55.95L18.5 20.5l-.95-2.55L15 17l2.55-.95.95-2.55z"
        />
      </svg>
    </div>
    <div class="ai-header__text">
      <h2 class="ai-header__title">顧問小幫手</h2>
    </div>
    <div class="ai-header__actions">
      <button
        class="ai-icon-btn"
        type="button"
        title="開新對話"
        aria-label="開新對話"
        :disabled="!canStartNew"
        @click="$emit('newChat')"
      >
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
          <path d="M18.4 2.6a2.1 2.1 0 0 1 3 3L12 15l-4 1 1-4 9.4-9.4z" />
        </svg>
      </button>
      <button class="ai-icon-btn" type="button" title="縮小" aria-label="縮小對話視窗" @click="$emit('close')">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
    </div>
  </header>
</template>

<style scoped>
.ai-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 14px 12px 14px 16px;
  background: var(--gradient-ai);
  color: #fff;
}
.ai-header__mark {
  display: grid;
  place-items: center;
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.2);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.25);
}
.ai-header__text {
  flex: 1;
  min-width: 0;
}
.ai-header__title {
  font-size: var(--text-lg);
  font-weight: 700;
  line-height: var(--leading-tight);
}
.ai-header__actions {
  display: flex;
  gap: 2px;
}
.ai-icon-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: #fff;
  cursor: pointer;
  transition: background var(--duration-fast);
}
.ai-icon-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.18);
}
.ai-icon-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.ai-icon-btn:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 1px;
}
</style>
