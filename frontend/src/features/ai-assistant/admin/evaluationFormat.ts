import type { ConfigVersion, CurrentEvaluation, EvalCaseRecord, EvalRunSummary, EvalSummary } from '../types'

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

export interface MetricRow {
  label: string
  base: number
  cand: number
  /** denominator */
  d: number
  direction: Direction
  lowerIsBetter: boolean
}

/** The five quality metrics, in display order. */
export function metricRows(s: EvalSummary): MetricRow[] {
  const b = s.baseline
  const c = s.candidate
  const row = (label: string, base: number, cand: number, d: number, lowerIsBetter = false): MetricRow => ({
    label, base, cand, d, lowerIsBetter, direction: rateDirection(base, cand, d, !lowerIsBetter),
  })
  return [
    row('正確率', b.correct, c.correct, b.total),
    row('工具選擇正確率', b.toolsOk, c.toolsOk, b.total),
    row('分類正確率', b.routeOk ?? 0, c.routeOk ?? 0, b.routeCases ?? 0),
    row('拒答率（應拒答題）', b.refusedExpected, c.refusedExpected, b.expectedRefusals),
    row('拒答率（一般題）', b.refusedNormal, c.refusedNormal, b.normalCases, true),
  ]
}

/** `percent` is the rounded change against the active version; null when it has no tokens. */
export function tokenRow(s: EvalSummary): { base: number; cand: number; direction: Direction; percent: number | null } {
  const base = s.baseline.avgTotalTokens
  const cand = s.candidate.avgTotalTokens
  return {
    base,
    cand,
    direction: tokenDirection(base, cand),
    percent: base === 0 ? null : Math.round(((cand - base) / base) * 100),
  }
}

/** Any worse quality metric wins; tokens never count. */
export function overallVerdict(s: EvalSummary): Direction {
  const rows = metricRows(s)
  if (rows.some((r) => r.direction === 'worse')) return 'worse'
  if (rows.some((r) => r.direction === 'better')) return 'better'
  return 'same'
}

export function changeText(r: MetricRow): string {
  if (r.d === 0) return '—'
  if (r.direction === 'same') return '持平'
  const diff = r.cand - r.base
  return `${r.direction === 'better' ? '變好' : '變差'}（${diff > 0 ? '+' : '−'}${Math.abs(diff)} 題）`
}

export function verdictSubtitle(s: EvalSummary): string {
  const rows = metricRows(s)
  const parts = rows
    .filter((r) => r.direction !== 'same')
    .map((r) => `${r.label} ${formatRate(r.base, r.d)} → ${formatRate(r.cand, r.d)}`)
  if (parts.length === 0) parts.push('各項指標持平')
  else if (rows.some((r) => r.direction === 'same')) parts.push('其他指標持平')
  const t = tokenRow(s)
  if (t.direction !== 'same' && t.percent !== null) {
    parts.push(t.percent > 0 ? `平均 Token 多 ${t.percent}%` : `平均 Token 少 ${Math.abs(t.percent)}%`)
  }
  return parts.join('；')
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

const MARK: Record<Direction, string> = { better: '▲', worse: '▼', same: '≈' }

/**
 * The symbol beside the 評測 tab: … while this config evaluates (another config's run does not count),
 * else the newest finished verdict while the draft is unchanged since; empty otherwise.
 */
export function verdictMark(
  history: EvalRunSummary[],
  current: CurrentEvaluation | null,
  configKey: string,
  wc: ConfigVersion | null,
): string {
  if (current?.configKey === configKey) return '…'
  const latest = history.find((r) => r.status === 'COMPLETED')
  if (!latest?.summary || versionState(latest, wc) !== 'evaluated') return ''
  return MARK[overallVerdict(latest.summary)]
}
