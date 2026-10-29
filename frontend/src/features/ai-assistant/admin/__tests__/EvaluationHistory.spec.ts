import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EvaluationHistory from '../EvaluationHistory.vue'
import { runSummary } from './evalFixtures'

describe('EvaluationHistory', () => {
  it('shows time, trigger and the correct-rate change', () => {
    const w = mount(EvaluationHistory, { props: { runs: [runSummary()], selectedId: null } })
    const text = w.find('.ev-hist__row').text()
    expect(text).toContain('10/24 15:07')
    expect(text).toContain('EEE22222')
    expect(text).toContain('81% → 94%')
  })

  it('marks runs whose dataset changed', () => {
    const runs = [runSummary({ datasetChanged: true }), runSummary({ runId: 'r2' })]
    const w = mount(EvaluationHistory, { props: { runs, selectedId: null } })
    const rows = w.findAll('.ev-hist__row')
    expect(rows[0]!.text()).toContain('題目已變更')
    expect(rows[1]!.text()).not.toContain('題目已變更')
  })

  it('labels failed runs by error code', () => {
    const runs = [
      runSummary({ runId: 'a', status: 'FAILED', errorCode: 'AI_EVAL_TIMEOUT', summary: null }),
      runSummary({ runId: 'b', status: 'FAILED', errorCode: 'AI_EVAL_INTERRUPTED', summary: null }),
      runSummary({ runId: 'c', status: 'FAILED', errorCode: 'AI_INTERNAL_ERROR', summary: null }),
    ]
    const w = mount(EvaluationHistory, { props: { runs, selectedId: null } })
    const rows = w.findAll('.ev-hist__row').map((r) => r.text())
    expect(rows[0]).toContain('逾時')
    expect(rows[1]).toContain('已中斷')
    expect(rows[2]).toContain('系統錯誤')
  })

  it('emits select with the run id and marks the selected row', async () => {
    const runs = [runSummary({ runId: 'a' }), runSummary({ runId: 'b' })]
    const w = mount(EvaluationHistory, { props: { runs, selectedId: 'b' } })
    const rows = w.findAll('.ev-hist__row')
    expect(rows[0]!.attributes('aria-current')).toBeUndefined()
    expect(rows[1]!.attributes('aria-current')).toBe('true')
    await rows[0]!.trigger('click')
    expect(w.emitted('select')).toEqual([['a']])
  })
})
