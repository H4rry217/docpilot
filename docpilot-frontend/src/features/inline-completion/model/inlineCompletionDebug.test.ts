import { afterEach, describe, expect, it, vi } from 'vitest'
import { recordInlineCompletionDebug } from './inlineCompletionDebug'

type InlineCompletionDebugGlobal = typeof globalThis & {
  __docpilotInlineCompletionDebugEvents?: unknown[]
}

afterEach(() => {
  delete (globalThis as InlineCompletionDebugGlobal).__docpilotInlineCompletionDebugEvents
  vi.restoreAllMocks()
})

describe('recordInlineCompletionDebug', () => {
  it('prints frontend completion operations without a verbose flag', () => {
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => undefined)

    recordInlineCompletionDebug('runtime', 'editor activity observed', {
      source: 'transaction'
    })

    expect(infoSpy).toHaveBeenCalledWith(
      '[inline-completion:runtime] editor activity observed',
      { source: 'transaction' }
    )
  })

  it('keeps recent debug events for manual inspection', () => {
    vi.spyOn(console, 'info').mockImplementation(() => undefined)

    recordInlineCompletionDebug('api', 'request started', {
      documentId: 'doc-1'
    })

    expect((globalThis as InlineCompletionDebugGlobal).__docpilotInlineCompletionDebugEvents)
      .toEqual([
        expect.objectContaining({
          scope: 'api',
          message: 'request started',
          detail: { documentId: 'doc-1' }
        })
      ])
  })
})
