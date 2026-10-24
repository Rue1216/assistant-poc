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

export interface ChatResponse {
  requestId: string
  answer: string
  blocks: AnswerBlock[]
  citations: Citation[]
  queriedAt: string
  disclaimer: string | null
  toolCalls: ToolCallSummary[]
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
}

export interface ChatMessageError {
  code: string
  message: string
  requestId?: string
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  state: 'done' | 'pending' | 'cancelled' | 'error'
  /** ISO timestamp the message was created in the browser (used for the time divider). */
  createdAt: string
  /** Server processing time of an assistant answer (ISO 8601). */
  queriedAt?: string
  blocks?: AnswerBlock[]
  citations?: Citation[]
  disclaimer?: string | null
  toolCalls?: ToolCallSummary[]
  error?: ChatMessageError
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
