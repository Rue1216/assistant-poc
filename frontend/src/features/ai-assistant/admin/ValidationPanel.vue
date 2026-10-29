<script setup lang="ts">
import { computed } from 'vue'
import type { ValidationState } from '../composables/usePromptAdmin'
import type { ConfigType, ValidationIssue } from '../types'
import { passedRuleDescriptions } from './promptLabels'

const props = defineProps<{
  validation: ValidationState
  errors: ValidationIssue[]
  configType: ConfigType
  /** The content the last check ran on. */
  checkedContent: string
  /** The form differs from the saved working copy, so the shown result may be stale. */
  dirty: boolean
}>()

const passed = computed(() => passedRuleDescriptions(props.configType, props.checkedContent))
const settled = computed(() => props.validation === 'passed' || props.validation === 'failed')
</script>

<template>
  <div v-if="validation === 'checking'" class="pm-validate pm-validate--checking" aria-live="polite">檢查中…</div>
  <details
    v-else-if="settled"
    class="pm-validate"
    :class="{ 'is-fail': validation === 'failed' }"
    :open="validation === 'failed'"
    aria-label="發布前檢查"
  >
    <summary class="pm-validate__summary" :class="validation === 'failed' ? 'pm-validate__summary--fail' : 'pm-validate__summary--ok'">
      {{ validation === 'failed' ? `✕ ${errors.length} 項未通過` : '✓ 發布前檢查通過' }}<small v-if="dirty" class="pm-validate__stale">・儲存後重新檢查</small>
    </summary>
    <ul v-if="validation === 'passed'" class="pm-validate__list pm-validate__list--ok">
      <li v-for="row in passed" :key="row" class="is-ok">{{ row }}</li>
    </ul>
    <ul v-else class="pm-validate__list pm-validate__list--fail" role="alert">
      <li v-for="(e, i) in errors" :key="i" class="is-error"><code>{{ e.rule }}</code>{{ e.message }}</li>
    </ul>
  </details>
</template>

<style scoped>
.pm-validate {
  font-size: var(--text-sm);
}

.pm-validate--checking {
  color: var(--color-text-subtle);
}

.pm-validate__summary {
  display: inline;
  cursor: pointer;
  font-weight: 600;
}

.pm-validate__summary--ok { color: var(--green-600); }
.pm-validate__summary--fail { color: var(--red-600); }

.pm-validate__stale {
  margin-left: 4px;
  font-size: var(--text-xs);
  color: var(--color-text-subtle);
}

.pm-validate__list {
  list-style: none;
  padding: 0;
  margin-top: 8px;
  display: grid;
  gap: 6px;
}

.pm-validate__list li {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.pm-validate__list li::before {
  flex: none;
  display: inline-grid;
  place-items: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  font-size: 10px;
  font-weight: 700;
  transform: translateY(2px);
}

.pm-validate__list .is-ok::before {
  content: '✓';
  background: var(--green-100);
  color: var(--green-700);
}

.pm-validate__list .is-error {
  color: var(--red-700);
}

.pm-validate__list .is-error::before {
  content: '✕';
  background: var(--red-50);
  color: var(--red-600);
}

.pm-validate__list code {
  flex: none;
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 0 5px;
  border-radius: 4px;
  background: var(--red-50);
  border: 1px solid var(--red-200);
}
</style>
