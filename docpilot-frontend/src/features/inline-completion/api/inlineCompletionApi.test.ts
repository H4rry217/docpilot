import { describe, expect, it } from 'vitest'
import { drainSseBuffer } from './inlineCompletionApi'

describe('inline completion SSE parser', () => {
  it('keeps partial events until the next chunk completes them', () => {
    const first = drainSseBuffer('event: delta\ndata: {"markdownDelta":"你')

    expect(first.events).toEqual([])
    expect(first.remainder).toContain('markdownDelta')

    const second = drainSseBuffer(`${first.remainder}好"}\n\n`)

    expect(second.events).toEqual([
      {
        event: 'delta',
        data: '{"markdownDelta":"你好"}'
      }
    ])
    expect(second.remainder).toBe('')
  })
})
