import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'

const show = vi.fn()
vi.mock('../useToast', () => ({ useToast: () => ({ show }) }))
const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

import { apiClient } from '../apiClient'
import LoginPage from '../pages/LoginPage.vue'

function loginAdapter(status: number, data: unknown): AxiosAdapter {
  return (config: InternalAxiosRequestConfig) =>
    Promise.reject(
      new AxiosError('failed', 'ERR_BAD_REQUEST', config, null, {
        status,
        statusText: '',
        data,
        headers: {},
        config,
      }),
    )
}

async function submitLogin() {
  const wrapper = mount(LoginPage)
  await wrapper.find('input[type="text"]').setValue('ICRM00028')
  await wrapper.find('form').trigger('submit')
  await flushPromises()
  return wrapper
}

describe('LoginPage', () => {
  beforeEach(() => {
    show.mockReset()
    push.mockReset()
    setActivePinia(createPinia())
  })

  it('lists the nine employee accounts as "帳號 職稱"', () => {
    const options = mount(LoginPage)
      .findAll('select option')
      .filter((o) => o.attributes('value'))
    expect(options).toHaveLength(9)
    expect(options[0].text()).toBe('ICRM00047 總行查詢人員')
    expect(options[0].attributes('value')).toBe('ICRM00047')
  })

  it('shows 帳號或密碼錯誤 inline on 401 without a toast', async () => {
    apiClient.defaults.adapter = loginAdapter(401, { code: 'AI_UNAUTHENTICATED', message: '請先登入' })
    const wrapper = await submitLogin()
    expect(wrapper.find('[role="alert"]').text()).toBe('帳號或密碼錯誤')
    expect(show).not.toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()
  })

  it('leaves other errors to the global toast and shows no inline message', async () => {
    apiClient.defaults.adapter = loginAdapter(500, { code: 'AI_INTERNAL_ERROR', message: '系統發生錯誤，請稍後再試' })
    const wrapper = await submitLogin()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(show).toHaveBeenCalledWith('系統發生錯誤，請稍後再試')
  })
})
