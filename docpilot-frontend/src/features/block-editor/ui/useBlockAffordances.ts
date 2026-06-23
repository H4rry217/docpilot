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
import {
  blockInfoForBlockId,
  type BlockMenuBlockInfo
} from './blockMenuItems'
import {
  eventTargetElement,
  targetFromElement
} from './blockSelectionGeometry'

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

const CONTEXT_TRIGGER_WIDTH_PX = 54
const INSERT_TRIGGER_WIDTH_PX = 28
const TRIGGER_HEIGHT_PX = 28
const TRIGGER_GAP_PX = 8
const SURFACE_EDGE_GAP_PX = 6
const BLOCK_HIGHLIGHT_OUTSET_X_PX = 10
const POINTER_BRIDGE_PADDING_PX = 8

export function useBlockAffordances({
  editor,
  surfaceRef
}: {
  editor: Editor | null
  surfaceRef: RefObject<HTMLDivElement | null>
}) {
  const [affordance, setAffordanceState] = useState<BlockAffordanceState | null>(null)
  const [menuOpen, setMenuOpenState] = useState(false)
  const affordanceRef = useRef<BlockAffordanceState | null>(null)
  const menuOpenRef = useRef(false)
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

  const setAffordance = useCallback((nextAffordance: BlockAffordanceState | null) => {
    affordanceRef.current = nextAffordance
    setAffordanceState((currentAffordance) => {
      if (sameAffordance(currentAffordance, nextAffordance)) return currentAffordance
      return nextAffordance
    })
  }, [])

  const activateBlockElement = useCallback((blockElement: HTMLElement | null) => {
    const activeEditor = editor
    const surface = surfaceRef.current
    if (!activeEditor || !surface || !blockElement) {
      if (!menuOpenRef.current) setBlockHighlight(null)
      if (!menuOpenRef.current) setAffordance(null)
      return
    }

    const blockId = blockElement.dataset.blockId
    if (!blockId) {
      if (!menuOpenRef.current) setBlockHighlight(null)
      if (!menuOpenRef.current) setAffordance(null)
      return
    }

    const block = blockInfoForBlockId(activeEditor, blockId)
    if (!block) {
      if (!menuOpenRef.current) setBlockHighlight(null)
      if (!menuOpenRef.current) setAffordance(null)
      return
    }

    const editorRect = activeEditor.view.dom.getBoundingClientRect()
    const surfaceRect = surface.getBoundingClientRect()
    const blockRect = blockElement.getBoundingClientRect()
    const selectionTarget = targetFromElement(blockElement, editorRect, blockId)
    const triggerWidth = block.isEmptyParagraph ? INSERT_TRIGGER_WIDTH_PX : CONTEXT_TRIGGER_WIDTH_PX
    const style = window.getComputedStyle(blockElement)
    const lineHeight = Number.parseFloat(style.lineHeight)
    const blockWidth = blockRect.width > 0 ? blockRect.width : editorRect.width
    const blockHeight = blockRect.height > 0
      ? blockRect.height
      : Number.isFinite(lineHeight) ? lineHeight : TRIGGER_HEIGHT_PX
    const highlightHeight = Math.max(TRIGGER_HEIGHT_PX, Math.round(blockHeight))
    const highlightTop = Math.round(blockRect.top - surfaceRect.top)
    const triggerTop = highlightTop + Math.max(0, Math.round((highlightHeight - TRIGGER_HEIGHT_PX) / 2))
    const triggerViewportLeft = Math.max(
      SURFACE_EDGE_GAP_PX,
      blockRect.left - triggerWidth - TRIGGER_GAP_PX
    )
    const highlightLeft = Number.isFinite(selectionTarget?.selectionLeft)
      ? blockRect.left - surfaceRect.left + Math.round(selectionTarget?.selectionLeft ?? 0) - BLOCK_HIGHLIGHT_OUTSET_X_PX
      : blockRect.left - surfaceRect.left - BLOCK_HIGHLIGHT_OUTSET_X_PX
    const highlightWidth = Number.isFinite(selectionTarget?.selectionWidth)
      ? Math.round(selectionTarget?.selectionWidth ?? 0) + (BLOCK_HIGHLIGHT_OUTSET_X_PX * 2)
      : blockWidth + (BLOCK_HIGHLIGHT_OUTSET_X_PX * 2)

    setBlockHighlight({
      blockId,
      selectionHeight: selectionTarget?.selectionHeight,
      selectionLeft: selectionTarget?.selectionLeft,
      selectionTop: selectionTarget?.selectionTop,
      selectionWidth: selectionTarget?.selectionWidth
    })

    setAffordance({
      block,
      highlight: {
        height: highlightHeight,
        left: Math.round(highlightLeft),
        top: highlightTop,
        width: Math.max(TRIGGER_HEIGHT_PX, Math.round(highlightWidth))
      },
      kind: block.isEmptyParagraph ? 'insert' : 'context',
      left: Math.round(triggerViewportLeft - surfaceRect.left),
      top: triggerTop
    })
  }, [editor, setAffordance, setBlockHighlight, surfaceRef])

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
    menuOpenRef.current = open
    setMenuOpenState(open)
  }, [])

  useEffect(() => {
    if (!editor) {
      setAffordance(null)
      return
    }

    const activeEditor: Editor = editor

    function handleEditorUpdate() {
      if (suppressHighlightTransactionRefreshRef.current) return

      if (menuOpenRef.current) {
        refreshCurrentGeometry()
        return
      }
      refreshFromSelection()
    }

    editor.on('selectionUpdate', handleEditorUpdate)
    editor.on('transaction', handleEditorUpdate)
    window.addEventListener('resize', refreshCurrentGeometry)
    window.addEventListener('scroll', refreshCurrentGeometry, true)

    const frame = window.requestAnimationFrame(refreshFromSelection)

    return () => {
      activeEditor.off('selectionUpdate', handleEditorUpdate)
      activeEditor.off('transaction', handleEditorUpdate)
      window.removeEventListener('resize', refreshCurrentGeometry)
      window.removeEventListener('scroll', refreshCurrentGeometry, true)
      window.cancelAnimationFrame(frame)
      setBlockHighlight(null)
    }
  }, [editor, refreshCurrentGeometry, refreshFromSelection, setAffordance, setBlockHighlight])

  useEffect(() => {
    if (!editor) return
    const surface = surfaceRef.current
    if (!surface) return
    const activeEditor: Editor = editor
    const activeSurface = surface

    function handlePointerMove(event: PointerEvent) {
      if (menuOpenRef.current) return
      const targetElement = eventTargetElement(event.target)
      if (targetElement?.closest('.block-affordance-layer')) return

      const blockElement = targetBlockElement(event.target, activeEditor.view.dom)
      if (blockElement) {
        activateBlockElement(blockElement)
        return
      }

      const currentAffordance = affordanceRef.current
      if (
        currentAffordance
        && pointerInsideAffordanceBridge(event, activeSurface, currentAffordance)
      ) {
        return
      }

      activateBlockElement(null)
    }

    function handlePointerLeave() {
      if (menuOpenRef.current) return

      refreshFromSelection()
    }

    activeSurface.addEventListener('pointermove', handlePointerMove)
    activeSurface.addEventListener('pointerleave', handlePointerLeave)

    return () => {
      activeSurface.removeEventListener('pointermove', handlePointerMove)
      activeSurface.removeEventListener('pointerleave', handlePointerLeave)
    }
  }, [activateBlockElement, editor, refreshFromSelection, surfaceRef])

  useEffect(() => {
    if (menuOpen) return
    const frame = window.requestAnimationFrame(refreshFromSelection)
    return () => window.cancelAnimationFrame(frame)
  }, [menuOpen, refreshFromSelection])

  return {
    affordance,
    menuOpen,
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

function pointerInsideAffordanceBridge(
  event: PointerEvent,
  surface: HTMLElement,
  affordance: BlockAffordanceState
): boolean {
  const surfaceRect = surface.getBoundingClientRect()
  const triggerWidth = affordance.kind === 'insert'
    ? INSERT_TRIGGER_WIDTH_PX
    : CONTEXT_TRIGGER_WIDTH_PX
  const pointerX = event.clientX - surfaceRect.left
  const pointerY = event.clientY - surfaceRect.top
  const triggerLeft = affordance.left
  const triggerRight = affordance.left + triggerWidth
  const blockLeft = affordance.highlight.left
  const minX = Math.min(triggerLeft, triggerRight, blockLeft) - POINTER_BRIDGE_PADDING_PX
  const maxX = Math.max(triggerLeft, triggerRight, blockLeft) + POINTER_BRIDGE_PADDING_PX
  const minY = affordance.top - POINTER_BRIDGE_PADDING_PX
  const maxY = affordance.top + TRIGGER_HEIGHT_PX + POINTER_BRIDGE_PADDING_PX

  return pointerX >= minX
    && pointerX <= maxX
    && pointerY >= minY
    && pointerY <= maxY
}

function blockIdSelector(blockId: string): string {
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
    return `[data-block-id="${CSS.escape(blockId)}"]`
  }

  return `[data-block-id="${blockId.replaceAll('\\', '\\\\').replaceAll('"', '\\"')}"]`
}
