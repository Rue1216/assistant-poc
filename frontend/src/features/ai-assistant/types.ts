export type ToolCallStatus = 'SUCCESS' | 'ERROR' | 'DENIED' | 'TIMEOUT'

export type AnswerBlockType = 'heading' | 'paragraph' | 'bullet' | 'numbered'

export interface AnswerBlock {
  type: AnswerBlockType
  text: string
  citations: number[]
}

export interface Citation {
  id: number
  toolName: string
  title: string
  target: string | null
  excerpt: string | null
  description: string | null
  sourceName: string | null
  url: string | null
  dataTime: string | null
  queriedAt: string
  cited: boolean
}

export interface ToolCallSummary {
  name: string
  purpose: string
  status: ToolCallStatus
  durationMs: number
}

export interface HistorySummary {
  text: string
  /** Counted from the oldest item of the `history` array sent in that request. */
  summarizedCount: number
}

export interface ChatResponse {
  traceId: string
  answer: string
  blocks: AnswerBlock[]
  citations: Citation[]
  queriedAt: string
  disclaimer: string | null
  toolCalls: ToolCallSummary[]
  historySummary: HistorySummary | null
}

export interface SuggestionCategory {
  configKey: string
  name: string
  questions: string[]
}

export interface HistoryItem {
  role: 'user' | 'assistant'
  content: string
}

export interface ChatRequestBody {
  message: string
  history: HistoryItem[]
  /** configKey of a quick-question category; present only for chip questions (skips routing). */
  categoryKey?: string
  /** Rolling summary of earlier turns; omitted when there is none (max 1000 code points). */
  historySummary?: string
}

export type FeedbackRating = 'UP' | 'DOWN'

export interface ChatMessageError {
  code: string
  message: string
  traceId?: string
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  state: 'done' | 'pending' | 'cancelled' | 'error'
  /** ISO timestamp the message was created in the browser (used for the time divider). */
  createdAt: string
  /** Conversation round the message belongs to; 開新對話 starts the next one. */
  round: number
  /** User messages only: the category a chip question came from, reused on retry. */
  categoryKey?: string
  /** Server processing time of an assistant answer (ISO 8601). */
  queriedAt?: string
  blocks?: AnswerBlock[]
  citations?: Citation[]
  disclaimer?: string | null
  toolCalls?: ToolCallSummary[]
  /** Server trace id of the answer; feedback is addressed by it. */
  traceId?: string
  feedback?: FeedbackRating | null
  error?: ChatMessageError
  /** Folded into the rolling summary: no longer sent as history. */
  summarized?: boolean
}

// ---- Prompt management (02 section 3) ----

export type ConfigType = 'SYSTEM_PROMPT' | 'SYSTEM_INSTRUCTION'
export type ConfigStatus = 'DRAFT' | 'SCHEDULED' | 'PUBLISHED' | 'ARCHIVED' | 'DISCARDED'
export type SaveMode = 'KEEP_STATUS' | 'DRAFT'
export type PublishMode = 'IMMEDIATE' | 'SCHEDULED'

/** `version` is internal: API paths only, never shown on screen. */
export interface ConfigVersionInfo {
  version: number
  status: ConfigStatus
  scheduledAt: string | null
  updatedBy: string
  updatedAt: string
}

export interface ConfigSummary {
  configKey: string
  configType: ConfigType
  name: string
  published: ConfigVersionInfo | null
  workingCopy: ConfigVersionInfo | null
}

/** Answer feedback counts of one prompt version (5-year window). */
export interface VersionFeedback {
  version: number
  up: number
  down: number
}

export interface VersionHistoryItem {
  version: number
  status: ConfigStatus
  scheduledAt: string | null
  changeNote: string | null
  basedOnVersion: number | null
  createdBy: string
  updatedBy: string
  updatedAt: string
}

export interface ConfigVersion {
  configKey: string
  configType: ConfigType
  name: string
  content: string
  exampleQuestions: string[]
  status: ConfigStatus
  version: number
  basedOnVersion: number | null
  changeNote: string | null
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  scheduledAt: string | null
  docVersion: number
}

export interface ValidationIssue {
  rule: string
  message: string
}

export interface ValidationReport {
  valid: boolean
  errors: ValidationIssue[]
}

export interface CreateConfigBody {
  configKey: string
  configType: ConfigType
  name: string
}

export interface SaveDraftBody {
  name: string
  content: string
  changeNote: string | null
  saveMode: SaveMode
  docVersion: number
}

export interface PublishBody {
  publishMode: PublishMode
  scheduledAt?: string
  docVersion: number
}

export interface ApplyBody {
  workingCopyDocVersion: number | null
  confirmOverwrite?: boolean
}

/** `details[0]` of 409 AI_CONFIG_OVERWRITE_PENDING. */
export interface OverwritePendingDetail {
  status: 'DRAFT' | 'SCHEDULED'
  scheduledAt: string | null
  updatedAt: string
  docVersion: number
}

// ---- Prompt evaluation (spec 2026-10-24 sections 5.2, 6.2-6.6) ----

export type EvalStatus = 'RUNNING' | 'COMPLETED' | 'FAILED'

/** Raw counts per prompt version; rates are computed in the UI. */
export interface EvalMetrics {
  correct: number
  total: number
  toolsOk: number
  refusedExpected: number
  expectedRefusals: number
  refusedNormal: number
  normalCases: number
  avgTotalTokens: number
  avgInputTokens: number
  avgOutputTokens: number
  avgDurationMs: number
  /** Absent on runs made before instruction routing; read as 0. */
  routeOk?: number
  routeCases?: number
}

export interface EvalSummary {
  baseline: EvalMetrics
  candidate: EvalMetrics
  judgeTokens: number
}

export interface JudgeVerdict {
  refused: boolean | null
  /** Empty when the judge failed (error = AI_EVAL_JUDGE_FAILED). */
  rubric: { text: string; met: boolean }[]
  comment: string | null
  error: string | null
  tokens: number
}

export interface CaseResult {
  outcome: 'ANSWERED' | 'FAILED'
  errorCode: string | null
  answer: string | null
  toolCalls: { toolName: string; status: string }[]
  modelCalls: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  durationMs: number
  unverifiedNumbers: string[]
  toolsOk: boolean
  numbersOk: boolean
  missingPhrases: string[]
  forbiddenPhrases: string[]
  judge: JudgeVerdict | null
  passed: boolean
  /** null when the case has no expected category; all three absent on runs before routing */
  routeOk?: boolean | null
  routeSource?: 'ROUTER' | 'CHIP' | 'FALLBACK' | null
  instructionKey?: string | null
}

export interface EvalCaseRecord {
  caseId: string
  group: string
  question: string
  expectRefusal: boolean
  /** configKey, 'none', or absent/null (the backend serialises unchecked cases as null) */
  expectCategory?: string | null
  baseline: CaseResult | null
  candidate: CaseResult | null
}

export interface EvalRunSummary {
  runId: string
  status: EvalStatus
  errorCode: string | null
  triggeredBy: string
  startedAt: string
  finishedAt: string | null
  candidateVersion: number
  candidateUpdatedAt: string
  candidateDocVersion: number
  datasetChanged: boolean
  totalCases: number
  completedCases: number
  summary: EvalSummary | null
}

export interface EvalRun extends EvalRunSummary {
  configKey: string
  cases: EvalCaseRecord[]
}

/** The one running evaluation (GET evaluations/current). */
export interface CurrentEvaluation {
  runId: string
  configKey: string
  triggeredBy: string
  startedAt: string
  totalCases: number
  completedCases: number
}

export interface EvalCase {
  id: string
  group: string
  accessLevel: string
  certNo: string
  question: string
  expect: {
    tools: string[]
    forbiddenTools: string[]
    refusal: boolean
    mustContain: string[]
    mustNotContain: string[]
    /** Tools that must be called in this order (a subsequence of the calls); empty means no order check */
    toolOrder: string[]
    /** configKey, 'none', or absent/null (the backend serialises unchecked cases as null) */
    category?: string | null
  }
  rubric: string[]
}

export interface EvalCases {
  datasetHash: string
  cases: EvalCase[]
}

export interface ToolOption {
  key: string
  name: string
  description: string
}

/** One tool the admin may reference as a {{tool}} marker in an instruction. */
export interface ToolCatalogItem {
  name: string
  purpose: string
  options: ToolOption[]
}
