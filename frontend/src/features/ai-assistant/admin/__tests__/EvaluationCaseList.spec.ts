import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EvaluationCaseList from '../EvaluationCaseList.vue'
import { rec, result } from './evalFixtures'

const same = rec('Q1-same', result(), result())
const changed = rec('Q3-changed', result({ passed: true }), result({ passed: false, missingPhrases: ['面談'] }))

async function expand(w: ReturnType<typeof mount>, id: string) {
  await w.findAll('.ev-case__toggle').find((b) => b.text().includes(id))!.trigger('click')
}

describe('EvaluationCaseList', () => {
  it('lists all cases and shows pass marks', () => {
    const w = mount(EvaluationCaseList, { props: { cases: [same, changed] } })
    expect(w.findAll('.ev-case')).toHaveLength(2)
    const header = w.findAll('.ev-case__toggle')[1]!.text()
    expect(header).toContain('已發布：通過')
    expect(header).toContain('編輯中：未通過')
    expect(header).not.toContain('→')
    expect(w.findAll('.ev-case__toggle')[0]!.text()).toContain('編輯中：通過')
  })

  it('labels a regression 變差, an improvement 變好, and nothing when equal', () => {
    const better = rec('Q4-better', result({ passed: false }), result({ passed: true }))
    const w = mount(EvaluationCaseList, { props: { cases: [same, changed, better] } })
    const headers = w.findAll('.ev-case__toggle').map((b) => b.text())
    expect(headers[0]).not.toMatch(/變好|變差/)
    expect(headers[1]).toContain('變差')
    expect(headers[2]).toContain('變好')
  })

  it('says 執行中 for a side that has not finished', () => {
    const w = mount(EvaluationCaseList, { props: { cases: [rec('Q1', result(), null), rec('Q2', null, null)] } })
    const headers = w.findAll('.ev-case__toggle').map((b) => b.text())
    expect(headers[0]).toContain('已發布：通過')
    expect(headers[0]).toContain('編輯中：執行中')
    expect(headers[0]).not.toMatch(/變好|變差/)
    expect(headers[1]).toContain('已發布：執行中')
  })

  it('keeps only changed cases when the filter is checked', async () => {
    const w = mount(EvaluationCaseList, { props: { cases: [same, changed] } })
    await w.find('input[type="checkbox"]').setValue(true)
    expect(w.text()).toContain('只看結果不同的題目')
    expect(w.findAll('.ev-case')).toHaveLength(1)
    expect(w.text()).toContain('Q3-changed')
  })

  it('renders the answers through SafeMarkdown in two columns', async () => {
    const evil = '**粗體** <script>alert(1)</script>'
    const w = mount(EvaluationCaseList, { props: { cases: [rec('Q1', result({ answer: evil }), result({ answer: '新回答' }))] } })
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
    const w = mount(EvaluationCaseList, { props: { cases: [rec('Q1', result(), bad)] } })
    await expand(w, 'Q1')
    const text = w.findAll('.ev-col')[1]!.text()
    expect(text).toContain('未提到：面談')
    expect(text).toContain('出現禁止字詞：王小明')
    expect(text).toContain('工具選擇不符')
    expect(text).toContain('有未核對的數字')
  })

  it('shows judge comment and unmet rubric items, or 無', async () => {
    const judged = result({ judge: { refused: false, rubric: [{ text: '列出待辦', met: false }], comment: '漏列待辦事項', error: null, tokens: 1 } })
    const w = mount(EvaluationCaseList, { props: { cases: [rec('Q1', result(), judged)] } })
    await expand(w, 'Q1')
    const cols = w.findAll('.ev-col')
    expect(cols[0]!.text()).toContain('評審：無')
    expect(cols[1]!.text()).toContain('評審：漏列待辦事項')
    expect(cols[1]!.text()).toContain('評分要點未達成：列出待辦')
  })

  it('says 評審失敗 when the judge failed', async () => {
    const failed = result({ judge: { refused: null, rubric: [], comment: null, error: 'AI_EVAL_JUDGE_FAILED', tokens: 0 } })
    const w = mount(EvaluationCaseList, { props: { cases: [rec('Q1', result(), failed)] } })
    await expand(w, 'Q1')
    expect(w.findAll('.ev-col')[1]!.text()).toContain('評審失敗')
  })

  it('shows the friendly message of a failed answer, or the code when unknown', async () => {
    const f1 = result({ outcome: 'FAILED', errorCode: 'AI_LLM_TIMEOUT', answer: null, passed: false, judge: null })
    const f2 = result({ outcome: 'FAILED', errorCode: 'AI_NEW_ONE', answer: null, passed: false, judge: null })
    const w = mount(EvaluationCaseList, { props: { cases: [rec('Q1', f1, f2)] } })
    await expand(w, 'Q1')
    const cols = w.findAll('.ev-col')
    expect(cols[0]!.text()).toContain('小幫手回應逾時，請稍後再試')
    expect(cols[1]!.text()).toContain('AI_NEW_ONE')
  })

  it('shows a placeholder for a version not run yet', async () => {
    const w = mount(EvaluationCaseList, { props: { cases: [rec('Q1', result(), null)] } })
    await expand(w, 'Q1')
    expect(w.findAll('.ev-col')[1]!.text()).toContain('尚未完成')
  })

  it('explains a refusal mismatch', async () => {
    const answered = result({ passed: false, judge: { refused: false, rubric: [], comment: null, error: null, tokens: 1 } })
    const refused = result({ passed: false, judge: { refused: true, rubric: [], comment: null, error: null, tokens: 1 } })
    const shouldRefuse = { ...rec('R1', result(), answered), expectRefusal: true }
    const shouldAnswer = { ...rec('R2', result(), refused), expectRefusal: false }
    const w = mount(EvaluationCaseList, { props: { cases: [shouldRefuse, shouldAnswer] } })
    await expand(w, 'R1')
    await expand(w, 'R2')
    const cols = w.findAll('.ev-col')
    expect(cols[1]!.text()).toContain('應拒答但未拒答')
    expect(cols[3]!.text()).toContain('不應拒答卻拒答')
  })

  it('shows no refusal line for a correct refusal', async () => {
    const refused = result({ judge: { refused: true, rubric: [], comment: null, error: null, tokens: 1 } })
    const w = mount(EvaluationCaseList, { props: { cases: [{ ...rec('R1', refused, refused), expectRefusal: true }] } })
    await expand(w, 'R1')
    expect(w.text()).not.toContain('拒答但未拒答')
    expect(w.text()).not.toContain('不應拒答卻拒答')
  })
})
