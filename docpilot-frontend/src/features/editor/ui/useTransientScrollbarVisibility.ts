import { useCallback, useEffect, useRef, useState } from 'react'

const DEFAULT_VISIBLE_MS = 900

export function useTransientScrollbarVisibility({
  disabled = false,
  visibleMs = DEFAULT_VISIBLE_MS
}: {
  disabled?: boolean
  visibleMs?: number
} = {}) {
  const [isScrollbarVisible, setIsScrollbarVisible] = useState(false)
  const scrollbarTimerRef = useRef<number | undefined>(undefined)

  const revealScrollbar = useCallback(() => {
    if (disabled) return

    setIsScrollbarVisible(true)
    window.clearTimeout(scrollbarTimerRef.current)
    scrollbarTimerRef.current = window.setTimeout(() => {
      setIsScrollbarVisible(false)
    }, visibleMs)
  }, [disabled, visibleMs])

  useEffect(() => {
    if (!disabled) return

    setIsScrollbarVisible(false)
    window.clearTimeout(scrollbarTimerRef.current)
  }, [disabled])

  useEffect(() => {
    return () => window.clearTimeout(scrollbarTimerRef.current)
  }, [])

  return {
    isScrollbarVisible,
    revealScrollbar
  }
}
