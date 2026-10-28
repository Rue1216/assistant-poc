import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { ChatResponse, Citation } from '../../types'

const { chat, putFeedback } = vi.hoisted(() => ({ chat: vi.fn(), putFeedback: vi.fn() }))
vi.mock('../../api/aiAssistantApi', () => ({ aiAssistantApi: { chat, putFeedback, suggestions: vi.fn() } }))
vi.mock('../../../../platform-mock/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }))

import { ApiError } from '../../../../platform-mock/apiClient'
import { useAssistantStore } from '../useAssistantStore'
import { COMPLAINT, RETIREE } from '../../../../testing/testCustomers'

const CITATION: Citation = {
  id: 1, toolName: 't', title: '上市個股行情', target: '2330', excerpt: null, description: null,
  sourceName: null, url: null, dataTime: null, queriedAt: '2026-10-19T14:03:24+08:00', cited: true,
}

function response(answer: string): ChatResponse {
  return {
    requestId: 'r1',
    answer,
    blocks: [{ type: 'paragraph', text: answer, citations: [1] }],
    citations: [CITATION],
    queriedAt: '2026-10-19T14:03:24+08:00',
    disclaimer: null,
    toolCalls: [],
  }
}

function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (e: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('useAssistantStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    chat.mockReset()
    putFeedback.mockReset()
  })

  it('stores blocks and citations on the bot message and keeps answer as content', async () => {
    chat.mockResolvedValue(response('答案'))
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    await store.send('q')
    const bot = store.messages[1]!
    expect(bot.content).toBe('答案')
    expect(bot.blocks).toEqual([{ type: 'paragraph', text: '答案', citations: [1] }])
    expect(bot.citations).toEqual([CITATION])
    await store.send('q2')
    expect(chat.mock.calls[1]![1].history).toEqual([
      { role: 'user', content: 'q' },
      { role: 'assistant', content: '答案' },
    ])
  })

  it('appends user and assistant messages', async () => {
    chat.mockResolvedValue(response('## 概況'))
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    await store.send('  幫我整理這位顧客的概況  ')

    expect(store.messages.map((m) => [m.role, m.state, m.content])).toEqual([
      ['user', 'done', '幫我整理這位顧客的概況'],
      ['assistant', 'done', '## 概況'],
    ])
    expect(chat).toHaveBeenCalledWith(RETIREE, { message: '幫我整理這位顧客的概況', history: [] }, expect.any(AbortSignal))
    expect(store.pending).toBe(false)
  })

  it('sends categoryKey when given, and omits the property otherwise', async () => {
    chat.mockResolvedValue(response('x'))
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    await store.send('q', 'instruction.contact-prep')
    expect(chat.mock.calls[0]![1]).toEqual({ message: 'q', history: [], categoryKey: 'instruction.contact-prep' })
    await store.send('q2')
    expect('categoryKey' in chat.mock.calls[1]![1]).toBe(false)
  })

  it('retry keeps the categoryKey of a chip question', async () => {
    chat.mockRejectedValueOnce(new ApiError(429, 'AI_RATE_LIMITED', 'x'))
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    await store.send('hello', 'instruction.market')
    chat.mockResolvedValueOnce(response('ok'))
    await store.retry(store.messages[1]!.id)
    expect(chat.mock.calls[1]![1].categoryKey).toBe('instruction.market')
  })

  it('prevents double submit while pending', async () => {
    const d = deferred<ChatResponse>()
    chat.mockReturnValue(d.promise)
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    const first = store.send('a')
    await store.send('b')
    expect(chat).toHaveBeenCalledTimes(1)
    d.resolve(response('x'))
    await first
    expect(store.messages).toHaveLength(2)
  })

  it('cancel marks message cancelled without toast', async () => {
    const d = deferred<ChatResponse>()
    chat.mockImplementation((_id: string, _body: unknown, signal: AbortSignal) => {
      signal.addEventListener('abort', () => d.reject(new DOMException('aborted', 'AbortError')))
      return d.promise
    })
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    const sending = store.send('hello')
    store.cancel()
    await sending

    expect(store.messages.map((m) => m.state)).toEqual(['cancelled', 'cancelled'])
    expect(store.pending).toBe(false)
    // no toast on cancel: the store never toasts and apiClient passes cancels through (see apiClient.spec)
  })

  it('clears conversation when customer changes', async () => {
    chat.mockResolvedValue(response('x'))
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    await store.send('hello')
    expect(store.messages).toHaveLength(2)
    store.bindCustomer(COMPLAINT)
    expect(store.messages).toEqual([])
  })

  /** One deferred per chat() call, with the signal that call received. */
  function queueChats() {
    const calls: { d: ReturnType<typeof deferred<ChatResponse>>; signal: AbortSignal }[] = []
    chat.mockImplementation((_id: string, _body: unknown, signal: AbortSignal) => {
      const d = deferred<ChatResponse>()
      calls.push({ d, signal })
      return d.promise
    })
    return calls
  }

  it('discards response that arrives after customer switch', async () => {
    const calls = queueChats()
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    const first = store.send('q1')
    store.bindCustomer(COMPLAINT)
    const second = store.send('q2')
    expect(calls).toHaveLength(2)
    expect(calls[0]!.signal.aborted).toBe(true)

    // RETIREE's answer arrives while COMPLAINT's request is in flight
    calls[0]!.d.resolve(response('王小明的回答'))
    await first

    expect(store.certNo).toBe(COMPLAINT)
    expect(store.messages.map((m) => [m.role, m.state, m.content])).toEqual([
      ['user', 'pending', 'q2'],
      ['assistant', 'pending', ''],
    ])
    expect(store.pending).toBe(true)

    // cancel() must abort COMPLAINT's request, not RETIREE's
    store.cancel()
    expect(calls[1]!.signal.aborted).toBe(true)

    // a fresh COMPLAINT request still lands as a done answer
    calls[1]!.d.reject(new DOMException('aborted', 'AbortError'))
    await second
    const third = store.send('q3')
    calls[2]!.d.resolve(response('林美君的回答'))
    await third
    expect(store.messages.at(-1)).toMatchObject({ state: 'done', content: '林美君的回答' })
    expect(store.messages.some((m) => m.content.includes(RETIREE))).toBe(false)
  })

  it('lets the new customer request complete after a stale one resolved', async () => {
    const calls = queueChats()
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    const first = store.send('q1')
    store.bindCustomer(COMPLAINT)
    const second = store.send('q2')
    calls[0]!.d.resolve(response('王小明的回答'))
    await first
    calls[1]!.d.resolve(response('林美君的回答'))
    await second
    expect(store.pending).toBe(false)
    expect(store.messages.map((m) => [m.state, m.content])).toEqual([
      ['done', 'q2'],
      ['done', '林美君的回答'],
    ])
  })

  it('discards an error that arrives after customer switch', async () => {
    const d = deferred<ChatResponse>()
    chat.mockReturnValue(d.promise)
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    const sending = store.send('hello')
    store.bindCustomer(COMPLAINT)
    d.reject(new ApiError(404, 'AI_CUSTOMER_NOT_FOUND', 'x'))
    await sending

    expect(store.unavailable).toBe(false)
    expect(store.messages).toEqual([])
  })

  it('marks unavailable on AI_CUSTOMER_NOT_FOUND', async () => {
    chat.mockRejectedValue(new ApiError(404, 'AI_CUSTOMER_NOT_FOUND', '查無此顧客或您沒有查看權限'))
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    await store.send('hello')
    expect(store.unavailable).toBe(true)
    expect(store.messages).toEqual([])
    await store.send('again')
    expect(chat).toHaveBeenCalledTimes(1)
  })

  it('keeps an error message with details and retries the question', async () => {
    chat.mockRejectedValueOnce(new ApiError(429, 'AI_RATE_LIMITED', '目前提問次數較多', 'a1b2c3d4'))
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    await store.send('hello')
    expect(store.messages.map((m) => m.state)).toEqual(['error', 'error'])
    expect(store.messages[1]!.error).toEqual({ code: 'AI_RATE_LIMITED', message: '目前提問次數較多', requestId: 'a1b2c3d4' })

    chat.mockResolvedValueOnce(response('ok'))
    await store.retry(store.messages[1]!.id)
    expect(store.messages.map((m) => [m.role, m.state, m.content])).toEqual([
      ['user', 'done', 'hello'],
      ['assistant', 'done', 'ok'],
    ])
    // failed attempt is not part of the history
    expect(chat.mock.calls[1]![1].history).toEqual([])
  })

  it('sends only done messages as history, max 20', async () => {
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    chat.mockResolvedValue(response('answer'))
    for (let i = 0; i < 12; i++) await store.send(`q${i}`)
    // one failed turn must not enter the history
    chat.mockRejectedValueOnce(new ApiError(503, 'AI_LLM_UNAVAILABLE', 'x'))
    await store.send('failed')

    chat.mockResolvedValue(response('last'))
    await store.send('next')
    const history = chat.mock.calls.at(-1)![1].history as { role: string; content: string }[]
    expect(history).toHaveLength(20)
    expect(history.map((h) => h.content)).not.toContain('failed')
    expect(history.at(-1)).toEqual({ role: 'assistant', content: 'answer' })
    expect(history[0]).toEqual({ role: 'user', content: 'q2' })
  })

  it('truncates each history item to 4000 characters', async () => {
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    chat.mockResolvedValue(response('x'.repeat(5000)))
    await store.send('q')
    chat.mockResolvedValue(response('y'))
    await store.send('next')
    const history = chat.mock.calls[1]![1].history as { content: string }[]
    expect(history[1]!.content).toHaveLength(4000)
  })

  describe('new round', () => {
    it('keeps earlier messages and sends only the current round as history', async () => {
      chat.mockResolvedValue(response('a'))
      const store = useAssistantStore()
      store.bindCustomer(RETIREE)
      await store.send('q1')
      store.startNewRound()
      expect(store.round).toBe(1)

      await store.send('q2')
      expect(chat.mock.calls[1]![1].history).toEqual([])
      await store.send('q3')
      expect(chat.mock.calls[2]![1].history).toEqual([
        { role: 'user', content: 'q2' },
        { role: 'assistant', content: 'a' },
      ])
      expect(store.messages.map((m) => [m.content, m.round])).toEqual([
        ['q1', 0], ['a', 0], ['q2', 1], ['a', 1], ['q3', 1], ['a', 1],
      ])
    })

    it('is a no-op while the current round is empty', async () => {
      chat.mockResolvedValue(response('a'))
      const store = useAssistantStore()
      store.bindCustomer(RETIREE)
      store.startNewRound()
      expect(store.round).toBe(0)

      await store.send('q1')
      store.startNewRound()
      store.startNewRound()
      expect(store.round).toBe(1)
    })

    it('cancels a running request and drops its late response', async () => {
      const calls = queueChats()
      const store = useAssistantStore()
      store.bindCustomer(RETIREE)
      const first = store.send('q1')
      store.startNewRound()
      expect(calls[0]!.signal.aborted).toBe(true)
      expect(store.pending).toBe(false)
      expect(store.round).toBe(1)

      calls[0]!.d.resolve(response('late'))
      await first
      expect(store.messages.map((m) => [m.state, m.content])).toEqual([
        ['cancelled', 'q1'],
        ['cancelled', ''],
      ])

      const second = store.send('q2')
      calls[1]!.d.resolve(response('fresh'))
      await second
      expect(chat.mock.calls[1]![1].history).toEqual([])
      expect(store.messages.at(-1)).toMatchObject({ state: 'done', content: 'fresh', round: 1 })
    })

    it('does not retry an error from an earlier round', async () => {
      chat.mockRejectedValueOnce(new ApiError(503, 'AI_LLM_UNAVAILABLE', 'x'))
      const store = useAssistantStore()
      store.bindCustomer(RETIREE)
      await store.send('hello')
      store.startNewRound()
      await store.retry(store.messages[1]!.id)
      expect(chat).toHaveBeenCalledTimes(1)
      expect(store.messages.map((m) => m.state)).toEqual(['error', 'error'])
    })

    it('starts again from round 0 when the customer changes', async () => {
      chat.mockResolvedValue(response('a'))
      const store = useAssistantStore()
      store.bindCustomer(RETIREE)
      await store.send('q1')
      store.startNewRound()
      store.bindCustomer(COMPLAINT)
      expect(store.round).toBe(0)
      expect(store.messages).toEqual([])
    })
  })

  it('resets unavailable when the customer changes', async () => {
    chat.mockRejectedValueOnce(new ApiError(404, 'AI_CUSTOMER_NOT_FOUND', 'x'))
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    await store.send('hello')
    expect(store.unavailable).toBe(true)

    store.bindCustomer(COMPLAINT)
    expect(store.unavailable).toBe(false)
    chat.mockResolvedValue(response('ok'))
    await store.send('hello')
    expect(store.messages.at(-1)).toMatchObject({ state: 'done', content: 'ok' })
  })

  it('retry keeps the failed pair when the re-send is not allowed', async () => {
    chat.mockRejectedValueOnce(new ApiError(503, 'AI_LLM_UNAVAILABLE', 'x'))
    const store = useAssistantStore()
    store.bindCustomer(RETIREE)
    await store.send('hello')
    store.markUnavailable()
    await store.retry(store.messages[1]!.id)
    expect(store.messages.map((m) => m.state)).toEqual(['error', 'error'])
  })

  describe('feedback', () => {
    async function answered() {
      chat.mockResolvedValue(response('答案'))
      const store = useAssistantStore()
      store.bindCustomer(RETIREE)
      await store.send('q')
      return store
    }

    it('send stores requestId', async () => {
      const store = await answered()
      expect(store.messages[1]!.requestId).toBe('r1')
    })

    it('rate updates optimistically and calls api', async () => {
      const store = await answered()
      const d = deferred<void>()
      putFeedback.mockReturnValue(d.promise)
      const p = store.rate(store.messages[1]!.id, 'UP')
      expect(store.messages[1]!.feedback).toBe('UP')
      expect(putFeedback).toHaveBeenCalledWith('r1', 'UP')
      d.resolve()
      await p
      expect(store.messages[1]!.feedback).toBe('UP')
    })

    it('rate reverts on failure', async () => {
      const store = await answered()
      putFeedback.mockRejectedValue(new Error('x'))
      await store.rate(store.messages[1]!.id, 'UP')
      expect(store.messages[1]!.feedback ?? null).toBeNull()
    })

    it('rapid clicks keep the last choice when an earlier request fails', async () => {
      const store = await answered()
      const first = deferred<void>()
      putFeedback.mockReturnValueOnce(first.promise).mockResolvedValueOnce(undefined)
      const id = store.messages[1]!.id
      const p1 = store.rate(id, 'UP')
      await store.rate(id, 'DOWN')
      first.reject(new Error('x'))
      await p1
      expect(store.messages[1]!.feedback).toBe('DOWN')
    })

    it('queues a newer click and sends it after the in-flight one succeeds', async () => {
      const store = await answered()
      const first = deferred<void>()
      putFeedback.mockReturnValueOnce(first.promise).mockResolvedValueOnce(undefined)
      const id = store.messages[1]!.id
      const p1 = store.rate(id, 'UP')
      await store.rate(id, 'DOWN')
      expect(putFeedback).toHaveBeenCalledTimes(1)
      first.resolve()
      await p1
      expect(putFeedback.mock.calls.map((c) => c[1])).toEqual(['UP', 'DOWN'])
      expect(store.messages[1]!.feedback).toBe('DOWN')
    })

    it('still sends the newer click when the in-flight one fails', async () => {
      const store = await answered()
      const first = deferred<void>()
      putFeedback.mockReturnValueOnce(first.promise).mockResolvedValueOnce(undefined)
      const id = store.messages[1]!.id
      const p1 = store.rate(id, 'UP')
      await store.rate(id, 'DOWN')
      first.reject(new Error('x'))
      await p1
      expect(putFeedback.mock.calls.map((c) => c[1])).toEqual(['UP', 'DOWN'])
      expect(store.messages[1]!.feedback).toBe('DOWN')
    })

    it('falls back to the confirmed value when a later rating fails', async () => {
      const store = await answered()
      const id = store.messages[1]!.id
      putFeedback.mockResolvedValueOnce(undefined).mockRejectedValueOnce(new Error('x'))
      await store.rate(id, 'UP')
      await store.rate(id, 'DOWN')
      expect(store.messages[1]!.feedback).toBe('UP')
    })

    it('sends nothing more when the user returns to the confirmed value', async () => {
      const store = await answered()
      const first = deferred<void>()
      putFeedback.mockReturnValueOnce(first.promise)
      const id = store.messages[1]!.id
      const p1 = store.rate(id, 'UP')
      await store.rate(id, 'DOWN')
      await store.rate(id, 'UP')
      first.resolve()
      await p1
      expect(putFeedback).toHaveBeenCalledTimes(1)
      expect(store.messages[1]!.feedback).toBe('UP')
    })

    it('feedback response after reset is ignored', async () => {
      const store = await answered()
      const d = deferred<void>()
      putFeedback.mockReturnValue(d.promise)
      const p = store.rate(store.messages[1]!.id, 'UP')
      store.reset()
      d.reject(new Error('x'))
      await expect(p).resolves.toBeUndefined()
      expect(store.messages).toEqual([])
    })
  })
})
