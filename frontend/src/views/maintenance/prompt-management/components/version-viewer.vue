<script setup lang="ts">
import { computed, useId } from 'vue'
import { useToast } from '@/composables/use-toast'
import SafeMarkdown from '@/utils/markdown/safe-markdown'
import type { ConfigVersion, VersionHistoryItem } from '@/beans/prompt-management-bean'
import { APPLICABLE, versionMeta } from '@/views/maintenance/prompt-management/prompt-labels'
import '@/assets/styles/prompt-admin.css'

const props = defineProps<{
  version: ConfigVersion
  versions: VersionHistoryItem[]
  canEdit: boolean
  busy: boolean
  conflict: boolean
  /** edit: the live version on the content tab (edit makes a draft); apply: a history version (apply makes a draft) */
  action: 'edit' | 'apply'
}>()
defineEmits<{ edit: []; apply: [version: number]; reload: [] }>()

const id = useId()
const toast = useToast()

const v = computed(() => props.version)
const meta = computed(() => versionMeta(v.value, props.versions))
const canApply = computed(() => props.canEdit && props.action === 'apply' && APPLICABLE.includes(v.value.status))

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
    <h2 :id="`${id}-title`" class="sr-only">{{ v.name }}</h2>
    <p class="pm-viewer__meta">{{ meta }}</p>

    <div v-if="conflict" class="pm-banner pm-banner--conflict" role="alert">
      <span class="pm-banner__icon" aria-hidden="true">!</span>
      <div><strong>內容已被他人更新。</strong>請重新載入後再操作。</div>
      <button class="btn btn--secondary btn--sm" type="button" @click="$emit('reload')">重新載入</button>
    </div>

    <div class="pm-editor__body">
      <div class="pm-preview"><div class="md"><safe-markdown :source="v.content" /></div></div>
    </div>

    <footer class="pm-actionbar">
      <p v-if="action === 'edit'" class="pm-actionbar__note">編輯會建立一份草稿，發布前不影響線上。</p>
      <div class="pm-actionbar__buttons">
        <button class="btn btn--ghost" type="button" @click="copyMarkdown">複製 Markdown</button>
        <button
          v-if="action === 'edit' && canEdit"
          class="btn btn--primary"
          type="button"
          :disabled="busy"
          @click="$emit('edit')"
        >
          編輯
        </button>
        <button
          v-if="canApply"
          class="btn btn--primary"
          type="button"
          :disabled="busy"
          @click="$emit('apply', v.version)"
        >
          以此版本建立草稿
        </button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.pm-viewer {
  min-width: 0;
}

/* grows with the content so the page scrolls and the sticky action bar stays reachable */
.pm-viewer .pm-preview {
  overflow: visible;
}

.pm-viewer__meta {
  margin: 0;
  padding: var(--space-3) var(--space-6) 0;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}

@media (max-width: 1100px) {
  .pm-viewer__meta {
    padding-left: var(--space-4);
    padding-right: var(--space-4);
  }
}
</style>
