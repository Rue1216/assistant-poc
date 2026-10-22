import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { h } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'

vi.mock('../useToast', () => ({ useToast: () => ({ show: vi.fn() }) }))

import { apiClient } from '../apiClient'
import MainLayout from '../MainLayout.vue'
import { useAuthStore } from '../useAuthStore'

async function mountLayout(allowLeave: boolean) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { username: 'it.admin', displayName: 'IT', roleKey: 'IT_ADMIN', roleName: 'IT', permissions: [] }
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: null })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', component: { render: () => h('p', '登入頁') } },
      {
        path: '/',
        component: MainLayout,
        children: [
          {
            path: '',
            // stands for a page that vetoes leaving, e.g. the prompt page with unsaved edits
            component: { beforeRouteLeave: () => allowLeave, render: () => h('p', '內容頁') },
          },
        ],
      },
    ],
  })
  await router.push('/')
  const wrapper = mount({ render: () => h(RouterView) }, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router, auth, post }
}

describe('MainLayout logout', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('does not log out when the current page vetoes leaving', async () => {
    const { wrapper, router, auth, post } = await mountLayout(false)

    await wrapper.find('button.logout').trigger('click')
    await flushPromises()

    expect(post).not.toHaveBeenCalled()
    expect(auth.user).not.toBeNull()
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('logs out and goes to the login page when leaving is allowed', async () => {
    const { wrapper, router, auth, post } = await mountLayout(true)

    await wrapper.find('button.logout').trigger('click')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/mock/auth/logout')
    expect(auth.user).toBeNull()
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
