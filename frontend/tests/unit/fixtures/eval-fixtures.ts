import type { CaseResult, EvalCaseRecord, EvalMetrics, EvalRun, EvalRunSummary, EvalSummary } from '@/beans/prompt-management-bean'

export function result(extra: Partial<CaseResult> = {}): CaseResult {
  return {
    outcome: 'ANSWERED', errorCode: null, answer: '回答內容', toolCalls: [], modelCalls: 1, inputTokens: 1, outputTokens: 1,
    totalTokens: 2, durationMs: 1, unverifiedNumbers: [], toolsOk: true, numbersOk: true, missingPhrases: [],
    forbiddenPhrases: [], judge: { refused: false, rubric: [], comment: null, error: null, tokens: 1 }, passed: true,
    routeOk: null, routeSource: null, instructionKey: null,
    ...extra,
  }
}

export function rec(caseId: string, baseline: CaseResult | null, candidate: CaseResult | null): EvalCaseRecord {
  return { caseId, group: '顧客經營', question: '問題', expectRefusal: false, expectCategory: null, baseline, candidate }
}

export function metrics(extra: Partial<EvalMetrics> = {}): EvalMetrics {
  return {
    correct: 13, total: 16, toolsOk: 15, refusedExpected: 2, expectedRefusals: 2, refusedNormal: 0, normalCases: 10,
    avgTotalTokens: 7420, avgInputTokens: 7000, avgOutputTokens: 420, avgDurationMs: 1000, routeOk: 11, routeCases: 13, ...extra,
  }
}

export function summary(base: Partial<EvalMetrics> = {}, cand: Partial<EvalMetrics> = {}): EvalSummary {
  return { baseline: metrics(base), candidate: metrics({ correct: 15, ...cand }), judgeTokens: 100 }
}

export function runSummary(extra: Partial<EvalRunSummary> = {}): EvalRunSummary {
  return {
    runId: 'r1', status: 'COMPLETED', errorCode: null, triggeredBy: 'EEE22222', startedAt: '2026-10-24T15:07:00+08:00',
    finishedAt: '2026-10-24T15:20:00+08:00', candidateVersion: 6, candidateUpdatedAt: '2026-10-24T14:58:00+08:00',
    candidateDocVersion: 5, datasetChanged: false, totalCases: 16, completedCases: 16, summary: summary(), ...extra,
  }
}

export function run(extra: Partial<EvalRun> = {}): EvalRun {
  return { ...runSummary(), configKey: 'instruction.contact-prep', cases: [], ...extra }
}
