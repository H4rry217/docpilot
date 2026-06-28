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
import { blockInfoForBlockId } from './blockMenuItems'
import {
  affordanceGeometryFromElement,
  blockElementById,
  blockElementFromSelection,
  hasVisibleRectLike,
  pointInRect,
  targetBlockElement
} from './blockAffordanceGeometry'
import {
  BLOCK_AFFORDANCE_CONTEXT_HANDLE_WIDTH_PX,
  BLOCK_AFFORDANCE_INSERT_HANDLE_WIDTH_PX,
  BLOCK_AFFORDANCE_TRIGGER_HEIGHT_PX,
  type BlockAffordanceKind,
  type BlockAffordanceState
} from './blockAffordanceTypes'
import { useBlockDragHandle } from './useBlockDragHandle'

const BLOCK_HIGHLIGHT_REVEAL_DELAY_MS = 240
const HANDLE_APPROACH_CORRIDOR_RIGHT_PX = 140
const HANDLE_APPROACH_OUTSET_X_PX = 8
const HANDLE_APPROACH_OUTSET_Y_PX = 10

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
    element.style.width = `${kind === 'insert' ? BLOCK_AFFORDANCE_INSERT_HANDLE_WIDTH_PX : BLOCK_AFFORDANCE_CONTEXT_HANDLE_WIDTH_PX}px`
    element.style.height = `${BLOCK_AFFORDANCE_TRIGGER_HEIGHT_PX}px`
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

  const portalElement = useBlockDragHandle({
    activateBlockElement,
    activateBlockPosition,
    clearHighlightRevealTimer,
    dragHandleApproachLockedRef,
    dragHandleElementRef,
    dragHandlePointerBlockElementRef,
    dragHandleReferenceRectRef,
    editor,
    menuOpenRef,
    notifyBlockInteractionStart,
    pendingBlockHighlightRef,
    refreshCurrentGeometry,
    releaseDragHandleApproachLock,
    setMenuOpen
  })

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
