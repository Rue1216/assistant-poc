import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import type { ChatMessage, Citation } from '../../types'

vi.mock('../../../../platform-mock/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }))

import AssistantMessage from '../AssistantMessage.vue'
import { formatTaipei } from '../../taipeiTime'

function cit(id: number, cited = true): Citation {
  return {
    id,
    toolName: 't',
    title: `標題${id}`,
    target: null,
    excerpt: null,
    description: '說明',
    sourceName: '平台顧客資料（模擬）',
    url: null,
    dataTime: null,
    queriedAt: '2026-10-19T14:03:22+08:00',
    cited,
  }
}

function msg(extra: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: 'm1',
    role: 'assistant',
    content: '### 概況\n- 本顧客為 VIP',
    state: 'done',
    createdAt: '2026-10-19T06:03:20Z',
    round: 0,
    queriedAt: '2026-10-19T14:03:24+08:00',
    blocks: [],
    citations: [],
    disclaimer: null,
    toolCalls: [],
    ...extra,
  }
}

describe('AssistantMessage', () => {
  it('renders answer blocks with citation buttons', () => {
    const w = mount(AssistantMessage, {
      props: {
        message: msg({
          blocks: [
            { type: 'heading', text: '概況', citations: [] },
            { type: 'paragraph', text: '<script>alert(1)</script> **粗體**', citations: [1] },
          ],
          citations: [cit(1)],
        }),
      },
    })
    expect(w.findAll('button.ai-cite')).toHaveLength(1)
    expect(w.find('script').exists()).toBe(false)
    expect(w.find('strong').text()).toBe('粗體')
    expect(w.find('.ai-msg__time').text()).toBe('14:03')
  })

  it('does not show tool calls', () => {
    const w = mount(AssistantMessage, {
      props: {
        message: msg({
          toolCalls: [{ name: 'getCustomerHoldings', purpose: '查詢顧客持有商品概況', status: 'SUCCESS', durationMs: 9 }],
        }),
      },
    })
    expect(w.text()).not.toContain('getCustomerHoldings')
    expect(w.text()).not.toContain('個工具')
    expect(w.find('.ai-meta').exists()).toBe(false)
  })

  it('shows disclaimer and citation list; ai-meta only with citations', () => {
    expect(mount(AssistantMessage, { props: { message: msg() } }).find('.ai-meta').exists()).toBe(false)
    const w = mount(AssistantMessage, {
      props: {
        message: msg({
          disclaimer: '資訊僅供內部經營參考，不構成投資建議',
          citations: [cit(1), cit(2, false)],
        }),
      },
    })
    expect(w.find('.ai-disclaimer').text()).toBe('資訊僅供內部經營參考，不構成投資建議')
    expect(w.find('.ai-meta').exists()).toBe(true)
    expect(w.findAll('.ai-citation')).toHaveLength(2)
  })

  it('clicking a citation button opens and highlights the item', async () => {
    Element.prototype.scrollIntoView = vi.fn()
    const w = mount(AssistantMessage, {
      props: {
        message: msg({
          blocks: [{ type: 'paragraph', text: '收盤價', citations: [2] }],
          citations: [cit(1), cit(2)],
        }),
      },
    })
    await w.get('button.ai-cite').trigger('click')
    const item = w.get('[data-citation-id="2"]')
    expect((item.element as HTMLDetailsElement).open).toBe(true)
    expect(item.classes()).toContain('ai-citation--flash')
  })

  it('copy button copies content', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    const w = mount(AssistantMessage, { props: { message: msg() } })
    await w.get('.ai-mini-btn').trigger('click')
    expect(writeText).toHaveBeenCalledWith('### 概況\n- 本顧客為 VIP')
  })
})

describe('AssistantMessage feedback', () => {
  it('shows feedback buttons for a done answer with traceId', () => {
    const w = mount(AssistantMessage, { props: { message: msg({ traceId: '0af7651916cd43dd8448eb211c80319c' }) } })
    expect(w.findComponent({ name: 'FeedbackButtons' }).exists()).toBe(true)
  })

  it('hides them without traceId or on error', () => {
    expect(mount(AssistantMessage, { props: { message: msg() } }).findComponent({ name: 'FeedbackButtons' }).exists()).toBe(false)
    const err = msg({ traceId: '0af7651916cd43dd8448eb211c80319c', state: 'error' })
    expect(mount(AssistantMessage, { props: { message: err } }).findComponent({ name: 'FeedbackButtons' }).exists()).toBe(false)
  })

  it('emits rate upward', async () => {
    const w = mount(AssistantMessage, { props: { message: msg({ traceId: '0af7651916cd43dd8448eb211c80319c', feedback: null }) } })
    await w.get('[aria-label="有幫助"]').trigger('click')
    expect(w.emitted('rate')).toEqual([['UP']])
  })
})

describe('formatTaipei', () => {
  it('formats in Asia/Taipei', () => {
    expect(formatTaipei('2026-10-24T01:00:00Z')).toBe('2026/10/24 09:00')
    expect(formatTaipei('2026-10-23T16:30:00Z')).toBe('2026/10/24 00:30')
  })
})
