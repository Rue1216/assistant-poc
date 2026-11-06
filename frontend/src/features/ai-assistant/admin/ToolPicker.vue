<script lang="ts">
let pickerSeq = 0
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { aiAssistantApi } from '../api/aiAssistantApi'
import type { ToolCatalogItem } from '../types'

const emit = defineEmits<{ insert: [marker: string] }>()

const uid = `pm-tools-${++pickerSeq}`
const listId = `${uid}-list`
// ids are index-based: a marker contains spaces, braces and a colon, which is not a valid IDREF
const optionId = (marker: string) => `${uid}-opt-${items.value.findIndex((i) => i.marker === marker)}`

interface Item {
  marker: string
  label: string
  /** the text after the label, e.g. `getCustomer: management` */
  code: string
  haystack: string
}

const root = ref<HTMLElement | null>(null)
const button = ref<HTMLButtonElement | null>(null)
const input = ref<HTMLInputElement | null>(null)
const retryButton = ref<HTMLButtonElement | null>(null)

const isOpen = ref(false)
const query = ref('')
const activeMarker = ref('')

const catalog = ref<ToolCatalogItem[] | null>(null)
const loading = ref(false)
const failed = ref(false)

// one item per option; a tool without options is a single item, the bare tool of an optioned tool is none
const items = computed<Item[]>(() =>
  (catalog.value ?? []).flatMap((t) =>
    t.options.length
      ? t.options.map((o) => ({
          marker: `{{${t.name}: ${o.key}}}`,
          label: o.name,
          code: `${t.name}: ${o.key}`,
          haystack: [t.name, o.key, o.name, o.description].join('\n').toLowerCase(),
        }))
      : [{ marker: `{{${t.name}}}`, label: t.purpose, code: t.name, haystack: [t.name, t.purpose].join('\n').toLowerCase() }],
  ),
)

const visible = computed(() => {
  const q = query.value.trim().toLowerCase()
  return q ? items.value.filter((i) => i.haystack.includes(q)) : items.value
})
const activeId = computed(() => (activeMarker.value && isOpen.value ? optionId(activeMarker.value) : undefined))
const showList = computed(() => !loading.value && !failed.value)

function resetActive() {
  if (!visible.value.some((i) => i.marker === activeMarker.value)) activeMarker.value = visible.value[0]?.marker ?? ''
}

async function load() {
  if (loading.value || catalog.value) return
  loading.value = true
  failed.value = false
  try {
    catalog.value = await aiAssistantApi.tools()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
  resetActive()
}

async function retry() {
  await load()
  // the retry button is replaced while loading; success or failure, keep focus in the panel
  if (isOpen.value) void nextTick(() => input.value?.focus())
}

function onRetryKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    close(true)
  } else if (event.key === 'Tab' && !event.shiftKey) {
    close(false)
  }
}

function onOutside(event: Event) {
  if (root.value && !root.value.contains(event.target as Node)) close(false)
}

function open() {
  if (isOpen.value) return void input.value?.focus()
  isOpen.value = true
  query.value = ''
  activeMarker.value = ''
  resetActive()
  document.addEventListener('pointerdown', onOutside)
  void nextTick(() => input.value?.focus())
  void load() // lazy: the catalog is fetched on the first open only
}

function close(focusButton: boolean) {
  if (!isOpen.value) return
  isOpen.value = false
  document.removeEventListener('pointerdown', onOutside)
  if (focusButton) void nextTick(() => button.value?.focus())
}

onBeforeUnmount(() => document.removeEventListener('pointerdown', onOutside))

function choose(marker: string) {
  // the editor takes focus back after inserting, so the button must not grab it
  close(false)
  emit('insert', marker)
}

function onQuery() {
  resetActive()
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
  const markers = visible.value.map((i) => i.marker)
  if (!markers.length) return
  const at = markers.indexOf(activeMarker.value)
  activeMarker.value = markers[Math.min(markers.length - 1, Math.max(0, at + step))]!
  document.getElementById(optionId(activeMarker.value))?.scrollIntoView?.({ block: 'nearest' })
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
      if (activeMarker.value) choose(activeMarker.value)
      break
    case 'Escape':
      event.preventDefault()
      close(true)
      break
    case 'Tab':
      // after a failed load, forward Tab reaches 重試 instead of closing the panel
      if (failed.value && !loading.value && !event.shiftKey && retryButton.value) {
        event.preventDefault()
        retryButton.value.focus()
      } else {
        close(false)
      }
      break
  }
}
</script>

<template>
  <div ref="root" class="pm-tools">
    <button
      ref="button"
      class="btn btn--secondary btn--sm"
      type="button"
      aria-haspopup="listbox"
      :aria-expanded="isOpen"
      :aria-controls="isOpen && showList ? listId : undefined"
      @click="onButtonClick"
      @keydown="onButtonKeydown"
    >
      插入工具 ▾
    </button>

    <div v-if="isOpen" class="pm-tools__panel">
      <input
        ref="input"
        v-model="query"
        class="input pm-tools__search"
        type="text"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded="true"
        aria-label="搜尋工具"
        placeholder="搜尋工具名稱或說明"
        autocomplete="off"
        :aria-controls="showList ? listId : undefined"
        :aria-activedescendant="activeId"
        @input="onQuery"
        @keydown="onInputKeydown"
      />
      <div v-if="loading" class="pm-tools__status">載入中…</div>
      <div v-else-if="failed" class="pm-tools__status">
        無法載入工具清單
        <button ref="retryButton" class="btn btn--ghost btn--sm" type="button" @click="retry" @keydown="onRetryKeydown">重試</button>
      </div>
      <ul v-else :id="listId" class="pm-tools__list" role="listbox" aria-label="工具清單">
        <li
          v-for="i in visible"
          :id="optionId(i.marker)"
          :key="i.marker"
          class="pm-tools__option"
          :class="{ 'is-active': i.marker === activeMarker }"
          role="option"
          :aria-selected="i.marker === activeMarker"
          @click="choose(i.marker)"
        >
          <span class="pm-tools__name">{{ i.label }}</span>
          <code class="pm-tools__code">{{ i.code }}</code>
        </li>
        <li v-if="!visible.length" class="pm-tools__status" role="presentation">沒有符合的工具</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.pm-tools {
  position: relative;
}

.pm-tools__panel {
  position: absolute;
  top: 100%;
  right: 0;
  z-index: 30;
  /* The editor pane clips its overflow and is 420px at its smallest, so the panel must fit inside it. */
  display: flex;
  flex-direction: column;
  min-width: 320px;
  max-height: 360px;
  margin-top: 4px;
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}

.pm-tools__search {
  width: 100%;
  margin-bottom: var(--space-2);
}

.pm-tools__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  list-style: none;
  margin: 0;
  padding: 0;
}

.pm-tools__option {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
}

.pm-tools__option:hover {
  background: var(--gray-25);
}

.pm-tools__option.is-active {
  outline: 2px solid var(--color-primary);
  outline-offset: -2px;
}

.pm-tools__name {
  font-weight: 600;
  color: var(--color-text-strong);
}

.pm-tools__code {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.pm-tools__status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-4);
  font-size: var(--text-sm);
  color: var(--color-text-subtle);
}
</style>
