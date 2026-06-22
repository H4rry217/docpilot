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
const DEFAULT_API_BASE_URL = API_PREFIX
const ABSOLUTE_URL_PATTERN = /^https?:\/\//i

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

function trimTrailingSlash(value: string): string {
  return value.length > 1 ? value.replace(/\/+$/, '') : value
}

function apiBaseUrl(): string {
  const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
  return trimTrailingSlash(configuredBaseUrl || DEFAULT_API_BASE_URL)
}

function controllerPath(path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  if (normalizedPath === API_PREFIX) return ''
  if (normalizedPath.startsWith(`${API_PREFIX}/`)) {
    return normalizedPath.slice(API_PREFIX.length)
  }
  return normalizedPath
}

function joinApiPath(baseUrl: string, path: string): string {
  if (!path) return baseUrl
  if (baseUrl === '/') return path
  return `${baseUrl}${path.startsWith('/') ? path : `/${path}`}`
}

/**
 * Frontend code uses backend controller paths. VITE_API_BASE_URL replaces the logical /api prefix per environment.
 */
export function apiPath(path: string): string {
  if (ABSOLUTE_URL_PATTERN.test(path)) return path
  return joinApiPath(apiBaseUrl(), controllerPath(path))
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
