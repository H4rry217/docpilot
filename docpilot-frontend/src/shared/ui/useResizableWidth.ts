import { useCallback, type PointerEvent as ReactPointerEvent } from 'react'

type ResizeDirection = 'left-panel' | 'right-panel'

export function useResizableWidth({
  width,
  min,
  max,
  direction,
  onChange
}: {
  width: number
  min: number
  max: number
  direction: ResizeDirection
  onChange: (width: number) => void
}) {
  return useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      event.preventDefault()
      const startX = event.clientX
      const startWidth = width
      const resizeSign = direction === 'left-panel' ? 1 : -1

      function clamp(nextWidth: number) {
        return Math.min(max, Math.max(min, nextWidth))
      }

      function handlePointerMove(moveEvent: PointerEvent) {
        onChange(clamp(startWidth + (moveEvent.clientX - startX) * resizeSign))
      }

      function handlePointerUp() {
        document.body.classList.remove('is-resizing-panel')
        window.removeEventListener('pointermove', handlePointerMove)
        window.removeEventListener('pointerup', handlePointerUp)
        window.removeEventListener('pointercancel', handlePointerUp)
      }

      document.body.classList.add('is-resizing-panel')
      window.addEventListener('pointermove', handlePointerMove)
      window.addEventListener('pointerup', handlePointerUp)
      window.addEventListener('pointercancel', handlePointerUp)
    },
    [direction, max, min, onChange, width]
  )
}
