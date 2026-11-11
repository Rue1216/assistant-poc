<script lang="ts">
let pickerSeq = 0
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { formatTaipeiShort } from '@/utils/taipei-time'
import type { ConfigSummary, ConfigType } from '@/beans/prompt-management-bean'

const props = defineProps<{ configs: ConfigSummary[]; selectedKey: string; loading: boolean }>()
const emit = defineEmits<{ select: [configKey: string] }>()

const uid = `pm-picker-${++pickerSeq}`
const listId = `${uid}-list`
const optionId = (key: string) => `${uid}-opt-${key}`

const GROUPS: { type: ConfigType; label: string }[] = [
  { type: 'SYSTEM_PROMPT', label: 'System Prompt' },
  { type: 'SYSTEM_INSTRUCTION', label: '問題分類' },
]

const root = ref<HTMLElement | null>(null)
const button = ref<HTMLButtonElement | null>(null)
const input = ref<HTMLInputElement | null>(null)

const isOpen = ref(false)
const query = ref('')
const activeKey = ref('')

const current = computed(() => props.configs.find((c) => c.configKey === props.selectedKey) ?? null)
const buttonText = computed(() => {
  if (current.value) return current.value.name
  return props.loading ? '載入中…' : '請選擇設定'
})

const groups = computed(() => {
  const q = query.value.trim().toLowerCase()
  const matches = (c: ConfigSummary) =>
    !q || c.name.toLowerCase().includes(q) || c.configKey.toLowerCase().includes(q)
  return GROUPS.map((g) => {
    const all = props.configs.filter((c) => c.configType === g.type)
    return {
      ...g,
      id: `${uid}-group-${g.type}`,
      title: g.type === 'SYSTEM_INSTRUCTION' ? `${g.label} ${all.length}` : g.label,
      items: all.filter(matches),
    }
  }).filter((g) => g.items.length)
})

const visible = computed(() => groups.value.flatMap((g) => g.items))
const activeId = computed(() => (activeKey.value && isOpen.value ? optionId(activeKey.value) : undefined))

function resetActive() {
  const keys = visible.value.map((c) => c.configKey)
  if (!keys.includes(activeKey.value)) {
    activeKey.value = keys.includes(props.selectedKey) ? props.selectedKey : (keys[0] ?? '')
  }
}
// the visible options change with the query and with the configs prop (e.g. a list refresh after a save)
watch(
  () => visible.value.map((c) => c.configKey).join('|'),
  () => {
    if (isOpen.value) resetActive()
  },
)

function badgeOf(c: ConfigSummary): { cls: string; text: string } | null {
  const wc = c.workingCopy
  if (wc?.status === 'SCHEDULED' && wc.scheduledAt) {
    return { cls: 'badge--scheduled', text: `已排程 ${formatTaipeiShort(wc.scheduledAt)}` }
  }
  return wc ? { cls: 'badge--draft', text: '草稿' } : null
}

function onOutside(event: Event) {
  if (root.value && !root.value.contains(event.target as Node)) close(false)
}

function open() {
  if (isOpen.value) return void input.value?.focus()
  isOpen.value = true
  query.value = ''
  activeKey.value = ''
  resetActive()
  document.addEventListener('pointerdown', onOutside)
  void nextTick(() => input.value?.focus())
}

function close(focusButton: boolean) {
  if (!isOpen.value) return
  isOpen.value = false
  document.removeEventListener('pointerdown', onOutside)
  if (focusButton) void nextTick(() => button.value?.focus())
}

onBeforeUnmount(() => document.removeEventListener('pointerdown', onOutside))

function choose(key: string) {
  close(true)
  if (key !== props.selectedKey) emit('select', key)
}

function onButtonClick() {
  if (isOpen.value) close(false)
  else open()
}

function onButtonKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    open()
  } else if (event.key === 'Escape' && isOpen.value) {
    close(true)
  }
}

function move(step: 1 | -1) {
  const keys = visible.value.map((c) => c.configKey)
  if (!keys.length) return
  const at = keys.indexOf(activeKey.value)
  const next = Math.min(keys.length - 1, Math.max(0, at + step))
  activeKey.value = keys[next]!
  document.getElementById(optionId(activeKey.value))?.scrollIntoView?.({ block: 'nearest' })
}

function onInputKeydown(event: KeyboardEvent) {
  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      move(1)
      break
    case 'ArrowUp':
      event.preventDefault()
      move(-1)
      break
    case 'Enter':
      event.preventDefault()
      if (activeKey.value) choose(activeKey.value)
      break
    case 'Escape':
      event.preventDefault()
      close(true)
      break
    case 'Tab':
      close(false)
      break
  }
}

defineExpose({ open })
</script>

<template>
  <div ref="root" class="pm-picker">
    <button
      ref="button"
      class="pm-picker__button pm-editor__title"
      type="button"
      aria-haspopup="listbox"
      :aria-expanded="isOpen"
      :aria-controls="isOpen ? listId : undefined"
      @click="onButtonClick"
      @keydown="onButtonKeydown"
    >
      <span>{{ buttonText }}</span><span class="pm-picker__caret" aria-hidden="true">▾</span>
    </button>

    <div v-if="isOpen" class="pm-picker__panel">
      <input
        ref="input"
        v-model="query"
        class="input pm-picker__search"
        type="text"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded="true"
        aria-label="搜尋設定"
        placeholder="搜尋名稱或 key"
        autocomplete="off"
        :aria-controls="listId"
        :aria-activedescendant="activeId"
        @keydown="onInputKeydown"
      />
      <ul :id="listId" class="pm-picker__list" role="listbox" aria-label="設定清單">
        <li v-for="g in groups" :key="g.type" role="presentation">
          <div :id="g.id" class="pm-picker__group" role="presentation">{{ g.title }}</div>
          <ul role="group" :aria-labelledby="g.id" class="pm-picker__items">
            <li
              v-for="c in g.items"
              :id="optionId(c.configKey)"
              :key="c.configKey"
              class="pm-picker__option"
              :class="{ 'is-current': c.configKey === selectedKey, 'is-active': c.configKey === activeKey }"
              role="option"
              :aria-selected="c.configKey === selectedKey"
              @click="choose(c.configKey)"
            >
              <span class="pm-picker__name">{{ c.name }}</span>
              <span v-if="badgeOf(c)" class="badge" :class="badgeOf(c)!.cls">{{ badgeOf(c)!.text }}</span>
            </li>
          </ul>
        </li>
        <li v-if="!groups.length" class="pm-picker__empty" role="presentation">
          {{ configs.length ? '沒有符合的設定' : '尚無設定' }}
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.pm-picker {
  position: relative;
  display: inline-block;
}

.pm-picker__button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: -8px;
  padding: 2px 8px;
  border: 0;
  border-radius: var(--radius-md);
  background: none;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}

.pm-picker__button:hover,
.pm-picker__button[aria-expanded='true'] {
  background: var(--gray-25);
}

.pm-picker__caret {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.pm-picker__panel {
  position: absolute;
  top: 100%;
  left: 0;
  z-index: 30;
  min-width: 320px;
  max-height: 60vh;
  overflow-y: auto;
  margin-top: 4px;
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}

.pm-picker__search {
  width: 100%;
  margin-bottom: var(--space-2);
}

.pm-picker__list,
.pm-picker__items {
  list-style: none;
  margin: 0;
  padding: 0;
}

.pm-picker__group {
  padding: var(--space-2) 12px 4px;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-muted);
}

.pm-picker__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: 10px 12px;
  border-radius: var(--radius-md);
  font-weight: 600;
  color: var(--color-text-strong);
  cursor: pointer;
}

.pm-picker__option:hover {
  background: var(--gray-25);
}

.pm-picker__option.is-active {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.pm-picker__option.is-current {
  background: var(--color-primary-subtle);
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.pm-picker__empty {
  padding: var(--space-4);
  text-align: center;
  font-size: var(--text-sm);
  color: var(--color-text-subtle);
}

@media (max-width: 1100px) {
  /* the panel is positioned against the header card, so it is as wide as the card */
  .pm-picker {
    position: static;
  }
  .pm-picker__panel {
    right: 0;
    min-width: 0;
  }
}
</style>
