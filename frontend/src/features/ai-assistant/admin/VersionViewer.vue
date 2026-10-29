<script setup lang="ts">
import { computed, useId } from 'vue'
import { useToast } from '../../../platform-mock/useToast'
import SafeMarkdown from '../markdown/SafeMarkdown'
import { formatTaipei, formatTaipeiShort } from '../taipeiTime'
import type { ConfigVersion, VersionHistoryItem } from '../types'
import { APPLICABLE, STATUS_BADGE, STATUS_LABEL } from './promptLabels'
import './prompt-admin.css'

const props = defineProps<{
  version: ConfigVersion
  versions: VersionHistoryItem[]
  workingCopy: ConfigVersion | null
  canEdit: boolean
  busy: boolean
  conflict: boolean
}>()
defineEmits<{ apply: [version: number]; 'create-draft': []; reload: [] }>()

const id = useId()
const toast = useToast()

const v = computed(() => props.version)
const time = computed(() => formatTaipei(v.value.updatedAt))
/** R27: no working copy and this is the live version, so offer 建立草稿 (02 section 3.5) instead of 套用. */
const offerCreateDraft = computed(() => !props.workingCopy && v.value.status === 'PUBLISHED')
const canApply = computed(() => props.canEdit && !offerCreateDraft.value && APPLICABLE.includes(v.value.status))

const appliedFrom = computed(() => {
  if (v.value.basedOnVersion == null) return '—'
  const source = props.versions.find((x) => x.version === v.value.basedOnVersion)
  return source ? `${formatTaipei(source.updatedAt)} 的版本` : '先前的版本'
})

const footerNote = computed(() => {
  if (offerCreateDraft.value) return '建立草稿會以目前生效的內容新增一份草稿，需再發布才會生效。'
  const wc = props.workingCopy
  if (!wc) return '套用會以這個版本的內容新增一份草稿，需再發布才會生效。'
  const current =
    wc.status === 'SCHEDULED' && wc.scheduledAt
      ? `目前預約於 ${formatTaipeiShort(wc.scheduledAt)} 發布的版本會取消排程並標為已捨棄`
      : `目前 ${formatTaipeiShort(wc.updatedAt)} 儲存的草稿會被標為已捨棄`
  return `套用會以這個版本的內容新增一份草稿；${current}，仍可在版本歷程中找回。新草稿需再發布才會生效。`
})

async function copyMarkdown() {
  try {
    await navigator.clipboard.writeText(v.value.content)
    toast.show('已複製 Markdown', 'info')
  } catch {
    toast.show('無法複製，請手動選取內容')
  }
}
</script>

<template>
  <section class="pm-viewer" :aria-labelledby="`${id}-title`">
    <header class="pm-editor__head">
      <div class="pm-editor__title-row">
        <h2 :id="`${id}-title`" class="pm-editor__title">{{ v.name }}</h2>
        <span class="badge" :class="STATUS_BADGE[v.status]">{{ STATUS_LABEL[v.status] }}</span>
      </div>
      <div class="pm-editor__meta">
        <code>{{ v.configKey }}</code>
        <template v-if="v.changeNote">
          <span class="pm-dot" aria-hidden="true"></span>
          <span>修改說明：{{ v.changeNote }}</span>
        </template>
      </div>
    </header>

    <div v-if="conflict" class="pm-banner pm-banner--conflict" role="alert">
      <span class="pm-banner__icon" aria-hidden="true">!</span>
      <div><strong>內容已被他人更新。</strong>請重新載入後再操作。</div>
      <button class="btn btn--secondary btn--sm" type="button" @click="$emit('reload')">重新載入</button>
    </div>

    <div class="pm-editor__body">
      <dl class="pm-verinfo">
        <div><dt>狀態</dt><dd>{{ STATUS_LABEL[v.status] }}</dd></div>
        <div><dt>原作者</dt><dd>{{ v.createdBy }}</dd></div>
        <div>
          <dt>{{ v.status === 'DISCARDED' ? '捨棄' : v.status === 'PUBLISHED' || v.status === 'ARCHIVED' ? '發布' : '最後儲存' }}</dt>
          <dd>{{ v.updatedBy }}・{{ time }}</dd>
        </div>
        <div>
          <dt>{{ v.status === 'DISCARDED' ? '原預約' : '預約時間' }}</dt>
          <dd>{{ v.scheduledAt ? formatTaipei(v.scheduledAt) : '—' }}</dd>
        </div>
        <div><dt>套用來源</dt><dd>{{ appliedFrom }}</dd></div>
      </dl>

      <div class="pm-pane pm-pane--auto">
        <div class="pm-pane__head">
          <span class="pm-pane__title">內容預覽</span>
          <button class="btn btn--ghost btn--sm" type="button" @click="copyMarkdown">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="9" width="11" height="11" rx="2" /><path d="M5 15V6a2 2 0 0 1 2-2h9" /></svg>
            複製 Markdown
          </button>
        </div>
        <div class="pm-preview"><div class="md"><SafeMarkdown :source="v.content" /></div></div>
      </div>
    </div>

    <footer class="pm-actionbar pm-actionbar--viewer">
      <p class="pm-actionbar__note">{{ footerNote }}</p>
      <div class="pm-actionbar__buttons">
        <button
          v-if="offerCreateDraft && canEdit"
          class="btn btn--primary"
          type="button"
          :disabled="busy"
          @click="$emit('create-draft')"
        >
          建立草稿
        </button>
        <button
          v-if="canApply"
          class="btn btn--primary"
          type="button"
          :disabled="busy"
          @click="$emit('apply', v.version)"
        >
          套用此版本
        </button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.pm-viewer {
  min-width: 0;
}

.pm-viewer .pm-preview {
  max-height: 560px;
}

.pm-verinfo {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(118px, 1fr));
  gap: 10px 16px;
  margin: 0;
  padding: 12px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--gray-25);
}

.pm-verinfo dt {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.pm-verinfo dd {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 600;
}
</style>
