import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { ConfigVersion } from '../../types'

const api = vi.hoisted(() => ({
  listEvaluations: vi.fn(),
  currentEvaluation: vi.fn(),
  getEvaluation: vi.fn(),
  startEvaluation: vi.fn(),
  evaluationCases: vi.fn(),
}))
vi.mock('../../api/aiAssistantApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/aiAssistantApi')>()),
  aiAssistantApi: api,
}))

import EvaluationView from '../EvaluationView.vue'
import { rec, result, run, runSummary } from './evalFixtures'

const KEY = 'instruction.contact-prep'
const wc = { configKey: KEY, version: 6, docVersion: 5, updatedAt: '2026-10-24T14:58:00+08:00' } as ConfigVersion

const baseProps = {
  configKey: KEY,
  name: '聯繫準備',
  workingCopy: wc as ConfigVersion | null,
  dirty: false,
  canEdit: true,
  nameOf: undefined as ((k: string) => string) | undefined,
}

async function mountView(props: Partial<typeof baseProps> = {}) {
  const w = mount(EvaluationView, { props: { ...baseProps, ...props } })
  await flushPromises()
  return w
}
const runButton = (w: Awaited<ReturnType<typeof mountView>>) => w.findAll('button').find((b) => b.text() === '執行評測')

describe('EvaluationView', () => {
  beforeEach(() => {
    for (const fn of Object.values(api)) fn.mockReset()
    api.listEvaluations.mockResolvedValue([])
    api.currentEvaluation.mockResolvedValue(null)
    api.evaluationCases.mockResolvedValue({ datasetHash: 'h', cases: [] })
  })

  it('hides the run button without edit permission', async () => {
    const w = await mountView({ canEdit: false })
    expect(runButton(w)).toBeUndefined()
  })

  it('enables the run button and starts with the saved docVersion', async () => {
    api.startEvaluation.mockResolvedValue({ runId: 'r9', status: 'RUNNING', totalCases: 16 })
    const w = await mountView()
    expect(runButton(w)!.attributes('disabled')).toBeUndefined()
    await runButton(w)!.trigger('click')
    await flushPromises()
    expect(api.startEvaluation).toHaveBeenCalledWith(KEY, 5)
    w.unmount()
  })

  it('does not leave an unhandled rejection when starting fails', async () => {
    api.startEvaluation.mockRejectedValue(new Error('boom'))
    const w = await mountView()
    await runButton(w)!.trigger('click')
    await flushPromises()
    expect(w.exists()).toBe(true)
  })

  it('disables the button while there are unsaved changes', async () => {
    const w = await mountView({ dirty: true })
    expect(runButton(w)!.attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('請先儲存，評測使用已儲存的內容')
  })

  it('disables the button and says who is running which evaluation', async () => {
    api.currentEvaluation.mockResolvedValue({
      runId: 'x', configKey: 'instruction.other', triggeredBy: 'EEE22222', startedAt: 's', totalCases: 16, completedCases: 7,
    })
    const w = await mountView({ nameOf: (k: string) => (k === 'instruction.other' ? '聯繫準備' : k) })
    expect(runButton(w)!.attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('EEE22222 正在評測「聯繫準備」（7/16）')
    w.unmount()
  })

  it('without a working copy disables the run, explains and hides the version state', async () => {
    const w = await mountView({ workingCopy: null })
    expect(runButton(w)!.attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('沒有編輯中的版本')
    expect(w.text()).not.toContain('尚未評測')
    expect(w.text()).not.toContain('已評測')
  })

  it.each([
    ['尚未評測', []],
    ['已評測', [runSummary()]],
    ['評測後內容已修改', [runSummary({ candidateDocVersion: 4 })]],
  ])('shows version state %s', async (text, history) => {
    api.listEvaluations.mockResolvedValue(history)
    api.getEvaluation.mockResolvedValue(run())
    const w = await mountView()
    expect(w.text()).toContain(text)
  })

  it('uses the newest COMPLETED run for the version state', async () => {
    api.listEvaluations.mockResolvedValue([
      runSummary({ runId: 'f', status: 'FAILED', errorCode: 'AI_EVAL_TIMEOUT', summary: null }),
      runSummary({ runId: 'ok' }),
    ])
    api.getEvaluation.mockResolvedValue(run())
    const w = await mountView()
    expect(w.text()).toContain('已評測')
  })

  it('shows a progress bar for a running evaluation of this key', async () => {
    api.currentEvaluation.mockResolvedValue({
      runId: 'r5', configKey: KEY, triggeredBy: 'EEE22222', startedAt: 's', totalCases: 16, completedCases: 7,
    })
    api.listEvaluations.mockResolvedValue([runSummary({ runId: 'r5', status: 'RUNNING', summary: null, completedCases: 7 })])
    api.getEvaluation.mockResolvedValue(run({ runId: 'r5', status: 'RUNNING', summary: null, completedCases: 7, cases: [rec('Q1', result(), result({ passed: false }))] }))
    const w = await mountView()
    const bar = w.find('[role="progressbar"]')
    expect(bar.attributes('aria-valuenow')).toBe('7')
    expect(bar.attributes('aria-valuemax')).toBe('16')
    expect(w.text()).toContain('執行中 7/16')
    expect(w.text()).toContain('Q1')
    w.unmount()
  })

  it('opens the cases dialog and loads the dataset', async () => {
    api.listEvaluations.mockResolvedValue([runSummary()])
    api.getEvaluation.mockResolvedValue(run())
    const w = await mountView()
    const btn = w.findAll('button').find((b) => b.text() === '查看題目（16）')!
    await btn.trigger('click')
    await flushPromises()
    expect(api.evaluationCases).toHaveBeenCalled()
    expect(w.find('[role="dialog"]').exists()).toBe(true)
  })

  it('shows the latest result and opens another run from the history', async () => {
    api.listEvaluations.mockResolvedValue([runSummary({ runId: 'a' }), runSummary({ runId: 'b' })])
    api.getEvaluation.mockResolvedValue(run({ runId: 'a' }))
    const w = await mountView()
    expect(w.text()).toContain('81%（13/16）')
    await w.findAll('.ev-hist__row')[1]!.trigger('click')
    await flushPromises()
    expect(api.getEvaluation).toHaveBeenLastCalledWith('b')
  })

  it('has no back button', async () => {
    const w = await mountView()
    expect(w.text()).not.toContain('返回')
  })
})
