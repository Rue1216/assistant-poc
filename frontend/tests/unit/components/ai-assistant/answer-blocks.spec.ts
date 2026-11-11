import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { AnswerBlock, Citation } from '@/beans/ai-assistant-bean'
import AnswerBlocks from '@/components/ai-assistant/answer-blocks'

const CITATION: Citation = {
  id: 1,
  toolName: 'market_quote',
  title: '上市個股行情',
  target: null,
  excerpt: null,
  description: null,
  sourceName: null,
  url: null,
  dataTime: null,
  queriedAt: '2026-10-19T14:03:24+08:00',
  cited: true,
}

function render(blocks: AnswerBlock[], citations: Citation[] = [CITATION]) {
  return mount(AnswerBlocks, { props: { blocks, citations } })
}

describe('AnswerBlocks', () => {
  it('groups consecutive bullets and numbered items into lists', () => {
    const w = render([
      { type: 'heading', text: '持有標的', citations: [] },
      { type: 'bullet', text: '甲', citations: [] },
      { type: 'bullet', text: '乙', citations: [] },
      { type: 'numbered', text: '丙', citations: [] },
      { type: 'paragraph', text: '結語', citations: [] },
    ])
    expect(w.find('h3').text()).toBe('持有標的')
    expect(w.findAll('ul > li')).toHaveLength(2)
    expect(w.findAll('ol > li')).toHaveLength(1)
    expect(w.find('p').text()).toContain('結語')
  })

  it('renders inline bold and safe links only', () => {
    const w = render([
      {
        type: 'paragraph',
        text: '**重點** [官網](https://www.twse.com.tw) [壞](javascript:alert(1)) <script>x</script>',
        citations: [],
      },
    ])
    expect(w.find('strong').text()).toBe('重點')
    expect(w.findAll('a')).toHaveLength(1)
    expect(w.find('a').attributes('href')).toBe('https://www.twse.com.tw/')
    expect(w.find('script').exists()).toBe(false)
    expect(w.text()).toContain('<script>x</script>')
  })

  it('adds an accessible citation button per citation', async () => {
    const w = render([{ type: 'paragraph', text: '股價', citations: [1] }])
    const btn = w.get('button.ai-cite')
    expect(btn.attributes('aria-label')).toBe('引用 1：上市個股行情')
    expect(btn.attributes('type')).toBe('button')
    expect(btn.text()).toBe('1')
    await btn.trigger('click')
    expect(w.emitted('cite')).toEqual([[1]])
  })

  it('skips citation ids that are not in the list', () => {
    const w = render([{ type: 'paragraph', text: '股價', citations: [1, 9] }])
    expect(w.findAll('button.ai-cite')).toHaveLength(1)
  })

  it('headings get no citation buttons', () => {
    const w = render([{ type: 'heading', text: '標題', citations: [1] }])
    expect(w.find('button').exists()).toBe(false)
  })

  it('puts citation buttons inside list items', () => {
    const w = render([{ type: 'bullet', text: '甲', citations: [1] }])
    expect(w.findAll('li button.ai-cite')).toHaveLength(1)
  })
})
