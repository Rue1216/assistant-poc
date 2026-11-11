import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EvaluationCasesDialog from '@/views/maintenance/prompt-management/components/evaluation-cases-dialog.vue'
import type { EvalCase, EvalCases } from '@/beans/prompt-management-bean'
import { RETIREE } from '../../../../fixtures/test-customers'

function kase(id: string, category?: string | null): EvalCase {
  return {
    id, group: '顧客經營', accessLevel: 'L1', certNo: RETIREE, question: '問題', rubric: [],
    expect: { tools: [], forbiddenTools: [], refusal: false, mustContain: [], mustNotContain: [], toolOrder: [], ...(category !== undefined ? { category } : {}) },
  }
}

const nameOf = (k: string) => (k === 'instruction.contact-prep' ? '接觸前準備' : k)

describe('EvaluationCasesDialog', () => {
  it('shows the expected category as name, 無 for none, and — when not set', () => {
    const cases: EvalCases = {
      datasetHash: 'h',
      cases: [kase('A', 'instruction.contact-prep'), kase('B', 'none'), kase('C'), kase('D', 'instruction.x'), kase('E', null)],
    }
    const w = mount(EvaluationCasesDialog, { props: { open: true, cases, nameOf } })
    const col = w.findAll('thead th').map((h) => h.text()).indexOf('預期分類')
    expect(col).toBeGreaterThanOrEqual(0)
    const cell = (i: number) => w.findAll('tbody tr')[i]!.findAll('td')[col]!.text()
    expect(cell(0)).toBe('接觸前準備')
    expect(cell(1)).toBe('無')
    expect(cell(2)).toBe('—')
    expect(cell(3)).toBe('instruction.x')
    expect(cell(4)).toBe('—')
  })

  it('shows the expected tool order joined by arrows, and — when empty', () => {
    const ordered = kase('A')
    ordered.expect.toolOrder = ['a', 'b']
    const w = mount(EvaluationCasesDialog, { props: { open: true, cases: { datasetHash: 'h', cases: [ordered, kase('B')] }, nameOf } })
    const headers = w.findAll('thead th').map((h) => h.text())
    const col = headers.indexOf('預期順序')
    expect(col).toBe(headers.indexOf('預期工具') + 1)
    const cell = (i: number) => w.findAll('tbody tr')[i]!.findAll('td')[col]!.text()
    expect(cell(0)).toBe('a → b')
    expect(cell(1)).toBe('—')
  })

  it('keeps the header cells on one line', () => {
    const w = mount(EvaluationCasesDialog, { props: { open: true, cases: { datasetHash: 'h', cases: [kase('A')] }, nameOf } })
    const ths = w.findAll('thead th')
    expect(ths.length).toBeGreaterThan(0)
    for (const th of ths) expect(th.classes()).toContain('ev-dialog__th')
  })
})
