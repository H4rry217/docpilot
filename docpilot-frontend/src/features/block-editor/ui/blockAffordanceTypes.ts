import type { BlockMenuBlockInfo } from './blockMenuItems'

export const BLOCK_AFFORDANCE_TRIGGER_HEIGHT_PX = 28
export const BLOCK_AFFORDANCE_CONTEXT_HANDLE_WIDTH_PX = 52
export const BLOCK_AFFORDANCE_INSERT_HANDLE_WIDTH_PX = 28
export const BLOCK_AFFORDANCE_HIGHLIGHT_OUTSET_X_PX = 10
export const BLOCK_AFFORDANCE_HIGHLIGHT_OUTSET_Y_PX = 4

export type BlockAffordanceKind = 'insert' | 'context'

export type BlockAffordanceState = {
  block: BlockMenuBlockInfo
  highlight: {
    height: number
    left: number
    top: number
    width: number
  }
  kind: BlockAffordanceKind
  left: number
  top: number
}

export type AffordanceGeometry = {
  highlightHeight: number
  highlightLeft: number
  highlightTop: number
  highlightWidth: number
  referenceRect: DOMRect
  selectionHeight?: number
  selectionLeft?: number
  selectionTop?: number
  selectionWidth?: number
}
