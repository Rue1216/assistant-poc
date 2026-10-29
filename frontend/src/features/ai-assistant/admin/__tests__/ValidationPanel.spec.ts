import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ValidationPanel from '../ValidationPanel.vue'
import type { ValidationState } from '../../composables/usePromptAdmin'

const ERRORS = [
  { rule: 'REQUIRED_SECTION', message: '缺少必要章節「## 適用問題」' },
  { rule: 'NOT_EMPTY', message: '內容不可為空' },
]

function mountPanel(validation: ValidationState, extra: { dirty?: boolean; errors?: typeof ERRORS } = {}) {
  return mount(ValidationPanel, {
    props: {
      validation,
      errors: extra.errors ?? [],
      configType: 'SYSTEM_INSTRUCTION',
      checkedContent: '## 適用問題\n內容\n\n## 範例問題\n- 問題一',
      dirty: extra.dirty ?? false,
    },
  })
}

describe('ValidationPanel', () => {
  it('idle renders nothing visible', () => {
    const w = mountPanel('idle')
    expect(w.text()).not.toContain('發布前檢查')
    expect(w.text()).not.toContain('尚未檢查')
  })

  it('checking shows 檢查中… as a polite live region', () => {
    const w = mountPanel('checking')
    expect(w.text()).toContain('檢查中…')
    expect(w.find('[aria-live="polite"]').exists()).toBe(true)
  })

  it('passed is a collapsed details with the summary and the passing rules', () => {
    const w = mountPanel('passed')
    const d = w.find('details')
    expect(d.attributes('open')).toBeUndefined()
    expect(d.find('summary').text()).toBe('✓ 發布前檢查通過')
    expect(d.find('li').exists()).toBe(true)
  })

  it('failed is an open details listing the errors', () => {
    const w = mountPanel('failed', { errors: ERRORS })
    const d = w.find('details')
    expect(d.attributes('open')).toBeDefined()
    expect(d.find('summary').text()).toBe('✕ 2 項未通過')
    expect(d.find('[role="alert"]').text()).toContain('缺少必要章節「## 適用問題」')
    expect(d.find('[role="alert"]').text()).toContain('內容不可為空')
  })

  it('dirty passed or failed adds the re-check hint', () => {
    expect(mountPanel('passed', { dirty: true }).text()).toContain('儲存後重新檢查')
    expect(mountPanel('failed', { dirty: true, errors: ERRORS }).text()).toContain('儲存後重新檢查')
    expect(mountPanel('passed').text()).not.toContain('儲存後重新檢查')
  })

  it('never has a button', () => {
    for (const s of ['idle', 'checking', 'passed', 'failed'] as const) {
      expect(mountPanel(s, { errors: ERRORS }).find('button').exists()).toBe(false)
    }
  })
})
