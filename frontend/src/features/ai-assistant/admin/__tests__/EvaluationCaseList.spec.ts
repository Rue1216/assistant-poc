import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EvaluationCaseList from '../EvaluationCaseList.vue'
import type { EvalCaseRecord } from '../../types'
import { rec, result } from './evalFixtures'

const same = rec('Q1-same', result(), result())
const changed = rec('Q3-changed', result({ passed: true }), result({ passed: false, missingPhrases: ['面談'] }))

const NAMES: Record<string, string> = { 'instruction.contact-prep': '接觸前準備', 'instruction.churn': '流失分析' }
const nameOf = (k: string) => NAMES[k] ?? k

/** Shows all cases unless showAll is false (the filter is checked by default). */
async function mountList(cases: EvalCaseRecord[], showAll = true) {
  const w = mount(EvaluationCaseList, { props: { cases, nameOf } })
  if (showAll) await w.find('input[type="checkbox"]').setValue(false)
  return w
}

async function expand(w: ReturnType<typeof mount>, id: string) {
  await w.findAll('.ev-case__toggle').find((b) => b.text().includes(id))!.trigger('click')
}

describe('EvaluationCaseList', () => {
  it('checks the filter by default and shows only cases whose result differs', () => {
    const w = mount(EvaluationCaseList, { props: { cases: [same, changed], nameOf } })
    expect((w.find('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(true)
    expect(w.text()).toContain('只看結果不同的題目')
    expect(w.findAll('.ev-case')).toHaveLength(1)
    expect(w.text()).toContain('Q3-changed')
  })

  it('explains an empty filtered list, and says 沒有符合的題目 when unfiltered', async () => {
    const w = mount(EvaluationCaseList, { props: { cases: [same], nameOf } })
    expect(w.find('.ev-cases__empty').text()).toBe('兩個版本的結果沒有差異（取消勾選可看全部）')
    await w.find('input[type="checkbox"]').setValue(false)
    expect(w.findAll('.ev-case')).toHaveLength(1)
    const empty = await mountList([])
    expect(empty.find('.ev-cases__empty').text()).toBe('沒有符合的題目')
  })

  it('shows every case once the filter is unchecked', async () => {
    const w = await mountList([same, changed])
    expect(w.findAll('.ev-case')).toHaveLength(2)
  })

  it('has one status label: 變差, 變好, or nothing when equal', async () => {
    const better = rec('Q4-better', result({ passed: false }), result({ passed: true }))
    const w = await mountList([same, changed, better])
    const headers = w.findAll('.ev-case__toggle').map((b) => b.text())
    expect(headers[0]).not.toMatch(/變好|變差|執行中/)
    expect(headers[1]).toContain('變差')
    expect(headers[2]).toContain('變好')
    for (const h of headers) {
      expect(h).not.toContain('已發布：')
      expect(h).not.toContain('編輯中：')
    }
  })

  it('says 執行中 when either side has not finished', async () => {
    const w = await mountList([rec('Q1', result(), null), rec('Q2', null, null)])
    const headers = w.findAll('.ev-case__toggle').map((b) => b.text())
    expect(headers[0]).toContain('執行中')
    expect(headers[1]).toContain('執行中')
    expect(headers[0]).not.toMatch(/變好|變差/)
  })

  it('renders the answers through SafeMarkdown in two columns', async () => {
    const evil = '**粗體** <script>alert(1)</script>'
    const w = await mountList([rec('Q1', result({ answer: evil }), result({ answer: '新回答' }))])
    await expand(w, 'Q1')
    const cols = w.findAll('.ev-col')
    expect(cols).toHaveLength(2)
    expect(cols[0]!.find('strong').text()).toBe('粗體')
    expect(w.find('script').exists()).toBe(false)
    expect(cols[0]!.text()).toContain('<script>')
    expect(cols[1]!.text()).toContain('新回答')
  })

  it('lists the failed checks', async () => {
    const bad = result({
      passed: false, toolsOk: false, numbersOk: false, unverifiedNumbers: ['99'],
      missingPhrases: ['面談'], forbiddenPhrases: ['王小明'],
    })
    const w = await mountList([rec('Q1', result(), bad)])
    await expand(w, 'Q1')
    const text = w.findAll('.ev-col')[1]!.text()
    expect(text).toContain('未提到：面談')
    expect(text).toContain('出現禁止字詞：王小明')
    expect(text).toContain('工具選擇不符')
    expect(text).toContain('有未核對的數字')
  })

  it('shows judge comment and unmet rubric items, or 無', async () => {
    const judged = result({ judge: { refused: false, rubric: [{ text: '列出待辦', met: false }], comment: '漏列待辦事項', error: null, tokens: 1 } })
    const w = await mountList([rec('Q1', result(), judged)])
    await expand(w, 'Q1')
    const cols = w.findAll('.ev-col')
    expect(cols[0]!.text()).toContain('評審：無')
    expect(cols[1]!.text()).toContain('評審：漏列待辦事項')
    expect(cols[1]!.text()).toContain('評分要點未達成：列出待辦')
  })

  it('says 評審失敗 when the judge failed', async () => {
    const failed = result({ judge: { refused: null, rubric: [], comment: null, error: 'AI_EVAL_JUDGE_FAILED', tokens: 0 } })
    const w = await mountList([rec('Q1', result(), failed)])
    await expand(w, 'Q1')
    expect(w.findAll('.ev-col')[1]!.text()).toContain('評審失敗')
  })

  it('shows the friendly message of a failed answer, or the code when unknown', async () => {
    const f1 = result({ outcome: 'FAILED', errorCode: 'AI_LLM_TIMEOUT', answer: null, passed: false, judge: null })
    const f2 = result({ outcome: 'FAILED', errorCode: 'AI_NEW_ONE', answer: null, passed: false, judge: null })
    const w = await mountList([rec('Q1', f1, f2)])
    await expand(w, 'Q1')
    const cols = w.findAll('.ev-col')
    expect(cols[0]!.text()).toContain('小幫手回應逾時，請稍後再試')
    expect(cols[1]!.text()).toContain('AI_NEW_ONE')
  })

  it('shows a placeholder for a version not run yet', async () => {
    const w = await mountList([rec('Q1', result(), null)])
    await expand(w, 'Q1')
    expect(w.findAll('.ev-col')[1]!.text()).toContain('尚未完成')
  })

  it('explains a refusal mismatch', async () => {
    const answered = result({ passed: false, judge: { refused: false, rubric: [], comment: null, error: null, tokens: 1 } })
    const refused = result({ passed: false, judge: { refused: true, rubric: [], comment: null, error: null, tokens: 1 } })
    const shouldRefuse = { ...rec('R1', result(), answered), expectRefusal: true }
    const shouldAnswer = { ...rec('R2', result(), refused), expectRefusal: false }
    const w = await mountList([shouldRefuse, shouldAnswer])
    await expand(w, 'R1')
    await expand(w, 'R2')
    const cols = w.findAll('.ev-col')
    expect(cols[1]!.text()).toContain('應拒答但未拒答')
    expect(cols[3]!.text()).toContain('不應拒答卻拒答')
  })

  it('shows no refusal line for a correct refusal', async () => {
    const refused = result({ judge: { refused: true, rubric: [], comment: null, error: null, tokens: 1 } })
    const w = await mountList([{ ...rec('R1', refused, refused), expectRefusal: true }])
    await expand(w, 'R1')
    expect(w.text()).not.toContain('拒答但未拒答')
    expect(w.text()).not.toContain('不應拒答卻拒答')
  })

  describe('routing', () => {
    const KEY = 'instruction.contact-prep'
    const routed = (r: Parameters<typeof result>[0], expectCategory: string | null = KEY) => ({
      ...rec('R1', result(r), result(r)),
      expectCategory,
    })

    it('shows the routed category name on each side', async () => {
      const w = await mountList([
        {
          ...rec('R1', result({ routeSource: 'ROUTER', instructionKey: KEY }), result({ routeSource: 'CHIP', instructionKey: 'instruction.churn' })),
          expectCategory: KEY,
        },
      ])
      await expand(w, 'R1')
      const cols = w.findAll('.ev-col')
      expect(cols[0]!.text()).toContain('接觸前準備')
      expect(cols[1]!.text()).toContain('流失分析')
    })

    it('shows the key when the name is unknown', async () => {
      const w = await mountList([routed({ routeSource: 'ROUTER', instructionKey: 'instruction.gone', routeOk: true })])
      await expand(w, 'R1')
      expect(w.findAll('.ev-col')[0]!.text()).toContain('instruction.gone')
    })

    it('shows 無 when the router chose no category', async () => {
      const w = await mountList([routed({ routeSource: 'ROUTER', instructionKey: null, routeOk: true }, 'none')])
      await expand(w, 'R1')
      expect(w.findAll('.ev-col')[0]!.find('.ev-col__route').text()).toContain('無')
    })

    it('says the route failed and all categories were loaded on FALLBACK', async () => {
      const w = await mountList([routed({ routeSource: 'FALLBACK', instructionKey: null })])
      await expand(w, 'R1')
      expect(w.findAll('.ev-col')[0]!.text()).toContain('路由失敗，載入全部')
    })

    it('shows no route line when the result has no routing info', async () => {
      const w = await mountList([rec('R1', result(), result())])
      await expand(w, 'R1')
      expect(w.findAll('.ev-col')[0]!.find('.ev-col__route').exists()).toBe(false)
    })

    it('lists 分類不符 with the expected category name when routeOk is false', async () => {
      const w = await mountList([routed({ routeSource: 'ROUTER', instructionKey: 'instruction.churn', routeOk: false })])
      await expand(w, 'R1')
      expect(w.findAll('.ev-col')[0]!.find('.ev-col__fails').text()).toContain('分類不符：預期 接觸前準備')
    })

    it('writes 預期 無 when the expected category is none', async () => {
      const w = await mountList([routed({ routeSource: 'ROUTER', instructionKey: KEY, routeOk: false }, 'none')])
      await expand(w, 'R1')
      expect(w.findAll('.ev-col')[0]!.text()).toContain('分類不符：預期 無')
    })

    it('does not list 分類不符 when the route is right or not checked', async () => {
      const w = await mountList([routed({ routeSource: 'ROUTER', instructionKey: KEY, routeOk: true }), routed({ routeOk: null })])
      await expand(w, 'R1')
      expect(w.text()).not.toContain('分類不符')
    })
  })
})
