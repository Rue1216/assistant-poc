<script setup lang="ts">
import { nextTick, ref, useId, watch } from 'vue'
import '@/assets/styles/prompt-admin.css'

const props = withDefaults(defineProps<{ open: boolean; busy?: boolean }>(), { busy: false })
const emit = defineEmits<{ create: [keySuffix: string, name: string]; cancel: [] }>()

/** The part after "instruction." of 02 section 3 configKey `^(system|instruction)\.[a-z0-9-]{1,50}$`. */
const KEY_SUFFIX_PATTERN = /^[a-z0-9-]{1,50}$/

const id = useId()
const key = ref('')
const name = ref('')
const keyError = ref<string | null>(null)
const nameError = ref<string | null>(null)
const keyInput = ref<HTMLInputElement | null>(null)

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    key.value = ''
    name.value = ''
    keyError.value = null
    nameError.value = null
    await nextTick()
    keyInput.value?.focus()
  },
)

function submit() {
  const k = key.value.trim()
  const n = name.value.trim()
  keyError.value = KEY_SUFFIX_PATTERN.test(k) ? null : 'Key 只能使用小寫英數與連字號，1–50 字'
  nameError.value = n && n.length <= 50 ? null : '請輸入名稱（最多 50 字）'
  if (!keyError.value && !nameError.value) emit('create', k, n)
}

function cancel() {
  if (!props.busy) emit('cancel')
}
</script>

<template>
  <div v-if="open" class="dialog" role="dialog" aria-modal="true" :aria-labelledby="`${id}-title`" @keydown.esc="cancel">
    <div class="dialog__backdrop" aria-hidden="true" @click="cancel"></div>
    <form class="dialog__panel dialog__panel--form" novalidate @submit.prevent="submit">
      <h2 :id="`${id}-title`" class="dialog__title">新增問題分類</h2>
      <p class="dialog__text">建立後會以範本產生一份草稿，填寫完成再發布。</p>
      <div class="dialog__fields">
        <div class="field">
          <label class="field__label" :for="`${id}-key`">Key</label>
          <div class="input-prefix" :class="{ 'is-invalid': keyError }">
            <span class="input-prefix__text">instruction.</span>
            <input
              :id="`${id}-key`"
              ref="keyInput"
              v-model="key"
              class="input"
              placeholder="phone-opening"
              autocomplete="off"
              :aria-invalid="!!keyError"
            />
          </div>
          <span v-if="keyError" class="field__error" role="alert">{{ keyError }}</span>
          <span v-else class="field__hint">小寫英數與連字號，最多 50 字，建立後不可修改</span>
        </div>
        <div class="field">
          <label class="field__label" :for="`${id}-name`">名稱</label>
          <input
            :id="`${id}-name`"
            v-model="name"
            class="input"
            placeholder="例如：電話開場白"
            maxlength="50"
            :aria-invalid="!!nameError"
          />
          <span v-if="nameError" class="field__error" role="alert">{{ nameError }}</span>
        </div>
      </div>
      <div class="dialog__actions">
        <button class="btn btn--secondary" type="button" :disabled="busy" @click="cancel">取消</button>
        <button class="btn btn--primary" type="submit" :disabled="busy">建立草稿</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.input-prefix {
  display: flex;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.input-prefix:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-focus-ring);
}

.input-prefix.is-invalid {
  border-color: var(--red-600);
}

.input-prefix__text {
  display: grid;
  place-items: center;
  padding: 0 10px;
  background: var(--gray-50);
  border-right: 1px solid var(--color-border-strong);
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.input-prefix .input {
  border: 0;
  border-radius: 0;
  font-family: var(--font-mono);
  font-size: var(--text-sm);
}

.input-prefix .input:focus {
  box-shadow: none;
}

.field__error {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--red-600);
}
</style>
