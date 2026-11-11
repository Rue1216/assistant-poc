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
