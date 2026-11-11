import { apiClient } from '@/utils/api-client'
import type { ChatRequestBody, ChatResponse, FeedbackRating, SuggestionCategory } from '@/beans/ai-assistant-bean'
import { CHAT_TIMEOUT_MS } from '@/components/ai-assistant/ai-assistant-constant'

const SILENT = { silentCodes: ['AI_CUSTOMER_NOT_FOUND'] }

function base(certNo: string): string {
  return `/ai-assistant/customers/${encodeURIComponent(certNo)}`
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
}
