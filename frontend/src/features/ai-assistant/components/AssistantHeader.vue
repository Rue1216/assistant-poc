<script setup lang="ts">
defineProps<{ customerId: string }>()
defineEmits<{ clear: []; close: [] }>()
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
      <p class="ai-header__sub"><span class="ai-header__live" aria-hidden="true"></span>正在檢視 {{ customerId }} · 個人 360</p>
    </div>
    <div class="ai-header__actions">
      <button class="ai-icon-btn" type="button" title="清除對話" aria-label="清除對話" @click="$emit('clear')">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 12a9 9 0 1 0 3-6.7" />
          <path d="M3 4v5h5" />
        </svg>
      </button>
      <button class="ai-icon-btn" type="button" title="縮小" aria-label="縮小對話視窗" @click="$emit('close')">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
          <path d="M6 12h12" />
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
.ai-header__sub {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-xs);
  opacity: 0.9;
}
.ai-header__live {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #b8f5d9;
  box-shadow: 0 0 0 3px rgba(184, 245, 217, 0.3);
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
.ai-icon-btn:hover {
  background: rgba(255, 255, 255, 0.18);
}
</style>
