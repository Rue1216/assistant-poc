import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import QuickQuestions from '../QuickQuestions.vue'
import type { SuggestionCategory } from '../../types'

const A: SuggestionCategory = { configKey: 'instruction.a', name: '分類甲', questions: ['問題一', '問題二'] }
const B: SuggestionCategory = { configKey: 'instruction.b', name: '分類乙', questions: [] }

describe('QuickQuestions', () => {
  it('skips categories without example questions', () => {
    const wrapper = mount(QuickQuestions, { props: { categories: [A, B] } })
    const groups = wrapper.findAll('.ai-quick-group')
    expect(groups).toHaveLength(1)
    expect(groups[0]!.text()).toContain('分類甲')
    expect(wrapper.text()).not.toContain('分類乙')
    expect(wrapper.findAll('button')).toHaveLength(2)
  })

  it('numbers the colour swatches after skipping empty categories', () => {
    const wrapper = mount(QuickQuestions, { props: { categories: [B, A] } })
    expect(wrapper.find('.ai-cat').classes()).toContain('ai-cat--1')
  })

  it('renders nothing when no category has questions', () => {
    const wrapper = mount(QuickQuestions, { props: { categories: [B] } })
    expect(wrapper.find('.ai-quick-groups').exists()).toBe(false)
  })
})
