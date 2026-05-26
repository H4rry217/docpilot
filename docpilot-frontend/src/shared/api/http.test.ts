import { afterEach, describe, expect, it, vi } from 'vitest'
import { postJson } from './http'

describe('postJson', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('posts json and returns parsed response', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 0, msg: 'OK', data: { ok: true } }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    )

    await expect(postJson('/workspace/list', {})).resolves.toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledWith('/workspace/list', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{}'
    })
  })

  it('throws ApiError for error payloads', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 40900, msg: 'conflict' }), {
        status: 409,
        headers: { 'Content-Type': 'application/json' }
      })
    )

    await expect(postJson('/document/content/save', { documentId: 'd1' })).rejects.toMatchObject({
      status: 409,
      message: 'conflict',
      payload: {
        code: 40900,
        msg: 'conflict'
      }
    })
  })
})
