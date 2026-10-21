import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

const { suggestions } = vi.hoisted(() => ({ suggestions: vi.fn() }))
vi.mock('../../api/aiAssistantApi', () => ({ aiAssistantApi: { chat: vi.fn(), suggestions } }))
vi.mock('../../../../platform-mock/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }))

import { useAuthStore } from '../../../../platform-mock/useAuthStore'
import AssistantLauncher from '../AssistantLauncher.vue'

function setup(permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().user = { username: 'u', displayName: 'U', roleKey: 'r', roleName: 'R', permissions }
  suggestions.mockResolvedValue([])
  return mount(AssistantLauncher, { props: { customerId: 'C001' }, global: { plugins: [pinia] } })
}

describe('AssistantLauncher', () => {
  it('renders nothing without AI_ASSISTANT_USE', () => {
    const w = setup([])
    expect(w.find('.ai-fab').exists()).toBe(false)
  })

  it('shows an icon-only button with an accessible name', () => {
    const w = setup(['AI_ASSISTANT_USE'])
    expect(w.find('.ai-fab').text()).toBe('')
    expect(w.find('.ai-fab').attributes('aria-label')).toBe('開啟顧問小幫手')
  })

  it('loads suggestions only on first open, and minimize hides the panel', async () => {
    const w = setup(['AI_ASSISTANT_USE'])
    expect(suggestions).not.toHaveBeenCalled()
    await w.find('.ai-fab').trigger('click')
    await flushPromises()
    expect(suggestions).toHaveBeenCalledTimes(1)
    expect(suggestions).toHaveBeenCalledWith('C001')
    expect(w.find('.ai-panel').attributes('style') ?? '').not.toContain('display: none')

    await w.find('button[aria-label="縮小對話視窗"]').trigger('click')
    expect(w.find('.ai-panel').attributes('style') ?? '').toContain('display: none')
    await w.find('.ai-fab').trigger('click')
    expect(suggestions).toHaveBeenCalledTimes(1)
  })
})
