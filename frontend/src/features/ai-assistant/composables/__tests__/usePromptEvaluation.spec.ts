import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import type { CurrentEvaluation, EvalRun, EvalRunSummary, EvalStatus } from '../../types'

const api = vi.hoisted(() => ({
  startEvaluation: vi.fn(),
  listEvaluations: vi.fn(),
  currentEvaluation: vi.fn(),
  getEvaluation: vi.fn(),
  evaluationCases: vi.fn(),
}))
vi.mock('../../api/aiAssistantApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/aiAssistantApi')>()),
  aiAssistantApi: api,
}))

import { ApiError } from '../../../../platform-mock/apiClient'
import { POLL_MS, usePromptEvaluation } from '../usePromptEvaluation'

const KEY = 'instruction.contact-prep'

function summary(runId: string, status: EvalStatus = 'COMPLETED', completed = 16): EvalRunSummary {
  return {
    runId, status, errorCode: null, triggeredBy: 'EEE22222', startedAt: 's', finishedAt: status === 'RUNNING' ? null : 'f',
    candidateVersion: 6, candidateUpdatedAt: 'u', candidateDocVersion: 5, datasetChanged: false, totalCases: 16,
    completedCases: completed, summary: null,
  }
}

function detail(runId: string, status: EvalStatus = 'COMPLETED', completed = 16): EvalRun {
  return { ...summary(runId, status, completed), configKey: KEY, cases: [] }
}

function current(runId: string, configKey = KEY): CurrentEvaluation {
  return { runId, configKey, triggeredBy: 'EEE22222', startedAt: 's', totalCases: 16, completedCases: 3 }
}

describe('usePromptEvaluation', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    for (const fn of Object.values(api)) fn.mockReset()
    api.listEvaluations.mockResolvedValue([])
    api.currentEvaluation.mockResolvedValue(null)
  })
  afterEach(() => vi.useRealTimers())

  it('refresh loads history, current and latest run', async () => {
    api.listEvaluations.mockResolvedValue([summary('r2'), summary('r1')])
    api.getEvaluation.mockResolvedValue(detail('r2'))
    const { state, refresh } = usePromptEvaluation(ref(KEY))
    await refresh()
    expect(state.history.map((h) => h.runId)).toEqual(['r2', 'r1'])
    expect(state.current).toBeNull()
    expect(state.run?.runId).toBe('r2')
    expect(api.getEvaluation).toHaveBeenCalledWith('r2')
  })

  it('start polls until finished', async () => {
    api.startEvaluation.mockResolvedValue({ runId: 'r3', status: 'RUNNING', totalCases: 16 })
    api.getEvaluation
      .mockResolvedValueOnce(detail('r3', 'RUNNING', 4))
      .mockResolvedValueOnce(detail('r3', 'COMPLETED', 16))
    const { state, start } = usePromptEvaluation(ref(KEY))
    await start(5)
    expect(api.startEvaluation).toHaveBeenCalledWith(KEY, 5)
    expect(state.starting).toBe(false)
    expect(state.current?.runId).toBe('r3')

    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(api.getEvaluation).toHaveBeenCalledTimes(1)
    expect(state.current?.completedCases).toBe(4)
    expect(api.listEvaluations).not.toHaveBeenCalled()

    api.listEvaluations.mockResolvedValue([summary('r3')])
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(api.getEvaluation).toHaveBeenCalledTimes(2)
    expect(state.run?.status).toBe('COMPLETED')
    expect(state.current).toBeNull()
    expect(state.history.map((h) => h.runId)).toEqual(['r3'])

    await vi.advanceTimersByTimeAsync(POLL_MS * 3)
    expect(api.getEvaluation).toHaveBeenCalledTimes(2)
  })

  it('opening an older run while this key runs keeps it on screen', async () => {
    let completed = 3
    let status: EvalStatus = 'RUNNING'
    api.currentEvaluation.mockResolvedValue(current('r5'))
    api.listEvaluations.mockResolvedValue([summary('r5', 'RUNNING', 3), summary('r1')])
    api.getEvaluation.mockImplementation(async (runId: string) =>
      runId === 'r5' ? detail('r5', status, completed) : detail(runId))
    const { state, refresh, open } = usePromptEvaluation(ref(KEY))
    await refresh()
    expect(state.run?.runId).toBe('r5')

    await open('r1')
    expect(state.run?.runId).toBe('r1')
    for (let i = 0; i < 3; i++) {
      completed += 2
      await vi.advanceTimersByTimeAsync(POLL_MS)
      expect(state.run?.runId).toBe('r1')
      expect(state.current?.completedCases).toBe(completed)
    }

    status = 'COMPLETED'
    api.listEvaluations.mockClear()
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(state.current).toBeNull()
    expect(state.run?.runId).toBe('r1')
    expect(api.listEvaluations).toHaveBeenCalledWith(KEY)
  })

  it('start follows the new run even when an older run is on screen', async () => {
    api.listEvaluations.mockResolvedValue([summary('r1')])
    api.getEvaluation.mockResolvedValue(detail('r1'))
    const { state, refresh, start } = usePromptEvaluation(ref(KEY))
    await refresh()
    expect(state.run?.runId).toBe('r1')

    api.startEvaluation.mockResolvedValue({ runId: 'r3', status: 'RUNNING', totalCases: 16 })
    api.getEvaluation
      .mockResolvedValueOnce(detail('r3', 'RUNNING', 4))
      .mockResolvedValueOnce(detail('r3', 'COMPLETED', 16))
    await start(5)
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(state.run?.runId).toBe('r3')
    expect(state.run?.completedCases).toBe(4)
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(state.run?.status).toBe('COMPLETED')
  })

  it('running conflict loads current instead of failing', async () => {
    api.startEvaluation.mockRejectedValue(new ApiError(409, 'AI_EVAL_RUNNING', 'busy', 'req', [{ runId: 'r9' }]))
    api.currentEvaluation.mockResolvedValue(current('r9', 'other.key'))
    const { state, start } = usePromptEvaluation(ref(KEY))
    await expect(start(5)).resolves.toBeUndefined()
    expect(state.current?.runId).toBe('r9')
    expect(state.starting).toBe(false)
    // another key's run: progress comes from currentEvaluation, never getEvaluation
    api.currentEvaluation.mockResolvedValue({ ...current('r9', 'other.key'), completedCases: 8 })
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(state.current?.completedCases).toBe(8)
    api.currentEvaluation.mockResolvedValue(null)
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(state.current).toBeNull()
    await vi.advanceTimersByTimeAsync(POLL_MS * 3)
    expect(api.getEvaluation).not.toHaveBeenCalled()
    expect(api.currentEvaluation).toHaveBeenCalledTimes(3)
  })

  it('switches to the run detail when the running evaluation becomes this key', async () => {
    api.startEvaluation.mockRejectedValue(new ApiError(409, 'AI_EVAL_RUNNING', 'busy', 'req', []))
    api.currentEvaluation.mockResolvedValue(current('r9', 'other.key'))
    const { state, start } = usePromptEvaluation(ref(KEY))
    await start(5)
    api.currentEvaluation.mockResolvedValue(current('r10'))
    api.getEvaluation.mockResolvedValue(detail('r10', 'RUNNING', 5))
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(state.current?.runId).toBe('r10')
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(api.getEvaluation).toHaveBeenCalledWith('r10')
    expect(state.current?.completedCases).toBe(5)
  })

  it('keeps progress through one poll failure and stops after three', async () => {
    api.startEvaluation.mockResolvedValue({ runId: 'r3', status: 'RUNNING', totalCases: 16 })
    api.getEvaluation
      .mockRejectedValueOnce(new Error('net'))
      .mockResolvedValueOnce(detail('r3', 'RUNNING', 6))
    const { state, start } = usePromptEvaluation(ref(KEY))
    await start(5)
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(state.current?.runId).toBe('r3')
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(state.current?.completedCases).toBe(6)

    api.getEvaluation.mockReset().mockRejectedValue(new Error('net'))
    await vi.advanceTimersByTimeAsync(POLL_MS * 2)
    expect(state.current).not.toBeNull()
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(state.current).toBeNull()
    expect(api.getEvaluation).toHaveBeenCalledTimes(3)
    await vi.advanceTimersByTimeAsync(POLL_MS * 3)
    expect(api.getEvaluation).toHaveBeenCalledTimes(3)
  })

  it('refresh while polling never runs two chains', async () => {
    api.currentEvaluation.mockResolvedValue(current('r5'))
    api.listEvaluations.mockResolvedValue([summary('r5', 'RUNNING', 3)])
    api.getEvaluation.mockResolvedValue(detail('r5', 'RUNNING', 3))
    const { refresh } = usePromptEvaluation(ref(KEY))
    await Promise.all([refresh(), refresh()])
    await refresh()
    api.getEvaluation.mockClear()
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(api.getEvaluation).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(api.getEvaluation).toHaveBeenCalledTimes(2)
  })

  it('rethrows other start errors and clears starting', async () => {
    api.startEvaluation.mockRejectedValue(new ApiError(500, 'AI_INTERNAL_ERROR', 'x'))
    const { state, start } = usePromptEvaluation(ref(KEY))
    await expect(start(5)).rejects.toBeInstanceOf(ApiError)
    expect(state.starting).toBe(false)
  })

  it('resumes polling from current', async () => {
    api.currentEvaluation.mockResolvedValue(current('r5'))
    api.listEvaluations.mockResolvedValue([summary('r5', 'RUNNING', 3)])
    api.getEvaluation.mockResolvedValue(detail('r5', 'RUNNING', 3))
    const { state, refresh } = usePromptEvaluation(ref(KEY))
    await refresh()
    expect(state.current?.runId).toBe('r5')
    const before = api.getEvaluation.mock.calls.length
    await vi.advanceTimersByTimeAsync(POLL_MS)
    expect(api.getEvaluation.mock.calls.length).toBe(before + 1)
  })

  it('stopPolling and scope dispose stop timers', async () => {
    api.currentEvaluation.mockResolvedValue(current('r5'))
    api.getEvaluation.mockResolvedValue(detail('r5', 'RUNNING', 3))
    const first = usePromptEvaluation(ref(KEY))
    await first.refresh()
    first.stopPolling()
    const n = api.getEvaluation.mock.calls.length
    await vi.advanceTimersByTimeAsync(POLL_MS * 3)
    expect(api.getEvaluation.mock.calls.length).toBe(n)

    const scope = effectScope()
    const second = scope.run(() => usePromptEvaluation(ref(KEY)))!
    await second.refresh()
    const m = api.getEvaluation.mock.calls.length
    scope.stop()
    await vi.advanceTimersByTimeAsync(POLL_MS * 3)
    expect(api.getEvaluation.mock.calls.length).toBe(m)
  })

  it('key change resets state', async () => {
    api.currentEvaluation.mockResolvedValue(current('r5'))
    api.listEvaluations.mockResolvedValue([summary('r5', 'RUNNING', 3)])
    api.getEvaluation.mockResolvedValue(detail('r5', 'RUNNING', 3))
    const key = ref(KEY)
    const { state, refresh } = usePromptEvaluation(key)
    await refresh()
    expect(state.run?.runId).toBe('r5')

    api.currentEvaluation.mockResolvedValue(null)
    api.listEvaluations.mockResolvedValue([])
    api.getEvaluation.mockClear()
    key.value = 'instruction.other'
    await nextTick()
    expect(state.run).toBeNull()
    expect(state.history).toEqual([])
    await vi.advanceTimersByTimeAsync(POLL_MS * 3)
    expect(api.getEvaluation).not.toHaveBeenCalled()
    expect(api.listEvaluations).toHaveBeenLastCalledWith('instruction.other')
  })

  it('open loads one run and loadCases loads the dataset once', async () => {
    api.getEvaluation.mockResolvedValue(detail('r1'))
    api.evaluationCases.mockResolvedValue({ datasetHash: 'h', cases: [] })
    const { state, open, loadCases } = usePromptEvaluation(ref(KEY))
    await open('r1')
    expect(state.run?.runId).toBe('r1')
    await loadCases()
    await loadCases()
    expect(state.cases?.datasetHash).toBe('h')
    expect(api.evaluationCases).toHaveBeenCalledTimes(1)
  })
})
