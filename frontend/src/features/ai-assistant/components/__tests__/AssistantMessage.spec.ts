import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import type { ChatMessage } from '../../types'

vi.mock('../../../../platform-mock/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }))

import AssistantMessage from '../AssistantMessage.vue'
import SafeMarkdown from '../../markdown/SafeMarkdown'
import { formatTaipei } from '../../taipeiTime'

function msg(extra: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: 'm1',
    role: 'assistant',
    content: '### 概況\n- 本顧客為 VIP',
    state: 'done',
    createdAt: '2026-10-19T06:03:20Z',
    queriedAt: '2026-10-19T14:03:24+08:00',
    sources: [],
    disclaimer: null,
    toolCalls: [],
    ...extra,
  }
}

describe('AssistantMessage', () => {
  it('renders answer through SafeMarkdown', () => {
    const w = mount(AssistantMessage, { props: { message: msg({ content: '<script>alert(1)</script> **粗體**' }) } })
    expect(w.findComponent(SafeMarkdown).props('source')).toBe('<script>alert(1)</script> **粗體**')
    expect(w.find('script').exists()).toBe(false)
    expect(w.find('strong').text()).toBe('粗體')
    expect(w.find('.ai-msg__time').text()).toBe('14:03')
  })

  it('shows tool call summary only when toolCalls non-empty', () => {
    expect(mount(AssistantMessage, { props: { message: msg() } }).find('details').exists()).toBe(false)

    const w = mount(AssistantMessage, {
      props: {
        message: msg({
          toolCalls: [
            { name: 'getCustomerHoldings', purpose: '查詢顧客持有商品概況', status: 'SUCCESS', durationMs: 9 },
            { name: 'getStockQuote', purpose: '查詢上市個股行情', status: 'TIMEOUT', durationMs: 1400 },
          ],
        }),
      },
    })
    const details = w.find('details')
    expect(details.find('summary').text()).toContain('使用 2 個工具 · 1.4 s')
    const rows = details.findAll('li')
    expect(rows).toHaveLength(2)
    expect(rows[0]!.text()).toContain('查詢顧客持有商品概況')
    expect(rows[0]!.text()).toContain('getCustomerHoldings')
    expect(rows[0]!.text()).toContain('成功')
    expect(rows[0]!.text()).toContain('9 ms')
    expect(rows[1]!.text()).toContain('逾時')
    expect(rows[1]!.text()).toContain('1.4 s')
  })

  it('shows sources with data time and queried time, and disclaimer', () => {
    const w = mount(AssistantMessage, {
      props: {
        message: msg({
          disclaimer: '資訊僅供內部經營參考，不構成投資建議',
          sources: [
            { name: '平台顧客資料（模擬）', url: null, dataTime: null, queriedAt: '2026-10-19T14:03:22+08:00' },
            { name: '臺灣證券交易所 OpenAPI', url: 'https://openapi.twse.com.tw/', dataTime: '2026-10-19', queriedAt: '2026-10-19T14:05:00+08:00' },
            { name: '壞連結', url: 'javascript:alert(1)', dataTime: null, queriedAt: '2026-10-19T14:05:00+08:00' },
          ],
        }),
      },
    })
    expect(w.find('.ai-disclaimer').text()).toBe('資訊僅供內部經營參考，不構成投資建議')
    const sources = w.findAll('.ai-source')
    expect(sources[0]!.text()).toContain('查詢 14:03')
    expect(sources[0]!.element.tagName).toBe('SPAN')
    expect(sources[1]!.element.tagName).toBe('A')
    expect(sources[1]!.attributes('href')).toBe('https://openapi.twse.com.tw/')
    expect(sources[1]!.attributes('rel')).toContain('noopener')
    expect(sources[1]!.text()).toContain('資料 2026-10-19')
    expect(sources[1]!.text()).toContain('查詢 14:05')
    expect(sources[2]!.element.tagName).toBe('SPAN')
  })
})

describe('formatTaipei', () => {
  it('formats in Asia/Taipei', () => {
    expect(formatTaipei('2026-10-24T01:00:00Z')).toBe('2026/10/24 09:00')
    expect(formatTaipei('2026-10-23T16:30:00Z')).toBe('2026/10/24 00:30')
  })
})
