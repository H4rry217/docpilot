import type { Editor } from '@tiptap/react'
import {
  BLOCK_AFFORDANCE_HIGHLIGHT_OUTSET_X_PX,
  BLOCK_AFFORDANCE_HIGHLIGHT_OUTSET_Y_PX,
  BLOCK_AFFORDANCE_TRIGGER_HEIGHT_PX,
  type AffordanceGeometry
} from './blockAffordanceTypes'
import { eventTargetElement, targetFromElement } from './blockSelectionGeometry'

export function affordanceGeometryFromElement({
  blockElement,
  blockId,
  editorRect,
  surfaceRect
}: {
  blockElement: HTMLElement
  blockId: string
  editorRect: DOMRect
  surfaceRect: DOMRect
}): AffordanceGeometry {
  const blockRect = blockElement.getBoundingClientRect()
  const selectionTarget = targetFromElement(blockElement, editorRect, blockId)
  const rowRect = firstListAffordanceRowRect(blockElement)
  const style = window.getComputedStyle(blockElement)
  const lineHeight = Number.parseFloat(style.lineHeight)
  const fallbackWidth = blockRect.width > 0 ? blockRect.width : editorRect.width
  const fallbackHeight = blockRect.height > 0
    ? blockRect.height
    : Number.isFinite(lineHeight) ? lineHeight : BLOCK_AFFORDANCE_TRIGGER_HEIGHT_PX
  const selectionLeft = Number.isFinite(selectionTarget?.selectionLeft)
    ? selectionTarget?.selectionLeft
    : undefined
  const selectionWidth = Number.isFinite(selectionTarget?.selectionWidth)
    ? selectionTarget?.selectionWidth
    : undefined
  const selectionTop = rowRect
    ? rowRect.top - blockRect.top
    : selectionTarget?.selectionTop
  const selectionHeight = rowRect
    ? rowRect.height
    : selectionTarget?.selectionHeight
  const visualTop = Number.isFinite(selectionTop)
    ? blockRect.top + (selectionTop ?? 0)
    : blockRect.top
  const visualHeight = Number.isFinite(selectionHeight)
    ? selectionHeight ?? fallbackHeight
    : fallbackHeight
  const visualLeft = Number.isFinite(selectionLeft)
    ? blockRect.left + (selectionLeft ?? 0)
    : blockRect.left
  const visualWidth = Number.isFinite(selectionWidth)
    ? selectionWidth ?? fallbackWidth
    : fallbackWidth
  const highlightLeft = visualLeft - surfaceRect.left - BLOCK_AFFORDANCE_HIGHLIGHT_OUTSET_X_PX
  const highlightTop = visualTop - surfaceRect.top - BLOCK_AFFORDANCE_HIGHLIGHT_OUTSET_Y_PX
  const highlightWidth = visualWidth + (BLOCK_AFFORDANCE_HIGHLIGHT_OUTSET_X_PX * 2)
  const highlightHeight = visualHeight + (BLOCK_AFFORDANCE_HIGHLIGHT_OUTSET_Y_PX * 2)

  return {
    highlightHeight: Math.max(BLOCK_AFFORDANCE_TRIGGER_HEIGHT_PX, Math.round(highlightHeight)),
    highlightLeft: Math.round(highlightLeft),
    highlightTop: Math.round(highlightTop),
    highlightWidth: Math.max(BLOCK_AFFORDANCE_TRIGGER_HEIGHT_PX, Math.round(highlightWidth)),
    referenceRect: domRectLike({
      height: Math.max(BLOCK_AFFORDANCE_TRIGGER_HEIGHT_PX, highlightHeight),
      left: visualLeft - BLOCK_AFFORDANCE_HIGHLIGHT_OUTSET_X_PX,
      top: visualTop - BLOCK_AFFORDANCE_HIGHLIGHT_OUTSET_Y_PX,
      width: Math.max(BLOCK_AFFORDANCE_TRIGGER_HEIGHT_PX, highlightWidth)
    }),
    selectionHeight,
    selectionLeft,
    selectionTop,
    selectionWidth
  }
}

function firstListAffordanceRowRect(blockElement: HTMLElement): DOMRect | null {
  if (blockElement.tagName === 'UL' || blockElement.tagName === 'OL') {
    const firstItem = Array.from(blockElement.children)
      .find((child): child is HTMLElement => child instanceof HTMLElement && child.tagName === 'LI')
    return firstItem ? firstListItemRowRect(firstItem) : null
  }

  if (blockElement.tagName === 'LI') {
    return firstListItemRowRect(blockElement)
  }

  return null
}

function firstListItemRowRect(listItem: HTMLElement): DOMRect | null {
  const firstRow = Array.from(listItem.children)
    .find((child): child is HTMLElement => (
      child instanceof HTMLElement
      && child.tagName !== 'UL'
      && child.tagName !== 'OL'
      && hasVisibleRect(child)
    ))
  if (firstRow) return firstRow.getBoundingClientRect()

  return hasVisibleRect(listItem) ? listItem.getBoundingClientRect() : null
}

function hasVisibleRect(element: HTMLElement): boolean {
  const rect = element.getBoundingClientRect()
  return rect.width > 0 && rect.height > 0
}

export function hasVisibleRectLike(rect: DOMRect): boolean {
  return rect.width > 0 && rect.height > 0
}

export function pointInRect(clientX: number, clientY: number, rect: DOMRect): boolean {
  return clientX >= rect.left
    && clientX <= rect.right
    && clientY >= rect.top
    && clientY <= rect.bottom
}

function domRectLike({
  height,
  left,
  top,
  width
}: {
  height: number
  left: number
  top: number
  width: number
}): DOMRect {
  const right = left + width
  const bottom = top + height
  return {
    bottom,
    height,
    left,
    right,
    top,
    width,
    x: left,
    y: top,
    toJSON: () => ({ bottom, height, left, right, top, width, x: left, y: top })
  } as DOMRect
}

export function blockElementFromSelection(editor: Editor): HTMLElement | null {
  const selection = editor.state.selection
  const domAtPosition = editor.view.domAtPos(selection.from)
  const element = domAtPosition.node instanceof Element
    ? domAtPosition.node
    : domAtPosition.node.parentElement

  return targetBlockElement(element, editor.view.dom)
}

export function targetBlockElement(target: EventTarget | null, editorDom: HTMLElement): HTMLElement | null {
  const element = eventTargetElement(target)
  if (!element || !editorDom.contains(element)) return null
  if (element.closest('.block-affordance-layer')) return null
  if (element.closest('.table-hover-indicator-layer, .table-divider-layer')) return null
  if (element.closest('.tableWrapper, table, td, th')) return null

  const visualContainer = element.closest<HTMLElement>([
    'li[data-block-id]',
    'blockquote[data-block-id]',
    'aside.docpilot-callout[data-block-id]',
    'details.docpilot-callout[data-block-id]'
  ].join(','))
  if (visualContainer && editorDom.contains(visualContainer)) return visualContainer

  const blockElement = element.closest<HTMLElement>('[data-block-id]')
  return blockElement && editorDom.contains(blockElement) ? blockElement : null
}

export function blockElementById(editor: Editor, blockId: string): HTMLElement | null {
  return editor.view.dom.querySelector<HTMLElement>(blockIdSelector(blockId))
}

function blockIdSelector(blockId: string): string {
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
    return `[data-block-id="${CSS.escape(blockId)}"]`
  }

  return `[data-block-id="${blockId.replaceAll('\\', '\\\\').replaceAll('"', '\\"')}"]`
}
