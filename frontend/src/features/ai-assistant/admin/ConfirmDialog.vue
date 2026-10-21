<script setup lang="ts">
import { nextTick, ref, useId, watch } from 'vue'
import './prompt-admin.css'

const props = withDefaults(
  defineProps<{ open: boolean; title: string; confirmLabel?: string; busy?: boolean }>(),
  { confirmLabel: '確定', busy: false },
)
const emit = defineEmits<{ confirm: []; cancel: [] }>()

const id = useId()
const confirmButton = ref<HTMLButtonElement | null>(null)

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    await nextTick()
    confirmButton.value?.focus()
  },
)

function cancel() {
  if (!props.busy) emit('cancel')
}
</script>

<template>
  <div v-if="open" class="dialog" role="dialog" aria-modal="true" :aria-labelledby="`${id}-title`" @keydown.esc="cancel">
    <div class="dialog__backdrop" aria-hidden="true" @click="cancel"></div>
    <div class="dialog__panel">
      <div class="dialog__icon dialog__icon--warn" aria-hidden="true">!</div>
      <h2 :id="`${id}-title`" class="dialog__title">{{ title }}</h2>
      <slot />
      <div class="dialog__actions">
        <button class="btn btn--secondary" type="button" :disabled="busy" @click="cancel">取消</button>
        <button ref="confirmButton" class="btn btn--primary" type="button" :disabled="busy" @click="emit('confirm')">
          {{ confirmLabel }}
        </button>
      </div>
    </div>
  </div>
</template>
