import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import EvaluationCaseList from '../EvaluationCaseList.vue'
import type { EvalCaseRecord } from '../../types'
import { rec, result } from './evalFixtures'

const same = rec('Q1-same', result(), result())
const changed = rec('Q3-changed', result({ passed: true }), result({ passed: false, missingPhrases: ['面談'] }))

const NAMES: Record<string, string> = { 'instruction.contact-prep': '接觸前準備', 'instruction.churn': '流失分析' }
const nameOf = (k: string) => NAMES[k] ?? k

const link = (w: ReturnType<typeof mount>) => w.find('.ev-cases__link')

/** Shows all cases unless showAll is false (only changed cases are shown by default). */
async function mountList(cases: EvalCaseRecord[], showAll = true) {
  const w = mount(EvaluationCaseList, { props: { cases, nameOf } })
  if (showAll) await link(w).trigger('click')
  return w
}

async function expand(w: ReturnType<typeof mount>, id: string) {
  await w.find(`[data-case="${id}"] .ev-case__toggle`).trigger('click')
}

describe('EvaluationCaseList', () => {
  it('titles the list by the changed count and shows only changed cases by default', () => {
    const w = mount(EvaluationCaseList, { props: { cases: [same, changed], nameOf } })
    expect(w.find('input[type="checkbox"]').exists()).toBe(false)
    expect(w.find('.ev-cases__title').text()).toBe('結果不同的題目 1 題')
    expect(w.findAll('.ev-case')).toHaveLength(1)
  })

  it('switches between the changed-only and the full list with the link', async () => {
    const other = rec('Q9-other', result({ passed: true }), result({ passed: false }))
    const w = mount(EvaluationCaseList, { props: { cases: [same, changed, other], nameOf } })
    expect(w.find('.ev-cases__title').text()).toBe('結果不同的題目 2 題')
    expect(link(w).text()).toBe('顯示全部 3 題')
    await link(w).trigger('click')
    expect(w.find('.ev-cases__title').text()).toBe('全部題目 3 題')
    expect(link(w).text()).toBe('只看結果不同的')
    expect(w.findAll('.ev-case')).toHaveLength(3)
    await link(w).trigger('click')
    expect(w.find('.ev-cases__title').text()).toBe('結果不同的題目 2 題')
  })

  it('says there is no difference when the changed-only list is empty', () => {
    const w = mount(EvaluationCaseList, { props: { cases: [same], nameOf } })
    expect(w.find('.ev-cases__empty').text()).toBe('兩個版本的結果沒有差異')
    expect(w.find('.ev-cases__title').text()).toBe('結果不同的題目 0 題')
  })

  it('says 沒有符合的題目 for an empty full list', async () => {
    const w = await mountList([])
    expect(w.find('.ev-cases__empty').text()).toBe('沒有符合的題目')
  })

  it('starts a row with the question, not the case id, and shows the id once expanded', async () => {
    const w = await mountList([{ ...changed, question: '客戶下週要來，我該準備什麼？' }])
    const row = w.find('.ev-case__toggle')
    expect(row.text().replace(/^[▸▾]\s*/, '')).toMatch(/^客戶下週要來，我該準備什麼？/)
    expect(row.text()).not.toContain('Q3-changed')
    expect(w.text()).not.toContain('Q3-changed')
    await expand(w, 'Q3-changed')
    expect(w.find('.ev-case__id').text()).toBe('Q3-changed')
  })

  it('titles the two columns 目前生效 and 草稿', async () => {
    const w = await mountList([changed])
    await expand(w, 'Q3-changed')
    expect(w.findAll('.ev-col__title').map((t) => t.text())).toEqual(['目前生效', '草稿'])
    expect(w.text()).not.toContain('已發布版本')
    expect(w.text()).not.toContain('編輯中版本')
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
    expect(text).toContain('工具選擇或順序不符')
    expect(text).toContain('有未核對的數字')
    expect(text).not.toContain('全部通過')
  })

  it('lists a tool mismatch together with an unmet rubric item', async () => {
    const bad = result({ passed: false, toolsOk: false, judge: { refused: false, rubric: [{ text: '列出待辦', met: false }], comment: null, error: null, tokens: 1 } })
    const w = await mountList([rec('Q1', result(), bad)])
    await expand(w, 'Q1')
    const text = w.findAll('.ev-col')[1]!.text()
    expect(text).toContain('工具選擇或順序不符')
    expect(text).toContain('評分要點未達成：列出待辦')
    expect(text).not.toContain('全部通過')
  })

  it('lists identical reasons each time without a duplicate key warning, also when they reorder', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      const judged = (...texts: string[]) =>
        result({ passed: false, judge: { refused: false, rubric: texts.map((text) => ({ text, met: false })), comment: null, error: null, tokens: 1 } })
      const w = await mountList([rec('Q1', result(), judged('列出待辦', '列出待辦', '說明風險'))])
      await expand(w, 'Q1')
      // a reorder makes Vue match the items by key
      await w.setProps({ cases: [rec('Q1', result(), judged('說明風險', '列出待辦', '列出待辦'))] })
      const items = w.findAll('.ev-col')[1]!.findAll('.ev-col__fails li').map((li) => li.text())
      expect(items).toEqual(['評分要點未達成：說明風險', '評分要點未達成：列出待辦', '評分要點未達成：列出待辦'])
      expect(warn).not.toHaveBeenCalled()
    } finally {
      warn.mockRestore()
    }
  })

  it('says 全部通過 without ticks or 評審：無 when everything passed', async () => {
    const w = await mountList([rec('Q1', result(), result())])
    await expand(w, 'Q1')
    for (const col of w.findAll('.ev-col')) {
      expect(col.text()).toContain('全部通過')
      expect(col.text()).not.toContain('評審：無')
      expect(col.text()).not.toMatch(/[✓✗]/)
      expect(col.text()).not.toContain('工具')
    }
  })

  it('shows the judge comment only together with an unmet point or a refusal mismatch', async () => {
    const judged = result({ passed: false, judge: { refused: false, rubric: [{ text: '列出待辦', met: false }], comment: '漏列待辦事項', error: null, tokens: 1 } })
    const praised = result({ judge: { refused: false, rubric: [{ text: '列出待辦', met: true }], comment: '很好', error: null, tokens: 1 } })
    const w = await mountList([rec('Q1', praised, judged)])
    await expand(w, 'Q1')
    const cols = w.findAll('.ev-col')
    expect(cols[0]!.text()).not.toContain('評審：')
    expect(cols[0]!.text()).toContain('全部通過')
    expect(cols[1]!.text()).toContain('評審：漏列待辦事項')
    expect(cols[1]!.text()).toContain('評分要點未達成：列出待辦')
  })

  it('says 評審失敗 when the judge failed, and not 全部通過', async () => {
    const failed = result({ judge: { refused: null, rubric: [], comment: null, error: 'AI_EVAL_JUDGE_FAILED', tokens: 0 } })
    const w = await mountList([rec('Q1', result(), failed)])
    await expand(w, 'Q1')
    const text = w.findAll('.ev-col')[1]!.text()
    expect(text).toContain('評審失敗')
    expect(text).not.toContain('全部通過')
  })

  it('shows the friendly message of a failed answer, or the code when unknown', async () => {
    const f1 = result({ outcome: 'FAILED', errorCode: 'AI_LLM_TIMEOUT', answer: null, passed: false, judge: null })
    const f2 = result({ outcome: 'FAILED', errorCode: 'AI_NEW_ONE', answer: null, passed: false, judge: null })
    const w = await mountList([rec('Q1', f1, f2)])
    await expand(w, 'Q1')
    const cols = w.findAll('.ev-col')
    expect(cols[0]!.text()).toContain('小幫手回應逾時，請稍後再試')
    expect(cols[1]!.text()).toContain('AI_NEW_ONE')
    expect(cols[1]!.text()).not.toContain('全部通過')
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

    it('shows no route line when the category is right', async () => {
      const w = await mountList([routed({ routeSource: 'ROUTER', instructionKey: KEY, routeOk: true })])
      await expand(w, 'R1')
      expect(w.text()).not.toContain('路由')
    })

    it('puts 分類不符 first, with the expected and the actual name', async () => {
      const bad = { passed: false, toolsOk: false, routeSource: 'ROUTER' as const, instructionKey: null, routeOk: false }
      const w = await mountList([routed(bad)])
      await expand(w, 'R1')
      const items = w.findAll('.ev-col')[0]!.findAll('.ev-col__fails li').map((li) => li.text())
      expect(items[0]).toBe('分類不符：預期 接觸前準備，實際 無')
      expect(items).toContain('工具選擇或順序不符')
    })

    it('names the actual category, or the key when unknown', async () => {
      const w = await mountList([
        {
          ...rec(
            'R1',
            result({ routeSource: 'ROUTER', instructionKey: 'instruction.churn', routeOk: false }),
            result({ routeSource: 'ROUTER', instructionKey: 'instruction.gone', routeOk: false }),
          ),
          expectCategory: KEY,
        },
      ])
      await expand(w, 'R1')
      const cols = w.findAll('.ev-col')
      expect(cols[0]!.text()).toContain('分類不符：預期 接觸前準備，實際 流失分析')
      expect(cols[1]!.text()).toContain('實際 instruction.gone')
    })

    it('says the route failed and all categories were loaded on FALLBACK', async () => {
      const w = await mountList([routed({ routeSource: 'FALLBACK', instructionKey: null, routeOk: false })])
      await expand(w, 'R1')
      const items = w.findAll('.ev-col')[0]!.findAll('.ev-col__fails li').map((li) => li.text())
      expect(items[0]).toBe('分類不符：預期 接觸前準備，實際 無（路由失敗，已載入全部）')
    })

    it('writes 預期 無 when the expected category is none', async () => {
      const w = await mountList([routed({ routeSource: 'ROUTER', instructionKey: KEY, routeOk: false }, 'none')])
      await expand(w, 'R1')
      expect(w.findAll('.ev-col')[0]!.text()).toContain('分類不符：預期 無，實際 接觸前準備')
    })

    it('does not list 分類不符 when the route is right or not checked', async () => {
      const w = await mountList([routed({ routeSource: 'ROUTER', instructionKey: KEY, routeOk: true }), routed({ routeOk: null })])
      await expand(w, 'R1')
      expect(w.text()).not.toContain('分類不符')
    })
  })
})
