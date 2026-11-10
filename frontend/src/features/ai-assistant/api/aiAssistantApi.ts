import { apiClient } from '../../../platform-mock/apiClient'
import type {
  ApplyBody,
  ChatRequestBody,
  ChatResponse,
  ConfigSummary,
  ConfigVersion,
  CreateConfigBody,
  CurrentEvaluation,
  EvalCases,
  EvalRun,
  EvalRunSummary,
  EvalStatus,
  FeedbackRating,
  PublishBody,
  SaveDraftBody,
  SuggestionCategory,
  ToolCatalogItem,
  ValidationReport,
  VersionFeedback,
  VersionHistoryItem,
} from '../types'

/** Backend worst case is ~145 s (spec 01 section 6). */
export const CHAT_TIMEOUT_MS = 150_000

const SILENT = { silentCodes: ['AI_CUSTOMER_NOT_FOUND'] }

/** Feedback statistics are a side panel: failures stay out of the toast. AI_UNAUTHENTICATED is deliberately absent: silencing it would also skip the session-expiry handler. */
const STATS_SILENT = { silentCodes: [...SILENT.silentCodes, 'AI_NETWORK_ERROR', 'AI_INTERNAL_ERROR', 'AI_FORBIDDEN', 'AI_CONFIG_NOT_FOUND'] }

/** Admin error codes the prompt management page shows in place instead of a toast (05 section 4). */
export const CONFLICT = 'AI_CONFIG_CONFLICT'
export const VALIDATION_FAILED = 'AI_CONFIG_VALIDATION_FAILED'
export const OVERWRITE_PENDING = 'AI_CONFIG_OVERWRITE_PENDING'
export const INPUT_INVALID = 'AI_INPUT_INVALID'
export const EVAL_RUNNING = 'AI_EVAL_RUNNING'

function base(certNo: string): string {
  return `/ai-assistant/customers/${encodeURIComponent(certNo)}`
}

const ADMIN = '/ai-assistant/admin/configs'
const EVALUATIONS = '/ai-assistant/admin/evaluations'

function configPath(configKey: string): string {
  return `${ADMIN}/${encodeURIComponent(configKey)}`
}

export const aiAssistantApi = {
  async chat(certNo: string, body: ChatRequestBody, signal: AbortSignal): Promise<ChatResponse> {
    const res = await apiClient.post<ChatResponse>(`${base(certNo)}/chat`, body, {
      signal,
      timeout: CHAT_TIMEOUT_MS,
      meta: SILENT,
    })
    return res.data
  },

  async suggestions(certNo: string): Promise<SuggestionCategory[]> {
    const res = await apiClient.get<{ categories: SuggestionCategory[] }>(`${base(certNo)}/suggestions`, {
      meta: SILENT,
    })
    return res.data.categories
  },

  /** Not silent: a failure (e.g. 404 AI_FEEDBACK_NOT_FOUND) shows the global toast. */
  async putFeedback(traceId: string, rating: FeedbackRating | null): Promise<void> {
    await apiClient.put(`/ai-assistant/chat/${encodeURIComponent(traceId)}/feedback`, { rating })
  },

  // ---- Prompt management (02 section 3) ----

  /** 3.1 */
  async listConfigs(): Promise<ConfigSummary[]> {
    const res = await apiClient.get<ConfigSummary[]>(ADMIN)
    return res.data
  },

  /** 3.2: 409 AI_CONFIG_INVALID_STATE (key exists) keeps the normal toast. */
  async createConfig(body: CreateConfigBody): Promise<ConfigVersion> {
    const res = await apiClient.post<ConfigVersion>(ADMIN, body)
    return res.data
  },

  /** 3.3 */
  async listVersions(configKey: string): Promise<VersionHistoryItem[]> {
    const res = await apiClient.get<VersionHistoryItem[]>(`${configPath(configKey)}/versions`)
    return res.data
  },

  /** Per-version thumbs-up and thumbs-down counts; versions without feedback are absent. */
  async feedbackStats(configKey: string): Promise<VersionFeedback[]> {
    const res = await apiClient.get<VersionFeedback[]>(`${configPath(configKey)}/feedback-stats`, { meta: STATS_SILENT })
    return res.data
  },

  /** 3.4 */
  async getVersion(configKey: string, version: number): Promise<ConfigVersion> {
    const res = await apiClient.get<ConfigVersion>(`${configPath(configKey)}/versions/${version}`)
    return res.data
  },

  /** 3.5 */
  async createDraft(configKey: string): Promise<ConfigVersion> {
    const res = await apiClient.post<ConfigVersion>(`${configPath(configKey)}/draft`, null, {
      meta: { silentCodes: [CONFLICT] },
    })
    return res.data
  },

  /** 3.6 */
  async saveDraft(configKey: string, body: SaveDraftBody): Promise<ConfigVersion> {
    const res = await apiClient.put<ConfigVersion>(`${configPath(configKey)}/draft`, body, {
      meta: { silentCodes: [CONFLICT, VALIDATION_FAILED] },
    })
    return res.data
  },

  /** 3.7 */
  async discardDraft(configKey: string, docVersion: number): Promise<void> {
    await apiClient.delete(`${configPath(configKey)}/draft`, {
      params: { docVersion },
      meta: { silentCodes: [CONFLICT] },
    })
  },

  /** 3.8 */
  async validateDraft(configKey: string): Promise<ValidationReport> {
    const res = await apiClient.post<ValidationReport>(`${configPath(configKey)}/draft/validate`, undefined, {
      meta: { silentCodes: [CONFLICT] },
    })
    return res.data
  },

  /**
   * 3.9: a 400 AI_INPUT_INVALID is silent here because the scheduledAt case is shown under the date
   * and time inputs; the caller toasts any other 400 itself.
   */
  async publish(configKey: string, body: PublishBody): Promise<ConfigVersion> {
    const res = await apiClient.post<ConfigVersion>(`${configPath(configKey)}/publish`, body, {
      meta: { silentCodes: [CONFLICT, VALIDATION_FAILED, INPUT_INVALID] },
    })
    return res.data
  },

  /** 3.10 */
  async applyVersion(configKey: string, version: number, body: ApplyBody): Promise<ConfigVersion> {
    const res = await apiClient.post<ConfigVersion>(`${configPath(configKey)}/versions/${version}/apply`, body, {
      meta: { silentCodes: [CONFLICT, OVERWRITE_PENDING] },
    })
    return res.data
  },

  // ---- Prompt evaluation (design 6.2-6.6) ----

  /** 202; 409 AI_EVAL_RUNNING is silent because the caller shows the running evaluation instead. */
  async startEvaluation(
    configKey: string,
    docVersion: number,
  ): Promise<{ runId: string; status: EvalStatus; totalCases: number }> {
    const res = await apiClient.post<{ runId: string; status: EvalStatus; totalCases: number }>(
      `${configPath(configKey)}/evaluations`,
      { docVersion },
      { meta: { silentCodes: [EVAL_RUNNING] } },
    )
    return res.data
  },

  /** The latest runs of one key, newest first, without cases. */
  async listEvaluations(configKey: string): Promise<EvalRunSummary[]> {
    const res = await apiClient.get<EvalRunSummary[]>(`${configPath(configKey)}/evaluations`)
    return res.data
  },

  /** 204 (nothing running) arrives as an empty body and becomes null. */
  async currentEvaluation(): Promise<CurrentEvaluation | null> {
    const res = await apiClient.get<CurrentEvaluation | ''>(`${EVALUATIONS}/current`)
    return res.status === 204 || !res.data ? null : res.data
  },

  async getEvaluation(runId: string): Promise<EvalRun> {
    const res = await apiClient.get<EvalRun>(`${EVALUATIONS}/${encodeURIComponent(runId)}`)
    return res.data
  },

  async evaluationCases(): Promise<EvalCases> {
    const res = await apiClient.get<EvalCases>('/ai-assistant/admin/evaluation-cases')
    return res.data
  },

  /** Tools an instruction may reference; the picker shows its own error, so no toast. */
  async tools(): Promise<ToolCatalogItem[]> {
    const res = await apiClient.get<{ tools: ToolCatalogItem[] }>('/ai-assistant/admin/tools', { meta: STATS_SILENT })
    return res.data.tools
  },
}
