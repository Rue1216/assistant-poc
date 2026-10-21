import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SafeMarkdown from '../../markdown/SafeMarkdown'
import MarkdownEditor from '../MarkdownEditor.vue'
import type { ConfigType } from '../../types'

function mountEditor(modelValue: string, configType: ConfigType = 'SYSTEM_INSTRUCTION') {
  const wrapper = mount(MarkdownEditor, {
    props: {
      modelValue,
      configType,
      'onUpdate:modelValue': (v: string) => wrapper.setProps({ modelValue: v }),
    },
  })
  return wrapper
}

function tab(wrapper: ReturnType<typeof mountEditor>, label: string) {
  return wrapper.findAll('[role="tab"]').find((t) => t.text() === label)!
}

describe('MarkdownEditor', () => {
  it('shows only the textarea on the edit tab and only the preview on the preview tab', async () => {
    const wrapper = mountEditor('## 適用問題\n內容')
    expect(tab(wrapper, '編輯').attributes('aria-selected')).toBe('true')
    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(wrapper.findComponent(SafeMarkdown).exists()).toBe(false)
    expect(wrapper.text()).toContain('/ 64 KB')

    await tab(wrapper, '預覽').trigger('click')

    expect(tab(wrapper, '預覽').attributes('aria-selected')).toBe('true')
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.findComponent(SafeMarkdown).exists()).toBe(true)
    expect(wrapper.text()).toContain('以目前內容渲染，含未儲存的修改')
  })

  it('preview renders unsaved textarea content through SafeMarkdown', async () => {
    const wrapper = mountEditor('## 舊標題')
    await wrapper.find('textarea').setValue('## 新標題\n- 未儲存的項目')
    expect(wrapper.emitted('update:modelValue')!.at(-1)).toEqual(['## 新標題\n- 未儲存的項目'])

    await tab(wrapper, '預覽').trigger('click')

    expect(wrapper.findComponent(SafeMarkdown).props('source')).toBe('## 新標題\n- 未儲存的項目')
    expect(wrapper.find('.md h2').text()).toBe('新標題')
    expect(wrapper.find('.md li').text()).toBe('未儲存的項目')
  })

  it('previews quick-question chips from the 範例問題 section for instructions', async () => {
    const wrapper = mountEditor('## 範例問題\n- 問題一\n- 問題二\n\n## 注意事項\n- 不是問題')
    await tab(wrapper, '預覽').trigger('click')
    expect(wrapper.find('.pm-chips-preview').text()).toContain('快捷按鈕預覽')
    expect(wrapper.findAll('.pm-chips-preview .chip').map((c) => c.text())).toEqual(['問題一', '問題二'])
  })

  it('has no chips preview for the system prompt, but lists its required sections while editing', async () => {
    const wrapper = mountEditor('## 角色\n\n## 回答格式', 'SYSTEM_PROMPT')
    const items = wrapper.findAll('.pm-sections__item')
    expect(items.map((i) => i.text())).toEqual(['## 角色', '## 安全規則', '## 回答格式'])
    expect(items.map((i) => i.classes('is-ok'))).toEqual([true, false, true])

    await tab(wrapper, '預覽').trigger('click')
    expect(wrapper.find('.pm-chips-preview').exists()).toBe(false)
  })

  it('marks lines that differ from the published version in the preview, with the legend', async () => {
    const wrapper = mount(MarkdownEditor, {
      props: {
        modelValue: '## 回答格式\n- 精簡\n- 新增的規則',
        configType: 'SYSTEM_PROMPT',
        baseline: '## 回答格式\n- 精簡',
      },
    })
    await tab(wrapper as never, '預覽').trigger('click')
    const items = wrapper.findAll('.md li')
    expect(items[0]!.find('mark').exists()).toBe(false)
    expect(items[1]!.find('mark.pm-diff').text()).toBe('新增的規則')
    expect(wrapper.find('.pm-preview-foot').text()).toBe('與目前生效的版本不同')
  })

  it('hides the legend when nothing was added and when there is no published version', async () => {
    const same = mount(MarkdownEditor, {
      props: { modelValue: '## 角色\n- 甲', configType: 'SYSTEM_PROMPT', baseline: '## 角色\n- 甲\n- 已刪除' },
    })
    await tab(same as never, '預覽').trigger('click')
    expect(same.find('mark').exists()).toBe(false)
    expect(same.find('.pm-preview-foot').exists()).toBe(false)

    const blank = mount(MarkdownEditor, {
      props: { modelValue: '## 角色\n\n- 甲', configType: 'SYSTEM_PROMPT', baseline: '## 角色\n- 甲' },
    })
    await tab(blank as never, '預覽').trigger('click')
    expect(blank.find('.pm-preview-foot').exists()).toBe(false)

    const never = mount(MarkdownEditor, { props: { modelValue: '## 角色\n- 甲', configType: 'SYSTEM_PROMPT', baseline: null } })
    await tab(never as never, '預覽').trigger('click')
    expect(never.find('mark').exists()).toBe(false)
    expect(never.find('.pm-preview-foot').exists()).toBe(false)
  })

  it('makes the textarea read-only when editing is not allowed', () => {
    const wrapper = mount(MarkdownEditor, { props: { modelValue: 'x', configType: 'SYSTEM_PROMPT', readonly: true } })
    expect(wrapper.find('textarea').attributes('readonly')).toBeDefined()
  })
})
