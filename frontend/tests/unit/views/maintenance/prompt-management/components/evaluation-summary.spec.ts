import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EvaluationSummary from '@/views/maintenance/prompt-management/components/evaluation-summary.vue'
import { summary } from '../../../../fixtures/eval-fixtures'

const row = (w: ReturnType<typeof mount>, label: string) => w.findAll('tbody tr').find((r) => r.text().includes(label))!

describe('EvaluationSummary', () => {
  it('shows rates with raw counts and the new headers in order', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary() } })
    const r = row(w, '正確率')
    expect(r.text()).toContain('81%（13/16）')
    expect(r.text()).toContain('94%（15/16）')
    expect(w.findAll('thead th').map((h) => h.text())).toEqual(['指標', '目前生效', '草稿', '變化'])
  })

  it('writes an improvement of more than one case in green text', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary() } })
    const c = row(w, '正確率').findAll('td')[2]!
    expect(c.text()).toBe('變好（+2 題）')
    expect(c.find('.is-better').exists() || c.classes().includes('is-better')).toBe(true)
  })

  it('writes a regression in red text', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary({ correct: 15 }, { correct: 12 }) } })
    const c = row(w, '正確率').findAll('td')[2]!
    expect(c.text()).toBe('變差（−3 題）')
    expect(c.find('.is-worse').exists() || c.classes().includes('is-worse')).toBe(true)
  })

  it('does not colour a one-case difference of the normal-case refusal rate', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary({ refusedNormal: 0 }, { refusedNormal: 1 }) } })
    const r = row(w, '拒答率（一般題）')
    expect(r.text()).toContain('10%（1/10）')
    expect(r.findAll('td')[2]!.text()).toBe('持平')
    expect(r.find('.is-better').exists()).toBe(false)
    expect(r.find('.is-worse').exists()).toBe(false)
  })

  it('treats a lower normal-case refusal rate as better', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary({ refusedNormal: 5 }, { refusedNormal: 1 }) } })
    expect(row(w, '拒答率（一般題）').find('.is-better').exists()).toBe(true)
  })

  it('shows the token change as text without symbols', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary({ avgTotalTokens: 3470 }, { avgTotalTokens: 3927 }) } })
    const r = row(w, '平均 Token')
    expect(r.text()).toContain('3,470')
    expect(r.text()).toContain('3,927')
    expect(r.text()).toContain('多 13%')
    expect(r.find('.is-worse').exists()).toBe(true)
    expect(r.text()).not.toContain('▼')
    expect(r.text()).not.toContain('▲')
  })

  it('shows fewer tokens in green and a small change as flat', () => {
    const less = mount(EvaluationSummary, { props: { summary: summary({ avgTotalTokens: 3470 }, { avgTotalTokens: 3000 }) } })
    expect(row(less, '平均 Token').text()).toContain('少 14%')
    expect(row(less, '平均 Token').find('.is-better').exists()).toBe(true)
    const flat = mount(EvaluationSummary, { props: { summary: summary({ avgTotalTokens: 7420 }, { avgTotalTokens: 7610 }) } })
    expect(row(flat, '平均 Token').findAll('td')[2]!.text()).toBe('持平')
    expect(row(flat, '平均 Token').find('.is-worse').exists()).toBe(false)
  })

  it('marks the two lower-is-better rows', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary() } })
    expect(w.text().match(/越低越好/g)).toHaveLength(2)
    expect(row(w, '拒答率（一般題）').text()).toContain('越低越好')
    expect(row(w, '平均 Token').text()).toContain('越低越好')
  })

  it('always shows the noise note', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary() } })
    expect(w.text()).toContain('差 1 題以內標為持平，可能只是模型隨機性。')
  })

  it('shows a dash where the denominator is zero', () => {
    const none = { expectedRefusals: 0, refusedExpected: 0 }
    const w = mount(EvaluationSummary, { props: { summary: summary(none, none) } })
    const r = row(w, '拒答率（應拒答題）')
    expect(r.findAll('td').map((c) => c.text())).toEqual(['—', '—', '—'])
  })

  it('shows the category accuracy row as rate and counts', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary({ routeOk: 11, routeCases: 13 }, { routeOk: 13, routeCases: 13 }) } })
    const r = row(w, '分類正確率')
    const cells = r.findAll('td')
    expect(cells[0]!.text()).toBe('85%（11/13）')
    expect(cells[1]!.text()).toBe('100%（13/13）')
    expect(cells[2]!.text()).toBe('變好（+2 題）')
  })

  it('does not mark a one-case difference in category accuracy', () => {
    const w = mount(EvaluationSummary, { props: { summary: summary({ routeOk: 12 }, { routeOk: 11 }) } })
    expect(row(w, '分類正確率').findAll('td')[2]!.text()).toBe('持平')
  })

  it('shows a dash for category accuracy when no case has an expected category', () => {
    const none = { routeOk: undefined, routeCases: undefined }
    const w = mount(EvaluationSummary, { props: { summary: summary(none, { routeOk: 0, routeCases: 0 }) } })
    const cells = row(w, '分類正確率').findAll('td')
    expect(cells[0]!.text()).toBe('—')
    expect(cells[1]!.text()).toBe('—')
  })
})
