import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiPath, postJson } from './http'

describe('postJson', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('prefixes backend controller paths with api', () => {
    expect(apiPath('/workspace/list')).toBe('/api/workspace/list')
    expect(apiPath('document/get')).toBe('/api/document/get')
    expect(apiPath('/api/user/settings/get')).toBe('/api/user/settings/get')
  })

  it('posts json and returns parsed response', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 0, msg: 'OK', data: { ok: true } }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      })
    )

    await expect(postJson('/workspace/list', {})).resolves.toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledWith('/api/workspace/list', {
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
