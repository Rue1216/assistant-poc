import { describe, expect, it } from 'vitest'
import type { CaseResult, ConfigVersion, EvalCaseRecord, EvalRunSummary } from '../../types'
import { caseChanged, caseErrorMessage, formatRate, rateDirection, tokenChange, tokenDirection, versionState } from '../evaluationFormat'

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

  it('formats the token change', () => {
    expect(tokenChange(7420, 7610)).toBe('+2.6%')
    expect(tokenChange(7000, 6783)).toBe('-3.1%')
    expect(tokenChange(0, 5)).toBe('—')
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
})
