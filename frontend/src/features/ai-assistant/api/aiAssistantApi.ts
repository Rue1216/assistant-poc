import { apiClient } from '../../../platform-mock/apiClient'
import type {
  ApplyBody,
  ChatRequestBody,
  ChatResponse,
  ConfigSummary,
  ConfigVersion,
  CreateConfigBody,
  PublishBody,
  SaveDraftBody,
  SuggestionCategory,
  ValidationReport,
  VersionHistoryItem,
} from '../types'

/** Backend worst case is ~145 s (spec 01 section 6). */
export const CHAT_TIMEOUT_MS = 150_000

const SILENT = { silentCodes: ['AI_CUSTOMER_NOT_FOUND'] }

/** Admin error codes the prompt management page shows in place instead of a toast (05 section 4). */
export const CONFLICT = 'AI_CONFIG_CONFLICT'
export const VALIDATION_FAILED = 'AI_CONFIG_VALIDATION_FAILED'
export const OVERWRITE_PENDING = 'AI_CONFIG_OVERWRITE_PENDING'
export const INPUT_INVALID = 'AI_INPUT_INVALID'

function base(customerId: string): string {
  return `/ai-assistant/customers/${encodeURIComponent(customerId)}`
}

const ADMIN = '/ai-assistant/admin/configs'

function configPath(configKey: string): string {
  return `${ADMIN}/${encodeURIComponent(configKey)}`
}

export const aiAssistantApi = {
  async chat(customerId: string, body: ChatRequestBody, signal: AbortSignal): Promise<ChatResponse> {
    const res = await apiClient.post<ChatResponse>(`${base(customerId)}/chat`, body, {
      signal,
      timeout: CHAT_TIMEOUT_MS,
      meta: SILENT,
    })
    return res.data
  },

  async suggestions(customerId: string): Promise<SuggestionCategory[]> {
    const res = await apiClient.get<{ categories: SuggestionCategory[] }>(`${base(customerId)}/suggestions`, {
      meta: SILENT,
    })
    return res.data.categories
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
}
