import { apiClient } from '@/utils/api-client'
import type {
  ApplyBody,
  ConfigSummary,
  ConfigVersion,
  CreateConfigBody,
  CurrentEvaluation,
  EvalCases,
  EvalRun,
  EvalRunSummary,
  EvalStatus,
  PublishBody,
  SaveDraftBody,
  ToolCatalogItem,
  ValidationReport,
  VersionFeedback,
  VersionHistoryItem,
} from '@/beans/prompt-management-bean'
import {
  CONFLICT,
  EVAL_RUNNING,
  INPUT_INVALID,
  OVERWRITE_PENDING,
  VALIDATION_FAILED,
} from '@/views/maintenance/prompt-management/prompt-management-constant'

/** Feedback statistics are a side panel: failures stay out of the toast. AI_UNAUTHENTICATED is deliberately absent: silencing it would also skip the session-expiry handler. */
const STATS_SILENT = {
  silentCodes: ['AI_CUSTOMER_NOT_FOUND', 'AI_NETWORK_ERROR', 'AI_INTERNAL_ERROR', 'AI_FORBIDDEN', 'AI_CONFIG_NOT_FOUND'],
}

const ADMIN = '/ai-assistant/admin/configs'
const EVALUATIONS = '/ai-assistant/admin/evaluations'

function configPath(configKey: string): string {
  return `${ADMIN}/${encodeURIComponent(configKey)}`
}

export const promptManagementApi = {
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
