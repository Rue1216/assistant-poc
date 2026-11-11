<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue'
import SafeMarkdown from '@/utils/markdown/safe-markdown'
import { extractExampleQuestions } from '@/utils/markdown/example-questions'
import { addedLineIndices } from '@/utils/markdown/line-diff'
import type { ConfigType } from '@/beans/prompt-management-bean'
import { byteLength, formatKb, MAX_CONTENT_BYTES, missingSections } from '@/views/maintenance/prompt-management/prompt-labels'
import ToolPicker from '@/views/maintenance/prompt-management/components/tool-picker.vue'
import '@/assets/styles/prompt-admin.css'

const props = withDefaults(
  defineProps<{
    modelValue: string
    configType: ConfigType
    readonly?: boolean
    /** Content of the published version; the preview marks lines that are not in it. null: never published. */
    baseline?: string | null
  }>(),
  { readonly: false, baseline: null },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const id = useId()
const tab = ref<'edit' | 'preview'>('edit')
const textarea = ref<HTMLTextAreaElement | null>(null)

const bytes = computed(() => byteLength(props.modelValue))
const missing = computed(() =>
  missingSections(props.configType, props.modelValue)
    .map((heading) => `「${heading}」`)
    .join('、'),
)
const chips = computed(() =>
  props.configType === 'SYSTEM_INSTRUCTION' ? extractExampleQuestions(props.modelValue) : [],
)

// R28: computed lazily, so it only runs while the preview tab renders
const changedLines = computed<ReadonlySet<number> | null>(() => {
  if (props.baseline == null) return null
  try {
    const added = addedLineIndices(props.baseline, props.modelValue)
    // an added blank line marks nothing, so it must not show the legend either
    const lines = props.modelValue.split(/\r\n|\r|\n/)
    return new Set([...added].filter((i) => lines[i]!.trim() !== ''))
  } catch {
    return null // too long to diff line by line: preview without marks
  }
})

function onInput(e: Event) {
  emit('update:modelValue', (e.target as HTMLTextAreaElement).value)
}

const canInsertTool = computed(
  () => tab.value === 'edit' && !props.readonly && props.configType === 'SYSTEM_INSTRUCTION',
)

/** Replaces the selection with the marker, then puts the cursor right after it. */
function insertAtCursor(marker: string) {
  const el = textarea.value
  if (!el) return
  const start = el.selectionStart
  const end = el.selectionEnd
  const caret = start + marker.length
  emit('update:modelValue', props.modelValue.slice(0, start) + marker + props.modelValue.slice(end))
  void nextTick(() => {
    el.focus()
    el.setSelectionRange(caret, caret)
  })
}
</script>

<template>
  <div class="pm-pane pm-md">
    <div class="pm-pane__head pm-md__head">
      <div class="pm-md__tabs" role="tablist" aria-label="內容（Markdown）">
        <button
          :id="`${id}-tab-edit`"
          class="pm-md__tab"
          type="button"
          role="tab"
          :aria-selected="tab === 'edit'"
          :aria-controls="`${id}-panel`"
          @click="tab = 'edit'"
        >
          編輯
        </button>
        <button
          :id="`${id}-tab-preview`"
          class="pm-md__tab"
          type="button"
          role="tab"
          :aria-selected="tab === 'preview'"
          :aria-controls="`${id}-panel`"
          @click="tab = 'preview'"
        >
          預覽
        </button>
      </div>
      <span v-if="missing" class="pm-md__missing">缺少{{ missing }}</span>
      <span v-if="tab === 'edit'" class="pm-pane__count" :class="{ 'is-over': bytes > MAX_CONTENT_BYTES }">
        {{ formatKb(bytes) }} / 64 KB
      </span>
      <span v-else class="pm-pane__count">以目前內容渲染，含未儲存的修改</span>
      <tool-picker v-if="canInsertTool" @insert="insertAtCursor" />
    </div>

    <div
      v-if="tab === 'edit'"
      :id="`${id}-panel`"
      class="pm-md__panel"
      role="tabpanel"
      :aria-labelledby="`${id}-tab-edit`"
    >
      <label class="sr-only" :for="`${id}-content`">內容</label>
      <textarea
        :id="`${id}-content`"
        ref="textarea"
        class="pm-code"
        spellcheck="false"
        :value="modelValue"
        :readonly="readonly"
        @input="onInput"
      ></textarea>
    </div>

    <div
      v-else
      :id="`${id}-panel`"
      class="pm-md__panel"
      role="tabpanel"
      :aria-labelledby="`${id}-tab-preview`"
    >
      <div class="pm-preview">
        <div class="md"><safe-markdown :source="modelValue" :highlight-lines="changedLines" /></div>
      </div>
      <div v-if="changedLines?.size" class="pm-preview-foot">
        <span class="pm-diff-key" aria-hidden="true"></span>與目前生效的版本不同
      </div>
      <div v-if="configType === 'SYSTEM_INSTRUCTION'" class="pm-chips-preview">
        <span class="pm-chips-preview__label">快捷按鈕預覽</span>
        <div v-if="chips.length" class="pm-chips-preview__list">
          <span v-for="(q, i) in chips" :key="i" class="chip">{{ q }}</span>
        </div>
        <span v-else class="pm-chips-preview__empty">沒有範例問題，對話視窗不會顯示這個分類</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pm-md:focus-within {
  border-color: var(--color-border-strong);
  box-shadow: none;
}

.pm-md:has(.pm-code:focus) {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.pm-md__head {
  gap: var(--space-3);
  padding-top: 0;
  padding-bottom: 0;
}

.pm-md__tabs {
  display: flex;
  align-self: stretch;
  gap: var(--space-4);
}

.pm-md__tab {
  display: inline-flex;
  align-items: center;
  margin-bottom: -1px;
  padding: 10px 2px 8px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color var(--duration-fast), border-color var(--duration-fast);
}

.pm-md__tab:hover {
  color: var(--color-text);
}

.pm-md__tab[aria-selected='true'] {
  border-bottom-color: var(--color-primary);
  color: var(--green-700);
}

.pm-md__panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.pm-pane__count.is-over {
  color: var(--red-600);
  font-weight: 600;
}

.pm-code {
  display: block;
  width: 100%;
  flex: 1;
  min-height: 0;
  padding: 14px 16px;
  border: 0;
  resize: none;
  background: var(--color-surface);
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.7;
  color: var(--gray-700);
  tab-size: 2;
}

.pm-code:focus {
  outline: none;
}

.pm-code[readonly] {
  background: var(--gray-25);
}

/* ---- 預覽中的差異標示（mark 由 SafeMarkdown 產生，位於子元件內） ---- */

.pm-preview :deep(mark.pm-diff) {
  padding: 1px 2px;
  border-radius: 3px;
  background: var(--green-100);
  color: inherit;
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.pm-preview-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-top: 1px dashed var(--color-border-strong);
  background: var(--gray-25);
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.pm-diff-key {
  width: 14px;
  height: 10px;
  border-radius: 2px;
  background: var(--green-100);
  box-shadow: inset 3px 0 0 var(--color-primary);
}

.pm-chips-preview {
  padding: 10px 14px 12px;
  border-top: 1px dashed var(--color-border-strong);
  background: var(--gradient-soft);
}

.pm-chips-preview__label {
  display: block;
  margin-bottom: 6px;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--color-text-muted);
}

.pm-chips-preview__list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pm-chips-preview .chip {
  cursor: default;
}

.pm-chips-preview__empty {
  font-size: var(--text-xs);
  color: var(--color-text-subtle);
}

.pm-md__missing {
  margin-left: auto;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--red-700);
}
</style>
