import type { Editor } from '@tiptap/react'
import type { BlockSelectionDecoration } from '../model/blockSelection'

export type Point = {
  x: number
  y: number
}

export type Rect = {
  left: number
  top: number
  right: number
  bottom: number
}

export type SelectableBlockTarget = {
  id: string
  element: HTMLElement
  hitRect: Rect
  rect: Rect
  selectionLeft: number
  selectionTop?: number
  selectionWidth: number
  selectionHeight?: number
}

const BLOCK_MARQUEE_START_DISTANCE_PX = 6
const BLOCK_SELECTION_COMPACT_END_OUTSET_PX = 2
const BLOCK_SELECTION_CLOSE_BLOCK_GAP_PX = 18

export function rectFromPoints(start: Point, end: Point): Rect {
  return {
    left: Math.min(start.x, end.x),
    top: Math.min(start.y, end.y),
    right: Math.max(start.x, end.x),
    bottom: Math.max(start.y, end.y)
  }
}

export function rectsOverlap(left: Rect, right: Rect): boolean {
  return left.left <= right.right
    && left.right >= right.left
    && left.top <= right.bottom
    && left.bottom >= right.top
}

export function pointFromClientPoint(point: Point, element: HTMLElement): Point {
  const rect = element.getBoundingClientRect()
  return {
    x: point.x - rect.left,
    y: point.y - rect.top
  }
}

export function pointFromElement(event: PointerEvent | React.PointerEvent, element: HTMLElement): Point {
  return pointFromClientPoint({ x: event.clientX, y: event.clientY }, element)
}

export function clientPointFromElementPoint(point: Point, element: HTMLElement): Point {
  const rect = element.getBoundingClientRect()
  return {
    x: rect.left + point.x,
    y: rect.top + point.y
  }
}

export function isEditorContentSelectionStart(target: HTMLElement, editorDom: HTMLElement): boolean {
  if (!editorDom.contains(target)) return false
  return Boolean(target.closest('[data-block-id], .tableWrapper, table, td, th'))
}

export function isInteractiveSelectionTarget(target: HTMLElement): boolean {
  return Boolean(target.closest([
    'button',
    'input',
    'select',
    'textarea',
    '[contenteditable="false"]',
    '.cm-editor',
    '.image-node',
    '.code-block-control',
    '.code-block-resize-handle',
    '.image-node-toolbar',
    '.image-node-caption',
    '.image-resize-handle',
    '.column-resize-handle',
    '.html-block-controls'
  ].join(',')))
}

export function isSelectableBlockElement(element: HTMLElement, editorDom: HTMLElement): boolean {
  const blockId = element.dataset.blockId
  if (!blockId || !editorDom.contains(element)) return false
  const style = window.getComputedStyle(element)
  if (style.display === 'none' || style.visibility === 'hidden') return false
  const rect = element.getBoundingClientRect()
  return rect.width > 0 && rect.height > 0
}

export function targetFromElement(element: HTMLElement, editorRect: DOMRect): SelectableBlockTarget | null {
  const rect = element.getBoundingClientRect()
  const blockId = element.dataset.blockId
  const blockRect = {
    left: rect.left,
    top: rect.top,
    right: rect.right,
    bottom: rect.bottom
  }

  if (!blockId || blockRect.right <= blockRect.left || blockRect.bottom <= blockRect.top) {
    return null
  }

  const imageNode = element.querySelector<HTMLElement>('.image-node')
  const imageRect = imageNode?.getBoundingClientRect()
  const selectionTop = imageRect ? imageRect.top - rect.top : undefined
  const selectionHeight = imageRect ? imageRect.height : undefined

  return {
    element,
    id: blockId,
    rect: blockRect,
    hitRect: {
      left: Math.min(editorRect.left, blockRect.left),
      top: blockRect.top,
      right: Math.max(editorRect.right, blockRect.right),
      bottom: blockRect.bottom
    },
    selectionLeft: editorRect.left - blockRect.left,
    selectionTop,
    selectionWidth: editorRect.width,
    selectionHeight
  }
}

export function withBlockRowHitRects(targets: SelectableBlockTarget[], editorRect: DOMRect): SelectableBlockTarget[] {
  const sortedTargets = [...targets].sort((left, right) => {
    if (left.rect.top !== right.rect.top) return left.rect.top - right.rect.top
    return left.rect.left - right.rect.left
  })

  return sortedTargets.map((target, index) => {
    const previous = sortedTargets[index - 1]
    const next = sortedTargets[index + 1]
    const top = previous
      ? Math.max(editorRect.top, Math.min(target.rect.top, (previous.rect.bottom + target.rect.top) / 2))
      : Math.max(editorRect.top, target.rect.top)
    const bottom = next
      ? Math.min(editorRect.bottom, Math.max(target.rect.bottom, (target.rect.bottom + next.rect.top) / 2))
      : Math.min(editorRect.bottom, target.rect.bottom)

    return {
      ...target,
      hitRect: {
        left: editorRect.left,
        top,
        right: editorRect.right,
        bottom: Math.max(bottom, top)
      }
    }
  })
}

export function selectableBlockTargets(editor: Editor): SelectableBlockTarget[] {
  const editorDom = editor.view.dom
  const editorRect = editorDom.getBoundingClientRect()
  const seenBlockIds = new Set<string>()
  const targets: SelectableBlockTarget[] = []

  editor.state.doc.descendants((node, position) => {
    const blockId = node.attrs.blockId
    if (typeof blockId !== 'string' || !blockId || seenBlockIds.has(blockId)) {
      return true
    }

    const domNode = editor.view.nodeDOM(position)
    if (!(domNode instanceof HTMLElement) || !isSelectableBlockElement(domNode, editorDom)) {
      return true
    }

    const target = targetFromElement(domNode, editorRect)
    if (target) {
      seenBlockIds.add(blockId)
      targets.push(target)
    }

    return true
  })

  for (const target of fallbackSelectableBlockTargets(editorDom)) {
    if (seenBlockIds.has(target.id)) continue
    seenBlockIds.add(target.id)
    targets.push(target)
  }

  return withBlockRowHitRects(targets, editorRect)
}

export function fallbackSelectableBlockTargets(editorDom: HTMLElement): SelectableBlockTarget[] {
  const editorRect = editorDom.getBoundingClientRect()
  return Array.from(editorDom.querySelectorAll<HTMLElement>('[data-block-id]'))
    .filter((element) => isSelectableBlockElement(element, editorDom))
    .map((element) => targetFromElement(element, editorRect))
    .filter((target): target is SelectableBlockTarget => target !== null)
}

export function isPastDragStartDistance(origin: Point, current: Point): boolean {
  const deltaX = current.x - origin.x
  const deltaY = current.y - origin.y
  return deltaX * deltaX + deltaY * deltaY >= BLOCK_MARQUEE_START_DISTANCE_PX * BLOCK_MARQUEE_START_DISTANCE_PX
}

export function isVisualContainerTarget(target: SelectableBlockTarget): boolean {
  return target.element.tagName === 'BLOCKQUOTE'
    || target.element.tagName === 'LI'
    || target.element.tagName === 'DETAILS'
    || target.element.matches('aside.docpilot-callout')
}

export function shouldRenderBlockOverlay(target: SelectableBlockTarget, targets: SelectableBlockTarget[]): boolean {
  const selectedContainerAncestor = targets.some((otherTarget) => (
    otherTarget !== target
    && isVisualContainerTarget(otherTarget)
    && otherTarget.element.contains(target.element)
  ))
  if (selectedContainerAncestor) return false

  if (isVisualContainerTarget(target)) return true

  return !targets.some((otherTarget) => (
    otherTarget !== target
    && target.element.contains(otherTarget.element)
  ))
}

export function visualBlockSelectionTargets(targets: SelectableBlockTarget[]): SelectableBlockTarget[] {
  return targets.filter((target) => shouldRenderBlockOverlay(target, targets))
}

export function selectionDecorationFromTarget(target: SelectableBlockTarget): BlockSelectionDecoration {
  return {
    blockId: target.id,
    selectionLeft: target.selectionLeft,
    selectionTop: target.selectionTop,
    selectionWidth: target.selectionWidth,
    selectionHeight: target.selectionHeight
  }
}

function isFixedHeightSelectionTarget(target: SelectableBlockTarget): boolean {
  return Number.isFinite(target.selectionHeight)
}

export function blockSelectionDecorationsFromTargets(targets: SelectableBlockTarget[]): BlockSelectionDecoration[] {
  const decorations = targets.map(selectionDecorationFromTarget)

  for (let index = 1; index < targets.length; index += 1) {
    const previousTarget = targets[index - 1]
    const currentTarget = targets[index]
    const isCloseToPrevious = currentTarget.rect.top - previousTarget.rect.bottom <= BLOCK_SELECTION_CLOSE_BLOCK_GAP_PX
    if (!isCloseToPrevious) continue
    if (!isFixedHeightSelectionTarget(previousTarget) && !isFixedHeightSelectionTarget(currentTarget)) continue

    decorations[index - 1] = {
      ...decorations[index - 1],
      selectionBlockEndOutset: BLOCK_SELECTION_COMPACT_END_OUTSET_PX
    }
  }

  return decorations
}

export function blockSelectionSignature(blockSelections: BlockSelectionDecoration[]): string {
  return blockSelections
    .map((blockSelection) => [
      blockSelection.blockId,
      Math.round(blockSelection.selectionLeft ?? 0),
      Math.round(blockSelection.selectionTop ?? 0),
      Math.round(blockSelection.selectionWidth ?? 0),
      Math.round(blockSelection.selectionHeight ?? 0)
    ].join(':'))
    .join('|')
}
