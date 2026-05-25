export type ApiErrorPayload = {
  code: string
  message: string
  timestamp?: string
}

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
  return typeof candidate.code === 'string' && typeof candidate.message === 'string'
}

export async function postJson<TResponse, TBody extends object = Record<string, never>>(
  path: string,
  body: TBody
): Promise<TResponse> {
  const response = await fetch(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body)
  })

  const text = await response.text()
  const payload: unknown = text ? JSON.parse(text) : undefined
  if (!response.ok) {
    const errorPayload = isApiErrorPayload(payload) ? payload : undefined
    throw new ApiError(response.status, errorPayload?.message ?? response.statusText, errorPayload)
  }
  return payload as TResponse
}
