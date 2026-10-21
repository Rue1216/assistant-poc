import { ref } from 'vue'
import { ApiError } from '../../../platform-mock/apiClient'
import { aiAssistantApi } from '../api/aiAssistantApi'
import { useAssistantStore } from '../stores/useAssistantStore'
import type { SuggestionCategory } from '../types'

export function useSuggestions() {
  const store = useAssistantStore()
  const categories = ref<SuggestionCategory[]>([])

  async function load(customerId: string): Promise<void> {
    categories.value = []
    try {
      const result = await aiAssistantApi.suggestions(customerId)
      if (store.customerId === customerId) categories.value = result
    } catch (e) {
      // other failures already raised the global toast; the welcome card just shows no chips
      if (store.customerId === customerId && e instanceof ApiError && e.code === 'AI_CUSTOMER_NOT_FOUND') {
        store.markUnavailable()
      }
    }
  }

  return { categories, load }
}
