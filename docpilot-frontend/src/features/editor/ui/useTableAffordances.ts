import type { Editor } from '@tiptap/react'
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type RefObject,
  type WheelEvent as ReactWheelEvent
} from 'react'
import {
  TABLE_DIVIDER_REVEAL_DELAY_MS,
  TABLE_HOVER_INDICATOR_HIDE_DELAY_MS
} from './tableAffordanceConstants'
import { runTableIndicatorSelect } from './tableAffordanceCommands'
import {
  closestTableDivider,
  isTableColumnResizing,
  isTableDividerHotspotPoint,
  tableDividerSignature,
  tableFromEditorSelection,
  tableHoverIndicatorFromPoint,
  tableHoverIndicatorFromTable
} from './tableAffordanceGeometry'
import type {
  Point,
  TableDividerAxis,
  TableDividerGeometry,
  TableHoverIndicatorGeometry,
  TableHoverIndicatorSegment
} from './tableAffordanceTypes'

function clearFrame(frame: number | undefined): undefined {
  if (frame !== undefined) {
    window.cancelAnimationFrame(frame)
  }
  return undefined
}

export function useTableAffordances({
  editor,
  surfaceRef
}: {
  editor: Editor | null
  surfaceRef: RefObject<HTMLElement | null>
}) {
  const lastPointerPointRef = useRef<Point | null>(null)
  const hoverIndicatorHideTimerRef = useRef<number | undefined>(undefined)
  const hoverIndicatorRefreshFrameRef = useRef<number | undefined>(undefined)
  const hoverIndicatorPointerInsideRef = useRef(false)
  const dividerHideTimerRef = useRef<number | undefined>(undefined)
  const dividerRevealTimerRef = useRef<number | undefined>(undefined)
  const dividerKeepAliveRef = useRef<TableDividerGeometry | null>(null)
  const pendingDividerSignatureRef = useRef('')
  const visibleDividerSignatureRef = useRef('')
  const [tableDivider, setTableDivider] = useState<TableDividerGeometry | null>(null)
  const [tableHoverIndicator, setTableHoverIndicator] = useState<TableHoverIndicatorGeometry | null>(null)

  const hideTableDivider = useCallback(() => {
    window.clearTimeout(dividerHideTimerRef.current)
    window.clearTimeout(dividerRevealTimerRef.current)
    dividerHideTimerRef.current = undefined
    dividerRevealTimerRef.current = undefined
    dividerKeepAliveRef.current = null
    pendingDividerSignatureRef.current = ''
    visibleDividerSignatureRef.current = ''
    setTableDivider(null)
  }, [])

  const hideTableHoverIndicator = useCallback(() => {
    window.clearTimeout(hoverIndicatorHideTimerRef.current)
    hoverIndicatorRefreshFrameRef.current = clearFrame(hoverIndicatorRefreshFrameRef.current)
    hoverIndicatorHideTimerRef.current = undefined
    hoverIndicatorPointerInsideRef.current = false
    setTableHoverIndicator(null)
  }, [])

  const resetTableAffordances = useCallback(() => {
    lastPointerPointRef.current = null
    hideTableDivider()
    hideTableHoverIndicator()
  }, [hideTableDivider, hideTableHoverIndicator])

  const keepTableHoverIndicatorVisible = useCallback(() => {
    window.clearTimeout(hoverIndicatorHideTimerRef.current)
    hoverIndicatorHideTimerRef.current = undefined
    hoverIndicatorPointerInsideRef.current = true
  }, [])

  const requestTableHoverIndicatorHide = useCallback(() => {
    hoverIndicatorPointerInsideRef.current = false
    if (hoverIndicatorHideTimerRef.current !== undefined) return

    hoverIndicatorHideTimerRef.current = window.setTimeout(() => {
      const surface = surfaceRef.current
      const point = lastPointerPointRef.current
      if (editor && isTableColumnResizing(editor)) {
        setTableHoverIndicator(null)
        hoverIndicatorHideTimerRef.current = undefined
        return
      }
      if (editor && surface && point) {
        const nextIndicator = tableHoverIndicatorFromPoint(editor, surface, point)
        if (nextIndicator) {
          setTableHoverIndicator(nextIndicator)
          hoverIndicatorHideTimerRef.current = undefined
          return
        }
      }

      setTableHoverIndicator(null)
      hoverIndicatorHideTimerRef.current = undefined
    }, TABLE_HOVER_INDICATOR_HIDE_DELAY_MS)
  }, [editor, surfaceRef])

  const cancelTableDividerHide = useCallback(() => {
    window.clearTimeout(dividerHideTimerRef.current)
    dividerHideTimerRef.current = undefined
  }, [])

  const keepTableDividerControlsVisible = useCallback(() => {
    cancelTableDividerHide()
    keepTableHoverIndicatorVisible()
  }, [cancelTableDividerHide, keepTableHoverIndicatorVisible])

  const requestTableDividerHide = useCallback(() => {
    if (pendingDividerSignatureRef.current && !visibleDividerSignatureRef.current) {
      hideTableDivider()
      return
    }

    if (dividerHideTimerRef.current !== undefined) return

    dividerHideTimerRef.current = window.setTimeout(() => {
      if (editor && isTableColumnResizing(editor)) {
        dividerKeepAliveRef.current = null
        hideTableDivider()
        return
      }

      const keepAliveDivider = dividerKeepAliveRef.current
      const surface = surfaceRef.current
      const point = lastPointerPointRef.current
      if (keepAliveDivider && editor && surface && point && isTableDividerHotspotPoint(point, surface, keepAliveDivider)) {
        const nextIndicator = tableHoverIndicatorFromPoint(editor, surface, point)
        if (nextIndicator) {
          const nextDivider = closestTableDivider(nextIndicator, keepAliveDivider)
          if (nextDivider) {
            const signature = tableDividerSignature(nextDivider)
            pendingDividerSignatureRef.current = ''
            visibleDividerSignatureRef.current = signature
            setTableDivider(nextDivider)
            dividerKeepAliveRef.current = nextDivider
            dividerHideTimerRef.current = undefined
            return
          }
        }
      }

      dividerKeepAliveRef.current = null
      hideTableDivider()
    }, 120)
  }, [editor, hideTableDivider, surfaceRef])

  const updateTableHoverIndicatorFromPoint = useCallback((point: Point | null) => {
    const surface = surfaceRef.current
    if (!editor || !surface) {
      hideTableHoverIndicator()
      return
    }
    if (isTableColumnResizing(editor)) {
      hideTableDivider()
      hideTableHoverIndicator()
      return
    }

    const nextIndicator = point ? tableHoverIndicatorFromPoint(editor, surface, point) : null
    if (nextIndicator) {
      window.clearTimeout(hoverIndicatorHideTimerRef.current)
      hoverIndicatorHideTimerRef.current = undefined
      setTableHoverIndicator(nextIndicator)
      return
    }

    if (!hoverIndicatorPointerInsideRef.current) {
      requestTableHoverIndicatorHide()
    }
  }, [
    editor,
    hideTableDivider,
    hideTableHoverIndicator,
    requestTableHoverIndicatorHide,
    surfaceRef
  ])

  const refreshTableHoverIndicatorFromSelection = useCallback(() => {
    const surface = surfaceRef.current
    if (!editor || !surface) {
      hideTableHoverIndicator()
      return null
    }
    if (isTableColumnResizing(editor)) {
      hideTableDivider()
      hideTableHoverIndicator()
      return null
    }

    const table = tableFromEditorSelection(editor)
    let nextIndicator = table ? tableHoverIndicatorFromTable(editor, surface, table) : null
    if (!nextIndicator && lastPointerPointRef.current) {
      nextIndicator = tableHoverIndicatorFromPoint(editor, surface, lastPointerPointRef.current)
    }
    if (!nextIndicator) {
      requestTableHoverIndicatorHide()
      return null
    }

    window.clearTimeout(hoverIndicatorHideTimerRef.current)
    hoverIndicatorHideTimerRef.current = undefined
    setTableHoverIndicator(nextIndicator)
    return nextIndicator
  }, [
    editor,
    hideTableDivider,
    hideTableHoverIndicator,
    requestTableHoverIndicatorHide,
    surfaceRef
  ])

  const refreshTableDividerFromReference = useCallback((reference: TableDividerGeometry) => {
    const nextIndicator = refreshTableHoverIndicatorFromSelection()
    if (!nextIndicator) {
      dividerKeepAliveRef.current = null
      hideTableDivider()
      return
    }

    const nextDivider = closestTableDivider(nextIndicator, reference)
    if (!nextDivider) {
      dividerKeepAliveRef.current = null
      hideTableDivider()
      return
    }

    const signature = tableDividerSignature(nextDivider)
    window.clearTimeout(dividerHideTimerRef.current)
    window.clearTimeout(dividerRevealTimerRef.current)
    dividerHideTimerRef.current = undefined
    dividerRevealTimerRef.current = undefined
    pendingDividerSignatureRef.current = ''
    visibleDividerSignatureRef.current = signature
    dividerKeepAliveRef.current = nextDivider
    setTableDivider(nextDivider)
  }, [hideTableDivider, refreshTableHoverIndicatorFromSelection])

  const scheduleTableDividerRefresh = useCallback((reference: TableDividerGeometry) => {
    refreshTableDividerFromReference(reference)

    hoverIndicatorRefreshFrameRef.current = clearFrame(hoverIndicatorRefreshFrameRef.current)
    hoverIndicatorRefreshFrameRef.current = window.requestAnimationFrame(() => {
      hoverIndicatorRefreshFrameRef.current = undefined
      refreshTableDividerFromReference(reference)
    })
  }, [refreshTableDividerFromReference])

  const revealTableDividerAfterDelay = useCallback((divider: TableDividerGeometry) => {
    const signature = tableDividerSignature(divider)
    if (visibleDividerSignatureRef.current === signature) return
    if (pendingDividerSignatureRef.current === signature) return

    dividerKeepAliveRef.current = null
    window.clearTimeout(dividerHideTimerRef.current)
    dividerHideTimerRef.current = undefined
    window.clearTimeout(dividerRevealTimerRef.current)
    if (visibleDividerSignatureRef.current) {
      visibleDividerSignatureRef.current = ''
      setTableDivider(null)
    }
    pendingDividerSignatureRef.current = signature
    dividerRevealTimerRef.current = window.setTimeout(() => {
      pendingDividerSignatureRef.current = ''
      visibleDividerSignatureRef.current = signature
      dividerKeepAliveRef.current = divider
      setTableDivider(divider)
    }, TABLE_DIVIDER_REVEAL_DELAY_MS)
  }, [])

  const handleTablePointerMove = useCallback((event: ReactPointerEvent<HTMLDivElement>) => {
    if (editor && isTableColumnResizing(editor)) {
      lastPointerPointRef.current = null
      hideTableDivider()
      hideTableHoverIndicator()
      return
    }
    const point = { x: event.clientX, y: event.clientY }
    lastPointerPointRef.current = point
    updateTableHoverIndicatorFromPoint(point)
    if (tableDivider && !isTableDividerHotspotPoint(point, surfaceRef.current, tableDivider)) {
      requestTableDividerHide()
    }
  }, [
    editor,
    hideTableDivider,
    hideTableHoverIndicator,
    requestTableDividerHide,
    surfaceRef,
    tableDivider,
    updateTableHoverIndicatorFromPoint
  ])

  const handleTablePointerLeave = useCallback(() => {
    lastPointerPointRef.current = null
    requestTableHoverIndicatorHide()
    hideTableDivider()
  }, [hideTableDivider, requestTableHoverIndicatorHide])

  const handleTableWheel = useCallback((event: ReactWheelEvent<HTMLDivElement>) => {
    if (!event.shiftKey) return

    const target = event.target
    if (!(target instanceof HTMLElement)) return

    const tableWrapper = target.closest<HTMLElement>('.tableWrapper')
    if (!tableWrapper || !event.currentTarget.contains(tableWrapper)) return

    const maxScrollLeft = tableWrapper.scrollWidth - tableWrapper.clientWidth
    if (maxScrollLeft <= 0) return

    const delta = event.deltaY !== 0 ? event.deltaY : event.deltaX
    if (delta === 0) return

    event.preventDefault()
    event.stopPropagation()

    const nextScrollLeft = Math.min(Math.max(tableWrapper.scrollLeft + delta, 0), maxScrollLeft)
    if (nextScrollLeft === tableWrapper.scrollLeft) return

    tableWrapper.scrollLeft = nextScrollLeft

    const point = { x: event.clientX, y: event.clientY }
    lastPointerPointRef.current = point
    window.requestAnimationFrame(() => updateTableHoverIndicatorFromPoint(point))
  }, [updateTableHoverIndicatorFromPoint])

  const handleTableDividerChanged = useCallback((divider: TableDividerGeometry) => {
    scheduleTableDividerRefresh(divider)
  }, [scheduleTableDividerRefresh])

  const selectTableIndicatorSegment = useCallback((axis: TableDividerAxis, segment: TableHoverIndicatorSegment) => {
    if (!editor) return

    if (runTableIndicatorSelect(editor, axis, segment)) {
      hideTableDivider()
      keepTableHoverIndicatorVisible()
      window.requestAnimationFrame(() => refreshTableHoverIndicatorFromSelection())
    }
  }, [
    editor,
    hideTableDivider,
    keepTableHoverIndicatorVisible,
    refreshTableHoverIndicatorFromSelection
  ])

  useEffect(() => {
    if (!editor) return

    function refreshTableHoverAffordances() {
      updateTableHoverIndicatorFromPoint(lastPointerPointRef.current)
    }

    editor.on('transaction', refreshTableHoverAffordances)
    window.addEventListener('resize', refreshTableHoverAffordances)
    window.addEventListener('scroll', refreshTableHoverAffordances, true)

    return () => {
      editor.off('transaction', refreshTableHoverAffordances)
      window.removeEventListener('resize', refreshTableHoverAffordances)
      window.removeEventListener('scroll', refreshTableHoverAffordances, true)
    }
  }, [editor, updateTableHoverIndicatorFromPoint])

  useEffect(() => {
    return () => {
      lastPointerPointRef.current = null
      window.clearTimeout(hoverIndicatorHideTimerRef.current)
      window.clearTimeout(dividerHideTimerRef.current)
      window.clearTimeout(dividerRevealTimerRef.current)
      hoverIndicatorRefreshFrameRef.current = clearFrame(hoverIndicatorRefreshFrameRef.current)
      hoverIndicatorHideTimerRef.current = undefined
      dividerHideTimerRef.current = undefined
      dividerRevealTimerRef.current = undefined
      hoverIndicatorPointerInsideRef.current = false
      pendingDividerSignatureRef.current = ''
      visibleDividerSignatureRef.current = ''
      dividerKeepAliveRef.current = null
    }
  }, [])

  return {
    handleTableDividerChanged,
    handleTablePointerLeave,
    handleTablePointerMove,
    handleTableWheel,
    keepTableDividerControlsVisible,
    keepTableHoverIndicatorVisible,
    requestTableDividerHide,
    requestTableHoverIndicatorHide,
    resetTableAffordances,
    revealTableDividerAfterDelay,
    selectTableIndicatorSegment,
    tableDivider,
    tableHoverIndicator
  }
}
