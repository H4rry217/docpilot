import {
  defaultComputePositionConfig,
  DragHandlePlugin,
  normalizeNestedOptions,
  type DragHandleRule
} from '@tiptap/extension-drag-handle'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import type { Transaction } from '@tiptap/pm/state'
import type { Editor } from '@tiptap/react'
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type RefObject
} from 'react'
import {
  setBlockAffordanceHighlight,
  type BlockAffordanceHighlightDecoration
} from '../model/blockAffordanceHighlight'
import { blockIdentityId } from '../model/docpilotBlockIdentity'
import {
  blockInfoForBlockId,
  type BlockMenuBlockInfo
} from './blockMenuItems'
import { eventTargetElement, targetFromElement } from './blockSelectionGeometry'

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

const TRIGGER_HEIGHT_PX = 28
const CONTEXT_HANDLE_WIDTH_PX = 52
const INSERT_HANDLE_WIDTH_PX = 28
const BLOCK_HIGHLIGHT_OUTSET_X_PX = 10
const BLOCK_HIGHLIGHT_OUTSET_Y_PX = 4
const BLOCK_HIGHLIGHT_REVEAL_DELAY_MS = 240
const DRAG_HANDLE_PLUGIN_KEY = 'docpilotBlockDragHandle'
const HANDLE_APPROACH_CORRIDOR_RIGHT_PX = 140
const HANDLE_APPROACH_OUTSET_X_PX = 8
const HANDLE_APPROACH_OUTSET_Y_PX = 10
const LIST_ITEM_NODE_TYPES = new Set(['listItem', 'taskItem'])
const LIST_WRAPPER_NODE_TYPES = new Set(['bulletList', 'orderedList', 'taskList'])
const TABLE_NODE_TYPES = new Set(['table', 'tableRow', 'tableCell', 'tableHeader'])

const docpilotDragHandleRules: DragHandleRule[] = [
  {
    id: 'docpilotExcludeListWrapperTargets',
    evaluate: ({ node }) => {
      if (LIST_WRAPPER_NODE_TYPES.has(node.type.name)) return 1500
      return 0
    }
  },
  {
    id: 'docpilotPreferDeepListItems',
    evaluate: ({ node, depth }) => {
      if (LIST_ITEM_NODE_TYPES.has(node.type.name)) return -(depth * 700)
      return 0
    }
  },
  {
    id: 'docpilotExcludeTableTargets',
    evaluate: ({ node, parent }) => {
      if (TABLE_NODE_TYPES.has(node.type.name)) return 1000
      if (parent && TABLE_NODE_TYPES.has(parent.type.name)) return 1000
      return 0
    }
  }
]

export function useBlockAffordances({
  editor,
  onBlockInteractionStart,
  surfaceRef
}: {
  editor: Editor | null
  onBlockInteractionStart?: () => void
  surfaceRef: RefObject<HTMLDivElement | null>
}) {
  const [affordance, setAffordanceState] = useState<BlockAffordanceState | null>(null)
  const [menuOpen, setMenuOpenState] = useState(false)
  const [portalElement, setPortalElement] = useState<HTMLElement | null>(null)
  const affordanceRef = useRef<BlockAffordanceState | null>(null)
  const dragHandleElementRef = useRef<HTMLDivElement | null>(null)
  const dragHandlePointerBlockElementRef = useRef<HTMLElement | null>(null)
  const dragHandleReferenceRectRef = useRef<DOMRect | null>(null)
  const dragHandleApproachLockedRef = useRef(false)
  const handleHoveringRef = useRef(false)
  const highlightRevealTimerRef = useRef<number | null>(null)
  const menuOpenRef = useRef(false)
  const pendingBlockHighlightRef = useRef<BlockAffordanceHighlightDecoration | null>(null)
  const highlightSignatureRef = useRef('')
  const suppressHighlightTransactionRefreshRef = useRef(false)

  const setBlockHighlight = useCallback((highlight: BlockAffordanceHighlightDecoration | null) => {
    const activeEditor = editor
    if (!activeEditor) return

    const signature = highlight ? blockHighlightSignature(highlight) : ''
    if (highlightSignatureRef.current === signature) return

    highlightSignatureRef.current = signature
    suppressHighlightTransactionRefreshRef.current = true
    try {
      setBlockAffordanceHighlight(activeEditor, highlight ? [highlight] : [])
    } finally {
      suppressHighlightTransactionRefreshRef.current = false
    }
  }, [editor])

  const notifyBlockInteractionStart = useCallback(() => {
    if (!onBlockInteractionStart) return

    suppressHighlightTransactionRefreshRef.current = true
    try {
      onBlockInteractionStart()
    } finally {
      suppressHighlightTransactionRefreshRef.current = false
    }
  }, [onBlockInteractionStart])

  const clearHighlightRevealTimer = useCallback(() => {
    if (highlightRevealTimerRef.current === null) return

    window.clearTimeout(highlightRevealTimerRef.current)
    highlightRevealTimerRef.current = null
  }, [])

  const scheduleBlockHighlightReveal = useCallback(() => {
    handleHoveringRef.current = true
    notifyBlockInteractionStart()
    clearHighlightRevealTimer()

    if (!pendingBlockHighlightRef.current) return

    highlightRevealTimerRef.current = window.setTimeout(() => {
      highlightRevealTimerRef.current = null
      const highlight = pendingBlockHighlightRef.current
      if (!highlight || (!handleHoveringRef.current && !menuOpenRef.current)) return

      setBlockHighlight(highlight)
    }, BLOCK_HIGHLIGHT_REVEAL_DELAY_MS)
  }, [clearHighlightRevealTimer, notifyBlockInteractionStart, setBlockHighlight])

  const hideBlockHighlightFromHandle = useCallback(() => {
    handleHoveringRef.current = false
    clearHighlightRevealTimer()

    if (!menuOpenRef.current) setBlockHighlight(null)
  }, [clearHighlightRevealTimer, setBlockHighlight])

  const setAffordance = useCallback((nextAffordance: BlockAffordanceState | null) => {
    affordanceRef.current = nextAffordance
    setAffordanceState((currentAffordance) => {
      if (sameAffordance(currentAffordance, nextAffordance)) return currentAffordance
      return nextAffordance
    })
  }, [])

  const setDragHandleElementSize = useCallback((kind: BlockAffordanceKind) => {
    const element = dragHandleElementRef.current
    if (!element) return

    element.dataset.affordanceKind = kind
    element.style.width = `${kind === 'insert' ? INSERT_HANDLE_WIDTH_PX : CONTEXT_HANDLE_WIDTH_PX}px`
    element.style.height = `${TRIGGER_HEIGHT_PX}px`
  }, [])

  const dispatchDragHandleLock = useCallback((locked: boolean) => {
    const activeEditor = editor
    if (!activeEditor || activeEditor.isDestroyed) return

    suppressHighlightTransactionRefreshRef.current = true
    try {
      activeEditor.view.dispatch(activeEditor.state.tr.setMeta('lockDragHandle', locked))
    } finally {
      suppressHighlightTransactionRefreshRef.current = false
    }
  }, [editor])

  const setDragHandleApproachLocked = useCallback((locked: boolean) => {
    if (dragHandleApproachLockedRef.current === locked) return

    dragHandleApproachLockedRef.current = locked
    dispatchDragHandleLock(menuOpenRef.current || locked)
  }, [dispatchDragHandleLock])

  const releaseDragHandleApproachLock = useCallback(() => {
    setDragHandleApproachLocked(false)
  }, [setDragHandleApproachLocked])

  const isCurrentHandleApproachPoint = useCallback((clientX: number, clientY: number) => {
    const currentAffordance = affordanceRef.current
    const dragHandleElement = dragHandleElementRef.current
    const referenceRect = dragHandleReferenceRectRef.current
    if (!currentAffordance || !dragHandleElement || !referenceRect) return false

    const handleRect = dragHandleElement.getBoundingClientRect()
    if (!hasVisibleRectLike(handleRect)) return false
    if (pointInRect(clientX, clientY, handleRect)) return false

    const top = Math.min(referenceRect.top, handleRect.top) - HANDLE_APPROACH_OUTSET_Y_PX
    const bottom = Math.max(referenceRect.bottom, handleRect.bottom) + HANDLE_APPROACH_OUTSET_Y_PX
    const left = handleRect.left - HANDLE_APPROACH_OUTSET_X_PX
    const right = Math.max(referenceRect.left, handleRect.right) + HANDLE_APPROACH_CORRIDOR_RIGHT_PX

    return clientX >= left
      && clientX <= right
      && clientY >= top
      && clientY <= bottom
  }, [])

  const activateBlockElement = useCallback((blockElement: HTMLElement | null) => {
    const activeEditor = editor
    const surface = surfaceRef.current
    if (!activeEditor || !surface || !blockElement) {
      dragHandleReferenceRectRef.current = null
      pendingBlockHighlightRef.current = null
      clearHighlightRevealTimer()
      if (!menuOpenRef.current) setBlockHighlight(null)
      if (!menuOpenRef.current) setAffordance(null)
      return
    }

    const blockId = blockElement.dataset.blockId
    if (!blockId) {
      dragHandleReferenceRectRef.current = null
      pendingBlockHighlightRef.current = null
      clearHighlightRevealTimer()
      if (!menuOpenRef.current) setBlockHighlight(null)
      if (!menuOpenRef.current) setAffordance(null)
      return
    }

    const block = blockInfoForBlockId(activeEditor, blockId)
    if (!block) {
      dragHandleReferenceRectRef.current = null
      pendingBlockHighlightRef.current = null
      clearHighlightRevealTimer()
      if (!menuOpenRef.current) setBlockHighlight(null)
      if (!menuOpenRef.current) setAffordance(null)
      return
    }

    const editorRect = activeEditor.view.dom.getBoundingClientRect()
    const surfaceRect = surface.getBoundingClientRect()
    const geometry = affordanceGeometryFromElement({
      blockElement,
      blockId,
      editorRect,
      surfaceRect
    })
    dragHandleReferenceRectRef.current = geometry.referenceRect
    pendingBlockHighlightRef.current = {
      blockId,
      selectionHeight: geometry.selectionHeight,
      selectionLeft: geometry.selectionLeft,
      selectionTop: geometry.selectionTop,
      selectionWidth: geometry.selectionWidth
    }

    if (highlightSignatureRef.current) {
      setBlockHighlight(pendingBlockHighlightRef.current)
    }

    const kind: BlockAffordanceKind = block.isEmptyParagraph ? 'insert' : 'context'
    setDragHandleElementSize(kind)

    setAffordance({
      block,
      highlight: {
        height: geometry.highlightHeight,
        left: geometry.highlightLeft,
        top: geometry.highlightTop,
        width: geometry.highlightWidth
      },
      kind,
      left: 0,
      top: 0
    })
  }, [clearHighlightRevealTimer, editor, setAffordance, setBlockHighlight, setDragHandleElementSize, surfaceRef])

  const activateBlockPosition = useCallback((position: number, node: ProseMirrorNode | null) => {
    const activeEditor = editor
    if (!activeEditor || position < 0) {
      activateBlockElement(null)
      return
    }

    const domNode = activeEditor.view.nodeDOM(position)
    const blockElement = domNode instanceof HTMLElement
      ? targetBlockElement(domNode, activeEditor.view.dom)
      : null

    if (blockElement) {
      activateBlockElement(blockElement)
      return
    }

    const blockId = node ? blockIdentityId(node.attrs) : ''
    activateBlockElement(blockId ? blockElementById(activeEditor, blockId) : null)
  }, [activateBlockElement, editor])

  const refreshFromSelection = useCallback(() => {
    const activeEditor = editor
    if (!activeEditor || menuOpenRef.current) return

    activateBlockElement(blockElementFromSelection(activeEditor))
  }, [activateBlockElement, editor])

  const refreshCurrentGeometry = useCallback(() => {
    const activeEditor = editor
    const currentAffordance = affordanceRef.current
    if (!activeEditor || !currentAffordance) {
      refreshFromSelection()
      return
    }

    activateBlockElement(blockElementById(activeEditor, currentAffordance.block.blockId))
  }, [activateBlockElement, editor, refreshFromSelection])

  const setMenuOpen = useCallback((open: boolean) => {
    if (open) notifyBlockInteractionStart()
    menuOpenRef.current = open
    setMenuOpenState(open)
    if (open) {
      clearHighlightRevealTimer()
      setBlockHighlight(pendingBlockHighlightRef.current)
    } else if (!handleHoveringRef.current) {
      clearHighlightRevealTimer()
      setBlockHighlight(null)
    }
    dispatchDragHandleLock(open || dragHandleApproachLockedRef.current)
  }, [clearHighlightRevealTimer, dispatchDragHandleLock, notifyBlockInteractionStart, setBlockHighlight])

  const closeMenuAndClearHighlight = useCallback(() => {
    handleHoveringRef.current = false
    menuOpenRef.current = false
    clearHighlightRevealTimer()
    setMenuOpenState(false)
    setBlockHighlight(null)
    dispatchDragHandleLock(dragHandleApproachLockedRef.current)
  }, [clearHighlightRevealTimer, dispatchDragHandleLock, setBlockHighlight])

  useEffect(() => {
    if (!editor) {
      setAffordance(null)
      return
    }

    const activeEditor: Editor = editor

    function handleEditorUpdate({ transaction }: { transaction?: Transaction } = {}) {
      if (suppressHighlightTransactionRefreshRef.current) return

      if (menuOpenRef.current && transaction?.docChanged) {
        closeMenuAndClearHighlight()
        window.requestAnimationFrame(refreshFromSelection)
        return
      }

      if (menuOpenRef.current) {
        refreshCurrentGeometry()
        return
      }
      refreshFromSelection()
    }

    editor.on('selectionUpdate', handleEditorUpdate)
    editor.on('transaction', handleEditorUpdate)
    activeEditor.view.dom.addEventListener('mousemove', handleEditorMouseMoveCapture, true)
    activeEditor.view.dom.addEventListener('mouseleave', handleEditorMouseLeaveCapture, true)
    window.addEventListener('resize', refreshCurrentGeometry)
    window.addEventListener('scroll', refreshCurrentGeometry, true)

    const frame = window.requestAnimationFrame(refreshFromSelection)

    return () => {
      activeEditor.off('selectionUpdate', handleEditorUpdate)
      activeEditor.off('transaction', handleEditorUpdate)
      activeEditor.view.dom.removeEventListener('mousemove', handleEditorMouseMoveCapture, true)
      activeEditor.view.dom.removeEventListener('mouseleave', handleEditorMouseLeaveCapture, true)
      window.removeEventListener('resize', refreshCurrentGeometry)
      window.removeEventListener('scroll', refreshCurrentGeometry, true)
      window.cancelAnimationFrame(frame)
      clearHighlightRevealTimer()
      pendingBlockHighlightRef.current = null
      setBlockHighlight(null)
    }

    function handleEditorMouseMoveCapture(event: MouseEvent) {
      const pointerBlockElement = targetBlockElement(event.target, activeEditor.view.dom)
      if (menuOpenRef.current) {
        const currentBlockId = affordanceRef.current?.block.blockId ?? ''
        const pointerBlockId = pointerBlockElement?.dataset.blockId ?? ''
        if (pointerBlockElement && pointerBlockId && pointerBlockId !== currentBlockId) {
          handleHoveringRef.current = false
          setMenuOpen(false)
          dragHandlePointerBlockElementRef.current = pointerBlockElement
          activateBlockElement(pointerBlockElement)
        }
        return
      }
      const isApproaching = isCurrentHandleApproachPoint(event.clientX, event.clientY)
      setDragHandleApproachLocked(isApproaching)
      if (!isApproaching) {
        dragHandlePointerBlockElementRef.current = pointerBlockElement
      }
    }

    function handleEditorMouseLeaveCapture(event: MouseEvent) {
      const dragHandleElement = dragHandleElementRef.current
      if (dragHandleElement && event.relatedTarget instanceof Node && dragHandleElement.contains(event.relatedTarget)) {
        return
      }

      setDragHandleApproachLocked(false)
      dragHandlePointerBlockElementRef.current = null
    }
  }, [
    activateBlockElement,
    clearHighlightRevealTimer,
    closeMenuAndClearHighlight,
    editor,
    isCurrentHandleApproachPoint,
    refreshCurrentGeometry,
    refreshFromSelection,
    setAffordance,
    setBlockHighlight,
    setMenuOpen,
    setDragHandleApproachLocked
  ])

  useEffect(() => {
    if (!editor) return
    const activeEditor: Editor = editor
    if (!activeEditor || activeEditor.isDestroyed) return

    const element = document.createElement('div')
    element.className = 'block-affordance-drag-handle'
    element.style.position = 'absolute'
    element.style.visibility = 'hidden'
    element.style.width = `${CONTEXT_HANDLE_WIDTH_PX}px`
    element.style.height = `${TRIGGER_HEIGHT_PX}px`
    element.dataset.dragging = 'false'
    element.addEventListener('mouseenter', releaseDragHandleApproachLock)
    dragHandleElementRef.current = element
    setPortalElement(element)

    const { plugin, unbind } = DragHandlePlugin({
      editor: activeEditor,
      element,
      pluginKey: DRAG_HANDLE_PLUGIN_KEY,
      computePositionConfig: defaultComputePositionConfig,
      getReferencedVirtualElement: () => {
        const rect = dragHandleReferenceRectRef.current
        return rect ? { getBoundingClientRect: () => rect } : null
      },
      nestedOptions: normalizeNestedOptions({
        defaultRules: true,
        edgeDetection: 'none',
        rules: docpilotDragHandleRules
      }),
      onNodeChange: ({ node, pos }) => {
        if (menuOpenRef.current || dragHandleApproachLockedRef.current) return
        const pointerBlockElement = dragHandlePointerBlockElementRef.current
        if (pointerBlockElement && activeEditor.view.dom.contains(pointerBlockElement)) {
          activateBlockElement(pointerBlockElement)
          return
        }
        activateBlockPosition(pos, node)
      },
      onElementDragStart: () => {
        notifyBlockInteractionStart()
        setMenuOpen(false)
      },
      onElementDragEnd: () => {
        window.requestAnimationFrame(refreshCurrentGeometry)
      }
    })

    activeEditor.registerPlugin(plugin)

    return () => {
      if (!activeEditor.isDestroyed) {
        activeEditor.unregisterPlugin(DRAG_HANDLE_PLUGIN_KEY)
      }
      unbind()
      element.removeEventListener('mouseenter', releaseDragHandleApproachLock)
      dragHandleReferenceRectRef.current = null
      dragHandleElementRef.current = null
      dragHandlePointerBlockElementRef.current = null
      dragHandleApproachLockedRef.current = false
      pendingBlockHighlightRef.current = null
      clearHighlightRevealTimer()
      setPortalElement(null)
    }
  }, [
    activateBlockPosition,
    clearHighlightRevealTimer,
    editor,
    notifyBlockInteractionStart,
    refreshCurrentGeometry,
    releaseDragHandleApproachLock,
    setMenuOpen
  ])

  useEffect(() => {
    if (menuOpen) return
    const frame = window.requestAnimationFrame(refreshFromSelection)
    return () => window.cancelAnimationFrame(frame)
  }, [menuOpen, refreshFromSelection])

  return {
    affordance,
    hideBlockHighlightFromHandle,
    menuOpen,
    portalElement,
    scheduleBlockHighlightReveal,
    setMenuOpen
  }
}

type AffordanceGeometry = {
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

function affordanceGeometryFromElement({
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
    : Number.isFinite(lineHeight) ? lineHeight : TRIGGER_HEIGHT_PX
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
  const highlightLeft = visualLeft - surfaceRect.left - BLOCK_HIGHLIGHT_OUTSET_X_PX
  const highlightTop = visualTop - surfaceRect.top - BLOCK_HIGHLIGHT_OUTSET_Y_PX
  const highlightWidth = visualWidth + (BLOCK_HIGHLIGHT_OUTSET_X_PX * 2)
  const highlightHeight = visualHeight + (BLOCK_HIGHLIGHT_OUTSET_Y_PX * 2)

  return {
    highlightHeight: Math.max(TRIGGER_HEIGHT_PX, Math.round(highlightHeight)),
    highlightLeft: Math.round(highlightLeft),
    highlightTop: Math.round(highlightTop),
    highlightWidth: Math.max(TRIGGER_HEIGHT_PX, Math.round(highlightWidth)),
    referenceRect: domRectLike({
      height: Math.max(TRIGGER_HEIGHT_PX, highlightHeight),
      left: visualLeft - BLOCK_HIGHLIGHT_OUTSET_X_PX,
      top: visualTop - BLOCK_HIGHLIGHT_OUTSET_Y_PX,
      width: Math.max(TRIGGER_HEIGHT_PX, highlightWidth)
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

function hasVisibleRectLike(rect: DOMRect): boolean {
  return rect.width > 0 && rect.height > 0
}

function pointInRect(clientX: number, clientY: number, rect: DOMRect): boolean {
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

function sameAffordance(left: BlockAffordanceState | null, right: BlockAffordanceState | null): boolean {
  if (left === right) return true
  if (!left || !right) return false

  return left.block.blockId === right.block.blockId
    && left.block.typeName === right.block.typeName
    && left.block.isEmptyParagraph === right.block.isEmptyParagraph
    && left.highlight.height === right.highlight.height
    && left.highlight.left === right.highlight.left
    && left.highlight.top === right.highlight.top
    && left.highlight.width === right.highlight.width
    && left.kind === right.kind
    && left.left === right.left
    && left.top === right.top
}

function blockHighlightSignature(highlight: BlockAffordanceHighlightDecoration): string {
  return [
    highlight.blockId,
    roundedSignaturePart(highlight.selectionLeft),
    roundedSignaturePart(highlight.selectionTop),
    roundedSignaturePart(highlight.selectionWidth),
    roundedSignaturePart(highlight.selectionHeight)
  ].join(':')
}

function roundedSignaturePart(value: number | undefined): string {
  return Number.isFinite(value) ? `${Math.round(value ?? 0)}` : ''
}

function blockElementFromSelection(editor: Editor): HTMLElement | null {
  const selection = editor.state.selection
  const domAtPosition = editor.view.domAtPos(selection.from)
  const element = domAtPosition.node instanceof Element
    ? domAtPosition.node
    : domAtPosition.node.parentElement

  return targetBlockElement(element, editor.view.dom)
}

function targetBlockElement(target: EventTarget | null, editorDom: HTMLElement): HTMLElement | null {
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

function blockElementById(editor: Editor, blockId: string): HTMLElement | null {
  return editor.view.dom.querySelector<HTMLElement>(blockIdSelector(blockId))
}

function blockIdSelector(blockId: string): string {
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
    return `[data-block-id="${CSS.escape(blockId)}"]`
  }

  return `[data-block-id="${blockId.replaceAll('\\', '\\\\').replaceAll('"', '\\"')}"]`
}
