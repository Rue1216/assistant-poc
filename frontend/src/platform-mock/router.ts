import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import { setUnauthorizedHandler } from './apiClient'
import { useAuthStore } from './useAuthStore'
import { useToast } from './useToast'

declare module 'vue-router' {
  interface RouteMeta {
    permission?: string
    /** the layout leaves out the left menu (customer 360, as in the prototype) */
    hideSidebar?: boolean
  }
}

export function createAppRouter(history: RouterHistory = createWebHistory()) {
  const router = createRouter({
    history,
    routes: [
      { path: '/login', component: () => import('./pages/LoginPage.vue') },
      {
        path: '/',
        component: () => import('./MainLayout.vue'),
        children: [
          { path: '', component: () => import('./pages/HomePage.vue') },
          { path: 'customer', component: () => import('./pages/CustomerListPage.vue') },
          {
            path: 'customer/:customerId',
            component: () => import('./pages/Customer360Page.vue'),
            props: true,
            meta: { hideSidebar: true },
          },
          {
            path: 'setting/prompt-management',
            component: () => import('./PromptManagementRoute.vue'),
            meta: { permission: 'AI_CONFIG_READ' },
          },
        ],
      },
    ],
  })

  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    if (!auth.user) await auth.fetchMe()

    if (to.path === '/login') return auth.user && !auth.loggingOut ? '/' : true
    if (!auth.user) return '/login'

    const required = to.meta.permission
    if (required && !auth.hasPermission(required)) {
      useToast().show('您沒有使用此功能的權限')
      return '/'
    }
    return true
  })

  setUnauthorizedHandler(() => {
    useAuthStore().user = null
    if (router.currentRoute.value.path !== '/login') void router.push('/login')
  })

  return router
}
