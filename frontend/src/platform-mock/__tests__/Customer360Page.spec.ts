import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { apiClient, ApiError } from '../apiClient'
import Customer360Page from '../pages/Customer360Page.vue'
import { HIGH_ASSET, NO_MARKETING, RETIREE, STOP_USE, UNKNOWN } from '../../testing/testCustomers'
import {
  HUANG_CUSTOMER,
  HUANG_REAL_TIME,
  RELATIONSHIP_CUSTOMER,
  WANG_CUSTOMER,
  WANG_REAL_TIME,
} from './customer360Fixtures'

const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

const WANG_DETAIL = { certNo: RETIREE, accessLevel: 'FULL', name: '王小明', segment: 'R01-退休樂活客群' }
const HUANG_DETAIL = { certNo: NO_MARKETING, accessLevel: 'FULL', name: '黃俊豪', segment: 'D04-數位新世代客群' }

/** Routes apiClient.get by URL suffix; an unlisted URL fails the test by rejecting. */
function mockApi(certNo: string, routes: Record<string, unknown>) {
  return vi.spyOn(apiClient, 'get').mockImplementation(async (url: string) => {
    const suffix = url.slice(`/mock/customers/${certNo}`.length)
    if (!(suffix in routes)) throw new Error(`unexpected request ${url}`)
    const value = routes[suffix]
    if (value instanceof Error) throw value
    return { data: value }
  })
}

const wangRoutes = (customer = WANG_CUSTOMER) => ({
  '/cip': { stopUseCustomerDataFlag: 'N' },
  '': WANG_DETAIL,
  '/360': customer,
  '/real-time': WANG_REAL_TIME,
})

async function mountPage(certNo = RETIREE) {
  const wrapper = mount(Customer360Page, {
    props: { certNo },
    global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, AssistantLauncher: true } },
  })
  await flushPromises()
  return wrapper
}

describe('Customer360Page', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    push.mockClear()
  })

  it('fills every card from the 360 API', async () => {
    mockApi(RETIREE, wangRoutes())

    const wrapper = await mountPage()

    const card = wrapper.find('.c360-namecard')
    expect(card.find('.c360-namecard__name').text()).toBe('王小明')
    expect(card.text()).toContain('R01-退休樂活客群')
    expect(card.text()).toContain('B101 信義分行')
    expect(card.text()).toContain('退休規劃')
    expect(wrapper.find('.c360-b').text()).toContain('定存 300 萬將於 2026/11/15 到期')
    const rank = wrapper.find('.c360-e')
    expect(rank.text()).toContain('新富')
    expect(rank.text()).toContain('距 菁英 尚差')
    expect(rank.text()).toContain('180 萬')
    expect(wrapper.find('.c360-h .is-highlight title').text()).toContain('海外債')
    const channel = wrapper.find('.c360-l')
    expect(channel.text()).toContain('客群平均')
    expect(channel.text()).not.toContain('回應率')
    const topbar = wrapper.find('.c360-topbar').text()
    expect(topbar).not.toContain('示意資料')
    expect(topbar).toContain('資料日期 2026/10/27')
    expect(wrapper.findComponent({ name: 'AssistantLauncher' }).exists()).toBe(true)
  })

  it("shows another customer's data", async () => {
    mockApi(NO_MARKETING, {
      '/cip': { stopUseCustomerDataFlag: 'N' },
      '': HUANG_DETAIL,
      '/360': HUANG_CUSTOMER,
      '/real-time': HUANG_REAL_TIME,
    })

    const wrapper = await mountPage(NO_MARKETING)

    expect(wrapper.find('.c360-namecard').text()).toContain('不宜行銷')
    expect(wrapper.find('.c360-e').text()).toContain('非理財會員')
  })

  it('hides the progress axis and benefits for a relationship rank', async () => {
    mockApi(RETIREE, wangRoutes(RELATIONSHIP_CUSTOMER))

    const wrapper = await mountPage()

    const rank = wrapper.find('.c360-e')
    expect(rank.find('.tier').exists()).toBe(false)
    expect(rank.find('.perks').exists()).toBe(false)
    expect(rank.text()).toContain('關係戶／親子戶')
  })

  it('blocks a customer who stopped data use', async () => {
    const get = mockApi(STOP_USE, { '/cip': { stopUseCustomerDataFlag: 'Y' } })

    const wrapper = await mountPage(STOP_USE)

    const dialog = wrapper.find('[role="alertdialog"]')
    expect(dialog.text()).toContain('此為停止蒐集利用個資顧客，故無法查詢')
    expect(get).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.c360-grid').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'AssistantLauncher' }).exists()).toBe(false)

    await dialog.find('button').trigger('click')

    expect(push).toHaveBeenCalledWith('/customer')
  })

  it('shows only the name card for a LIMITED view', async () => {
    const get = mockApi(HIGH_ASSET, {
      '/cip': { stopUseCustomerDataFlag: 'N' },
      '': { certNo: 'E2*****893', accessLevel: 'LIMITED', name: '周**', segment: 'D01' },
    })

    const wrapper = await mountPage(HIGH_ASSET)

    expect(wrapper.find('.c360-namecard').exists()).toBe(true)
    expect(wrapper.find('.c360-limited').text()).toContain('您的角色僅能查看基本資料')
    expect(wrapper.find('.c360-b').exists()).toBe(false)
    expect(wrapper.find('.dash-card').exists()).toBe(false)
    expect(get.mock.calls.map((c) => c[0])).toEqual([
      `/mock/customers/${HIGH_ASSET}/cip`,
      `/mock/customers/${HIGH_ASSET}`,
    ])
  })

  it('shows an empty state when the 360 API fails', async () => {
    mockApi(RETIREE, {
      '/cip': { stopUseCustomerDataFlag: 'N' },
      '': WANG_DETAIL,
      '/360': new ApiError(500, 'AI_INTERNAL_ERROR', 'boom'),
      '/real-time': WANG_REAL_TIME,
    })

    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('無法載入 360 資料')
    expect(wrapper.find('.c360-namecard__name').text()).toBe('王小明')
    expect(wrapper.find('.c360-b').exists()).toBe(false)
  })

  it('shows the not-found message for an unknown or inaccessible customer', async () => {
    vi.spyOn(apiClient, 'get').mockRejectedValue(new ApiError(404, 'AI_CUSTOMER_NOT_FOUND', 'not found'))

    const wrapper = await mountPage(UNKNOWN)

    expect(wrapper.find('.c360-notfound').text()).toBe('查無此顧客或您沒有查看權限')
    expect(wrapper.find('.c360-grid').exists()).toBe(false)
  })
})
