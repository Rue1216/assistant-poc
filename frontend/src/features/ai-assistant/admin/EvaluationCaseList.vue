<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import SafeMarkdown from '../markdown/SafeMarkdown'
import type { CaseResult, EvalCaseRecord } from '../types'
import { caseChanged, caseErrorMessage } from './evaluationFormat'

const props = defineProps<{ cases: EvalCaseRecord[] }>()

const onlyChanged = ref(false)
const open = reactive(new Set<string>())

const shown = computed(() => (onlyChanged.value ? props.cases.filter(caseChanged) : props.cases))

function toggle(id: string) {
  if (open.has(id)) open.delete(id)
  else open.add(id)
}

const mark = (r: CaseResult | null) => (r === null ? '…' : r.passed ? '✓' : '✗')
const tick = (ok: boolean) => (ok ? '✓' : '✗')

function failures(r: CaseResult, expectRefusal: boolean): string[] {
  const out: string[] = []
  if (r.missingPhrases.length) out.push(`未提到：${r.missingPhrases.join('、')}`)
  if (r.forbiddenPhrases.length) out.push(`出現禁止字詞：${r.forbiddenPhrases.join('、')}`)
  if (!r.toolsOk) out.push('工具選擇不符')
  if (!r.numbersOk) out.push('有未核對的數字')
  const j = r.judge
  if (j && !j.error && j.refused !== expectRefusal) out.push(expectRefusal ? '應拒答但未拒答' : '不應拒答卻拒答')
  return out
}

function judgeText(r: CaseResult): string {
  const j = r.judge
  if (!j) return ''
  if (j.error) return '評審失敗'
  return `評審：${j.comment ? j.comment : '無'}`
}

const unmet = (r: CaseResult) => (r.judge?.rubric ?? []).filter((x) => !x.met).map((x) => x.text)
</script>

<template>
  <div class="ev-cases">
    <label class="ev-cases__filter">
      <input v-model="onlyChanged" type="checkbox" />
      只看結果不同的題目
    </label>
    <p v-if="shown.length === 0" class="ev-cases__empty">沒有符合的題目</p>
    <div v-for="c in shown" :key="c.caseId" class="ev-case">
      <button
        class="ev-case__toggle"
        type="button"
        :aria-expanded="open.has(c.caseId)"
        @click="toggle(c.caseId)"
      >
        <span aria-hidden="true">{{ open.has(c.caseId) ? '▾' : '▸' }}</span>
        <span class="ev-case__id">{{ c.caseId }}</span>
        <span class="ev-case__group">{{ c.group }}</span>
        <span class="ev-case__marks">{{ mark(c.baseline) }} → {{ mark(c.candidate) }}</span>
      </button>
      <div v-if="open.has(c.caseId)" class="ev-case__body">
        <p class="ev-case__question">{{ c.question }}</p>
        <div class="ev-cols">
          <section
            v-for="side in [
              { title: '已發布版本', r: c.baseline },
              { title: '編輯中版本', r: c.candidate },
            ]"
            :key="side.title"
            class="ev-col"
          >
            <h4 class="ev-col__title">{{ side.title }}</h4>
            <p v-if="!side.r" class="ev-col__pending">尚未完成</p>
            <template v-else>
              <p v-if="side.r.outcome === 'FAILED'" class="ev-col__error">
                {{ caseErrorMessage(side.r.errorCode) || '作答失敗' }}
              </p>
              <div v-else class="md"><SafeMarkdown :source="side.r.answer ?? ''" /></div>
              <p class="ev-col__checks">
                工具 {{ tick(side.r.toolsOk) }} 字詞
                {{ tick(side.r.missingPhrases.length === 0 && side.r.forbiddenPhrases.length === 0) }} 數字
                {{ tick(side.r.numbersOk) }}
              </p>
              <ul v-if="failures(side.r, c.expectRefusal).length" class="ev-col__fails">
                <li v-for="f in failures(side.r, c.expectRefusal)" :key="f">{{ f }}</li>
              </ul>
              <p v-if="judgeText(side.r)" class="ev-col__judge" :class="{ 'is-failed': side.r.judge?.error }">
                {{ judgeText(side.r) }}
              </p>
              <ul v-if="unmet(side.r).length" class="ev-col__fails">
                <li v-for="t in unmet(side.r)" :key="t">評分要點未達成：{{ t }}</li>
              </ul>
            </template>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ev-cases__filter {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: var(--text-sm);
}
.ev-cases__empty {
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}
.ev-case {
  border-bottom: 1px solid var(--color-border);
}
.ev-case__toggle {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 8px 12px;
  border: 0;
  background: transparent;
  font: inherit;
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
  color: var(--color-text);
}
.ev-case__toggle:hover {
  background: var(--gray-25);
}
.ev-case__id {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
}
.ev-case__group {
  color: var(--color-text-muted);
}
.ev-case__marks {
  margin-left: auto;
  font-weight: 600;
}
.ev-case__body {
  padding: 4px 12px 14px;
}
.ev-case__question {
  margin-bottom: 8px;
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}
.ev-cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--space-4);
}
.ev-col {
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}
.ev-col__title {
  margin-bottom: 6px;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-muted);
}
.ev-col__error,
.ev-col__fails,
.ev-col__judge.is-failed {
  color: var(--red-700);
}
.ev-col__pending {
  color: var(--color-text-muted);
}
.ev-col__checks,
.ev-col__judge {
  margin-top: 8px;
  font-size: var(--text-sm);
}
.ev-col__fails {
  margin: 4px 0 0;
  padding-left: 18px;
  font-size: var(--text-sm);
}
@media (max-width: 900px) {
  .ev-cols {
    grid-template-columns: 1fr;
  }
}
</style>
