import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ApiError } from '@/utils/api-client'
import { aiAssistantApi } from '@/components/ai-assistant/ai-assistant-api'
import { nowIso } from '@/utils/taipei-time'
import type { ChatMessage, FeedbackRating, HistoryItem } from '@/beans/ai-assistant-bean'

export const MAX_HISTORY_MESSAGES = 20
export const MAX_HISTORY_CONTENT = 4000

const NOT_FOUND_CODE = 'AI_CUSTOMER_NOT_FOUND'

export const useAssistantStore = defineStore('aiAssistant', () => {
  const certNo = ref<string | null>(null)
  const messages = ref<ChatMessage[]>([])
  const pending = ref(false)
  const unavailable = ref(false)
  // New chat keeps earlier messages on screen but starts a new round: only the current round is sent as history.
  const round = ref(0)
  // Rolling summary of earlier turns of the current round; sent with each request, replaced by the server's newer one.
  const summary = ref<string | null>(null)

  // The in-flight request. Anything that invalidates it sets this to null, so a late
  // response (customer switch, cancel, new round) is recognised as stale and dropped.
  let current: AbortController | null = null
  let seq = 0

  // Per-message feedback tracking (not reactive): `desired` is the latest click, `confirmed` the
  // last value the server accepted, `inFlight` marks a running request. Cleared on reset/customer switch so late responses are ignored.
  const feedbackState = new Map<string, { desired: FeedbackRating | null; confirmed: FeedbackRating | null; inFlight: boolean }>()

  const nextId = () => `m${++seq}`

  function abortCurrent(): void {
    current?.abort()
    current = null
    pending.value = false
  }

  function buildHistory(): { items: HistoryItem[]; ids: string[] } {
    const kept = messages.value
      .filter((m) => m.round === round.value && m.state === 'done' && m.content.length > 0 && !m.summarized)
      .slice(-MAX_HISTORY_MESSAGES)
    return {
      items: kept.map((m) => ({ role: m.role, content: m.content.slice(0, MAX_HISTORY_CONTENT) })),
      ids: kept.map((m) => m.id),
    }
  }

  function bindCustomer(id: string): void {
    if (certNo.value === id) return
    abortCurrent()
    messages.value = []
    feedbackState.clear()
    unavailable.value = false
    round.value = 0
    summary.value = null
    certNo.value = id
  }

  /** Leaves the customer page: forget everything. */
  function reset(): void {
    abortCurrent()
    messages.value = []
    feedbackState.clear()
    unavailable.value = false
    round.value = 0
    summary.value = null
    certNo.value = null
  }

  function markUnavailable(): void {
    unavailable.value = true
  }

  async function send(text: string, categoryKey?: string): Promise<void> {
    const message = text.trim()
    const cid = certNo.value
    if (!message || !cid || pending.value || unavailable.value) return

    const { items: history, ids } = buildHistory()
    const now = nowIso()
    const r = round.value
    messages.value.push(
      { id: nextId(), role: 'user', content: message, state: 'pending', createdAt: now, round: r, ...(categoryKey ? { categoryKey } : {}) },
      { id: nextId(), role: 'assistant', content: '', state: 'pending', createdAt: now, round: r },
    )
    // take the reactive proxies so later mutations re-render
    const user = messages.value[messages.value.length - 2]!
    const bot = messages.value[messages.value.length - 1]!

    const controller = new AbortController()
    current = controller
    pending.value = true

    try {
      const res = await aiAssistantApi.chat(cid, { message, history, ...(categoryKey ? { categoryKey } : {}), ...(summary.value ? { historySummary: summary.value } : {}) }, controller.signal)
      if (current !== controller) return
      user.state = 'done'
      bot.state = 'done'
      bot.traceId = res.traceId
      bot.content = res.answer
      bot.queriedAt = res.queriedAt
      bot.blocks = res.blocks
      bot.citations = res.citations
      bot.disclaimer = res.disclaimer
      bot.toolCalls = res.toolCalls
      if (res.historySummary) {
        summary.value = res.historySummary.text
        const folded = new Set(ids.slice(0, res.historySummary.summarizedCount))
        for (const m of messages.value) if (folded.has(m.id)) m.summarized = true
      }
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
            ? { code: e.code, message: e.message, traceId: e.traceId }
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

  /** Re-send the question behind a failed answer (current round only). */
  async function retry(errorMessageId: string): Promise<void> {
    // same guards as send(): never remove the failed pair unless the re-send can go out
    if (pending.value || unavailable.value || !certNo.value) return
    const i = messages.value.findIndex((m) => m.id === errorMessageId && m.state === 'error')
    const question = i > 0 ? messages.value[i - 1] : undefined
    if (!question || question.role !== 'user' || question.round !== round.value) return
    const text = question.content
    const categoryKey = question.categoryKey
    messages.value.splice(i - 1, 2)
    await send(text, categoryKey)
  }

  /** Start a new round after the current one; a running request is cancelled. No-op while the current round is empty. */
  function startNewRound(): void {
    if (!messages.value.some((m) => m.round === round.value)) return
    cancel()
    round.value++
    summary.value = null
  }

  /**
   * Rate an answer. The UI shows the latest click at once; requests are serialised per message (one PUT in
   * flight), so the server ends up with the last click. If the newest value is rejected the UI falls back
   * to what the server last accepted.
   */
  async function rate(messageId: string, rating: FeedbackRating | null): Promise<void> {
    const m = messages.value.find((x) => x.id === messageId)
    if (!m || !m.traceId) return
    let entry = feedbackState.get(messageId)
    if (!entry) {
      const initial = m.feedback ?? null
      entry = { desired: initial, confirmed: initial, inFlight: false }
      feedbackState.set(messageId, entry)
    }
    entry.desired = rating
    m.feedback = rating
    if (entry.inFlight) return // the running loop sends the newest value when the current request settles
    entry.inFlight = true
    while (entry.desired !== entry.confirmed) {
      const sending = entry.desired
      try {
        await aiAssistantApi.putFeedback(m.traceId, sending)
        if (feedbackState.get(messageId) !== entry) return // conversation was reset meanwhile
        entry.confirmed = sending
      } catch {
        if (feedbackState.get(messageId) !== entry) return
        // the global toast already reported it; a newer click is still sent, otherwise show the server's value
        if (entry.desired === sending) {
          entry.desired = entry.confirmed
          m.feedback = entry.confirmed
        }
      }
    }
    entry.inFlight = false
  }

  return { certNo, messages, pending, unavailable, round, summary, bindCustomer, reset, markUnavailable, send, cancel, retry, startNewRound, rate }
})
