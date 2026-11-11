import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { ChatResponse } from '@/beans/ai-assistant-bean'

const { chat, putFeedback } = vi.hoisted(() => ({ chat: vi.fn(), putFeedback: vi.fn() }))
vi.mock('@/components/ai-assistant/ai-assistant-api', () => ({ aiAssistantApi: { chat, putFeedback, suggestions: vi.fn() } }))
vi.mock('@/composables/use-toast', () => ({ useToast: () => ({ show: vi.fn() }) }))

import { MAX_MESSAGE_LENGTH, useAssistantChat } from '@/composables/use-assistant-chat'
import { useAssistantStore } from '@/stores/use-assistant-store'
import { RETIREE } from '../fixtures/test-customers'

function response(answer: string): ChatResponse {
  return {
    traceId: '0af7651916cd43dd8448eb211c80319c',
    answer,
    blocks: [{ type: 'paragraph', text: answer, citations: [] }],
    citations: [],
    queriedAt: '2026-10-19T14:03:24+08:00',
    disclaimer: null,
    toolCalls: [],
    historySummary: null,
  }
}

function setup() {
  const store = useAssistantStore()
  store.bindCustomer(RETIREE)
  return { store, chatState: useAssistantChat() }
}

describe('useAssistantChat', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    chat.mockReset()
    putFeedback.mockReset()
  })

  it('cuts the draft at MAX_MESSAGE_LENGTH and counts its length', () => {
    const { chatState } = setup()
    chatState.setDraft('字'.repeat(MAX_MESSAGE_LENGTH + 5))
    expect(chatState.draft.value).toHaveLength(MAX_MESSAGE_LENGTH)
    expect(chatState.count.value).toBe(MAX_MESSAGE_LENGTH)
  })

  it('can send only a non-blank draft', () => {
    const { chatState } = setup()
    expect(chatState.canSend.value).toBe(false)
    chatState.setDraft('   ')
    expect(chatState.canSend.value).toBe(false)
    chatState.setDraft('近期交易')
    expect(chatState.canSend.value).toBe(true)
  })

  it('submits the trimmed draft and clears it', async () => {
    chat.mockResolvedValue(response('答案'))
    const { chatState } = setup()
    chatState.setDraft('  近期交易  ')
    chatState.submit()
    expect(chatState.draft.value).toBe('')
    await flushPromises()
    expect(chat.mock.calls[0]![1]).toMatchObject({ message: '近期交易' })
    expect(chatState.messages.value.map((message) => message.content)).toEqual(['近期交易', '答案'])
  })

  it('does not submit while a request is pending and keeps the draft', async () => {
    chat.mockReturnValue(new Promise(() => {}))
    const { chatState } = setup()
    chatState.ask('第一題')
    expect(chatState.pending.value).toBe(true)
    chatState.setDraft('第二題')
    expect(chatState.canSend.value).toBe(false)
    chatState.submit()
    expect(chatState.draft.value).toBe('第二題')
    expect(chat).toHaveBeenCalledTimes(1)
  })

  it('does not submit when the customer is unavailable', () => {
    const { store, chatState } = setup()
    store.markUnavailable()
    chatState.setDraft('近期交易')
    expect(chatState.canSend.value).toBe(false)
    chatState.submit()
    expect(chat).not.toHaveBeenCalled()
  })

  it('asks a quick question with its category key', async () => {
    chat.mockResolvedValue(response('答案'))
    const { chatState } = setup()
    chatState.ask('客戶近期有什麼交易？', 'instruction.contact-prep')
    await flushPromises()
    expect(chat.mock.calls[0]![1]).toMatchObject({
      message: '客戶近期有什麼交易？',
      categoryKey: 'instruction.contact-prep',
    })
  })

  it('cancel marks the pending pair cancelled', () => {
    chat.mockReturnValue(new Promise(() => {}))
    const { chatState } = setup()
    chatState.ask('近期交易')
    chatState.cancel()
    expect(chatState.pending.value).toBe(false)
    expect(chatState.messages.value.map((message) => message.state)).toEqual(['cancelled', 'cancelled'])
  })

  it('startNewRound moves to the next round', async () => {
    chat.mockResolvedValue(response('答案'))
    const { chatState } = setup()
    chatState.ask('近期交易')
    await flushPromises()
    chatState.startNewRound()
    expect(chatState.round.value).toBe(1)
  })

  it('retry re-sends the question behind a failed answer', async () => {
    chat.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(response('答案'))
    const { chatState } = setup()
    chatState.ask('近期交易')
    await flushPromises()
    const failed = chatState.messages.value.find((message) => message.state === 'error' && message.role === 'assistant')!
    chatState.retry(failed.id)
    await flushPromises()
    expect(chat).toHaveBeenCalledTimes(2)
    expect(chatState.messages.value.map((message) => message.content)).toEqual(['近期交易', '答案'])
  })

  it('rate sends the rating for the answer', async () => {
    chat.mockResolvedValue(response('答案'))
    putFeedback.mockResolvedValue(undefined)
    const { chatState } = setup()
    chatState.ask('近期交易')
    await flushPromises()
    const answer = chatState.messages.value[1]!
    chatState.rate(answer.id, 'UP')
    await flushPromises()
    expect(putFeedback).toHaveBeenCalledWith('0af7651916cd43dd8448eb211c80319c', 'UP')
    expect(chatState.messages.value[1]!.feedback).toBe('UP')
  })
})
