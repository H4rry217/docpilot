import { afterEach, describe, expect, it, vi } from 'vitest'
import { createClientMutationId } from './clientMutationId'

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('createClientMutationId', () => {
  it('uses crypto.randomUUID when it is available', () => {
    const randomUUID = vi.fn(() => 'fixed-random-uuid')
    vi.stubGlobal('crypto', { randomUUID })

    expect(createClientMutationId()).toBe('fixed-random-uuid')
    expect(randomUUID).toHaveBeenCalledTimes(1)
  })

  it('generates a UUID v4 with getRandomValues when randomUUID is unavailable', () => {
    const getRandomValues = vi.fn((bytes: Uint8Array) => {
      bytes.set([0, 1, 2, 3, 4, 5, 6, 7, 128, 9, 10, 11, 12, 13, 14, 15])
      return bytes
    })
    vi.stubGlobal('crypto', { getRandomValues })

    const mutationId = createClientMutationId()

    expect(mutationId).toBe('00010203-0405-4607-8009-0a0b0c0d0e0f')
    expect(getRandomValues).toHaveBeenCalledTimes(1)
  })

  it('falls back to a deterministic local id without secure crypto APIs', () => {
    vi.stubGlobal('crypto', undefined)
    vi.spyOn(Date, 'now').mockReturnValue(1710000000000)

    expect(createClientMutationId()).toMatch(/^mutation-ltk9ukg0-[a-z0-9]+$/)
  })
})
