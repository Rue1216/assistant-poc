<script setup lang="ts">
import { nextTick, ref, useId, watch } from 'vue'
import type { EvalCases } from '../types'
import './prompt-admin.css'

const props = defineProps<{
  open: boolean
  cases: EvalCases | null
  /** Display name of a category by configKey (falls back to the key). */
  nameOf: (configKey: string) => string
}>()
const emit = defineEmits<{ close: [] }>()

const id = useId()
const closeButton = ref<HTMLButtonElement | null>(null)

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    await nextTick()
    closeButton.value?.focus()
  },
)

/** Absent or null reads as —; 'none' (expects no category) as 無. */
const expectedCategory = (key: string | null | undefined) => (key == null ? '—' : key === 'none' ? '無' : props.nameOf(key))

const list = (items: string[]) => (items.length ? items.join('、') : '—')
</script>

<template>
  <div v-if="open" class="dialog" role="dialog" aria-modal="true" :aria-labelledby="`${id}-title`" @keydown.esc="emit('close')">
    <div class="dialog__backdrop" aria-hidden="true" @click="emit('close')"></div>
    <div class="dialog__panel ev-dialog">
      <h2 :id="`${id}-title`" class="dialog__title">評測題目（唯讀）</h2>
      <p v-if="!cases" class="dialog__text">載入中…</p>
      <div v-else class="ev-dialog__scroll">
        <table class="ev-dialog__table">
          <thead>
            <tr>
              <th class="ev-dialog__th" scope="col">題號</th>
              <th class="ev-dialog__th" scope="col">分組</th>
              <th class="ev-dialog__th" scope="col">存取等級／顧客</th>
              <th class="ev-dialog__th" scope="col">問題</th>
              <th class="ev-dialog__th" scope="col">預期工具</th>
              <th class="ev-dialog__th" scope="col">應拒答</th>
              <th class="ev-dialog__th" scope="col">預期分類</th>
              <th class="ev-dialog__th" scope="col">評分要點</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in cases.cases" :key="c.id">
              <td>{{ c.id }}</td>
              <td>{{ c.group }}</td>
              <td>{{ c.accessLevel }}／{{ c.certNo }}</td>
              <td>{{ c.question }}</td>
              <td>{{ list(c.expect.tools) }}</td>
              <td>{{ c.expect.refusal ? '是' : '否' }}</td>
              <td>{{ expectedCategory(c.expect.category) }}</td>
              <td>
                <ul v-if="c.rubric.length" class="ev-dialog__rubric">
                  <li v-for="r in c.rubric" :key="r">{{ r }}</li>
                </ul>
                <span v-else>—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="dialog__actions">
        <button ref="closeButton" class="btn btn--secondary" type="button" @click="emit('close')">關閉</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ev-dialog {
  width: min(1100px, 94vw);
  max-width: none;
}
.ev-dialog__scroll {
  max-height: 60vh;
  overflow: auto;
}
.ev-dialog__table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--text-sm);
}
.ev-dialog__table th,
.ev-dialog__table td {
  padding: 6px 10px;
  text-align: left;
  vertical-align: top;
  border-bottom: 1px solid var(--color-border);
}
.ev-dialog__th {
  white-space: nowrap;
}
.ev-dialog__rubric {
  margin: 0;
  padding-left: 16px;
}
</style>
