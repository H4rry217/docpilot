import { ApiError } from '../../../shared/api/http'

export type InlineCompletionShape =
  | 'SHORT'
  | 'SENTENCE'
  | 'PARAGRAPH'
  | 'LIST_ITEM'
  | 'TABLE_CELL'
  | 'CODE_LINE'

export type InlineCompletionTrigger = 'IDLE' | 'MANUAL'

export type InlineCompletionBlockContext = {
  id?: string
  type: string
  text: string
  textBeforeCursor: string
  textAfterCursor: string
}

export type InlineCompletionRequest = {
  workspaceId: string
  documentId: string
  cursor: {
    from: number
    to: number
  }
  currentBlock: InlineCompletionBlockContext
  headingPath: string[]
  nearbyBlocks: InlineCompletionBlockContext[]
  trigger: InlineCompletionTrigger
  clientVersion: string
}

export type InlineCompletionDiagnostic = {
  path: string
  code: string
  message: string
}

export type InlineCompletionMetaEvent = {
  completionId: string
  modelId: string
  shape: InlineCompletionShape
}

export type InlineCompletionDeltaEvent = {
  markdownDelta: string
}

export type InlineCompletionDoneEvent = {
  markdown: string
  previewText: string
  shape: InlineCompletionShape
  diagnostics: InlineCompletionDiagnostic[]
}

export type InlineCompletionErrorEvent = {
  code: string
  message: string
}

export type InlineCompletionStreamHandlers = {
  onMeta?: (event: InlineCompletionMetaEvent) => void
  onDelta?: (event: InlineCompletionDeltaEvent) => void
  onDone?: (event: InlineCompletionDoneEvent) => void
  onError?: (event: InlineCompletionErrorEvent) => void
}

type SseEvent = {
  event: string
  data: string
}

type ErrorPayload = {
  code?: number
  msg?: string
  message?: string
}

type InlineCompletionStreamStats = {
  chunks: number
  chunkChars: number
  events: number
  metaEvents: number
  deltaEvents: number
  doneEvents: number
  errorEvents: number
  deltaChars: number
}

type InlineCompletionDebugGlobal = typeof globalThis & {
  __docpilotInlineCompletionDebugEvents?: Array<{
    time: string
    message: string
    detail?: unknown
  }>
}

const INLINE_COMPLETION_DEBUG_EVENT_LIMIT = 100
const INLINE_COMPLETION_DEBUG_PREVIEW_CHARS = 400
const INLINE_COMPLETION_VERBOSE_LOG_KEY = 'docpilot.inlineCompletion.verboseLogs'

export async function streamInlineCompletion(
  input: InlineCompletionRequest,
  handlers: InlineCompletionStreamHandlers,
  signal: AbortSignal
): Promise<void> {
  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
    'Content-Type': 'application/json'
  }
  const token = globalThis.localStorage?.getItem('docpilot.auth.token')
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch('/inline-completion/stream', {
    method: 'POST',
    headers,
    body: JSON.stringify(input),
    signal
  })

  if (!response.ok) {
    if (response.status === 401) {
      globalThis.localStorage?.removeItem('docpilot.auth.token')
      globalThis.window?.dispatchEvent(new Event('docpilot.auth.invalid'))
    }
    const message = await errorMessage(response)
    throw new ApiError(response.status, message)
  }
  if (!response.body) {
    throw new Error('Inline completion stream is empty')
  }
  debugInlineCompletionStream('response opened', {
    status: response.status,
    contentType: response.headers.get('content-type')
  })

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const stats = emptyStreamStats()
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      stats.chunks += 1
      stats.chunkChars += chunk.length
      debugInlineCompletionStream('chunk received', {
        chars: chunk.length,
        preview: previewText(chunk)
      })
      buffer += chunk
      const parsed = drainSseBuffer(buffer)
      buffer = parsed.remainder
      parsed.events.forEach((event) => {
        recordStreamEvent(event, stats)
        debugInlineCompletionStream('event parsed', {
          event: event.event,
          dataPreview: previewText(event.data)
        })
        dispatchSseEvent(event, handlers)
      })
    }
    buffer += decoder.decode()
    const parsed = drainSseBuffer(buffer, true)
    parsed.events.forEach((event) => {
      recordStreamEvent(event, stats)
      debugInlineCompletionStream('event parsed after stream flush', {
        event: event.event,
        dataPreview: previewText(event.data)
      })
      dispatchSseEvent(event, handlers)
    })
    debugInlineCompletionStream(stats.doneEvents > 0 ? 'stream closed' : 'stream closed before done', stats)
  } finally {
    reader.releaseLock()
  }
}

async function errorMessage(response: Response): Promise<string> {
  const text = await response.text().catch(() => '')
  if (!text) return response.statusText
  try {
    const payload = JSON.parse(text) as ErrorPayload
    return payload.msg ?? payload.message ?? response.statusText
  } catch {
    return text
  }
}

export function drainSseBuffer(buffer: string, flush = false): {
  events: SseEvent[]
  remainder: string
} {
  const normalized = buffer.replace(/\r\n/g, '\n')
  const events: SseEvent[] = []
  let searchFrom = 0

  while (true) {
    const boundary = normalized.indexOf('\n\n', searchFrom)
    if (boundary === -1) break
    const chunk = normalized.slice(searchFrom, boundary)
    const event = parseSseEvent(chunk)
    if (event) events.push(event)
    searchFrom = boundary + 2
  }

  const remainder = normalized.slice(searchFrom)
  if (flush && remainder.trim()) {
    const event = parseSseEvent(remainder)
    if (event) events.push(event)
    return { events, remainder: '' }
  }
  return { events, remainder }
}

function parseSseEvent(chunk: string): SseEvent | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of chunk.split('\n')) {
    if (!line || line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trimStart())
    }
  }
  if (!dataLines.length) return null
  return {
    event,
    data: dataLines.join('\n')
  }
}

function dispatchSseEvent(event: SseEvent, handlers: InlineCompletionStreamHandlers) {
  const payload = JSON.parse(event.data) as unknown
  if (event.event === 'meta') {
    debugInlineCompletionStream('meta received', payload)
    handlers.onMeta?.(payload as InlineCompletionMetaEvent)
  } else if (event.event === 'delta') {
    debugInlineCompletionStream('delta received', payload)
    handlers.onDelta?.(payload as InlineCompletionDeltaEvent)
  } else if (event.event === 'done') {
    debugInlineCompletionStream('done received', payload)
    handlers.onDone?.(payload as InlineCompletionDoneEvent)
  } else if (event.event === 'error') {
    debugInlineCompletionStream('error event received', payload)
    handlers.onError?.(payload as InlineCompletionErrorEvent)
  }
}

function emptyStreamStats(): InlineCompletionStreamStats {
  return {
    chunks: 0,
    chunkChars: 0,
    events: 0,
    metaEvents: 0,
    deltaEvents: 0,
    doneEvents: 0,
    errorEvents: 0,
    deltaChars: 0
  }
}

function recordStreamEvent(event: SseEvent, stats: InlineCompletionStreamStats): void {
  stats.events += 1
  if (event.event === 'meta') {
    stats.metaEvents += 1
    return
  }
  if (event.event === 'done') {
    stats.doneEvents += 1
    return
  }
  if (event.event === 'error') {
    stats.errorEvents += 1
    return
  }
  if (event.event !== 'delta') {
    return
  }
  stats.deltaEvents += 1
  try {
    const payload = JSON.parse(event.data) as { markdownDelta?: unknown }
    if (typeof payload.markdownDelta === 'string') {
      stats.deltaChars += payload.markdownDelta.length
    }
  } catch {
    // Keep stream diagnostics best-effort; dispatchSseEvent will surface malformed JSON.
  }
}

function debugInlineCompletionStream(message: string, detail?: unknown): void {
  const target = globalThis as InlineCompletionDebugGlobal
  const events = target.__docpilotInlineCompletionDebugEvents ?? []
  events.push({
    time: new Date().toISOString(),
    message: `stream:${message}`,
    detail
  })
  if (events.length > INLINE_COMPLETION_DEBUG_EVENT_LIMIT) {
    events.splice(0, events.length - INLINE_COMPLETION_DEBUG_EVENT_LIMIT)
  }
  target.__docpilotInlineCompletionDebugEvents = events

  if (!shouldPrintInlineCompletionStreamLog(message)) return
  if (message === 'delta received') {
    console.info('[inline-completion:delta]', deltaPreview(detail), detail)
    return
  }
  console.info(`[inline-completion:stream] ${message}`, detail)
}

function previewText(value: string): string {
  if (value.length <= INLINE_COMPLETION_DEBUG_PREVIEW_CHARS) return value
  return `${value.slice(0, INLINE_COMPLETION_DEBUG_PREVIEW_CHARS)}...`
}

function shouldPrintInlineCompletionStreamLog(message: string): boolean {
  if (globalThis.localStorage?.getItem(INLINE_COMPLETION_VERBOSE_LOG_KEY) === 'true') {
    return true
  }
  return message === 'response opened'
    || message === 'meta received'
    || message === 'delta received'
    || message === 'done received'
    || message === 'error event received'
    || message === 'stream closed'
    || message === 'stream closed before done'
}

function deltaPreview(detail: unknown): string {
  if (typeof detail === 'object' && detail !== null && 'markdownDelta' in detail) {
    const delta = (detail as { markdownDelta?: unknown }).markdownDelta
    return typeof delta === 'string' ? previewText(delta) : ''
  }
  return ''
}
