import { describe, expect, it } from 'vitest'
import type { CaseResult, ConfigVersion, EvalCaseRecord, EvalRunSummary } from '@/beans/prompt-management-bean'
import { caseChanged, caseErrorMessage, changeText, formatRate, metricRows, overallVerdict, rateDirection, tokenDirection, tokenRow, verdictMark, verdictSubtitle, versionState } from '@/views/maintenance/prompt-management/evaluation-format'
import { runSummary, summary } from '../../../fixtures/eval-fixtures'

function result(extra: Partial<CaseResult> = {}): CaseResult {
  return {
    outcome: 'ANSWERED', errorCode: null, answer: 'a', toolCalls: [], modelCalls: 1, inputTokens: 1, outputTokens: 1,
    totalTokens: 2, durationMs: 1, unverifiedNumbers: [], toolsOk: true, numbersOk: true, missingPhrases: [],
    forbiddenPhrases: [], judge: { refused: false, rubric: [], comment: null, error: null, tokens: 1 }, passed: true,
    ...extra,
  }
}

function rec(baseline: CaseResult | null, candidate: CaseResult | null): EvalCaseRecord {
  return { caseId: 'Q1', group: 'g', question: 'q', expectRefusal: false, baseline, candidate }
}

function run(extra: Partial<EvalRunSummary> = {}): EvalRunSummary {
  return {
    runId: 'r1', status: 'COMPLETED', errorCode: null, triggeredBy: 'EEE22222', startedAt: 's', finishedAt: 'f',
    candidateVersion: 6, candidateUpdatedAt: 'u', candidateDocVersion: 5, datasetChanged: false, totalCases: 16,
    completedCases: 16, summary: null, ...extra,
  }
}

const wc = { version: 6, docVersion: 5 } as ConfigVersion

describe('evaluationFormat', () => {
  it('formats rates', () => {
    expect(formatRate(13, 16)).toBe('81%')
    expect(formatRate(16, 16)).toBe('100%')
  })

  it('rate with zero denominator is a dash', () => {
    expect(formatRate(0, 0)).toBe('—')
    expect(rateDirection(0, 0, 0, true)).toBe('same')
  })

  it('rate direction ignores a one-case difference', () => {
    expect(rateDirection(13, 15, 16, true)).toBe('better')
    expect(rateDirection(13, 14, 16, true)).toBe('same')
    expect(rateDirection(15, 13, 16, true)).toBe('worse')
    expect(rateDirection(0, 2, 10, false)).toBe('worse')
    expect(rateDirection(2, 0, 10, false)).toBe('better')
  })

  it('token direction needs a 5% change and lower is better', () => {
    expect(tokenDirection(7420, 7610)).toBe('same')
    expect(tokenDirection(7000, 7400)).toBe('worse')
    expect(tokenDirection(7000, 6000)).toBe('better')
    expect(tokenDirection(0, 100)).toBe('same')
  })

  it('detects a changed case', () => {
    expect(caseChanged(rec(result(), result()))).toBe(false)
    expect(caseChanged(rec(result(), result({ passed: false })))).toBe(true)
    expect(caseChanged(rec(result(), result({ toolsOk: false })))).toBe(true)
    expect(caseChanged(rec(result(), result({ judge: { refused: true, rubric: [], comment: null, error: null, tokens: 1 } })))).toBe(true)
    expect(caseChanged(rec(result({ judge: null }), result({ judge: null })))).toBe(false)
    expect(caseChanged(rec(result({ passed: false, routeOk: false }), result({ passed: false, routeOk: true })))).toBe(true)
    expect(caseChanged(rec(result({ instructionKey: 'instruction.a' }), result({ instructionKey: 'instruction.b' })))).toBe(true)
    expect(caseChanged(rec(result({ instructionKey: 'instruction.a', routeOk: true }), result({ instructionKey: 'instruction.a', routeOk: true })))).toBe(false)
    expect(caseChanged(rec(null, result()))).toBe(false)
    expect(caseChanged(rec(result(), null))).toBe(false)
  })

  it('derives the version state', () => {
    expect(versionState(run(), null)).toBe('none')
    expect(versionState(undefined, wc)).toBe('not-evaluated')
    expect(versionState(run({ candidateVersion: 5 }), wc)).toBe('not-evaluated')
    expect(versionState(run(), wc)).toBe('evaluated')
    expect(versionState(run({ candidateDocVersion: 4 }), wc)).toBe('modified')
  })

  it('maps case error codes to the friendly messages, unknown codes to themselves', () => {
    expect(caseErrorMessage('AI_LLM_TIMEOUT')).toBe('小幫手回應逾時，請稍後再試')
    expect(caseErrorMessage('AI_CUSTOMER_NOT_FOUND')).toBe('查無此顧客或您沒有查看權限')
    expect(caseErrorMessage('AI_TOKEN_BUDGET_EXCEEDED')).toBe('問題涉及的資料太多，請縮小範圍後再問')
    expect(caseErrorMessage('AI_REQUEST_TIMEOUT')).toBe('處理時間過長，請簡化問題後再試')
    expect(caseErrorMessage('AI_LLM_RATE_LIMITED')).toBe('小幫手目前使用量較高，請稍後再試')
    expect(caseErrorMessage('AI_LLM_UNAVAILABLE')).toBe('小幫手暫時無法使用，其他功能不受影響')
    expect(caseErrorMessage('AI_ANSWER_FORMAT_INVALID')).toBe('回答格式異常，請重試')
    expect(caseErrorMessage('AI_INTERNAL_ERROR')).toBe('系統發生錯誤，請稍後再試')
    expect(caseErrorMessage('AI_SOMETHING_NEW')).toBe('AI_SOMETHING_NEW')
    expect(caseErrorMessage(null)).toBe('')
  })

  // same-everything baseline; tests override single fields
  const flat = { correct: 11, total: 17, toolsOk: 16, routeOk: 12, routeCases: 13, refusedExpected: 2, expectedRefusals: 2, refusedNormal: 0, normalCases: 10, avgTotalTokens: 3470 }
  const sum = (cand: Record<string, number> = {}) => summary(flat, { ...flat, ...cand })
  const rowOf = (s: ReturnType<typeof sum>, label: string) => metricRows(s).find((r) => r.label === label)!

  it('lists the five quality rows in order', () => {
    const rows = metricRows(sum())
    expect(rows.map((r) => r.label)).toEqual(['正確率', '工具選擇正確率', '分類正確率', '拒答率（應拒答題）', '拒答率（一般題）'])
    expect(rows.map((r) => r.lowerIsBetter)).toEqual([false, false, false, false, true])
    expect(rows[0]).toMatchObject({ base: 11, cand: 11, d: 17, direction: 'same' })
  })

  it('token row gives a rounded percent, null when the base is 0', () => {
    expect(tokenRow(sum({ avgTotalTokens: 3927 }))).toEqual({ base: 3470, cand: 3927, direction: 'worse', percent: 13 })
    expect(tokenRow(sum({ avgTotalTokens: 3000 }))).toMatchObject({ direction: 'better', percent: -14 })
    expect(tokenRow(summary({ ...flat, avgTotalTokens: 0 }, flat)).percent).toBeNull()
  })

  it('overall verdict follows the quality rows only', () => {
    expect(overallVerdict(sum({ correct: 14 }))).toBe('better')
    expect(overallVerdict(sum({ correct: 14, toolsOk: 13 }))).toBe('worse')
    expect(overallVerdict(sum({ correct: 12 }))).toBe('same')
    expect(overallVerdict(sum({ avgTotalTokens: 3927 }))).toBe('same')
    expect(overallVerdict(sum({ refusedNormal: 3 }))).toBe('worse')
  })

  it('writes the change text with the sign of cand minus base', () => {
    expect(changeText(rowOf(sum({ correct: 14 }), '正確率'))).toBe('變好（+3 題）')
    expect(changeText(rowOf(sum({ refusedNormal: 2 }), '拒答率（一般題）'))).toBe('變差（+2 題）')
    expect(changeText(rowOf(summary({ ...flat, correct: 14 }, flat), '正確率'))).toBe('變差（−3 題）')
    expect(changeText(rowOf(sum({ correct: 12 }), '正確率'))).toBe('持平')
    const none = { ...flat, expectedRefusals: 0, refusedExpected: 0 }
    expect(changeText(rowOf(summary(none, none), '拒答率（應拒答題）'))).toBe('—')
  })

  it('writes the verdict subtitle', () => {
    expect(verdictSubtitle(sum({ correct: 14, avgTotalTokens: 3927 }))).toBe('正確率 65% → 82%；其他指標持平；平均 Token 多 13%')
    expect(verdictSubtitle(sum())).toBe('各項指標持平')
    expect(verdictSubtitle(sum({ avgTotalTokens: 3000 }))).toBe('各項指標持平；平均 Token 少 14%')
    expect(verdictSubtitle(sum({ correct: 14, toolsOk: 13 }))).toBe('正確率 65% → 82%；工具選擇正確率 94% → 76%；其他指標持平')
  })

  it('does not count metrics without data as 持平', () => {
    const noData = { routeCases: 0, routeOk: 0, expectedRefusals: 0, refusedExpected: 0, normalCases: 0, refusedNormal: 0 }
    const changed = summary({ ...flat, ...noData }, { ...flat, ...noData, correct: 14, toolsOk: 13 })
    expect(verdictSubtitle(changed)).toBe('正確率 65% → 82%；工具選擇正確率 94% → 76%')
    const empty = { ...flat, ...noData, total: 0, correct: 0, toolsOk: 0 }
    expect(verdictSubtitle(summary(empty, empty))).toBe('各項指標持平')
  })
})

describe('verdictMark', () => {
  const KEY = 'instruction.contact-prep'
  const wc = { configKey: KEY, version: 6, docVersion: 5 } as ConfigVersion
  const running = (configKey: string) => ({ runId: 'x', configKey, triggeredBy: 'E', startedAt: 's', totalCases: 16, completedCases: 1 })

  it('shows an ellipsis while this config is evaluating', () => {
    expect(verdictMark([], running(KEY), KEY, wc)).toBe('…')
  })

  it("verdictMark ignores another key's running evaluation", () => {
    expect(verdictMark([runSummary()], running('instruction.other'), KEY, wc)).toBe('▲')
  })

  it('maps the verdict of the newest completed run to a symbol', () => {
    expect(verdictMark([runSummary()], null, KEY, wc)).toBe('▲')
    expect(verdictMark([runSummary({ summary: summary({ correct: 15 }, { correct: 13 }) })], null, KEY, wc)).toBe('▼')
    expect(verdictMark([runSummary({ summary: summary({}, { correct: 13 }) })], null, KEY, wc)).toBe('≈')
  })

  it('skips failed runs when looking for the newest completed one', () => {
    const failed = runSummary({ runId: 'f', status: 'FAILED', summary: null })
    expect(verdictMark([failed, runSummary()], null, KEY, wc)).toBe('▲')
  })

  it('is empty when modified, without history, without a working copy or without a summary', () => {
    expect(verdictMark([runSummary({ candidateDocVersion: 4 })], null, KEY, wc)).toBe('')
    expect(verdictMark([], null, KEY, wc)).toBe('')
    expect(verdictMark([runSummary()], null, KEY, null)).toBe('')
    expect(verdictMark([runSummary({ summary: null })], null, KEY, wc)).toBe('')
  })
})
