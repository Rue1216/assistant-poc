<script setup lang="ts">
import { computed, useId } from 'vue'
import type { PromptAdmin } from '../composables/usePromptAdmin'
import { formatTaipei } from '../taipeiTime'
import type { ConfigType, PublishMode, SaveMode } from '../types'
import MarkdownEditor from './MarkdownEditor.vue'
import PromptAssemblyGuide from './PromptAssemblyGuide.vue'
import PublishBar from './PublishBar.vue'
import ValidationPanel from './ValidationPanel.vue'
import './prompt-admin.css'

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

function onSave(mode: SaveMode) {
  void props.admin.save(mode)
}
function onPublish(mode: PublishMode, date: string, time: string) {
  void props.admin.publish(mode, date, time)
}
</script>

<template>
  <section class="pm-editor card" :aria-labelledby="`${id}-title`">
    <header class="pm-editor__head">
      <div class="pm-editor__title-row">
        <h2 :id="`${id}-title`" class="pm-editor__title">{{ wc.name }}</h2>
        <span v-if="scheduledAt" class="badge badge--scheduled">已排程 {{ formatTaipei(scheduledAt) }}</span>
        <span v-else class="badge badge--draft">草稿</span>
      </div>
      <div class="pm-editor__meta">
        <code>{{ wc.configKey }}</code>
        <span class="pm-dot" aria-hidden="true"></span>
        <span v-if="state.published">目前生效：<strong>{{ formatTaipei(state.published.updatedAt) }}</strong> 發布的版本</span>
        <span v-else>尚未發布</span>
        <span class="pm-dot" aria-hidden="true"></span>
        <span>最後儲存：{{ wc.updatedBy }} · {{ formatTaipei(wc.updatedAt) }}</span>
      </div>
    </header>

    <div v-if="configType === 'SYSTEM_PROMPT'" class="pm-banner pm-banner--info" role="note">
      <span class="pm-banner__icon" aria-hidden="true">i</span>
      <div>
        <strong>System Prompt 只有一份，套用於所有角色與所有對話。</strong>
        發布後立即影響每一次回答，請先確認預覽與發布前檢查。
      </div>
    </div>

    <div v-if="scheduledAt" class="pm-banner pm-banner--scheduled" role="status">
      <span class="pm-banner__icon" aria-hidden="true">⏱</span>
      <div>
        <strong>此版本預約於 {{ formatTaipei(scheduledAt) }} 自動發布。</strong>
        按「儲存」會保留排程，並以你為預約者；按「存成草稿」會取消排程。
      </div>
    </div>

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
      <PromptAssemblyGuide
        v-if="configType === 'SYSTEM_PROMPT'"
        :instruction-count="publishedInstructionCount"
        @go-instructions="emit('go-instructions')"
      />

      <div class="pm-form-row">
        <div class="field">
          <label class="field__label" :for="`${id}-name`">名稱</label>
          <input
            :id="`${id}-name`"
            v-model="state.form.name"
            class="input"
            maxlength="50"
            :readonly="!canEdit"
          />
        </div>
        <div class="field">
          <label class="field__label" :for="`${id}-note`">修改說明 <span class="field__hint">（選填，最多 200 字）</span></label>
          <input
            :id="`${id}-note`"
            v-model="state.form.changeNote"
            class="input"
            maxlength="200"
            :readonly="!canEdit"
          />
        </div>
      </div>

      <MarkdownEditor
        v-model="state.form.content"
        :config-type="configType"
        :readonly="!canEdit"
        :baseline="state.published?.content ?? null"
      />

      <ValidationPanel
        :validation="state.validation"
        :errors="state.errors"
        :config-type="configType"
        :checked-content="state.checkedContent"
        :can-check="canEdit"
        :busy="state.busy"
        @check="admin.validate()"
      />
    </div>

    <PublishBar
      v-if="canEdit"
      :status="wc.status === 'SCHEDULED' ? 'SCHEDULED' : 'DRAFT'"
      :scheduled-at="scheduledAt"
      :can-publish="canPublish"
      :busy="state.busy"
      :schedule-error="state.scheduleError"
      @save="onSave"
      @publish="onPublish"
      @discard="emit('discard')"
    />
  </section>
</template>

<style scoped>
.pm-editor {
  min-width: 0;
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
