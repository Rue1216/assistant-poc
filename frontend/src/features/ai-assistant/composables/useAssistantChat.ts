import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useAssistantStore } from '../stores/useAssistantStore'

export const MAX_MESSAGE_LENGTH = 1000

/** Composer state and actions on top of the conversation store. */
export function useAssistantChat() {
  const store = useAssistantStore()
  const { messages, pending, unavailable } = storeToRefs(store)
  const draft = ref('')

  const count = computed(() => draft.value.length)
  const canSend = computed(() => !pending.value && !unavailable.value && draft.value.trim().length > 0)

  function setDraft(value: string): void {
    // UTF-16 code units: same as native maxlength and the backend's Java String.length (R26)
    draft.value = value.slice(0, MAX_MESSAGE_LENGTH)
  }

  function submit(): void {
    if (!canSend.value) return
    const text = draft.value.trim()
    draft.value = ''
    void store.send(text)
  }

  function ask(question: string): void {
    void store.send(question)
  }

  return {
    messages,
    pending,
    unavailable,
    draft,
    count,
    canSend,
    setDraft,
    submit,
    ask,
    cancel: store.cancel,
    retry: (id: string) => void store.retry(id),
    clear: store.clear,
  }
}
