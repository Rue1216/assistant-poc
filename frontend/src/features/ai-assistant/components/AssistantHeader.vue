<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'

defineProps<{ customerId: string; hasMessages: boolean }>()
const emit = defineEmits<{ newChat: []; delete: []; close: [] }>()

const menuOpen = ref(false)
const menuRoot = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const menu = ref<HTMLElement | null>(null)

function enabledItems(): HTMLButtonElement[] {
  return Array.from(menu.value?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]:not(:disabled)') ?? [])
}

async function openMenu(): Promise<void> {
  menuOpen.value = true
  document.addEventListener('pointerdown', onPointerOutside, true)
  await nextTick()
  enabledItems()[0]?.focus()
}

function closeMenu(refocus = false): void {
  if (!menuOpen.value) return
  menuOpen.value = false
  document.removeEventListener('pointerdown', onPointerOutside, true)
  if (refocus) trigger.value?.focus()
}

function toggleMenu(): void {
  if (menuOpen.value) closeMenu()
  else void openMenu()
}

function onPointerOutside(e: PointerEvent): void {
  if (!menuRoot.value?.contains(e.target as Node)) closeMenu()
}

function onMenuKeydown(e: KeyboardEvent): void {
  if (!menuOpen.value) return
  if (e.key === 'Escape') {
    e.preventDefault()
    closeMenu(true)
    return
  }
  if (e.key === 'Tab') {
    closeMenu()
    return
  }
  const items = enabledItems()
  if (items.length === 0 || !['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(e.key)) return
  e.preventDefault()
  const i = items.indexOf(document.activeElement as HTMLButtonElement)
  let next: number
  if (e.key === 'Home') next = 0
  else if (e.key === 'End') next = items.length - 1
  else if (e.key === 'ArrowDown') next = i < 0 ? 0 : (i + 1) % items.length
  else next = i < 0 ? items.length - 1 : (i - 1 + items.length) % items.length
  items[next]!.focus()
}

function choose(action: 'newChat' | 'delete'): void {
  closeMenu(true)
  if (action === 'newChat') emit('newChat')
  else emit('delete')
}

onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerOutside, true))
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
      <div ref="menuRoot" class="ai-menu" @keydown="onMenuKeydown">
        <button
          ref="trigger"
          class="ai-icon-btn"
          :class="{ 'is-active': menuOpen }"
          type="button"
          title="更多動作"
          aria-label="更多動作"
          aria-haspopup="menu"
          :aria-expanded="menuOpen"
          aria-controls="ai-header-menu"
          @click="toggleMenu"
        >
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
            <circle cx="5" cy="12" r="1.8" />
            <circle cx="12" cy="12" r="1.8" />
            <circle cx="19" cy="12" r="1.8" />
          </svg>
        </button>
        <div v-if="menuOpen" id="ai-header-menu" ref="menu" class="ai-menu__list" role="menu" aria-label="對話動作">
          <button class="ai-menu__item" type="button" role="menuitem" @click="choose('newChat')">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M12 20h9" />
              <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z" />
            </svg>
            開新對話
          </button>
          <button class="ai-menu__item" type="button" role="menuitem" disabled title="尚未開放">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M3 12a9 9 0 1 0 3-6.7" />
              <path d="M3 4v5h5" />
              <path d="M12 7v5l3 2" />
            </svg>
            查看對話紀錄
          </button>
          <button
            class="ai-menu__item ai-menu__item--danger"
            type="button"
            role="menuitem"
            :disabled="!hasMessages"
            @click="choose('delete')"
          >
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M4 7h16" />
              <path d="M10 11v6M14 11v6" />
              <path d="M6 7l1 13h10l1-13" />
              <path d="M9 7V4h6v3" />
            </svg>
            刪除對話
          </button>
        </div>
      </div>
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
.ai-icon-btn:hover,
.ai-icon-btn.is-active {
  background: rgba(255, 255, 255, 0.18);
}
.ai-icon-btn:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 1px;
}
.ai-menu {
  position: relative;
}
.ai-menu__list {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  min-width: 176px;
  padding: 4px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}
.ai-menu__item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 36px;
  padding: 0 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--color-text);
  font-size: var(--text-sm);
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
}
.ai-menu__item svg {
  flex: none;
  color: var(--color-text-muted);
}
.ai-menu__item:hover:not(:disabled),
.ai-menu__item:focus-visible {
  background: var(--gray-50);
  outline: none;
}
.ai-menu__item--danger,
.ai-menu__item--danger svg {
  color: var(--red-600);
}
.ai-menu__item--danger:hover:not(:disabled),
.ai-menu__item--danger:focus-visible {
  background: var(--red-50);
}
.ai-menu__item:disabled,
.ai-menu__item:disabled svg {
  color: var(--color-text-subtle);
  cursor: not-allowed;
}
</style>
