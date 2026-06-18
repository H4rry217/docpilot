export type ApiErrorPayload = {
  code: number
  msg: string
  requestId?: string
  timestamp?: number
}

type ResultPayload<T> = {
  code: number
  msg: string
  data?: T
  requestId?: string
  timestamp?: number
}

type PostJsonOptions = {
  signal?: AbortSignal
}

const API_PREFIX = '/api'

export class ApiError extends Error {
  readonly status: number
  readonly payload?: ApiErrorPayload

  constructor(status: number, message: string, payload?: ApiErrorPayload) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.payload = payload
  }
}

function isApiErrorPayload(value: unknown): value is ApiErrorPayload {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return typeof candidate.code === 'number' && typeof candidate.msg === 'string'
}

function isResultPayload<T>(value: unknown): value is ResultPayload<T> {
  return isApiErrorPayload(value)
}

/**
 * Frontend code uses backend controller paths; the dev server and deployment edge expose them under /api.
 */
export function apiPath(path: string): string {
  if (/^https?:\/\//i.test(path)) return path
  if (path === API_PREFIX || path.startsWith(`${API_PREFIX}/`)) return path
  return `${API_PREFIX}${path.startsWith('/') ? path : `/${path}`}`
}

export async function postJson<TResponse, TBody extends object = Record<string, never>>(
  path: string,
  body: TBody,
  options: PostJsonOptions = {}
): Promise<TResponse> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }
  const token = globalThis.localStorage?.getItem('docpilot.auth.token')
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(apiPath(path), {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    signal: options.signal
  })

  const text = await response.text()
  const payload: unknown = text ? JSON.parse(text) : undefined
  if (!response.ok) {
    if (response.status === 401) {
      globalThis.localStorage?.removeItem('docpilot.auth.token')
      globalThis.window?.dispatchEvent(new Event('docpilot.auth.invalid'))
    }
    const errorPayload = isApiErrorPayload(payload) ? payload : undefined
    throw new ApiError(response.status, errorPayload?.msg ?? response.statusText, errorPayload)
  }
  return isResultPayload<TResponse>(payload) ? (payload.data as TResponse) : (payload as TResponse)
}
