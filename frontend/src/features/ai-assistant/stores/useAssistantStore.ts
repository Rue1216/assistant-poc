import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ApiError } from '../../../platform-mock/apiClient'
import { aiAssistantApi } from '../api/aiAssistantApi'
import type { ChatMessage, HistoryItem } from '../types'

export const MAX_HISTORY_MESSAGES = 20
export const MAX_HISTORY_CONTENT = 4000

const NOT_FOUND_CODE = 'AI_CUSTOMER_NOT_FOUND'

export const useAssistantStore = defineStore('aiAssistant', () => {
  const customerId = ref<string | null>(null)
  const messages = ref<ChatMessage[]>([])
  const pending = ref(false)
  const unavailable = ref(false)

  // The in-flight request. Anything that invalidates it sets this to null, so a late
  // response (customer switch, cancel, clear) is recognised as stale and dropped.
  let current: AbortController | null = null
  let seq = 0

  const nextId = () => `m${++seq}`

  function abortCurrent(): void {
    current?.abort()
    current = null
    pending.value = false
  }

  function buildHistory(): HistoryItem[] {
    return messages.value
      .filter((m) => m.state === 'done' && m.content.length > 0)
      .slice(-MAX_HISTORY_MESSAGES)
      .map((m) => ({ role: m.role, content: m.content.slice(0, MAX_HISTORY_CONTENT) }))
  }

  function bindCustomer(id: string): void {
    if (customerId.value === id) return
    abortCurrent()
    messages.value = []
    unavailable.value = false
    customerId.value = id
  }

  /** Leaves the customer page: forget everything. */
  function reset(): void {
    abortCurrent()
    messages.value = []
    unavailable.value = false
    customerId.value = null
  }

  function markUnavailable(): void {
    unavailable.value = true
  }

  async function send(text: string): Promise<void> {
    const message = text.trim()
    const cid = customerId.value
    if (!message || !cid || pending.value || unavailable.value) return

    const history = buildHistory()
    const now = new Date().toISOString()
    messages.value.push(
      { id: nextId(), role: 'user', content: message, state: 'pending', createdAt: now },
      { id: nextId(), role: 'assistant', content: '', state: 'pending', createdAt: now },
    )
    // take the reactive proxies so later mutations re-render
    const user = messages.value[messages.value.length - 2]!
    const bot = messages.value[messages.value.length - 1]!

    const controller = new AbortController()
    current = controller
    pending.value = true

    try {
      const res = await aiAssistantApi.chat(cid, { message, history }, controller.signal)
      if (current !== controller) return
      user.state = 'done'
      bot.state = 'done'
      bot.content = res.answer
      bot.queriedAt = res.queriedAt
      bot.sources = res.sources
      bot.disclaimer = res.disclaimer
      bot.toolCalls = res.toolCalls
    } catch (e) {
      if (current !== controller) return
      if (e instanceof ApiError && e.code === NOT_FOUND_CODE) {
        unavailable.value = true
        messages.value = messages.value.filter((m) => m.id !== user.id && m.id !== bot.id)
      } else {
        user.state = 'error'
        bot.state = 'error'
        bot.error =
          e instanceof ApiError
            ? { code: e.code, message: e.message, requestId: e.requestId }
            : { code: 'UNKNOWN', message: '發生未預期的錯誤，請稍後再試' }
      }
    } finally {
      if (current === controller) {
        current = null
        pending.value = false
      }
    }
  }

  function cancel(): void {
    if (!current) return
    for (const m of messages.value) {
      if (m.state === 'pending') m.state = 'cancelled'
    }
    abortCurrent()
  }

  /** Re-send the question behind a failed answer. */
  async function retry(errorMessageId: string): Promise<void> {
    // same guards as send(): never remove the failed pair unless the re-send can go out
    if (pending.value || unavailable.value || !customerId.value) return
    const i = messages.value.findIndex((m) => m.id === errorMessageId && m.state === 'error')
    const question = i > 0 ? messages.value[i - 1] : undefined
    if (!question || question.role !== 'user') return
    const text = question.content
    messages.value.splice(i - 1, 2)
    await send(text)
  }

  function clear(): void {
    abortCurrent()
    messages.value = []
  }

  return { customerId, messages, pending, unavailable, bindCustomer, reset, markUnavailable, send, cancel, retry, clear }
})
