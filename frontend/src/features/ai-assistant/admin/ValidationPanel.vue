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
  canCheck: boolean
  busy: boolean
}>()
defineEmits<{ check: [] }>()

const passed = computed(() => passedRuleDescriptions(props.configType, props.checkedContent))
</script>

<template>
  <section class="pm-validate" :class="{ 'is-fail': validation === 'failed' }" aria-label="發布前檢查">
    <div class="pm-validate__head">
      <span class="pm-validate__title">發布前檢查</span>
      <span v-if="validation === 'passed'" class="pm-validate__summary pm-validate__summary--ok">全部通過</span>
      <span v-else-if="validation === 'failed'" class="pm-validate__summary pm-validate__summary--fail">
        {{ errors.length }} 項未通過
      </span>
      <span v-else class="pm-validate__summary pm-validate__summary--idle">尚未檢查</span>
      <button
        v-if="canCheck"
        class="btn btn--ghost btn--sm"
        type="button"
        title="有未儲存的修改時，會先儲存再檢查"
        :disabled="busy"
        @click="$emit('check')"
      >
        重新檢查
      </button>
    </div>
    <ul v-if="validation === 'passed'" class="pm-validate__list pm-validate__list--ok">
      <li v-for="row in passed" :key="row" class="is-ok">{{ row }}</li>
    </ul>
    <ul v-else-if="validation === 'failed'" class="pm-validate__list pm-validate__list--fail" role="alert">
      <li v-for="(e, i) in errors" :key="i" class="is-error"><code>{{ e.rule }}</code>{{ e.message }}</li>
    </ul>
  </section>
</template>

<style scoped>
.pm-validate {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--gray-25);
  padding: 12px 16px;
}

.pm-validate.is-fail {
  background: var(--red-50);
  border-color: var(--red-200);
}

.pm-validate__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.pm-validate__title {
  font-weight: 600;
  font-size: var(--text-sm);
}

.pm-validate__summary {
  font-size: var(--text-xs);
  font-weight: 600;
}

.pm-validate__summary--ok { color: var(--green-600); }
.pm-validate__summary--fail { color: var(--red-600); }
.pm-validate__summary--idle { color: var(--color-text-subtle); font-weight: 400; }

.pm-validate__head .btn {
  margin-left: auto;
}

.pm-validate__list {
  list-style: none;
  padding: 0;
  margin-top: 8px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 6px 16px;
  font-size: var(--text-sm);
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

.pm-validate__list--fail {
  grid-template-columns: 1fr;
}
</style>
