import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, effectScope, h, ref, type EffectScope } from 'vue'
import type { ConfigVersion, EvalCase } from '@/beans/prompt-management-bean'

const api = vi.hoisted(() => ({
  listEvaluations: vi.fn(),
  currentEvaluation: vi.fn(),
  getEvaluation: vi.fn(),
  startEvaluation: vi.fn(),
  evaluationCases: vi.fn(),
}))
vi.mock('@/views/maintenance/prompt-management/prompt-management-api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/views/maintenance/prompt-management/prompt-management-api')>()),
  promptManagementApi: api,
}))

import { POLL_MS, usePromptEvaluation } from '@/composables/use-prompt-evaluation'
import EvaluationView from '@/views/maintenance/prompt-management/components/evaluation-view.vue'
import { rec, result, run, runSummary, summary } from '../../../../fixtures/eval-fixtures'

const KEY = 'instruction.contact-prep'
const wc = { configKey: KEY, version: 6, docVersion: 5, updatedAt: '2026-10-24T14:58:00+08:00' } as ConfigVersion

const baseProps = {
  configKey: KEY,
  name: '聯繫準備',
  workingCopy: wc as ConfigVersion | null,
  dirty: false,
  canEdit: true,
  busy: false,
  nameOf: undefined as ((k: string) => string) | undefined,
}

const dataset = (n: number): EvalCase[] =>
  Array.from({ length: n }, (_, i) => ({
    id: `Q${i}`, group: '顧客經營', accessLevel: 'A', certNo: '1', question: '問題', rubric: [],
    expect: { tools: [], forbiddenTools: [], refusal: false, mustContain: [], mustNotContain: [], toolOrder: [], category: null },
  }))

const scopes: EffectScope[] = []
const mounted: VueWrapper[] = []

/** The composable lives in the page: the test owns it the same way and refreshes it before mounting the view. */
async function mountView(
  props: Partial<typeof baseProps> = {},
  beforeMount?: (e: ReturnType<typeof usePromptEvaluation>) => void,
) {
  const scope = effectScope()
  scopes.push(scope)
  const evaluation = scope.run(() => usePromptEvaluation(ref(KEY)))!
  await evaluation.refresh()
  beforeMount?.(evaluation)
  const Wrapper = defineComponent({
    emits: ['save-and-run'],
    setup(_, { emit }) {
      return () => h(EvaluationView, { evaluation, ...baseProps, ...props, onSaveAndRun: () => emit('save-and-run') })
    },
  })
  const w = mount(Wrapper)
  mounted.push(w)
  await flushPromises()
  return w
}

const verdict = (w: VueWrapper) => w.find('.ev-verdict')
const title = (w: VueWrapper) => verdict(w).find('.ev-verdict__title').text()
const buttonTexts = (w: VueWrapper) => verdict(w).findAll('button').map((b) => b.text())
const button = (w: VueWrapper, text: string) => verdict(w).findAll('button').find((b) => b.text() === text)!
const link = (w: VueWrapper, text: string) => w.findAll('button').find((b) => b.text() === text)!

describe('EvaluationView', () => {
  beforeEach(() => {
    for (const fn of Object.values(api)) fn.mockReset()
    api.listEvaluations.mockResolvedValue([])
    api.currentEvaluation.mockResolvedValue(null)
    api.getEvaluation.mockResolvedValue(run())
    api.evaluationCases.mockResolvedValue({ datasetHash: 'h', cases: [] })
  })

  afterEach(() => {
    mounted.splice(0).forEach((w) => w.unmount())
    scopes.splice(0).forEach((s) => s.stop())
  })

  it('does not call the evaluation api on its own and drops the old title rows', async () => {
    const w = await mountView()
    expect(api.listEvaluations).toHaveBeenCalledTimes(1) // only the test wrapper's refresh
    expect(w.text()).not.toContain('評測：')
    expect(w.text()).not.toContain('最近一次結果')
    expect(w.text()).not.toContain('編輯中版本')
  })

  describe('verdict card', () => {
    it('1. shows progress while this config is evaluating, without buttons', async () => {
      api.currentEvaluation.mockResolvedValue({ runId: 'r5', configKey: KEY, triggeredBy: 'EEE22222', startedAt: 's', totalCases: 16, completedCases: 7 })
      api.listEvaluations.mockResolvedValue([runSummary({ runId: 'r5', status: 'RUNNING', summary: null, completedCases: 7 })])
      api.getEvaluation.mockResolvedValue(run({ runId: 'r5', status: 'RUNNING', summary: null, completedCases: 7, cases: [rec('Q1', result(), result({ passed: false }))] }))
      const w = await mountView()
      expect(title(w)).toBe('評測中 7/16')
      expect(buttonTexts(w)).toEqual([])
      const bar = verdict(w).find('[role="progressbar"]')
      expect(bar.attributes('aria-valuenow')).toBe('7')
      expect(bar.attributes('aria-valuemax')).toBe('16')
      expect(w.find('.ev-case').exists()).toBe(true)
    })

    it('2. says who is evaluating another config, without buttons', async () => {
      api.currentEvaluation.mockResolvedValue({ runId: 'x', configKey: 'instruction.other', triggeredBy: 'EEE22222', startedAt: 's', totalCases: 16, completedCases: 7 })
      const w = await mountView({ nameOf: (k) => (k === 'instruction.other' ? '顧客概況' : k) })
      expect(title(w)).toBe('EEE22222 正在評測「顧客概況」（7/16），完成後才能執行')
      expect(buttonTexts(w)).toEqual([])
    })

    it('2. leaves out the prefix when the trigger is unknown', async () => {
      api.currentEvaluation.mockResolvedValue({ runId: 'x', configKey: 'instruction.other', triggeredBy: '', startedAt: 's', totalCases: 16, completedCases: 7 })
      const w = await mountView({ nameOf: () => '顧客概況' })
      expect(title(w)).toBe('正在評測「顧客概況」（7/16），完成後才能執行')
    })

    it('3. without a working copy and without history says there is no record', async () => {
      const w = await mountView({ workingCopy: null })
      expect(title(w)).toBe('還沒有評測紀錄')
      expect(verdict(w).text()).toContain('評測會比較草稿和目前生效的版本，請先在內容分頁按「編輯」')
      expect(buttonTexts(w)).toEqual([])
    })

    it('3. without a working copy still shows the latest conclusion', async () => {
      api.listEvaluations.mockResolvedValue([runSummary()])
      const w = await mountView({ workingCopy: null })
      expect(title(w)).toBe('草稿比目前生效的版本好')
      expect(verdict(w).text()).toContain('請先在內容分頁按「編輯」')
      expect(buttonTexts(w)).toEqual([])
    })

    it('4. warns about unsaved changes and offers save and run', async () => {
      const w = await mountView({ dirty: true })
      expect(title(w)).toBe('有未儲存的修改，評測只會用已儲存的內容')
      expect(buttonTexts(w)).toEqual(['儲存並評測'])
      expect(button(w, '儲存並評測').attributes('disabled')).toBeUndefined()
    })

    it('4. disables save and run while busy', async () => {
      const w = await mountView({ dirty: true, busy: true })
      expect(button(w, '儲存並評測').attributes('disabled')).toBeDefined()
    })

    it('4. emits save-and-run', async () => {
      const w = await mountView({ dirty: true })
      await button(w, '儲存並評測').trigger('click')
      expect(w.emitted('save-and-run')).toHaveLength(1)
    })

    it('5. a failed run offers to re-run', async () => {
      api.listEvaluations.mockResolvedValue([runSummary({ status: 'FAILED', errorCode: 'AI_EVAL_TIMEOUT', summary: null })])
      api.getEvaluation.mockResolvedValue(run({ status: 'FAILED', errorCode: 'AI_EVAL_TIMEOUT', summary: null }))
      const w = await mountView()
      expect(title(w)).toBe('這次評測沒有完成，已完成的題目仍列在下方')
      expect(buttonTexts(w)).toEqual(['重新評測'])
    })

    it('5. a failed run of an older save of the draft falls through to the not-evaluated state', async () => {
      const failed = { status: 'FAILED' as const, errorCode: 'AI_EVAL_TIMEOUT', summary: null, candidateDocVersion: 3 }
      api.listEvaluations.mockResolvedValue([runSummary(failed)])
      api.getEvaluation.mockResolvedValue(run(failed))
      const w = await mountView({ workingCopy: { ...wc, docVersion: 4 } })
      expect(title(w)).toBe('這份草稿還沒評測')
    })

    it('5. a failed run of an older save after a completed one falls through to the modified state', async () => {
      const failed = { runId: 'r2', status: 'FAILED' as const, errorCode: 'AI_EVAL_TIMEOUT', summary: null, candidateDocVersion: 3 }
      api.listEvaluations.mockResolvedValue([runSummary(failed), runSummary({ candidateDocVersion: 3 })])
      api.getEvaluation.mockResolvedValue(run(failed))
      const w = await mountView({ workingCopy: { ...wc, docVersion: 4 } })
      expect(title(w)).toBe('評測後內容已修改，結果可能不準')
    })

    it('5. a failed run of another version of the draft does not count', async () => {
      const failed = { status: 'FAILED' as const, errorCode: 'AI_EVAL_TIMEOUT', summary: null, candidateVersion: 5 }
      api.listEvaluations.mockResolvedValue([runSummary(failed)])
      api.getEvaluation.mockResolvedValue(run(failed))
      const w = await mountView()
      expect(title(w)).toBe('這份草稿還沒評測')
    })

    it('6. an unevaluated draft says how many cases will be compared', async () => {
      const w = await mountView({}, (e) => {
        e.state.cases = { datasetHash: 'h', cases: dataset(17) }
      })
      expect(title(w)).toBe('這份草稿還沒評測')
      expect(verdict(w).text()).toContain('會用 17 題分別問目前生效的版本和草稿，比較兩者的回答，約需 2–3 分鐘。')
      expect(buttonTexts(w)).toEqual(['執行評測'])
      expect(button(w, '執行評測').classes()).toContain('btn--primary')
    })

    it('6. says 固定題目 while the case count is unknown', async () => {
      const w = await mountView()
      expect(verdict(w).text()).toContain('會用固定題目分別問目前生效的版本和草稿')
      expect(verdict(w).text()).not.toContain('null')
    })

    it('6. starts the run with the saved docVersion', async () => {
      api.startEvaluation.mockResolvedValue({ runId: 'r9', status: 'RUNNING', totalCases: 16 })
      const w = await mountView()
      await button(w, '執行評測').trigger('click')
      await flushPromises()
      expect(api.startEvaluation).toHaveBeenCalledWith(KEY, 5)
      expect(title(w)).toBe('評測中 0/16')
    })

    it('does not leave an unhandled rejection when starting fails', async () => {
      api.startEvaluation.mockRejectedValue(new Error('boom'))
      const w = await mountView()
      await button(w, '執行評測').trigger('click')
      await flushPromises()
      expect(w.exists()).toBe(true)
    })

    it('7. a modified draft is marked as possibly inaccurate', async () => {
      api.listEvaluations.mockResolvedValue([runSummary({ candidateDocVersion: 4 })])
      const w = await mountView()
      expect(title(w)).toBe('評測後內容已修改，結果可能不準')
      expect(verdict(w).classes()).toContain('ev-verdict--neutral')
      expect(buttonTexts(w)).toEqual(['重新評測'])
    })

    it.each([
      ['better', summary(), '草稿比目前生效的版本好'],
      ['worse', summary({ correct: 15 }, { correct: 13 }), '草稿有指標變差'],
      ['same', summary({}, { correct: 13 }), '草稿和目前生效的版本差不多'],
    ])('8. an evaluated draft with verdict %s', async (tone, sum, text) => {
      api.listEvaluations.mockResolvedValue([runSummary({ summary: sum })])
      api.getEvaluation.mockResolvedValue(run({ summary: sum }))
      const w = await mountView()
      expect(title(w)).toBe(text)
      expect(verdict(w).classes()).toContain(`ev-verdict--${tone}`)
      expect(buttonTexts(w)).toEqual(['重新評測'])
    })

    it('8. shows the subtitle with the changed metrics', async () => {
      api.listEvaluations.mockResolvedValue([runSummary()])
      const w = await mountView()
      expect(verdict(w).text()).toContain('正確率 81% → 94%')
    })

    it.each([
      ['unsaved', { dirty: true }, []],
      ['unevaluated', {}, []],
      ['evaluated', {}, [runSummary()]],
    ])('shows no button without edit permission (%s)', async (_n, props, history) => {
      api.listEvaluations.mockResolvedValue(history)
      const w = await mountView({ ...props, canEdit: false })
      expect(buttonTexts(w)).toEqual([])
    })
  })

  it('collapses the full metrics by default', async () => {
    api.listEvaluations.mockResolvedValue([runSummary()])
    const w = await mountView()
    const details = w.find('details.ev-metrics')
    expect(details.exists()).toBe(true)
    expect(details.attributes('open')).toBeUndefined()
    expect(details.find('summary').text()).toContain('全部指標')
    expect(details.text()).toContain('正確率')
  })

  it('shows the history only after the link is pressed', async () => {
    api.listEvaluations.mockResolvedValue([runSummary()])
    const w = await mountView()
    expect(w.find('.ev-hist__row').exists()).toBe(false)
    await link(w, '評測歷程（1）').trigger('click')
    expect(w.findAll('.ev-hist__row')).toHaveLength(1)
  })

  it('opens the cases dialog from the 題庫 link and loads the dataset', async () => {
    api.evaluationCases.mockResolvedValue({ datasetHash: 'h', cases: dataset(16) })
    const w = await mountView()
    await w.findAll('button').find((b) => b.text().startsWith('題庫'))!.trigger('click')
    await flushPromises()
    expect(api.evaluationCases).toHaveBeenCalled()
    expect(w.find('[role="dialog"]').exists()).toBe(true)
    expect(link(w, '題庫（16 題）')).toBeDefined()
  })

  it('offers 回到最新 when an older run is on screen and reloads the latest one', async () => {
    api.listEvaluations.mockResolvedValue([runSummary({ runId: 'a' }), runSummary({ runId: 'b', startedAt: '2026-10-23T10:00:00+08:00' })])
    api.getEvaluation.mockImplementation(async (id: string) =>
      run({ runId: id, startedAt: id === 'b' ? '2026-10-23T10:00:00+08:00' : '2026-10-24T15:07:00+08:00' }),
    )
    const w = await mountView()
    expect(w.text()).not.toContain('回到最新')
    await link(w, '評測歷程（2）').trigger('click')
    await w.findAll('.ev-hist__row')[1]!.trigger('click')
    await flushPromises()
    expect(api.getEvaluation).toHaveBeenLastCalledWith('b')
    expect(w.text()).toContain('正在看 10/23 10:00 的結果')
    await link(w, '回到最新').trigger('click')
    await flushPromises()
    expect(api.getEvaluation).toHaveBeenLastCalledWith('a')
    expect(w.text()).not.toContain('回到最新')
  })

  it('does not offer 回到最新 while this config re-evaluates', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    try {
      api.listEvaluations.mockResolvedValue([runSummary({ runId: 'a' })])
      api.getEvaluation.mockImplementation(async (id: string) =>
        id === 'r9' ? run({ runId: 'r9', status: 'RUNNING', summary: null, completedCases: 1 }) : run({ runId: 'a' }),
      )
      api.startEvaluation.mockResolvedValue({ runId: 'r9', status: 'RUNNING', totalCases: 16 })
      const w = await mountView({}, (e) => {
        e.state.history = [runSummary({ runId: 'a', candidateDocVersion: 4 })]
      })
      await button(w, '重新評測').trigger('click')
      await flushPromises()
      await vi.advanceTimersByTimeAsync(POLL_MS)
      await flushPromises()
      expect(api.getEvaluation).toHaveBeenLastCalledWith('r9')
      expect(w.text()).not.toContain('回到最新')
      expect(title(w)).toMatch(/^評測中/)
    } finally {
      vi.useRealTimers()
    }
  })

  it('says there is no comparable result when an evaluated run has no summary', async () => {
    api.listEvaluations.mockResolvedValue([runSummary({ summary: null })])
    api.getEvaluation.mockResolvedValue(run({ summary: null }))
    const w = await mountView()
    expect(title(w)).toBe('這次評測沒有可比較的結果')
    expect(verdict(w).classes()).toContain('ev-verdict--neutral')
    expect(buttonTexts(w)).toEqual(['重新評測'])
  })

  it('has no back button', async () => {
    const w = await mountView()
    expect(w.text()).not.toContain('返回')
  })
})
