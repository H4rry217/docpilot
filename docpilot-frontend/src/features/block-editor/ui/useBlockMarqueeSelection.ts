import type { Editor } from '@tiptap/react'
import {
  useCallback,
  useEffect,
  useRef,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent,
  type RefObject
} from 'react'
import {
  deleteBlocksByIds,
  setBlockSelectionDecorations,
  type BlockSelectionDecoration
} from '../model/blockSelection'
import {
  blockSelectionDecorationsFromTargets,
  blockSelectionSignature,
  clientPointFromElementPoint,
  eventTargetElement,
  isInteractiveSelectionTarget,
  isPastDragStartDistance,
  pointFromClientPoint,
  pointFromElement,
  rectFromPoints,
  rectsOverlap,
  selectableBlockTargets,
  visualBlockSelectionTargets,
  type Point,
  type SelectableBlockTarget
} from './blockSelectionGeometry'

type DragSelectionState = {
  editor: Editor
  frame: number | null
  isSelecting: boolean
  latestClientPoint: Point
  originClientPoint: Point
  originSurfacePoint: Point
  targets: SelectableBlockTarget[]
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

  const clearBlockSelection = useCallback(() => {
    selectedBlockIdsRef.current.clear()
    selectedVisualBlockIdsRef.current.clear()
    selectedVisualSignatureRef.current = ''
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
    applyVisualBlockSelection(
      blockSelectionDecorationsFromTargets(visualTargets)
    )
  }

  function setMarqueeVisible(visible: boolean) {
    const element = marqueeRef.current
    if (!element) return
    element.hidden = !visible
  }

  function updateMarqueeElement(origin: Point, current: Point) {
    const element = marqueeRef.current
    if (!element) return
    const rect = rectFromPoints(origin, current)
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
      clearBlockSelection()
    }
  }, [clearBlockSelection])

  function shouldStartBlockMarquee(event: ReactPointerEvent<HTMLDivElement>): boolean {
    if (!editor || event.button !== 0) return false
    const target = eventTargetElement(event.target)
    if (!target) return false
    if (!surfaceRef.current?.contains(target)) return false
    if (isInteractiveSelectionTarget(target)) return false

    // Block marquee intentionally starts from normal document text too; the
    // drag threshold keeps ordinary clicks as cursor placement.
    return true
  }

  function applyDragSelection(clientPoint: Point) {
    const dragState = dragSelectionRef.current
    const surface = surfaceRef.current
    if (!dragState || !surface) return

    dragState.latestClientPoint = clientPoint
    const currentSurfacePoint = pointFromClientPoint(clientPoint, surface)
    updateMarqueeElement(dragState.originSurfacePoint, currentSurfacePoint)

    const selectionRect = rectFromPoints(
      clientPointFromElementPoint(dragState.originSurfacePoint, surface),
      clientPoint
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

  function handleSurfacePointerDown(event: ReactPointerEvent<HTMLDivElement>) {
    const activeEditor = editor
    if (!activeEditor) {
      if (selectedBlockIdsRef.current.size) {
        clearBlockSelection()
      }
      return
    }

    if (!shouldStartBlockMarquee(event)) {
      if (selectedBlockIdsRef.current.size) {
        clearBlockSelection()
      }
      return
    }

    const dragEditor: Editor = activeEditor
    const surfaceElement = event.currentTarget
    try {
      surfaceElement.setPointerCapture(event.pointerId)
    } catch {
      // Pointer capture is best-effort; window listeners below still handle the drag.
    }
    const originClientPoint = { x: event.clientX, y: event.clientY }
    const originSurfacePoint = pointFromElement(event, surfaceElement)
    clearBlockSelection()
    dragSelectionRef.current = {
      editor: activeEditor,
      frame: null,
      isSelecting: false,
      latestClientPoint: originClientPoint,
      originClientPoint,
      originSurfacePoint,
      targets: selectableBlockTargets(activeEditor)
    }

    function handlePointerMove(pointerEvent: PointerEvent) {
      const dragState = dragSelectionRef.current
      if (!dragState) return

      const clientPoint = { x: pointerEvent.clientX, y: pointerEvent.clientY }
      if (!dragState.isSelecting) {
        if (!isPastDragStartDistance(dragState.originClientPoint, clientPoint)) return
        dragState.isSelecting = true
        updateMarqueeElement(dragState.originSurfacePoint, pointFromClientPoint(clientPoint, surfaceElement))
        setMarqueeVisible(true)
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
        dragEditor.commands.focus()
      }
      dragSelectionRef.current = null
      setMarqueeVisible(false)
      try {
        surfaceElement.releasePointerCapture(pointerEvent.pointerId)
      } catch {
        // The pointer may already be released by the browser.
      }
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
    }

    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
  }

  function handleSurfaceClickCapture(event: ReactMouseEvent<HTMLDivElement>) {
    if (!suppressNextClickRef.current) return
    suppressNextClickRef.current = false
    event.preventDefault()
    event.stopPropagation()
  }

  const isBlockSelectionDragging = useCallback(() => dragSelectionRef.current !== null, [])

  return {
    clearBlockSelection,
    handleSurfaceClickCapture,
    handleSurfacePointerDown,
    isBlockSelectionDragging
  }
}
