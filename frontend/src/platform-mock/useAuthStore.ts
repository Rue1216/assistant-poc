import { defineStore } from 'pinia'
import { apiClient, UNAUTHENTICATED_CODE } from './apiClient'

export interface Me {
  username: string
  displayName: string
  roleKey: string
  roleName: string
  permissions: string[]
}

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null as Me | null }),
  actions: {
    hasPermission(p: string): boolean {
      return this.user?.permissions.includes(p) ?? false
    },
    async login(username: string, password: string): Promise<void> {
      const res = await apiClient.post<Me>('/mock/auth/login', { username, password })
      this.user = res.data
    },
    async logout(): Promise<void> {
      try {
        await apiClient.post('/mock/auth/logout')
      } finally {
        this.user = null
      }
    },
    /** Also primes the XSRF-TOKEN cookie. A 401 here is normal and silent. */
    async fetchMe(): Promise<void> {
      try {
        const res = await apiClient.get<Me>('/mock/auth/me', {
          meta: { silentCodes: [UNAUTHENTICATED_CODE] },
        })
        this.user = res.data
      } catch {
        this.user = null
      }
    },
  },
})
