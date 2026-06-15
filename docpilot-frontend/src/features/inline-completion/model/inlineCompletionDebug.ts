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
const INLINE_COMPLETION_VERBOSE_LOG_KEY = 'docpilot.inlineCompletion.verboseLogs'

/**
 * Keeps recent inline-completion diagnostics available for manual inspection without
 * filling the console during normal editing.
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

  if (globalThis.localStorage?.getItem(INLINE_COMPLETION_VERBOSE_LOG_KEY) === 'true') {
    console.info(`[inline-completion:${scope}] ${message}`, detail)
  }
}
