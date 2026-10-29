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
import { ATTENTION, COMPLAINT, RETIREE } from '../../../../testing/testCustomers'

const CATEGORIES: SuggestionCategory[] = [
  { configKey: 'instruction.customer-overview', name: '顧客概況', questions: ['幫我整理這位顧客的概況', '這位顧客有哪些標籤？'] },
  { configKey: 'instruction.opportunity', name: '經營機會', questions: ['這位顧客有哪些值得關注的經營機會？'] },
  { configKey: 'instruction.contact-prep', name: '聯繫準備', questions: ['整理近期互動，幫我準備下次聯繫重點'] },
  { configKey: 'instruction.contact-topics', name: '聯繫話題', questions: ['這位顧客持有的股票最近有什麼可以聊的？'] },
  { configKey: 'instruction.market', name: '市場快訊', questions: ['今天大盤收盤如何？'] },
]

function answer(text = '回答'): ChatResponse {
  return { requestId: 'r', answer: text, blocks: [{ type: 'paragraph', text, citations: [] }], citations: [], queriedAt: '2026-10-19T14:03:24+08:00', disclaimer: null, toolCalls: [] }
}

async function mountPanel(certNo = RETIREE, roleKey = 'SALES_RM') {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().user = {
    username: roleKey === 'ADMIN' ? 'EEE11111' : 'sales.rm',
    displayName: 'Test',
    roleKey,
    roleName: roleKey,
    permissions: ['AI_ASSISTANT_USE'],
  }
  const wrapper = mount(AssistantPanel, { props: { certNo }, global: { plugins: [pinia] } })
  await flushPromises()
  return { wrapper, store: useAssistantStore() }
}

describe('AssistantPanel', () => {
  beforeEach(() => {
    chat.mockReset()
    suggestions.mockReset()
    suggestions.mockResolvedValue(CATEGORIES)
  })

  it('header shows only the title, without the viewing status', async () => {
    const { wrapper } = await mountPanel(ATTENTION)
    expect(wrapper.find('.ai-header').text()).toBe('顧問小幫手')
    expect(wrapper.text()).not.toContain('正在檢視')
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
    expect(chat.mock.calls[0]![1].categoryKey).toBe('instruction.customer-overview')
  })

  it('typing in the composer sends no categoryKey', async () => {
    chat.mockResolvedValue(answer())
    const { wrapper } = await mountPanel()
    const input = wrapper.find('textarea')
    await input.setValue('你好')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect('categoryKey' in chat.mock.calls[0]![1]).toBe(false)
  })

  it('shows all categories for LIMITED users without the limited note', async () => {
    // the backend returns the same five categories to every accessible user (spec 02 2.2)
    const { wrapper } = await mountPanel(RETIREE, 'ADMIN')
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
    const { wrapper, store } = await mountPanel(RETIREE)
    await store.send('hello')
    expect(store.messages).toHaveLength(2)
    await wrapper.setProps({ certNo: COMPLAINT })
    await flushPromises()
    expect(store.messages).toEqual([])
    expect(suggestions).toHaveBeenLastCalledWith(COMPLAINT)
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

  it('while waiting the send button becomes a stop button; stopping shows 已取消', async () => {
    chat.mockImplementation(
      (_id: string, _b: unknown, signal: AbortSignal) =>
        new Promise((_, rej) => signal.addEventListener('abort', () => rej(new DOMException('aborted', 'AbortError')))),
    )
    const { wrapper } = await mountPanel()
    await wrapper.find('textarea').setValue('hello')
    await wrapper.find('button.ai-send').trigger('click')
    expect(wrapper.find('button.ai-send').exists()).toBe(false)
    expect(wrapper.find('.ai-messages button').exists()).toBe(false)

    await wrapper.find('button[aria-label="停止回答"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('已取消')
    expect(wrapper.text()).not.toContain('正在查詢資料並整理回答')
    expect(wrapper.find('button[aria-label="停止回答"]').exists()).toBe(false)
    expect(wrapper.find('button.ai-send').exists()).toBe(true)
  })

  it('clicking a follow-up suggestion sends it with its categoryKey and closes the list', async () => {
    chat.mockResolvedValue(answer())
    const { wrapper, store } = await mountPanel()
    await store.send('hello')
    await flushPromises()
    const pop = wrapper.find('details.ai-quick-pop')
    ;(pop.element as HTMLDetailsElement).open = true
    await pop.findAll('button.chip')[2]!.trigger('click')
    await flushPromises()
    expect(chat.mock.calls[1]![1].message).toBe('這位顧客有哪些值得關注的經營機會？')
    expect(chat.mock.calls[1]![1].categoryKey).toBe('instruction.opportunity')
    expect((pop.element as HTMLDetailsElement).open).toBe(false)
  })

  it('shows the AI notice under the input instead of a banner', async () => {
    const { wrapper } = await mountPanel()
    expect(wrapper.find('.ai-notice').exists()).toBe(false)
    const foot = wrapper.find('.ai-composer__foot')
    expect(foot.text()).toContain('AI 產生的內容可能有誤，請以系統資料為準')
    expect(foot.text()).not.toContain('Enter 送出')
    expect(foot.find('.ai-composer__count').text()).toBe('0 / 1000')
  })

  it('scrolls to the start of a new answer rather than its end', async () => {
    chat.mockResolvedValue(answer('長回答'))
    const { wrapper, store } = await mountPanel()
    // jsdom has no layout: the list starts at 100px, the answer at 500px
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      return { top: this.matches('article.ai-msg') ? 500 : 100 } as DOMRect
    })
    await store.send('hello')
    await flushPromises()
    expect(wrapper.find('.ai-messages').element.scrollTop).toBe(384)
    rect.mockRestore()
  })

  describe('new chat button', () => {
    const newChat = (w: Awaited<ReturnType<typeof mountPanel>>['wrapper']) => w.find('button[aria-label="開新對話"]')
    const dividers = (w: Awaited<ReturnType<typeof mountPanel>>['wrapper']) => w.findAll('.ai-divider').map((d) => d.text())
    const hints = (w: Awaited<ReturnType<typeof mountPanel>>['wrapper']) => w.findAll('.ai-divider__hint').map((d) => d.text())
    const NEW_ROUND_HINT = '以下為新對話，不會參考上方的對話內容'

    it('replaces the more-actions menu; history and delete are gone', async () => {
      const { wrapper } = await mountPanel()
      expect(wrapper.find('button[aria-label="更多動作"]').exists()).toBe(false)
      expect(wrapper.find('[role="menu"]').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('刪除對話')
      expect(wrapper.text()).not.toContain('查看對話紀錄')
      expect(newChat(wrapper).exists()).toBe(true)
    })

    it('is disabled until the current round has a message', async () => {
      chat.mockResolvedValue(answer())
      const { wrapper, store } = await mountPanel()
      expect(newChat(wrapper).attributes('disabled')).toBeDefined()
      expect(wrapper.find('.ai-welcome').exists()).toBe(true)

      await store.send('hello')
      await flushPromises()
      expect(newChat(wrapper).attributes('disabled')).toBeUndefined()

      await newChat(wrapper).trigger('click')
      expect(newChat(wrapper).attributes('disabled')).toBeDefined()
    })

    it('keeps the earlier conversation, adds a new-round divider and clears the draft', async () => {
      chat.mockResolvedValue(answer('第一輪回答'))
      const { wrapper, store } = await mountPanel()
      await store.send('hello')
      await wrapper.find('textarea').setValue('草稿')
      await newChat(wrapper).trigger('click')

      expect(wrapper.text()).toContain('第一輪回答')
      expect(wrapper.find('.ai-welcome').exists()).toBe(false)
      expect(dividers(wrapper)).toHaveLength(2)
      // before the new round has a message, the hint is the divider text itself
      expect(dividers(wrapper)[1]).toBe(NEW_ROUND_HINT)
      expect(hints(wrapper)).toEqual([])
      expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('')

      await store.send('again')
      await flushPromises()
      expect(dividers(wrapper)).toHaveLength(2)
      expect(dividers(wrapper)[0]).toMatch(/^今天 \d{2}:\d{2}$/)
      expect(dividers(wrapper)[1]).toMatch(/^今天 \d{2}:\d{2}$/)
      // the hint moves under the divider; only later rounds carry it, never the first
      expect(hints(wrapper)).toEqual([NEW_ROUND_HINT])
    })

    it('hides retry on an error from an earlier round', async () => {
      chat.mockRejectedValueOnce(new ApiError(503, 'AI_LLM_UNAVAILABLE', '服務暫時無法使用'))
      const { wrapper, store } = await mountPanel()
      await store.send('hello')
      await flushPromises()
      expect(wrapper.find('[role="alert"] button').exists()).toBe(true)

      await newChat(wrapper).trigger('click')
      expect(wrapper.find('[role="alert"]').text()).toContain('服務暫時無法使用')
      expect(wrapper.find('[role="alert"] button').exists()).toBe(false)
    })
  })

  it('minimize emits close', async () => {
    const { wrapper } = await mountPanel()
    await wrapper.find('button[aria-label="縮小對話視窗"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
