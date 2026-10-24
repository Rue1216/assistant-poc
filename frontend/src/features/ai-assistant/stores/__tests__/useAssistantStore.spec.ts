import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { ChatResponse, Citation } from '../../types'

const { chat } = vi.hoisted(() => ({ chat: vi.fn() }))
vi.mock('../../api/aiAssistantApi', () => ({ aiAssistantApi: { chat, suggestions: vi.fn() } }))
vi.mock('../../../../platform-mock/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }))

import { ApiError } from '../../../../platform-mock/apiClient'
import { useAssistantStore } from '../useAssistantStore'

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
  })

  it('stores blocks and citations on the bot message and keeps answer as content', async () => {
    chat.mockResolvedValue(response('答案'))
    const store = useAssistantStore()
    store.bindCustomer('C001')
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
    store.bindCustomer('C001')
    await store.send('  幫我整理這位顧客的概況  ')

    expect(store.messages.map((m) => [m.role, m.state, m.content])).toEqual([
      ['user', 'done', '幫我整理這位顧客的概況'],
      ['assistant', 'done', '## 概況'],
    ])
    expect(chat).toHaveBeenCalledWith('C001', { message: '幫我整理這位顧客的概況', history: [] }, expect.any(AbortSignal))
    expect(store.pending).toBe(false)
  })

  it('prevents double submit while pending', async () => {
    const d = deferred<ChatResponse>()
    chat.mockReturnValue(d.promise)
    const store = useAssistantStore()
    store.bindCustomer('C001')
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
    store.bindCustomer('C001')
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
    store.bindCustomer('C001')
    await store.send('hello')
    expect(store.messages).toHaveLength(2)
    store.bindCustomer('C002')
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
    store.bindCustomer('C001')
    const first = store.send('q1')
    store.bindCustomer('C002')
    const second = store.send('q2')
    expect(calls).toHaveLength(2)
    expect(calls[0]!.signal.aborted).toBe(true)

    // C001's answer arrives while C002's request is in flight
    calls[0]!.d.resolve(response('C001 的回答'))
    await first

    expect(store.customerId).toBe('C002')
    expect(store.messages.map((m) => [m.role, m.state, m.content])).toEqual([
      ['user', 'pending', 'q2'],
      ['assistant', 'pending', ''],
    ])
    expect(store.pending).toBe(true)

    // cancel() must abort C002's request, not C001's
    store.cancel()
    expect(calls[1]!.signal.aborted).toBe(true)

    // a fresh C002 request still lands as a done answer
    calls[1]!.d.reject(new DOMException('aborted', 'AbortError'))
    await second
    const third = store.send('q3')
    calls[2]!.d.resolve(response('C002 的回答'))
    await third
    expect(store.messages.at(-1)).toMatchObject({ state: 'done', content: 'C002 的回答' })
    expect(store.messages.some((m) => m.content.includes('C001'))).toBe(false)
  })

  it('lets the new customer request complete after a stale one resolved', async () => {
    const calls = queueChats()
    const store = useAssistantStore()
    store.bindCustomer('C001')
    const first = store.send('q1')
    store.bindCustomer('C002')
    const second = store.send('q2')
    calls[0]!.d.resolve(response('C001 的回答'))
    await first
    calls[1]!.d.resolve(response('C002 的回答'))
    await second
    expect(store.pending).toBe(false)
    expect(store.messages.map((m) => [m.state, m.content])).toEqual([
      ['done', 'q2'],
      ['done', 'C002 的回答'],
    ])
  })

  it('discards an error that arrives after customer switch', async () => {
    const d = deferred<ChatResponse>()
    chat.mockReturnValue(d.promise)
    const store = useAssistantStore()
    store.bindCustomer('C001')
    const sending = store.send('hello')
    store.bindCustomer('C002')
    d.reject(new ApiError(404, 'AI_CUSTOMER_NOT_FOUND', 'x'))
    await sending

    expect(store.unavailable).toBe(false)
    expect(store.messages).toEqual([])
  })

  it('marks unavailable on AI_CUSTOMER_NOT_FOUND', async () => {
    chat.mockRejectedValue(new ApiError(404, 'AI_CUSTOMER_NOT_FOUND', '查無此顧客或您沒有查看權限'))
    const store = useAssistantStore()
    store.bindCustomer('C001')
    await store.send('hello')
    expect(store.unavailable).toBe(true)
    expect(store.messages).toEqual([])
    await store.send('again')
    expect(chat).toHaveBeenCalledTimes(1)
  })

  it('keeps an error message with details and retries the question', async () => {
    chat.mockRejectedValueOnce(new ApiError(429, 'AI_RATE_LIMITED', '目前提問次數較多', 'a1b2c3d4'))
    const store = useAssistantStore()
    store.bindCustomer('C001')
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
    store.bindCustomer('C001')
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
    store.bindCustomer('C001')
    chat.mockResolvedValue(response('x'.repeat(5000)))
    await store.send('q')
    chat.mockResolvedValue(response('y'))
    await store.send('next')
    const history = chat.mock.calls[1]![1].history as { content: string }[]
    expect(history[1]!.content).toHaveLength(4000)
  })

  it('clear() empties messages and drops a late response', async () => {
    const calls = queueChats()
    const store = useAssistantStore()
    store.bindCustomer('C001')
    const first = store.send('q1')
    store.clear()
    expect(calls[0]!.signal.aborted).toBe(true)
    const second = store.send('q2')

    calls[0]!.d.resolve(response('late'))
    await first
    expect(store.messages.map((m) => [m.state, m.content])).toEqual([
      ['pending', 'q2'],
      ['pending', ''],
    ])
    expect(store.pending).toBe(true)

    store.cancel()
    expect(calls[1]!.signal.aborted).toBe(true)
    calls[1]!.d.reject(new DOMException('aborted', 'AbortError'))
    await second

    const third = store.send('q3')
    calls[2]!.d.resolve(response('fresh'))
    await third
    expect(store.messages.at(-1)).toMatchObject({ state: 'done', content: 'fresh' })
  })

  it('resets unavailable when the customer changes', async () => {
    chat.mockRejectedValueOnce(new ApiError(404, 'AI_CUSTOMER_NOT_FOUND', 'x'))
    const store = useAssistantStore()
    store.bindCustomer('C001')
    await store.send('hello')
    expect(store.unavailable).toBe(true)

    store.bindCustomer('C002')
    expect(store.unavailable).toBe(false)
    chat.mockResolvedValue(response('ok'))
    await store.send('hello')
    expect(store.messages.at(-1)).toMatchObject({ state: 'done', content: 'ok' })
  })

  it('retry keeps the failed pair when the re-send is not allowed', async () => {
    chat.mockRejectedValueOnce(new ApiError(503, 'AI_LLM_UNAVAILABLE', 'x'))
    const store = useAssistantStore()
    store.bindCustomer('C001')
    await store.send('hello')
    store.markUnavailable()
    await store.retry(store.messages[1]!.id)
    expect(store.messages.map((m) => m.state)).toEqual(['error', 'error'])
  })
})
