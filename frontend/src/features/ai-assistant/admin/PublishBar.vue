<script setup lang="ts">
import { ref, watch } from 'vue'
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

/** DRAFT only: the date and time row is opened by 預約… and closed by 取消 (a scheduled copy always shows it) */
const scheduling = ref(false)
const date = ref('')
const time = ref('')

// 02 section 3.0: a scheduled working copy starts with its current time, otherwise the next full hour
watch(
  [() => props.status, () => props.scheduledAt],
  ([status, scheduledAt]) => {
    scheduling.value = false
    const when = status === 'SCHEDULED' && scheduledAt ? isoToTaipeiDateTime(scheduledAt) : nextHourTaipei()
    date.value = when.date
    time.value = when.time
  },
  { immediate: true },
)
</script>

<template>
  <footer class="pm-actionbar">
    <div class="pm-actionbar__lead"><slot /></div>
    <div class="pm-actionbar__buttons">
      <button v-if="status === 'SCHEDULED'" class="btn btn--ghost" type="button" :disabled="busy" @click="emit('save', 'DRAFT')">
        取消排程
      </button>
      <button v-else class="btn btn--ghost" type="button" :disabled="busy" @click="emit('discard')">捨棄草稿</button>

      <!-- 已排程: 取消排程 [日期][時間] 儲存 …; 草稿預約中: 儲存 [日期][時間] 取消 預約發布 (spec 3.1) -->
      <template v-if="status === 'SCHEDULED' && canPublish">
        <span class="pm-publish__when">
          <input v-model="date" class="input" type="date" aria-label="預約日期" :aria-invalid="!!scheduleError" />
          <input v-model="time" class="input" type="time" aria-label="預約時間" :aria-invalid="!!scheduleError" />
          <span v-if="scheduleError" class="pm-publish__error" role="alert">{{ scheduleError }}</span>
        </span>
      </template>

      <button class="btn btn--secondary" type="button" :disabled="busy" @click="emit('save', 'KEEP_STATUS')">儲存</button>

      <template v-if="status === 'DRAFT' && scheduling && canPublish">
        <span class="pm-publish__when">
          <input v-model="date" class="input" type="date" aria-label="預約日期" :aria-invalid="!!scheduleError" />
          <input v-model="time" class="input" type="time" aria-label="預約時間" :aria-invalid="!!scheduleError" />
          <span v-if="scheduleError" class="pm-publish__error" role="alert">{{ scheduleError }}</span>
        </span>
      </template>

      <template v-if="canPublish">
        <template v-if="status === 'SCHEDULED'">
          <button class="btn btn--secondary" type="button" :disabled="busy" @click="emit('publish', 'IMMEDIATE', date, time)">
            立即發布
          </button>
          <button class="btn btn--primary" type="button" :disabled="busy" @click="emit('publish', 'SCHEDULED', date, time)">
            更新預約
          </button>
        </template>
        <template v-else-if="scheduling">
          <button class="btn btn--link" type="button" :disabled="busy" @click="scheduling = false">取消</button>
          <button class="btn btn--primary" type="button" :disabled="busy" @click="emit('publish', 'SCHEDULED', date, time)">
            預約發布
          </button>
        </template>
        <template v-else>
          <button class="btn btn--secondary" type="button" :disabled="busy" @click="scheduling = true">預約…</button>
          <button class="btn btn--primary" type="button" :disabled="busy" @click="emit('publish', 'IMMEDIATE', date, time)">
            立即發布
          </button>
        </template>
      </template>
    </div>
  </footer>
</template>

<style scoped>
.pm-actionbar__lead {
  flex: 1 1 240px;
  min-width: 0;
}

.pm-actionbar__buttons {
  align-items: center;
}

.pm-publish__when {
  display: inline-flex;
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

.btn--link {
  padding: 0 4px;
  border: 0;
  background: none;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
  text-decoration: underline;
  cursor: pointer;
}

.btn--link:hover:not(:disabled) {
  color: var(--color-text);
}

.btn--link:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
