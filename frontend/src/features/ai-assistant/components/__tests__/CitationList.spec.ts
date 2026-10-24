import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import type { Citation } from '../../types'
import CitationList from '../CitationList.vue'

function cit(extra: Partial<Citation> & { id: number }): Citation {
  return {
    toolName: 'getStockQuote',
    title: '上市個股行情',
    target: '台積電 2330',
    excerpt: null,
    description: null,
    sourceName: null,
    url: null,
    dataTime: null,
    queriedAt: '2026-10-21T14:20:00+08:00',
    cited: true,
    ...extra,
  }
}

const citations: Citation[] = [
  cit({
    id: 1,
    excerpt: '<b>x</b>',
    sourceName: '臺灣證券交易所 OpenAPI',
    url: 'https://openapi.twse.com.tw/',
    dataTime: '2026-10-21',
  }),
  cit({
    id: 2,
    toolName: 'getCustomerHoldings',
    title: '顧客持有商品',
    target: null,
    description: '3 筆',
    sourceName: '平台顧客資料（模擬）',
  }),
  cit({ id: 3, title: '壞連結', target: null, cited: false, sourceName: '某來源', url: 'javascript:alert(1)' }),
]

afterEach(() => {
  vi.useRealTimers()
})

describe('CitationList', () => {
  it('lists cited items first and groups uncited under 其他查詢資料', () => {
    const w = mount(CitationList, { props: { citations } })
    expect(w.get('.ai-citations__label').text()).toBe('引用（3）')
    expect(w.findAll('.ai-citations__cited > li')).toHaveLength(2)
    expect(w.get('.ai-citations__other-label').text()).toBe('其他查詢資料')
    expect(w.findAll('.ai-citations__other > li')).toHaveLength(1)
  })

  it('omits the other group when everything is cited', () => {
    const w = mount(CitationList, { props: { citations: citations.slice(0, 2) } })
    expect(w.find('.ai-citations__other-label').exists()).toBe(false)
  })

  it('shows external excerpt and internal description as plain text', () => {
    const w = mount(CitationList, { props: { citations } })
    const first = w.get('[data-citation-id="1"]')
    expect(first.text()).toContain('<b>x</b>')
    expect(first.find('b').exists()).toBe(false)
    expect(w.get('[data-citation-id="2"]').text()).toContain('3 筆')
    expect(w.get('[data-citation-id="3"]').find('.ai-citation__body').exists()).toBe(false)
  })

  it('summary and meta lines', () => {
    const w = mount(CitationList, { props: { citations } })
    const first = w.get('[data-citation-id="1"]')
    expect(first.get('summary').text()).toBe('[1] 上市個股行情 · 台積電 2330')
    const meta = first.get('.ai-citation__meta')
    expect(meta.text()).toContain('資料 2026-10-21')
    expect(meta.text()).toContain('查詢 14:20')
    const a = meta.get('a')
    expect(a.attributes('href')).toBe('https://openapi.twse.com.tw/')
    expect(a.attributes('target')).toBe('_blank')
    expect(a.attributes('rel')).toContain('noopener')

    const internal = w.get('[data-citation-id="2"]')
    expect(internal.get('summary').text()).not.toContain(' · ')
    expect(internal.get('.ai-citation__meta').text()).toContain('平台顧客資料（模擬）')
    expect(internal.find('a').exists()).toBe(false)

    const bad = w.get('[data-citation-id="3"]')
    expect(bad.get('.ai-citation__meta').text()).toContain('某來源')
    expect(bad.find('a').exists()).toBe(false)
  })

  it('reveal opens, scrolls to and flashes the item', async () => {
    vi.useFakeTimers()
    const scroll = vi.fn()
    Element.prototype.scrollIntoView = scroll
    const w = mount(CitationList, { props: { citations } })
    ;(w.vm as unknown as { reveal(id: number): void }).reveal(2)
    await nextTick()
    const item = w.get('[data-citation-id="2"]')
    expect((item.element as HTMLDetailsElement).open).toBe(true)
    expect(scroll).toHaveBeenCalled()
    expect(item.classes()).toContain('ai-citation--flash')
    vi.advanceTimersByTime(1500)
    await nextTick()
    expect(item.classes()).not.toContain('ai-citation--flash')
  })
})
