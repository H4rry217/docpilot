import type { Editor } from '@tiptap/react'
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type RefObject
} from 'react'
import {
  deleteBlocksByIds,
  setBlockSelectionDecorations,
  type BlockSelectionDecoration
} from '../model/blockSelection'
import {
  blockMarqueeStartModeForTarget,
  blockSelectionDecorationsFromTargets,
  blockSelectionSignature,
  clampPointToRect,
  clientPointFromElementPoint,
  hasTableBlockTarget,
  isPastDragStartDistance,
  pointFromClientPoint,
  rectFromPoints,
  rectsOverlap,
  selectableBlockTargets,
  visualBlockSelectionTargets,
  type Point,
  type Rect,
  type SelectableBlockTarget
} from './blockSelectionGeometry'

type BlockSelectionUiState = {
  hasSelectedTableBlock: boolean
  isDragging: boolean
}

type DragSelectionState = {
  editor: Editor
  frame: number | null
  isSelecting: boolean
  latestClientPoint: Point
  originClientPoint: Point
  originSurfacePoint: Point
  boundaryElement: HTMLElement
  targets: SelectableBlockTarget[]
}

function elementLocalRect(element: HTMLElement): Rect {
  return {
    left: 0,
    top: 0,
    right: element.clientWidth,
    bottom: element.clientHeight
  }
}

function selectionBoundaryElement(surface: HTMLElement): HTMLElement {
  const documentMain = surface.closest('.document-main')
  if (documentMain instanceof HTMLElement) return documentMain

  const documentCanvas = surface.closest('.document-canvas')
  if (documentCanvas instanceof HTMLElement) return documentCanvas

  return surface
}

function boundaryLocalRect(surface: HTMLElement, boundary: HTMLElement): Rect {
  const surfaceRect = surface.getBoundingClientRect()
  const boundaryRect = boundary.getBoundingClientRect()
  const rect = {
    left: boundaryRect.left - surfaceRect.left,
    top: boundaryRect.top - surfaceRect.top,
    right: boundaryRect.right - surfaceRect.left,
    bottom: boundaryRect.bottom - surfaceRect.top
  }

  if (rect.right <= rect.left || rect.bottom <= rect.top) {
    return elementLocalRect(surface)
  }

  return rect
}

export function useBlockMarqueeSelection({
  editor,
  marqueeRef,
  surfaceRef
}: {
  editor: Editor | null
  marqueeRef: RefObject<HTMLDivElement | null>
  surfaceRef: RefObject<HTMLDivElement | null>
}) {
  const selectedBlockIdsRef = useRef<Set<string>>(new Set())
  const selectedVisualBlockIdsRef = useRef<Set<string>>(new Set())
  const selectedVisualSignatureRef = useRef('')
  const dragSelectionRef = useRef<DragSelectionState | null>(null)
  const suppressNextClickRef = useRef(false)
  const [uiState, setUiState] = useState<BlockSelectionUiState>({
    hasSelectedTableBlock: false,
    isDragging: false
  })

  function updateUiState(nextState: Partial<BlockSelectionUiState>) {
    setUiState((currentState) => {
      const next = {
        ...currentState,
        ...nextState
      }
      return next.hasSelectedTableBlock === currentState.hasSelectedTableBlock
        && next.isDragging === currentState.isDragging
        ? currentState
        : next
    })
  }

  const clearBlockSelection = useCallback(() => {
    selectedBlockIdsRef.current.clear()
    selectedVisualBlockIdsRef.current.clear()
    selectedVisualSignatureRef.current = ''
    updateUiState({ hasSelectedTableBlock: false })
    if (editor) {
      setBlockSelectionDecorations(editor, [])
    }
  }, [editor])

  function applyVisualBlockSelection(blockSelections: BlockSelectionDecoration[]) {
    if (!editor) return

    const nextIds = new Set(blockSelections.map((blockSelection) => blockSelection.blockId))
    const nextSignature = blockSelectionSignature(blockSelections)
    if (nextSignature === selectedVisualSignatureRef.current) {
      return
    }

    selectedVisualBlockIdsRef.current = nextIds
    selectedVisualSignatureRef.current = nextSignature
    setBlockSelectionDecorations(editor, blockSelections)
  }

  function applySelectedBlockTargets(targets: SelectableBlockTarget[]) {
    const visualTargets = visualBlockSelectionTargets(targets)
    selectedBlockIdsRef.current = new Set(visualTargets.map((target) => target.id))
    updateUiState({
      hasSelectedTableBlock: hasTableBlockTarget(visualTargets)
    })
    applyVisualBlockSelection(
      blockSelectionDecorationsFromTargets(visualTargets)
    )
  }

  function setMarqueeVisible(visible: boolean) {
    const element = marqueeRef.current
    if (!element) return
    element.hidden = !visible
  }

  function updateMarqueeElement(surface: HTMLElement, boundary: HTMLElement, origin: Point, current: Point) {
    const element = marqueeRef.current
    if (!element) return
    const bounds = boundaryLocalRect(surface, boundary)
    const rect = rectFromPoints(
      clampPointToRect(origin, bounds),
      clampPointToRect(current, bounds)
    )
    element.style.transform = `translate(${rect.left}px, ${rect.top}px)`
    element.style.width = `${rect.right - rect.left}px`
    element.style.height = `${rect.bottom - rect.top}px`
  }

  useEffect(() => {
    if (!editor) return
    const activeEditor: Editor = editor

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Backspace' && event.key !== 'Delete') return
      if (!selectedBlockIdsRef.current.size) return
      const activeElement = document.activeElement
      const surface = surfaceRef.current
      if (
        activeElement
        && activeElement !== document.body
        && surface
        && !surface.contains(activeElement)
      ) {
        return
      }

      event.preventDefault()
      if (deleteBlocksByIds(activeEditor, selectedBlockIdsRef.current)) {
        clearBlockSelection()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [clearBlockSelection, editor, surfaceRef])

  useEffect(() => {
    return () => {
      const dragState = dragSelectionRef.current
      if (dragState && dragState.frame !== null) {
        window.cancelAnimationFrame(dragState.frame)
      }
      dragSelectionRef.current = null
      suppressNextClickRef.current = false
      setMarqueeVisible(false)
      updateUiState({ isDragging: false })
      clearBlockSelection()
    }
  }, [clearBlockSelection])

  function applyDragSelection(clientPoint: Point) {
    const dragState = dragSelectionRef.current
    const surface = surfaceRef.current
    if (!dragState || !surface) return

    dragState.latestClientPoint = clientPoint
    const surfaceBounds = boundaryLocalRect(surface, dragState.boundaryElement)
    const originSurfacePoint = clampPointToRect(dragState.originSurfacePoint, surfaceBounds)
    const currentSurfacePoint = clampPointToRect(pointFromClientPoint(clientPoint, surface), surfaceBounds)
    updateMarqueeElement(surface, dragState.boundaryElement, originSurfacePoint, currentSurfacePoint)

    const selectionRect = rectFromPoints(
      clientPointFromElementPoint(originSurfacePoint, surface),
      clientPointFromElementPoint(currentSurfacePoint, surface)
    )
    dragState.targets = selectableBlockTargets(dragState.editor)
    applySelectedBlockTargets(
      dragState.targets.filter((target) => rectsOverlap(selectionRect, target.hitRect))
    )
  }

  function scheduleDragSelection(clientPoint: Point) {
    const dragState = dragSelectionRef.current
    if (!dragState) return

    dragState.latestClientPoint = clientPoint
    if (dragState.frame !== null) return

    dragState.frame = window.requestAnimationFrame(() => {
      const latestDragState = dragSelectionRef.current
      if (!latestDragState) return
      latestDragState.frame = null
      applyDragSelection(latestDragState.latestClientPoint)
    })
  }

  function handleBoundaryPointerDown(event: PointerEvent, boundaryElement: HTMLElement) {
    const activeEditor = editor
    if (!activeEditor) {
      if (selectedBlockIdsRef.current.size) {
        clearBlockSelection()
      }
      return
    }

    const startMode = blockMarqueeStartModeForTarget({
      boundaryElement,
      button: event.button,
      target: event.target
    })
    if (startMode === 'ignore') {
      if (selectedBlockIdsRef.current.size) {
        clearBlockSelection()
      }
      return
    }

    const surfaceElement = surfaceRef.current
    if (!surfaceElement) return
    const activeSurfaceElement: HTMLElement = surfaceElement

    const dragEditor: Editor = activeEditor
    try {
      boundaryElement.setPointerCapture(event.pointerId)
    } catch {
      // Pointer capture is best-effort; window listeners below still handle the drag.
    }
    const originClientPoint = { x: event.clientX, y: event.clientY }
    const originSurfacePoint = pointFromClientPoint(originClientPoint, activeSurfaceElement)
    clearBlockSelection()
    dragSelectionRef.current = {
      editor: activeEditor,
      frame: null,
      isSelecting: false,
      latestClientPoint: originClientPoint,
      originClientPoint,
      originSurfacePoint,
      boundaryElement,
      targets: selectableBlockTargets(activeEditor)
    }

    function handlePointerMove(pointerEvent: PointerEvent) {
      const dragState = dragSelectionRef.current
      if (!dragState) return

      const clientPoint = { x: pointerEvent.clientX, y: pointerEvent.clientY }
      if (!dragState.isSelecting) {
        if (!isPastDragStartDistance(dragState.originClientPoint, clientPoint)) return
        dragState.isSelecting = true
        updateMarqueeElement(
          activeSurfaceElement,
          dragState.boundaryElement,
          dragState.originSurfacePoint,
          pointFromClientPoint(clientPoint, activeSurfaceElement)
        )
        setMarqueeVisible(true)
        updateUiState({ isDragging: true })
      }

      pointerEvent.preventDefault()
      window.getSelection()?.removeAllRanges()
      scheduleDragSelection(clientPoint)
    }

    function handlePointerUp(pointerEvent: PointerEvent) {
      const dragState = dragSelectionRef.current
      if (dragState && dragState.frame !== null) {
        window.cancelAnimationFrame(dragState.frame)
      }
      if (dragState?.isSelecting) {
        pointerEvent.preventDefault()
        suppressNextClickRef.current = true
        applyDragSelection({ x: pointerEvent.clientX, y: pointerEvent.clientY })
        dragEditor.commands.focus(undefined, { scrollIntoView: false })
      }
      dragSelectionRef.current = null
      setMarqueeVisible(false)
      updateUiState({ isDragging: false })
      try {
        boundaryElement.releasePointerCapture(pointerEvent.pointerId)
      } catch {
        // The pointer may already be released by the browser.
      }
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
    }

    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
  }

  function handleBoundaryClickCapture(event: MouseEvent) {
    if (!suppressNextClickRef.current) return
    suppressNextClickRef.current = false
    event.preventDefault()
    event.stopPropagation()
  }

  useEffect(() => {
    const surface = surfaceRef.current
    if (!surface) return
    const boundaryElement = selectionBoundaryElement(surface)

    function handlePointerDown(event: PointerEvent) {
      handleBoundaryPointerDown(event, boundaryElement)
    }

    boundaryElement.addEventListener('pointerdown', handlePointerDown, true)
    boundaryElement.addEventListener('click', handleBoundaryClickCapture, true)
    return () => {
      boundaryElement.removeEventListener('pointerdown', handlePointerDown, true)
      boundaryElement.removeEventListener('click', handleBoundaryClickCapture, true)
    }
  }, [clearBlockSelection, editor, surfaceRef])

  const isBlockSelectionDragging = useCallback(() => dragSelectionRef.current !== null, [])

  return {
    blockSelectionUiState: uiState,
    clearBlockSelection,
    isBlockSelectionDragging
  }
}
