import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { ChatMessage } from '@/beans/ai-assistant-bean'
import MessageList from '@/components/ai-assistant/message-list.vue'

const stub = { template: '<div class="msg" />' }

function make(round: number, i: number, summarized = false): ChatMessage {
  return {
    id: `r${round}-${i}`,
    role: i % 2 === 0 ? 'user' : 'assistant',
    content: 'x',
    state: 'done',
    createdAt: '2026-10-19T06:03:20Z',
    round,
    summarized,
  }
}

function mountList(messages: ChatMessage[], round = 0) {
  return mount(MessageList, {
    props: { messages, round, categories: [], unavailable: false },
    global: {
      stubs: {
        UserMessage: stub,
        AssistantMessage: stub,
        PendingMessage: stub,
        ErrorMessage: stub,
        WelcomeCard: stub,
      },
    },
  })
}

const hints = (w: ReturnType<typeof mountList>) =>
  w.findAll('.ai-divider span').filter((s) => s.text().includes('已整理成摘要'))

describe('MessageList summary hint', () => {
  it('shows the summary hint after the last summarized message', () => {
    const msgs = [0, 1, 2, 3, 4, 5].map((i) => make(0, i, i < 4))
    const w = mountList(msgs)
    const found = hints(w)
    expect(found).toHaveLength(1)
    expect(found[0]!.text()).toBe('以上 4 則對話已整理成摘要，小幫手會參考摘要繼續回答')
    const kids = Array.from(w.find('.ai-thread').element.children)
    const idx = kids.indexOf(found[0]!.element.closest('.ai-round-break')!)
    const before = kids.slice(0, idx).filter((k) => k.classList.contains('msg'))
    const after = kids.slice(idx + 1).filter((k) => k.classList.contains('msg'))
    expect(before).toHaveLength(4)
    expect(after).toHaveLength(2)
  })

  it('counts per round', () => {
    const msgs = [
      ...[0, 1, 2, 3].map((i) => make(0, i, i < 2)),
      ...[0, 1, 2, 3, 4, 5].map((i) => make(1, i, i < 4)),
    ]
    const found = hints(mountList(msgs, 1))
    expect(found.map((f) => f.text())).toEqual([
      '以上 2 則對話已整理成摘要，小幫手會參考摘要繼續回答',
      '以上 4 則對話已整理成摘要，小幫手會參考摘要繼續回答',
    ])
  })

  it('no hint without summarized messages', () => {
    const msgs = [0, 1, 2, 3].map((i) => make(0, i))
    expect(hints(mountList(msgs))).toHaveLength(0)
  })
})
