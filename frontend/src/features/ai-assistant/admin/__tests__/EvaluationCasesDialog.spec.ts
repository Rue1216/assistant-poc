import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EvaluationCasesDialog from '../EvaluationCasesDialog.vue'
import type { EvalCase, EvalCases } from '../../types'

function kase(id: string, category?: string | null): EvalCase {
  return {
    id, group: '顧客經營', accessLevel: 'L1', customerId: 'C001', question: '問題', rubric: [],
    expect: { tools: [], forbiddenTools: [], refusal: false, mustContain: [], mustNotContain: [], ...(category !== undefined ? { category } : {}) },
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
})
