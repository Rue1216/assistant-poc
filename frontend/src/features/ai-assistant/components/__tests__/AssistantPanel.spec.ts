import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { ChatResponse, SuggestionCategory } from '../../types'

const { chat, suggestions } = vi.hoisted(() => ({ chat: vi.fn(), suggestions: vi.fn() }))
vi.mock('../../api/aiAssistantApi', () => ({ aiAssistantApi: { chat, suggestions } }))
vi.mock('../../../../platform-mock/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }))

import { ApiError } from '../../../../platform-mock/apiClient'
import { useAuthStore } from '../../../../platform-mock/useAuthStore'
import { useAssistantStore } from '../../stores/useAssistantStore'
import AssistantPanel from '../AssistantPanel.vue'

const CATEGORIES: SuggestionCategory[] = [
  { configKey: 'instruction.customer-overview', name: '顧客概況', questions: ['幫我整理這位顧客的概況', '這位顧客有哪些標籤？'] },
  { configKey: 'instruction.opportunity', name: '經營機會', questions: ['這位顧客有哪些值得關注的經營機會？'] },
  { configKey: 'instruction.contact-prep', name: '聯繫準備', questions: ['整理近期互動，幫我準備下次聯繫重點'] },
  { configKey: 'instruction.contact-topics', name: '聯繫話題', questions: ['這位顧客持有的股票最近有什麼可以聊的？'] },
  { configKey: 'instruction.market', name: '市場快訊', questions: ['今天大盤收盤如何？'] },
]

function answer(text = '回答'): ChatResponse {
  return { requestId: 'r', answer: text, sources: [], queriedAt: '2026-10-19T14:03:24+08:00', disclaimer: null, toolCalls: [] }
}

async function mountPanel(customerId = 'C001', roleKey = 'SALES_RM') {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().user = {
    username: roleKey === 'BACKOFFICE_ADMIN' ? 'backoffice.admin' : 'sales.rm',
    displayName: 'Test',
    roleKey,
    roleName: roleKey,
    permissions: ['AI_ASSISTANT_USE'],
  }
  const wrapper = mount(AssistantPanel, { props: { customerId }, global: { plugins: [pinia] } })
  await flushPromises()
  return { wrapper, store: useAssistantStore() }
}

describe('AssistantPanel', () => {
  beforeEach(() => {
    chat.mockReset()
    suggestions.mockReset()
    suggestions.mockResolvedValue(CATEGORIES)
  })

  it('shows the customer id in the header only', async () => {
    const { wrapper } = await mountPanel('C003')
    expect(wrapper.find('.ai-header__sub').text()).toContain('正在檢視 C003 · 個人 360')
  })

  it('Enter sends, Shift+Enter inserts newline', async () => {
    chat.mockResolvedValue(answer())
    const { wrapper } = await mountPanel()
    const input = wrapper.find('textarea')
    await input.setValue('你好')

    const shift = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, cancelable: true })
    input.element.dispatchEvent(shift)
    expect(shift.defaultPrevented).toBe(false)
    expect(chat).not.toHaveBeenCalled()

    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(chat).toHaveBeenCalledTimes(1)
    expect(chat.mock.calls[0]![1].message).toBe('你好')
    expect((input.element as HTMLTextAreaElement).value).toBe('')
  })

  it('does not send while IME composing', async () => {
    const { wrapper } = await mountPanel()
    const input = wrapper.find('textarea')
    await input.setValue('你好')
    await input.trigger('keydown', { key: 'Enter', isComposing: true })
    await flushPromises()
    expect(chat).not.toHaveBeenCalled()
  })

  it('does not send whitespace-only input', async () => {
    const { wrapper } = await mountPanel()
    const input = wrapper.find('textarea')
    await input.setValue('   \n ')
    await input.trigger('keydown', { key: 'Enter' })
    expect(chat).not.toHaveBeenCalled()
    expect(wrapper.find('button.ai-send').attributes('disabled')).toBeDefined()
  })

  it('limits input to 1000 characters and shows counter', async () => {
    const { wrapper } = await mountPanel()
    const input = wrapper.find('textarea')
    expect(wrapper.find('.ai-composer__count').text()).toBe('0 / 1000')
    await input.setValue('a'.repeat(1200))
    expect((input.element as HTMLTextAreaElement).value).toHaveLength(1000)
    expect(wrapper.find('.ai-composer__count').text()).toBe('1000 / 1000')
    await input.setValue('abc')
    expect(wrapper.find('.ai-composer__count').text()).toBe('3 / 1000')
  })

  it('disables input when unavailable', async () => {
    const { wrapper, store } = await mountPanel()
    store.markUnavailable()
    await flushPromises()
    expect(wrapper.text()).toContain('查無此顧客或您沒有查看權限')
    expect(wrapper.find('textarea').attributes('disabled')).toBeDefined()
    expect(wrapper.find('button.ai-send').attributes('disabled')).toBeDefined()
  })

  it('marks unavailable when suggestions answer 404', async () => {
    suggestions.mockRejectedValue(new ApiError(404, 'AI_CUSTOMER_NOT_FOUND', 'x'))
    const { wrapper } = await mountPanel()
    expect(wrapper.text()).toContain('查無此顧客或您沒有查看權限')
    expect(wrapper.find('textarea').attributes('disabled')).toBeDefined()
  })

  it('clicking a suggestion chip sends that question', async () => {
    chat.mockResolvedValue(answer())
    const { wrapper } = await mountPanel()
    const groups = wrapper.findAll('.ai-quick-group')
    expect(groups.map((g) => g.find('h4').text())).toEqual(['顧客概況', '經營機會', '聯繫準備', '聯繫話題', '市場快訊'])
    await groups[0]!.findAll('button.chip')[1]!.trigger('click')
    await flushPromises()
    expect(chat.mock.calls[0]![1].message).toBe('這位顧客有哪些標籤？')
    expect(wrapper.find('.ai-user, .ai-msg--user').exists()).toBe(true)
  })

  it('shows all categories for LIMITED users without the limited note', async () => {
    // the backend returns the same five categories to every accessible user (spec 02 2.2)
    const { wrapper } = await mountPanel('C001', 'BACKOFFICE_ADMIN')
    expect(wrapper.findAll('.ai-quick-group')).toHaveLength(5)
    expect(wrapper.text()).not.toContain('僅能查看基本輪廓')
  })

  it('shows the welcome card without chips when suggestions fail', async () => {
    suggestions.mockRejectedValue(new ApiError(503, 'AI_LLM_UNAVAILABLE', 'x'))
    const { wrapper } = await mountPanel()
    expect(wrapper.text()).toContain('您好，我可以協助您掌握本顧客')
    expect(wrapper.findAll('.chip')).toHaveLength(0)
  })

  it('reloads suggestions and clears the conversation when the customer changes', async () => {
    chat.mockResolvedValue(answer())
    const { wrapper, store } = await mountPanel('C001')
    await store.send('hello')
    expect(store.messages).toHaveLength(2)
    await wrapper.setProps({ customerId: 'C002' })
    await flushPromises()
    expect(store.messages).toEqual([])
    expect(suggestions).toHaveBeenLastCalledWith('C002')
  })

  it('shows pending message while waiting and error message on failure', async () => {
    let reject!: (e: unknown) => void
    chat.mockReturnValue(new Promise((_, rej) => (reject = rej)))
    const { wrapper } = await mountPanel()
    await wrapper.find('textarea').setValue('hello')
    await wrapper.find('button.ai-send').trigger('click')
    expect(wrapper.text()).toContain('正在查詢資料並整理回答')

    reject(new ApiError(429, 'AI_RATE_LIMITED', '目前提問次數較多，請稍候再試', 'a1b2c3d4'))
    await flushPromises()
    expect(wrapper.text()).not.toContain('正在查詢資料並整理回答')
    const err = wrapper.find('[role="alert"]')
    expect(err.text()).toContain('目前提問次數較多，請稍候再試')
    expect(err.text()).toContain('AI_RATE_LIMITED · 追蹤碼 a1b2c3d4')

    chat.mockResolvedValue(answer('重試成功'))
    await err.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('重試成功')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('cancel shows 已取消', async () => {
    chat.mockImplementation(
      (_id: string, _b: unknown, signal: AbortSignal) =>
        new Promise((_, rej) => signal.addEventListener('abort', () => rej(new DOMException('aborted', 'AbortError')))),
    )
    const { wrapper } = await mountPanel()
    await wrapper.find('textarea').setValue('hello')
    await wrapper.find('button.ai-send').trigger('click')
    await wrapper.find('.ai-mini-btn').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('已取消')
    expect(wrapper.text()).not.toContain('正在查詢資料並整理回答')
  })

  it('clear button empties the conversation and close emits close', async () => {
    chat.mockResolvedValue(answer())
    const { wrapper, store } = await mountPanel()
    await store.send('hello')
    await wrapper.find('button[aria-label="清除對話"]').trigger('click')
    expect(store.messages).toEqual([])
    await wrapper.find('button[aria-label="縮小對話視窗"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
