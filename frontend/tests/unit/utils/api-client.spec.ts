import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, CanceledError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'

const show = vi.fn()
vi.mock('@/composables/use-toast', () => ({ useToast: () => ({ show }) }))

import {
  ApiError,
  apiClient,
  NETWORK_ERROR_CODE,
  NETWORK_ERROR_MESSAGE,
  setUnauthorizedHandler,
} from '@/utils/api-client'

const originalAdapter = apiClient.defaults.adapter

function failWith(status: number, data: unknown): AxiosAdapter {
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

describe('apiClient', () => {
  beforeEach(() => show.mockReset())
  afterEach(() => {
    setUnauthorizedHandler(null)
    apiClient.defaults.adapter = originalAdapter
  })

  it('maps error body to ApiError', async () => {
    apiClient.defaults.adapter = failWith(409, {
      code: 'AI_CONFIG_CONFLICT',
      message: '內容已被他人更新，請重新載入',
      traceId: '463ac35c9f6413ad48485a3953bb6124',
      details: [],
    })
    const err = await apiClient.get('/x').catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(409)
    expect(err.code).toBe('AI_CONFIG_CONFLICT')
    expect(err.message).toBe('內容已被他人更新，請重新載入')
    expect(err.traceId).toBe('463ac35c9f6413ad48485a3953bb6124')
  })

  it('ignores a legacy requestId field', async () => {
    apiClient.defaults.adapter = failWith(400, { code: 'AI_X', message: 'm', requestId: 'old' })
    const err = await apiClient.get('/x').catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.traceId).toBeUndefined()
  })

  it('shows toast with server message', async () => {
    apiClient.defaults.adapter = failWith(400, { code: 'AI_VALIDATION_FAILED', message: '欄位有誤' })
    await apiClient.get('/x').catch(() => undefined)
    expect(show).toHaveBeenCalledWith('欄位有誤')
  })

  it('skips toast for silent codes', async () => {
    apiClient.defaults.adapter = failWith(409, {
      code: 'AI_CONFIG_OVERWRITE_PENDING',
      message: '已有排程中的版本',
    })
    const err = await apiClient
      .get('/x', { meta: { silentCodes: ['AI_CONFIG_OVERWRITE_PENDING'] } })
      .catch((e) => e)
    expect(err.code).toBe('AI_CONFIG_OVERWRITE_PENDING')
    expect(show).not.toHaveBeenCalled()
  })

  it('does not toast on abort', async () => {
    apiClient.defaults.adapter = () => Promise.reject(new CanceledError('canceled'))
    const err = await apiClient.get('/x').catch((e) => e)
    expect(err).toBeInstanceOf(CanceledError)
    expect(err).not.toBeInstanceOf(ApiError)
    expect(show).not.toHaveBeenCalled()
  })

  it('shows network message when no response', async () => {
    apiClient.defaults.adapter = (config) =>
      Promise.reject(new AxiosError('timeout of 30000ms exceeded', 'ECONNABORTED', config))
    const err = await apiClient.get('/x').catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(0)
    expect(err.code).toBe(NETWORK_ERROR_CODE)
    expect(err.message).toBe(NETWORK_ERROR_MESSAGE)
    expect(show).toHaveBeenCalledWith('網路連線異常，請稍後再試')
  })

  it('shows the generic error, not the network message, for a proxy 502 with an HTML body', async () => {
    apiClient.defaults.adapter = failWith(502, '<html>Bad Gateway</html>')
    const err = await apiClient.get('/x').catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err.status).toBe(502)
    expect(err.code).toBe('AI_INTERNAL_ERROR')
    expect(err.message).toBe('系統發生錯誤，請稍後再試')
    expect(show).toHaveBeenCalledWith('系統發生錯誤，請稍後再試')
    expect(show).not.toHaveBeenCalledWith('網路連線異常，請稍後再試')
  })

  it('shows the generic error for a CSRF 403 with an HTML body', async () => {
    apiClient.defaults.adapter = failWith(403, '<html>Forbidden</html>')
    const err = await apiClient.get('/x').catch((e) => e)
    expect(err.status).toBe(403)
    expect(err.code).toBe('AI_INTERNAL_ERROR')
    expect(show).toHaveBeenCalledWith('系統發生錯誤，請稍後再試')
  })

  it('toasts and calls the unauthorized handler once on a non-silent 401', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    apiClient.defaults.adapter = failWith(401, { code: 'AI_UNAUTHENTICATED', message: '請先登入' })
    await apiClient.get('/x').catch(() => undefined)
    expect(show).toHaveBeenCalledWith('請先登入')
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('neither toasts nor calls the unauthorized handler on a silent 401', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    apiClient.defaults.adapter = failWith(401, { code: 'AI_UNAUTHENTICATED', message: '請先登入' })
    await apiClient.get('/x', { meta: { silentCodes: ['AI_UNAUTHENTICATED'] } }).catch(() => undefined)
    expect(show).not.toHaveBeenCalled()
    expect(handler).not.toHaveBeenCalled()
  })

  it('uses a 30s default timeout', () => {
    expect(apiClient.defaults.timeout).toBe(30000)
  })
})
