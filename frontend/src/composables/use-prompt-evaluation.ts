import { onScopeDispose, reactive, watch, type Ref } from 'vue'
import { ApiError } from '@/utils/api-client'
import { promptManagementApi } from '@/views/maintenance/prompt-management/prompt-management-api'
import { EVAL_RUNNING } from '@/views/maintenance/prompt-management/prompt-management-constant'
import type { CurrentEvaluation, EvalCases, EvalRun, EvalRunSummary } from '@/beans/prompt-management-bean'

export const POLL_MS = 3000

/**
 * State and actions of the evaluation panel for the selected config key (design 8.3).
 * Polling is a recursive setTimeout so a slow request never overlaps the next one.
 */
export function usePromptEvaluation(configKey: Ref<string>) {
  const state = reactive({
    current: null as CurrentEvaluation | null,
    history: [] as EvalRunSummary[],
    /** The run on screen (the latest, or one opened from the history). */
    run: null as EvalRun | null,
    cases: null as EvalCases | null,
    starting: false,
  })

  let timer: ReturnType<typeof setTimeout> | null = null
  /** Bumped by stopPolling and key changes so late responses are dropped. */
  let epoch = 0
  /** A run the admin asked to see (started or opened) before its detail is on screen. */
  let followed: string | null = null

  function stopPolling() {
    epoch++
    if (timer !== null) clearTimeout(timer)
    timer = null
  }

  async function loadHistory(key: string, at: number) {
    const history = await promptManagementApi.listEvaluations(key)
    if (at === epoch) state.history = history
  }

  const MAX_FAILURES = 3

  /** One pending timer at most: every reschedule replaces the previous handle. */
  function schedule(fn: () => Promise<void>) {
    if (timer !== null) clearTimeout(timer)
    timer = setTimeout(() => {
      timer = null
      void fn()
    }, POLL_MS)
  }

  /** Starts a fresh chain; any earlier chain is cancelled by the epoch bump. */
  function beginPolling(current: CurrentEvaluation, key: string) {
    stopPolling()
    const at = epoch
    if (current.configKey === key) pollOwn(current.runId, at, 0)
    else pollOther(at, key, 0)
  }

  /** A run of this key: its detail carries the progress and the cases. */
  function pollOwn(runId: string, at: number, failures: number) {
    schedule(async () => {
      try {
        const run = await promptManagementApi.getEvaluation(runId)
        if (at !== epoch) return
        // Progress always updates; the run on screen is replaced only while the admin is still viewing this one.
        if (isViewing(runId)) state.run = run
        if (run.status === 'RUNNING') {
          state.current = {
            runId,
            configKey: run.configKey,
            triggeredBy: run.triggeredBy,
            startedAt: run.startedAt,
            totalCases: run.totalCases,
            completedCases: run.completedCases,
          }
          pollOwn(runId, at, 0)
        } else {
          state.current = null
          await loadHistory(run.configKey, at)
        }
      } catch {
        // The global handler already told the user; a single failure keeps the progress and retries.
        if (at !== epoch) return
        if (failures + 1 >= MAX_FAILURES) state.current = null
        else pollOwn(runId, at, failures + 1)
      }
    })
  }

  function isViewing(runId: string) {
    return state.run === null || state.run.runId === runId || followed === runId
  }

  /** A run of another key only shows progress; it releases the start button when it ends. */
  function pollOther(at: number, key: string, failures: number) {
    schedule(async () => {
      try {
        const current = await promptManagementApi.currentEvaluation()
        if (at !== epoch) return
        state.current = current
        if (!current) {
          await loadHistory(key, at)
        } else if (current.configKey === key) {
          pollOwn(current.runId, at, 0)
        } else {
          pollOther(at, key, 0)
        }
      } catch {
        if (at !== epoch) return
        if (failures + 1 >= MAX_FAILURES) state.current = null
        else pollOther(at, key, failures + 1)
      }
    })
  }

  /** Reloads history and the running evaluation, shows the latest run, and resumes polling this key's run. */
  async function refresh() {
    const at = epoch
    const key = configKey.value
    const [history, current] = await Promise.all([promptManagementApi.listEvaluations(key), promptManagementApi.currentEvaluation()])
    if (at !== epoch) return
    state.history = history
    state.current = current
    if (history.length > 0) {
      const run = await promptManagementApi.getEvaluation(history[0].runId)
      if (at !== epoch) return
      state.run = run
    } else {
      state.run = null
    }
    if (current) beginPolling(current, key)
  }

  /** A second run is refused with 409: show the one that is running instead of failing. */
  async function start(docVersion: number) {
    if (state.starting) return
    state.starting = true
    const at = epoch
    const key = configKey.value
    try {
      const res = await promptManagementApi.startEvaluation(key, docVersion)
      if (at !== epoch) return
      const started: CurrentEvaluation = {
        runId: res.runId,
        configKey: key,
        triggeredBy: '',
        startedAt: '',
        totalCases: res.totalCases,
        completedCases: 0,
      }
      state.current = started
      followed = res.runId
      beginPolling(started, key)
    } catch (e) {
      if (!(e instanceof ApiError) || e.code !== EVAL_RUNNING) throw e
      const current = await promptManagementApi.currentEvaluation()
      if (at !== epoch) return
      state.current = current
      if (current?.configKey === key) followed = current.runId
      if (current) beginPolling(current, key)
    } finally {
      state.starting = false
    }
  }

  async function open(runId: string) {
    const at = epoch
    followed = runId
    const run = await promptManagementApi.getEvaluation(runId)
    if (at === epoch) state.run = run
  }

  /** The dataset is the same for every key and loaded once. */
  async function loadCases() {
    if (state.cases) return
    state.cases = await promptManagementApi.evaluationCases()
  }

  watch(configKey, () => {
    stopPolling()
    followed = null
    state.run = null
    state.history = []
    state.current = null
    state.starting = false
    void refresh().catch(() => {}) // already toasted by apiClient
  })

  onScopeDispose(stopPolling)

  return { state, refresh, start, open, loadCases, stopPolling }
}

export type PromptEvaluation = ReturnType<typeof usePromptEvaluation>
