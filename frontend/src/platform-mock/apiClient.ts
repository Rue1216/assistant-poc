import axios, { AxiosError, type AxiosInstance } from 'axios'
import { useToast } from './useToast'

export const NETWORK_ERROR_MESSAGE = '網路連線異常，請稍後再試'
export const NETWORK_ERROR_CODE = 'AI_NETWORK_ERROR'
export const UNAUTHENTICATED_CODE = 'AI_UNAUTHENTICATED'
export const INTERNAL_ERROR_CODE = 'AI_INTERNAL_ERROR'
export const INTERNAL_ERROR_MESSAGE = '系統發生錯誤，請稍後再試'

declare module 'axios' {
  interface AxiosRequestConfig {
    meta?: { silentCodes?: string[] }
  }
}

export class ApiError extends Error {
  status: number
  code: string
  traceId?: string
  details: unknown[]

  constructor(status: number, code: string, message: string, traceId?: string, details: unknown[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.traceId = traceId
    this.details = details
  }
}

let unauthorizedHandler: (() => void) | null = null

/** Registered by the router so apiClient does not import it (avoids a cycle). */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

interface ErrorBody {
  code: string
  message: string
  traceId?: string
  details?: unknown[]
}

function isErrorBody(data: unknown): data is ErrorBody {
  if (typeof data !== 'object' || data === null) return false
  const d = data as Record<string, unknown>
  return typeof d.code === 'string' && typeof d.message === 'string'
}

export const apiClient: AxiosInstance = axios.create({
  baseURL: '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  timeout: 30000,
})

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    // cancelled requests pass through untouched: no ApiError, no toast
    if (axios.isCancel(error)) return Promise.reject(error)

    const axiosError = error as AxiosError
    const data = axiosError.response?.data
    const response = axiosError.response
    let apiError: ApiError
    if (response && isErrorBody(data)) {
      apiError = new ApiError(response.status, data.code, data.message, data.traceId, data.details ?? [])
    } else if (response) {
      // the server (or a proxy) answered, but not with an ApiError body: not a connectivity problem
      apiError = new ApiError(response.status, INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE)
    } else {
      apiError = new ApiError(0, NETWORK_ERROR_CODE, NETWORK_ERROR_MESSAGE)
    }

    const silent = axiosError.config?.meta?.silentCodes?.includes(apiError.code) ?? false
    if (!silent) {
      useToast().show(apiError.message)
      if (apiError.status === 401 && unauthorizedHandler) unauthorizedHandler()
    }
    return Promise.reject(apiError)
  },
)
