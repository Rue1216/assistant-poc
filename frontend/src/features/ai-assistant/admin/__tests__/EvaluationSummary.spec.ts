import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EvaluationSummary from '../EvaluationSummary.vue'
import { summary } from './evalFixtures'

const row = (w: ReturnType<typeof mount>, label: string) => w.findAll('tbody tr').find((r) => r.text().includes(label))!

describe('EvaluationSummary', () => {
  it('shows rates with raw counts for both versions', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary() } })
    const r = row(w, '正確率')
    expect(r.text()).toContain('81%（13/16）')
    expect(r.text()).toContain('94%（15/16）')
    expect(w.find('thead').text()).toContain('已發布版本')
    expect(w.find('thead').text()).toContain('編輯中版本')
  })

  it('marks an improvement of more than one case green', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary() } })
    const mark = row(w, '正確率').find('.ev-dir')
    expect(mark.text()).toBe('▲')
    expect(mark.classes()).toContain('is-better')
  })

  it('marks a regression red', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary({ correct: 15 }, { correct: 12 }) } })
    const mark = row(w, '正確率').find('.ev-dir')
    expect(mark.text()).toBe('▼')
    expect(mark.classes()).toContain('is-worse')
  })

  it('does not colour a one-case difference of the normal-case refusal rate', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary({ refusedNormal: 0 }, { refusedNormal: 1 }) } })
    const r = row(w, '拒答率（一般題）')
    expect(r.text()).toContain('10%（1/10）')
    expect(r.find('.is-better').exists()).toBe(false)
    expect(r.find('.is-worse').exists()).toBe(false)
  })

  it('treats a lower normal-case refusal rate as better', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary({ refusedNormal: 5 }, { refusedNormal: 1 }) } })
    expect(row(w, '拒答率（一般題）').find('.is-better').exists()).toBe(true)
  })

  it('shows the token change without colour under 5%', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary({ avgTotalTokens: 7420 }, { avgTotalTokens: 7610 }) } })
    const r = row(w, '平均 Token')
    expect(r.text()).toContain('7,420')
    expect(r.text()).toContain('7,610')
    expect(r.text()).toContain('+2.6%')
    expect(r.find('.is-better').exists()).toBe(false)
    expect(r.find('.is-worse').exists()).toBe(false)
  })

  it('always shows the noise note', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary() } })
    expect(w.text()).toContain('差異在 1 題以內可能是模型隨機性')
  })

  it('shows a dash where the denominator is zero', () => {
    const none = { expectedRefusals: 0, refusedExpected: 0 }
    const w = mount(EvaluationSummary, { props: { summary: summary(none, none) } })
    const r = row(w, '拒答率（應拒答題）')
    expect(r.findAll('td').slice(0, 2).map((c) => c.text())).toEqual(['—', '—'])
  })
})
