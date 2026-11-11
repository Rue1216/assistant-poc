import { ref } from 'vue'
import { ApiError } from '@/utils/api-client'
import { aiAssistantApi } from '@/components/ai-assistant/ai-assistant-api'
import { useAssistantStore } from '@/stores/use-assistant-store'
import type { SuggestionCategory } from '@/beans/ai-assistant-bean'

export function useSuggestions() {
  const store = useAssistantStore()
  const categories = ref<SuggestionCategory[]>([])

  async function load(certNo: string): Promise<void> {
    categories.value = []
    try {
      const result = await aiAssistantApi.suggestions(certNo)
      if (store.certNo === certNo) categories.value = result
    } catch (e) {
      // other failures already raised the global toast; the welcome card just shows no chips
      if (store.certNo === certNo && e instanceof ApiError && e.code === 'AI_CUSTOMER_NOT_FOUND') {
        store.markUnavailable()
      }
    }
  }

  return { categories, load }
}
