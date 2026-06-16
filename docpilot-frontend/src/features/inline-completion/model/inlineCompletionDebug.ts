type InlineCompletionDebugGlobal = typeof globalThis & {
  __docpilotInlineCompletionDebugEvents?: InlineCompletionDebugEvent[]
}

type InlineCompletionDebugEvent = {
  time: string
  scope: string
  message: string
  detail?: unknown
}

const INLINE_COMPLETION_DEBUG_EVENT_LIMIT = 100

/**
 * Keeps recent inline-completion diagnostics available and prints every frontend
 * completion operation so trigger decisions are visible during editing.
 */
export function recordInlineCompletionDebug(
  scope: string,
  message: string,
  detail?: unknown
): void {
  const target = globalThis as InlineCompletionDebugGlobal
  const events = target.__docpilotInlineCompletionDebugEvents ?? []
  events.push({
    time: new Date().toISOString(),
    scope,
    message,
    detail
  })
  if (events.length > INLINE_COMPLETION_DEBUG_EVENT_LIMIT) {
    events.splice(0, events.length - INLINE_COMPLETION_DEBUG_EVENT_LIMIT)
  }
  target.__docpilotInlineCompletionDebugEvents = events

  console.info(`[inline-completion:${scope}] ${message}`, detail)
}
