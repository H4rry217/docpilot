import { describe, expect, it } from 'vitest'
import {
  blockSelectionSignature,
  isPastDragStartDistance,
  rectFromPoints,
  rectsOverlap,
  shouldRenderBlockOverlay,
  type SelectableBlockTarget
} from './blockSelectionGeometry'

function target(element: HTMLElement, id: string): SelectableBlockTarget {
  return {
    element,
    hitRect: { left: 0, top: 0, right: 100, bottom: 100 },
    id,
    rect: { left: 0, top: 0, right: 100, bottom: 100 },
    selectionLeft: 0,
    selectionTop: 0,
    selectionWidth: 100,
    selectionHeight: 20
  }
}

describe('block selection geometry', () => {
  it('builds normalized rectangles and detects overlap', () => {
    expect(rectFromPoints({ x: 10, y: 30 }, { x: 2, y: 4 })).toEqual({
      left: 2,
      top: 4,
      right: 10,
      bottom: 30
    })
    expect(rectsOverlap(
      { left: 0, top: 0, right: 10, bottom: 10 },
      { left: 10, top: 10, right: 20, bottom: 20 }
    )).toBe(true)
    expect(rectsOverlap(
      { left: 0, top: 0, right: 9, bottom: 9 },
      { left: 10, top: 10, right: 20, bottom: 20 }
    )).toBe(false)
  })

  it('uses the drag threshold before starting marquee selection', () => {
    expect(isPastDragStartDistance({ x: 0, y: 0 }, { x: 3, y: 4 })).toBe(false)
    expect(isPastDragStartDistance({ x: 0, y: 0 }, { x: 6, y: 0 })).toBe(true)
  })

  it('suppresses child overlays when a visual container is selected', () => {
    const blockquote = document.createElement('blockquote')
    const paragraph = document.createElement('p')
    blockquote.append(paragraph)
    const container = target(blockquote, 'container')
    const child = target(paragraph, 'child')

    expect(shouldRenderBlockOverlay(container, [container, child])).toBe(true)
    expect(shouldRenderBlockOverlay(child, [container, child])).toBe(false)
  })

  it('creates stable selection signatures', () => {
    expect(blockSelectionSignature([
      {
        blockId: 'a',
        selectionHeight: 20.6,
        selectionLeft: 1.2,
        selectionTop: 2.4,
        selectionWidth: 100.5
      }
    ])).toBe('a:1:2:101:21')
  })
})
