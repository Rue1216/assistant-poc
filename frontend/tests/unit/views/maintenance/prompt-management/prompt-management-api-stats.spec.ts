import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'

const show = vi.hoisted(() => vi.fn())
vi.mock('@/composables/use-toast', () => ({ useToast: () => ({ show }) }))

import { apiClient, setUnauthorizedHandler } from '@/utils/api-client'
import { promptManagementApi } from '@/views/maintenance/prompt-management/prompt-management-api'

const originalAdapter = apiClient.defaults.adapter

function failWith(status: number, code: string): AxiosAdapter {
  return (config: InternalAxiosRequestConfig) =>
    Promise.reject(
      new AxiosError('failed', 'ERR_BAD_REQUEST', config, null, {
        status,
        statusText: '',
        data: { code, message: '錯誤訊息' },
        headers: {},
        config,
      }),
    )
}

describe('promptManagementApi.feedbackStats failures', () => {
  beforeEach(() => show.mockReset())
  afterEach(() => {
    setUnauthorizedHandler(null)
    apiClient.defaults.adapter = originalAdapter
  })

  it.each([
    [403, 'AI_FORBIDDEN'],
    [404, 'AI_CONFIG_NOT_FOUND'],
    [500, 'AI_INTERNAL_ERROR'],
  ])('rejects without a toast on %i %s', async (status, code) => {
    apiClient.defaults.adapter = failWith(status, code)
    await expect(promptManagementApi.feedbackStats('instruction.contact-prep')).rejects.toMatchObject({ code })
    expect(show).not.toHaveBeenCalled()
  })

  it('still runs the session-expiry handler on 401', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    apiClient.defaults.adapter = failWith(401, 'AI_UNAUTHENTICATED')
    await promptManagementApi.feedbackStats('instruction.contact-prep').catch(() => undefined)
    expect(handler).toHaveBeenCalled()
  })
})
