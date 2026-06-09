import { describe, expect, it, vi } from 'vitest'
import { canvasToPngBlob } from './svgToPngBlob'

describe('canvasToPngBlob', () => {
  it('rejects when the canvas cannot produce a blob', async () => {
    const canvas = document.createElement('canvas')
    canvas.toBlob = vi.fn((callback: BlobCallback) => callback(null))

    await expect(canvasToPngBlob(canvas)).rejects.toThrow('Failed to create diagram image')
  })
})
