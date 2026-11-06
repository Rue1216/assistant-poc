import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import SafeMarkdown from '../../markdown/SafeMarkdown'
import MarkdownEditor from '../MarkdownEditor.vue'
import ToolPicker from '../ToolPicker.vue'
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

  it('says which required heading is missing in the header, only for instructions', () => {
    expect(mountEditor('## 範例問題\n- 問題一').text()).toContain('缺少「## 適用問題」')
    expect(mountEditor('## 適用問題').text()).not.toContain('缺少')
    for (const c of ['', '   ', '## 角色']) expect(mountEditor(c, 'SYSTEM_PROMPT').text()).not.toContain('缺少')
    expect(mountEditor('## 範例問題').text()).not.toContain('必要章節')
  })

  it('has no chips preview and no required-section list for the system prompt', async () => {
    const wrapper = mountEditor('## 角色\n\n## 回答格式', 'SYSTEM_PROMPT')
    expect(wrapper.text()).not.toContain('必要章節')
    expect(wrapper.find('.pm-sections').exists()).toBe(false)

    await tab(wrapper, '預覽').trigger('click')
    expect(wrapper.find('.pm-chips-preview').exists()).toBe(false)
  })

  it('explains that a category without example questions is not shown in the chat window', async () => {
    const wrapper = mountEditor('## 適用問題\n內容')
    await tab(wrapper, '預覽').trigger('click')
    expect(wrapper.find('.pm-chips-preview').text()).toContain('沒有範例問題，對話視窗不會顯示這個分類')
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

  describe('tool insertion', () => {
    const toolButton = (w: ReturnType<typeof mount>) => w.findComponent(ToolPicker)

    it('inserts the marker at the cursor and moves the cursor after it', async () => {
      const wrapper = mount(MarkdownEditor, {
        props: {
          modelValue: 'abcd',
          configType: 'SYSTEM_INSTRUCTION' as const,
          'onUpdate:modelValue': (v: string) => wrapper.setProps({ modelValue: v }),
        },
        attachTo: document.body,
      })
      const ta = wrapper.find('textarea').element as HTMLTextAreaElement
      ta.setSelectionRange(2, 2)
      toolButton(wrapper).vm.$emit('insert', '{{x}}')
      await nextTick()
      await nextTick()
      expect(ta.value).toBe('ab{{x}}cd')
      expect(wrapper.emitted('update:modelValue')!.at(-1)).toEqual(['ab{{x}}cd'])
      expect(ta.selectionStart).toBe(7)
      expect(ta.selectionEnd).toBe(7)
      expect(document.activeElement).toBe(ta)
      wrapper.unmount()
    })

    it('replaces the selected text', async () => {
      const wrapper = mount(MarkdownEditor, {
        props: {
          modelValue: 'abcd',
          configType: 'SYSTEM_INSTRUCTION' as const,
          'onUpdate:modelValue': (v: string) => wrapper.setProps({ modelValue: v }),
        },
        attachTo: document.body,
      })
      const ta = wrapper.find('textarea').element as HTMLTextAreaElement
      ta.setSelectionRange(1, 3)
      toolButton(wrapper).vm.$emit('insert', '{{x}}')
      await nextTick()
      await nextTick()
      expect(wrapper.emitted('update:modelValue')!.at(-1)).toEqual(['a{{x}}d'])
      expect(ta.selectionStart).toBe(6)
      wrapper.unmount()
    })

    it('shows the button only for an editable instruction on the edit tab', async () => {
      const ok = mount(MarkdownEditor, { props: { modelValue: 'x', configType: 'SYSTEM_INSTRUCTION' } })
      expect(ok.findComponent(ToolPicker).exists()).toBe(true)
      await tab(ok as never, '預覽').trigger('click')
      expect(ok.findComponent(ToolPicker).exists()).toBe(false)

      const prompt = mount(MarkdownEditor, { props: { modelValue: 'x', configType: 'SYSTEM_PROMPT' } })
      expect(prompt.findComponent(ToolPicker).exists()).toBe(false)

      const ro = mount(MarkdownEditor, { props: { modelValue: 'x', configType: 'SYSTEM_INSTRUCTION', readonly: true } })
      expect(ro.findComponent(ToolPicker).exists()).toBe(false)
    })
  })

  it('makes the textarea read-only when editing is not allowed', () => {
    const wrapper = mount(MarkdownEditor, { props: { modelValue: 'x', configType: 'SYSTEM_PROMPT', readonly: true } })
    expect(wrapper.find('textarea').attributes('readonly')).toBeDefined()
  })
})
