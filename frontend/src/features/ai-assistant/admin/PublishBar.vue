<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { isoToTaipeiDateTime, nextHourTaipei } from '../taipeiTime'
import type { PublishMode, SaveMode } from '../types'
import './prompt-admin.css'

const props = defineProps<{
  status: 'DRAFT' | 'SCHEDULED'
  scheduledAt: string | null
  canPublish: boolean
  busy: boolean
  scheduleError: string | null
}>()
const emit = defineEmits<{
  save: [mode: SaveMode]
  publish: [mode: PublishMode, date: string, time: string]
  discard: []
}>()

const name = useId()
const mode = ref<PublishMode>('IMMEDIATE')
const date = ref('')
const time = ref('')

// 02 section 3.0: a scheduled working copy defaults to 預約發布 with its current time
watch(
  [() => props.status, () => props.scheduledAt],
  ([status, scheduledAt]) => {
    const scheduled = status === 'SCHEDULED' && scheduledAt
    mode.value = scheduled ? 'SCHEDULED' : 'IMMEDIATE'
    const when = scheduled ? isoToTaipeiDateTime(scheduledAt) : nextHourTaipei()
    date.value = when.date
    time.value = when.time
  },
  { immediate: true },
)

const publishLabel = computed(() => {
  if (mode.value === 'IMMEDIATE') return '立即發布'
  return props.status === 'SCHEDULED' ? '更新預約' : '預約發布'
})

const discardBlocked = computed(() => props.status === 'SCHEDULED')

function onDiscard() {
  if (!discardBlocked.value) emit('discard')
}
</script>

<template>
  <footer class="pm-actionbar">
    <fieldset class="pm-publish">
      <legend class="pm-publish__legend">發布方式</legend>
      <label class="radio"><input v-model="mode" type="radio" :name="name" value="IMMEDIATE" /> 立即發布</label>
      <label class="radio"><input v-model="mode" type="radio" :name="name" value="SCHEDULED" /> 預約發布</label>
      <div v-if="mode === 'SCHEDULED'" class="pm-publish__when">
        <input v-model="date" class="input" type="date" aria-label="預約日期" :aria-invalid="!!scheduleError" />
        <input v-model="time" class="input" type="time" aria-label="預約時間" :aria-invalid="!!scheduleError" />
        <span v-if="scheduleError" class="pm-publish__error" role="alert">{{ scheduleError }}</span>
        <span v-else class="field__hint">至少 1 分鐘後、最多 90 天內</span>
      </div>
    </fieldset>
    <div class="pm-actionbar__buttons">
      <button
        class="btn btn--ghost"
        type="button"
        :disabled="busy"
        :aria-disabled="discardBlocked ? 'true' : undefined"
        :title="discardBlocked ? '已排程的版本需先「存成草稿」才能捨棄' : undefined"
        @click="onDiscard"
      >
        捨棄草稿
      </button>
      <button class="btn btn--secondary" type="button" :disabled="busy" @click="emit('save', 'DRAFT')">存成草稿</button>
      <button class="btn btn--secondary" type="button" :disabled="busy" @click="emit('save', 'KEEP_STATUS')">儲存</button>
      <button
        v-if="canPublish"
        class="btn btn--primary"
        type="button"
        :disabled="busy"
        @click="emit('publish', mode, date, time)"
      >
        {{ publishLabel }}
      </button>
    </div>
  </footer>
</template>

<style scoped>
.pm-publish {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
  margin: 0;
  padding: 0;
  border: 0;
  min-width: 0;
}

.pm-publish__legend {
  float: left;
  padding: 0;
  font-size: var(--text-sm);
  font-weight: 600;
}

.pm-publish__when {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.pm-publish__when .input {
  width: auto;
  height: 32px;
}

.pm-publish__when .input[aria-invalid='true'] {
  border-color: var(--red-600);
}

.pm-publish__error {
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--red-600);
}
</style>
