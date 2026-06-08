import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent, type RefObject } from 'react'
import {
  clampSpecialBlockWidth,
  MIN_SPECIAL_BLOCK_WIDTH,
  type ResizeCorner
} from './codeBlockNodeViewUtils'

export function useMermaidResize({
  frameRef,
  onCommitWidth,
  onSelect,
  storedPreviewWidth
}: {
  frameRef: RefObject<HTMLDivElement | null>
  onCommitWidth: (width: number) => void
  onSelect: () => void
  storedPreviewWidth: number | null
}) {
  const resizeRef = useRef<{
    corner: ResizeCorner
    startX: number
    startY: number
    startWidth: number
    startHeight: number
  } | null>(null)
  const draftWidthRef = useRef<number | null>(null)
  const commitWidthRef = useRef(onCommitWidth)
  const storedPreviewWidthRef = useRef(storedPreviewWidth)
  const [draftWidth, setDraftWidth] = useState<number | null>(null)

  commitWidthRef.current = onCommitWidth
  storedPreviewWidthRef.current = storedPreviewWidth

  useEffect(() => {
    function handlePointerMove(event: PointerEvent) {
      const resizeState = resizeRef.current
      if (!resizeState) return
      const deltaX = event.clientX - resizeState.startX
      const deltaY = event.clientY - resizeState.startY
      const aspectRatio = resizeState.startHeight > 0 ? resizeState.startWidth / resizeState.startHeight : 1
      const widthFromX = resizeState.startWidth + deltaX * resizeState.corner.x
      const widthFromY = (resizeState.startHeight + deltaY * resizeState.corner.y) * aspectRatio
      const nextWidth = Math.abs(deltaX) > Math.abs(deltaY * aspectRatio) ? widthFromX : widthFromY
      const clampedWidth = clampSpecialBlockWidth(nextWidth)
      draftWidthRef.current = clampedWidth
      setDraftWidth(clampedWidth)
    }

    function handlePointerUp() {
      if (resizeRef.current && draftWidthRef.current) {
        commitWidthRef.current(draftWidthRef.current)
      }
      resizeRef.current = null
      draftWidthRef.current = null
      setDraftWidth(null)
    }

    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
    return () => {
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
    }
  }, [])

  function startMermaidResize(event: ReactPointerEvent, corner: ResizeCorner) {
    event.preventDefault()
    event.stopPropagation()
    onSelect()
    const rect = frameRef.current?.getBoundingClientRect()
    resizeRef.current = {
      corner,
      startX: event.clientX,
      startY: event.clientY,
      startWidth: rect?.width ?? storedPreviewWidthRef.current ?? MIN_SPECIAL_BLOCK_WIDTH,
      startHeight: rect?.height ?? MIN_SPECIAL_BLOCK_WIDTH
    }
  }

  return {
    activePreviewWidth: draftWidth ?? storedPreviewWidth,
    isResizing: draftWidth !== null,
    startMermaidResize
  }
}
