<script setup lang="ts">
import { computed, useId, watch } from 'vue'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import type { PromptAdmin } from '@/composables/use-prompt-admin'
import type { ConfigType, PublishMode, SaveMode } from '@/beans/prompt-management-bean'
import MarkdownEditor from '@/views/maintenance/prompt-management/components/markdown-editor.vue'
import PublishBar from '@/views/maintenance/prompt-management/components/publish-bar.vue'
import ValidationPanel from '@/views/maintenance/prompt-management/components/validation-panel.vue'
import { draftFormSchema } from '@/views/maintenance/prompt-management/prompt-management-schema'
import '@/assets/styles/prompt-admin.css'

const props = defineProps<{
  /** usePromptAdmin of the selected key; must have a working copy. The form fields bind to its state. */
  admin: PromptAdmin
  configType: ConfigType
  publishedInstructionCount: number
  canEdit: boolean
  canPublish: boolean
}>()
/** discard and reload may throw away unsaved edits, so the page confirms them first (R29) */
const emit = defineEmits<{ 'go-instructions': []; discard: []; reload: [] }>()

const id = useId()
const state = computed(() => props.admin.state)
const wc = computed(() => props.admin.state.workingCopy!)
const scheduledAt = computed(() => (wc.value.status === 'SCHEDULED' ? wc.value.scheduledAt : null))

// The field values stay in usePromptAdmin (dirty tracking, reload and save read them there); this form
// mirrors name and change note so they are validated before a save or publish goes out.
const { errors, validate, setValues } = useForm({ validationSchema: toTypedSchema(draftFormSchema) })
let validated = false
watch(
  () => [state.value.form.name, state.value.form.changeNote] as const,
  ([name, changeNote]) => setValues({ name, changeNote }, validated),
  { immediate: true },
)

/** After the first attempt the errors follow the fields as they are edited. */
async function validateForm(): Promise<boolean> {
  validated = true
  return (await validate()).valid
}

async function onSave(mode: SaveMode): Promise<void> {
  if (await validateForm()) void props.admin.save(mode)
}
async function onPublish(mode: PublishMode, date: string, time: string): Promise<void> {
  if (await validateForm()) void props.admin.publish(mode, date, time)
}
</script>

<template>
  <section class="pm-editor" aria-label="編輯內容">
    <div v-if="state.conflict" class="pm-banner pm-banner--conflict" role="alert">
      <span class="pm-banner__icon" aria-hidden="true">!</span>
      <div>
        <strong>內容已被他人更新。</strong>
        請重新載入後再編輯；在你按「重新載入」之前，畫面上的內容不會被清除。
      </div>
      <button class="btn btn--secondary btn--sm" type="button" :disabled="state.loading" @click="emit('reload')">
        重新載入
      </button>
    </div>

    <div class="pm-editor__body">
      <p v-if="configType === 'SYSTEM_PROMPT'" class="pm-editor__hint">
        這份會和 {{ publishedInstructionCount }} 份已發布的問題分類一起送給模型，影響所有對話。
        <button class="btn btn--ghost btn--sm" type="button" @click="emit('go-instructions')">查看問題分類 →</button>
      </p>

      <div class="pm-form-row">
        <div class="field">
          <label class="field__label" :for="`${id}-name`">名稱</label>
          <input
            :id="`${id}-name`"
            v-model="state.form.name"
            class="input"
            maxlength="50"
            :readonly="!canEdit"
            :aria-invalid="!!errors.name"
          />
          <span v-if="errors.name" class="field__error" role="alert">{{ errors.name }}</span>
        </div>
        <div class="field">
          <label class="field__label" :for="`${id}-note`">修改說明</label>
          <input
            :id="`${id}-note`"
            v-model="state.form.changeNote"
            class="input"
            maxlength="200"
            placeholder="選填，例如：補充無權限時的回覆方式"
            :readonly="!canEdit"
            :aria-invalid="!!errors.changeNote"
          />
          <span v-if="errors.changeNote" class="field__error" role="alert">{{ errors.changeNote }}</span>
        </div>
      </div>

      <markdown-editor
        v-model="state.form.content"
        :config-type="configType"
        :readonly="!canEdit"
        :baseline="state.published?.content ?? null"
      />
    </div>

    <publish-bar
      v-if="canEdit"
      :status="wc.status === 'SCHEDULED' ? 'SCHEDULED' : 'DRAFT'"
      :scheduled-at="scheduledAt"
      :can-publish="canPublish"
      :busy="state.busy"
      :schedule-error="state.scheduleError"
      @save="onSave"
      @publish="onPublish"
      @discard="emit('discard')"
    >
      <validation-panel
        :validation="state.validation"
        :errors="state.errors"
        :config-type="configType"
        :checked-content="state.checkedContent"
        :dirty="admin.dirty.value"
      />
    </publish-bar>
  </section>
</template>

<style scoped>
.pm-editor {
  min-width: 0;
}

.pm-editor__hint {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

.pm-form-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
  gap: var(--space-4);
}

@media (max-width: 1100px) {
  .pm-form-row {
    grid-template-columns: 1fr;
  }
}
</style>
