import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { apiClient, ApiError } from '../apiClient'
import Customer360Page from '../pages/Customer360Page.vue'

const FULL = {
  customerId: 'C001',
  accessLevel: 'FULL',
  name: '王小明',
  segment: 'VIP',
  relationshipYears: 8,
  riskProfile: '穩健型',
  marketingConsent: true,
  preferredChannel: '電話',
  tags: ['退休規劃', '高股息偏好'],
  assetAmount: 7_500_000,
}

async function mountPage(customerId = 'C001') {
  const wrapper = mount(Customer360Page, {
    props: { customerId },
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, AssistantLauncher: true } },
  })
  await flushPromises()
  return wrapper
}

describe('Customer360Page', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('fills the name card from the customer API and shows the sample cards', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: FULL })

    const wrapper = await mountPage()

    const card = wrapper.find('.c360-namecard')
    expect(card.find('.c360-namecard__name').text()).toBe('王小明')
    expect(card.find('.c360-namecard__id').text()).toBe('C001・往來 8 年')
    expect(card.text()).toContain('VIP')
    expect(card.text()).toContain('退休規劃')
    expect(card.text()).toContain('穩健型')
    expect(card.text()).toContain('同意')
    expect(card.text()).toContain('1,000 萬以下')
    expect(wrapper.find('.c360-b').exists()).toBe(true)
    expect(wrapper.find('.c360-m').exists()).toBe(true)
    expect(wrapper.find('.c360-topbar').text()).toContain('圖表為示意資料')
  })

  it('shows only the name card for a LIMITED view', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { customerId: 'C003', accessLevel: 'LIMITED', name: '陳**', segment: '一般', relationshipYears: 3 },
    })

    const wrapper = await mountPage('C003')

    expect(wrapper.find('.c360-namecard').exists()).toBe(true)
    expect(wrapper.find('.c360-b').exists()).toBe(false)
    expect(wrapper.find('.dash-card').exists()).toBe(false)
    expect(wrapper.find('.c360-limited').text()).toContain('您的角色僅能查看基本資料')
    expect(wrapper.find('.c360-topbar').text()).not.toContain('示意資料')
  })

  it('shows the not-found message for an unknown or inaccessible customer', async () => {
    vi.spyOn(apiClient, 'get').mockRejectedValue(new ApiError(404, 'AI_CUSTOMER_NOT_FOUND', 'not found'))

    const wrapper = await mountPage('C999')

    expect(wrapper.find('.c360-notfound').text()).toBe('查無此顧客或您沒有查看權限')
    expect(wrapper.find('.c360-grid').exists()).toBe(false)
  })
})
