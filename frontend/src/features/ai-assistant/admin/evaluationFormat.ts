import type { ConfigVersion, EvalCaseRecord, EvalRunSummary } from '../types'

export type Direction = 'better' | 'worse' | 'same'

/** Token averages that differ by less than this share count as the same. */
const TOKEN_THRESHOLD = 0.05

export function formatRate(n: number, d: number): string {
  return d === 0 ? '—' : Math.round((n / d) * 100) + '%'
}

/** base and cand are counts out of d; a difference of one case is noise. */
export function rateDirection(base: number, cand: number, d: number, higherIsBetter: boolean): Direction {
  if (d === 0 || Math.abs(cand - base) <= 1) return 'same'
  return (cand > base) === higherIsBetter ? 'better' : 'worse'
}

/** Fewer tokens is better. */
export function tokenDirection(base: number, cand: number): Direction {
  if (base === 0 || Math.abs(cand - base) / base < TOKEN_THRESHOLD) return 'same'
  return cand < base ? 'better' : 'worse'
}

export function tokenChange(base: number, cand: number): string {
  if (base === 0) return '—'
  const pct = ((cand - base) / base) * 100
  return (pct >= 0 ? '+' : '') + pct.toFixed(1) + '%'
}

/** Only cases with both results can show a difference; a different route counts as one too. */
export function caseChanged(c: EvalCaseRecord): boolean {
  const b = c.baseline
  const n = c.candidate
  if (!b || !n) return false
  return (
    b.passed !== n.passed ||
    b.toolsOk !== n.toolsOk ||
    b.judge?.refused !== n.judge?.refused ||
    b.routeOk !== n.routeOk ||
    b.instructionKey !== n.instructionKey
  )
}

export type VersionState = 'none' | 'not-evaluated' | 'evaluated' | 'modified'

/** `latest` is the newest COMPLETED run of the key in the history. */
export function versionState(latest: EvalRunSummary | undefined, wc: ConfigVersion | null): VersionState {
  if (!wc) return 'none'
  if (!latest || latest.status !== 'COMPLETED' || latest.candidateVersion !== wc.version) return 'not-evaluated'
  return latest.candidateDocVersion === wc.docVersion ? 'evaluated' : 'modified'
}

/** Same wording as the backend's ErrorCode messages, so a failed case reads like a failed chat. */
const CASE_ERROR_MESSAGES: Record<string, string> = {
  AI_CUSTOMER_NOT_FOUND: '查無此顧客或您沒有查看權限',
  AI_TOKEN_BUDGET_EXCEEDED: '問題涉及的資料太多，請縮小範圍後再問',
  AI_LLM_TIMEOUT: '小幫手回應逾時，請稍後再試',
  AI_REQUEST_TIMEOUT: '處理時間過長，請簡化問題後再試',
  AI_LLM_RATE_LIMITED: '小幫手目前使用量較高，請稍後再試',
  AI_LLM_UNAVAILABLE: '小幫手暫時無法使用，其他功能不受影響',
  AI_ANSWER_FORMAT_INVALID: '回答格式異常，請重試',
  AI_INTERNAL_ERROR: '系統發生錯誤，請稍後再試',
}

/** Unknown codes are shown as they are. */
export function caseErrorMessage(code: string | null): string {
  if (code === null) return ''
  return CASE_ERROR_MESSAGES[code] ?? code
}
