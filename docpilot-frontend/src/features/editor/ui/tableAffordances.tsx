import { columnResizingPluginKey, findTable, TableMap } from '@tiptap/pm/tables'
import type { Editor } from '@tiptap/react'
import { Plus } from 'lucide-react'
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type RefObject,
  type WheelEvent as ReactWheelEvent
} from 'react'
import { TABLE_DEFAULT_COLUMN_WIDTH_PX } from '../model/tableConstants'
import './TableAffordances.css'

type Point = {
  x: number
  y: number
}

type Rect = {
  left: number
  top: number
  right: number
  bottom: number
}

export type TableDividerAxis = 'column' | 'row'

export type TableDividerGeometry = {
  axis: TableDividerAxis
  cell: HTMLTableCellElement
  handleLeft: number
  handleTop: number
  insert: 'after' | 'before'
  lineLeft: number
  lineTop: number
  lineHeight?: number
  lineWidth?: number
}

export type TableHoverIndicatorSegment = {
  afterDivider?: TableDividerGeometry
  beforeDivider?: TableDividerGeometry
  offset: number
  size: number
}

export type TableHoverIndicatorGeometry = {
  columns: TableHoverIndicatorSegment[]
  rows: TableHoverIndicatorSegment[]
  tableHeight: number
  tableLeft: number
  tableTop: number
  tableWidth: number
}

const TABLE_DIVIDER_GUTTER_PX = 8
const TABLE_DIVIDER_REVEAL_DELAY_MS = 160
const TABLE_DIVIDER_HOTSPOT_RADIUS_PX = 22
const TABLE_HOVER_INDICATOR_DOT_ANCHOR_OUTSET_PX = 18
const TABLE_HOVER_INDICATOR_HIDE_DELAY_MS = 240
const TABLE_HOVER_INDICATOR_RAIL_SIZE_PX = 12

function pointFromClientPoint(point: Point, element: HTMLElement): Point {
  const rect = element.getBoundingClientRect()
  return {
    x: point.x - rect.left,
    y: point.y - rect.top
  }
}

function rectContainsPoint(rect: Rect, point: Point, inset = 0): boolean {
  return point.x >= rect.left - inset
    && point.x <= rect.right + inset
    && point.y >= rect.top - inset
    && point.y <= rect.bottom + inset
}

function visibleTableClientRect(table: HTMLTableElement): Rect | null {
  const tableRect = table.getBoundingClientRect()
  const wrapperRect = table.closest<HTMLElement>('.tableWrapper')?.getBoundingClientRect() ?? tableRect
  const left = Math.max(tableRect.left, wrapperRect.left)
  const right = Math.min(tableRect.right, wrapperRect.right)
  const top = Math.max(tableRect.top, wrapperRect.top)
  const bottom = Math.min(tableRect.bottom, wrapperRect.bottom)

  if (right <= left || bottom <= top) return null
  return { left, top, right, bottom }
}

function tableFromPoint(editorDom: HTMLElement, point: Point): HTMLTableElement | null {
  const element = document.elementFromPoint(point.x, point.y)
  const directTable = element instanceof HTMLElement
    ? element.closest<HTMLTableElement>('table')
    : null
  const directVisibleRect = directTable ? visibleTableClientRect(directTable) : null
  if (directTable && editorDom.contains(directTable) && directVisibleRect && rectContainsPoint(directVisibleRect, point)) {
    return directTable
  }

  return Array.from(editorDom.querySelectorAll<HTMLTableElement>('table'))
    .find((table) => {
      const visibleRect = visibleTableClientRect(table)
      return visibleRect ? rectContainsPoint(visibleRect, point, TABLE_DIVIDER_GUTTER_PX) : false
    })
    ?? null
}

function isTableDividerHotspotPoint(
  point: Point,
  surface?: HTMLElement | null,
  divider?: TableDividerGeometry | null
): boolean {
  const element = document.elementFromPoint(point.x, point.y)
  if (
    element instanceof HTMLElement
    && element.closest('.table-divider-insert, .table-hover-column-dot, .table-hover-row-dot') !== null
  ) {
    return true
  }

  if (!surface || !divider) return false

  const surfacePoint = pointFromClientPoint(point, surface)
  return Math.abs(surfacePoint.x - divider.handleLeft) <= TABLE_DIVIDER_HOTSPOT_RADIUS_PX
    && Math.abs(surfacePoint.y - divider.handleTop) <= TABLE_DIVIDER_HOTSPOT_RADIUS_PX
}

function tableHoverIndicatorFromTable(surface: HTMLElement, table: HTMLTableElement): TableHoverIndicatorGeometry | null {
  const surfaceRect = surface.getBoundingClientRect()
  const tableRect = table.getBoundingClientRect()
  const wrapperRect = table.closest<HTMLElement>('.tableWrapper')?.getBoundingClientRect() ?? tableRect
  const firstRow = table.rows.item(0)
  if (!firstRow || tableRect.width <= 0 || tableRect.height <= 0) return null

  const visibleLeft = Math.max(tableRect.left, wrapperRect.left)
  const visibleRight = Math.min(tableRect.right, wrapperRect.right)
  const visibleWidth = visibleRight - visibleLeft
  if (visibleWidth <= 0) return null

  const tableLeft = visibleLeft - surfaceRect.left
  const tableTop = tableRect.top - surfaceRect.top
  // Mirrors the top/left rail transform plus the external dot offset in CSS.
  const columnHandleTop = tableTop - TABLE_HOVER_INDICATOR_DOT_ANCHOR_OUTSET_PX
  const rowHandleLeft = tableLeft - TABLE_HOVER_INDICATOR_DOT_ANCHOR_OUTSET_PX

  return {
    columns: Array.from(firstRow.cells)
      .map((cell, index): TableHoverIndicatorSegment | null => {
        const rect = cell.getBoundingClientRect()
        const clippedLeft = Math.max(rect.left, visibleLeft)
        const clippedRight = Math.min(rect.right, visibleRight)
        if (clippedRight <= clippedLeft) return null

        const offset = clippedLeft - visibleLeft
        const isLeftDividerVisible = rect.left >= visibleLeft && rect.left <= visibleRight
        const isRightDividerVisible = rect.right >= visibleLeft && rect.right <= visibleRight
        const left = rect.left - surfaceRect.left
        const right = rect.right - surfaceRect.left
        const afterDivider: TableDividerGeometry | undefined = isRightDividerVisible ? {
          axis: 'column',
          cell,
          handleLeft: right,
          handleTop: columnHandleTop,
          insert: 'after',
          lineHeight: tableRect.height + TABLE_HOVER_INDICATOR_DOT_ANCHOR_OUTSET_PX,
          lineLeft: right,
          lineTop: columnHandleTop
        } : undefined
        const beforeDivider: TableDividerGeometry | undefined = index === 0 && isLeftDividerVisible ? {
          axis: 'column',
          cell,
          handleLeft: left,
          handleTop: columnHandleTop,
          insert: 'before',
          lineHeight: tableRect.height + TABLE_HOVER_INDICATOR_DOT_ANCHOR_OUTSET_PX,
          lineLeft: left,
          lineTop: columnHandleTop
        } : undefined
        return {
          afterDivider,
          beforeDivider,
          offset,
          size: clippedRight - clippedLeft
        }
      })
      .filter((column): column is TableHoverIndicatorSegment => column !== null),
    rows: Array.from(table.rows)
      .map((row, index): TableHoverIndicatorSegment | null => {
        const rect = row.getBoundingClientRect()
        const cell = row.cells.item(0)
        if (!cell) return null
        const offset = rect.top - tableRect.top
        const afterDivider: TableDividerGeometry = {
          axis: 'row',
          cell,
          handleLeft: rowHandleLeft,
          handleTop: rect.bottom - surfaceRect.top,
          insert: 'after',
          lineLeft: rowHandleLeft,
          lineTop: rect.bottom - surfaceRect.top,
          lineWidth: visibleWidth + TABLE_HOVER_INDICATOR_DOT_ANCHOR_OUTSET_PX
        }
        const beforeDivider: TableDividerGeometry | undefined = index === 0 ? {
          axis: 'row',
          cell,
          handleLeft: rowHandleLeft,
          handleTop: rect.top - surfaceRect.top,
          insert: 'before',
          lineLeft: rowHandleLeft,
          lineTop: rect.top - surfaceRect.top,
          lineWidth: visibleWidth + TABLE_HOVER_INDICATOR_DOT_ANCHOR_OUTSET_PX
        } : undefined
        return {
          afterDivider,
          beforeDivider,
          offset,
          size: rect.height
        }
      })
      .filter((row): row is TableHoverIndicatorSegment => row !== null),
    tableHeight: tableRect.height,
    tableLeft,
    tableTop,
    tableWidth: visibleWidth
  }
}

function tableHoverIndicatorFromPoint(editor: Editor, surface: HTMLElement, point: Point): TableHoverIndicatorGeometry | null {
  const table = tableFromPoint(editor.view.dom, point)
  return table ? tableHoverIndicatorFromTable(surface, table) : null
}

function tableFromEditorSelection(editor: Editor): HTMLTableElement | null {
  const tableResult = findTable(editor.state.selection.$from)
  if (!tableResult) return null

  const tableDom = editor.view.nodeDOM(tableResult.pos)
  if (tableDom instanceof HTMLTableElement) return tableDom
  if (tableDom instanceof HTMLElement) {
    return tableDom.querySelector<HTMLTableElement>('table')
  }

  return null
}

export function isTableColumnResizing(editor: Editor): boolean {
  const resizeState = columnResizingPluginKey.getState(editor.state)
  return Boolean(resizeState?.dragging) || editor.view.dom.classList.contains('resize-cursor')
}

function tableDividerSignature(divider: TableDividerGeometry): string {
  return [
    divider.axis,
    divider.insert,
    Math.round(divider.lineLeft),
    Math.round(divider.lineTop),
    Math.round(divider.lineHeight ?? 0),
    Math.round(divider.lineWidth ?? 0)
  ].join(':')
}

function tableDividersFromIndicator(geometry: TableHoverIndicatorGeometry): TableDividerGeometry[] {
  return [
    ...geometry.columns.flatMap((column) => [column.beforeDivider, column.afterDivider]),
    ...geometry.rows.flatMap((row) => [row.beforeDivider, row.afterDivider])
  ].filter((divider): divider is TableDividerGeometry => divider !== undefined)
}

function closestTableDivider(
  geometry: TableHoverIndicatorGeometry,
  reference: TableDividerGeometry
): TableDividerGeometry | null {
  let closestDivider: TableDividerGeometry | null = null
  let closestScore = Number.POSITIVE_INFINITY

  for (const divider of tableDividersFromIndicator(geometry)) {
    if (divider.axis !== reference.axis) continue

    const deltaLeft = Math.abs(divider.handleLeft - reference.handleLeft)
    const deltaTop = Math.abs(divider.handleTop - reference.handleTop)
    const insertPenalty = divider.insert === reference.insert ? 0 : 1000
    const score = deltaLeft + deltaTop + insertPenalty
    if (score >= closestScore) continue

    closestDivider = divider
    closestScore = score
  }

  return closestDivider
}

function tableCellPosition(editor: Editor, cell: HTMLTableCellElement): number | null {
  const rect = cell.getBoundingClientRect()
  const position = editor.view.posAtCoords({
    left: rect.left + Math.min(8, Math.max(rect.width / 2, 1)),
    top: rect.top + Math.min(8, Math.max(rect.height / 2, 1))
  })?.pos
  return typeof position === 'number' ? position : null
}

function columnWidthsFromTableDom(table: HTMLTableElement): number[] {
  const firstRow = table.rows.item(0)
  if (!firstRow) return []

  return Array.from(firstRow.cells).flatMap((cell) => {
    const colspan = Math.max(cell.colSpan || 1, 1)
    const width = Math.max(
      TABLE_DEFAULT_COLUMN_WIDTH_PX,
      Math.round(cell.getBoundingClientRect().width / colspan)
    )
    return Array.from({ length: colspan }, () => width)
  })
}

function columnInsertIndexFromDivider(divider: TableDividerGeometry, firstRow: HTMLTableRowElement): number {
  let index = 0
  for (const cell of Array.from(firstRow.cells)) {
    const colspan = Math.max(cell.colSpan || 1, 1)
    if (cell === divider.cell) {
      return divider.insert === 'before' ? index : index + colspan
    }
    index += colspan
  }

  return divider.insert === 'before' ? 0 : index
}

function insertedColumnWidthPlanFromDivider(divider: TableDividerGeometry): number[] | null {
  if (divider.axis !== 'column') return null

  const table = divider.cell.closest('table')
  const firstRow = table?.rows.item(0)
  if (!table || !firstRow) return null

  const columnWidths = columnWidthsFromTableDom(table)
  if (!columnWidths.length) return null

  const insertIndex = columnInsertIndexFromDivider(divider, firstRow)
  const sourceIndex = divider.insert === 'before' ? insertIndex : insertIndex - 1
  const insertedWidth = columnWidths[Math.min(Math.max(sourceIndex, 0), columnWidths.length - 1)]
    ?? TABLE_DEFAULT_COLUMN_WIDTH_PX
  const nextColumnWidths = columnWidths.slice()
  nextColumnWidths.splice(insertIndex, 0, insertedWidth)
  return nextColumnWidths
}

function applyTableColumnWidths(editor: Editor, columnWidths: number[]): boolean {
  const tableResult = findTable(editor.state.selection.$from)
  if (!tableResult) return false

  const table = tableResult.node
  const map = TableMap.get(table)
  if (!map.width) return false

  const averageWidth = Math.max(
    TABLE_DEFAULT_COLUMN_WIDTH_PX,
    Math.round(columnWidths.reduce((total, width) => total + width, 0) / Math.max(columnWidths.length, 1))
  )
  const normalizedWidths = Array.from({ length: map.width }, (_, index) => (
    Math.max(
      TABLE_DEFAULT_COLUMN_WIDTH_PX,
      Math.round(columnWidths[index] ?? columnWidths[index - 1] ?? averageWidth)
    )
  ))
  const nextAttrsByPosition = new Map<number, Record<string, unknown>>()

  for (let col = 0; col < map.width; col += 1) {
    const width = normalizedWidths[col]
    for (let row = 0; row < map.height; row += 1) {
      const mapIndex = row * map.width + col
      if (row && map.map[mapIndex] === map.map[mapIndex - map.width]) continue

      const relativePos = map.map[mapIndex]
      const cell = table.nodeAt(relativePos)
      if (!cell) continue

      const absolutePos = tableResult.start + relativePos
      const attrs = nextAttrsByPosition.get(absolutePos) ?? { ...cell.attrs }
      const colspan = typeof attrs.colspan === 'number' ? attrs.colspan : 1
      const colwidth = Array.isArray(attrs.colwidth)
        ? attrs.colwidth.slice()
        : Array.from({ length: colspan }, () => 0)
      const widthIndex = colspan === 1 ? 0 : col - map.colCount(relativePos)
      if (widthIndex < 0 || widthIndex >= colspan) continue

      colwidth[widthIndex] = width
      attrs.colwidth = colwidth
      nextAttrsByPosition.set(absolutePos, attrs)
    }
  }

  if (!nextAttrsByPosition.size) return false

  const tr = editor.state.tr
  for (const [position, attrs] of nextAttrsByPosition) {
    tr.setNodeMarkup(position, null, attrs)
  }
  if (!tr.docChanged) return false

  editor.view.dispatch(tr)
  return true
}

function runTableDividerInsert(editor: Editor, divider: TableDividerGeometry): boolean {
  const nextColumnWidths = insertedColumnWidthPlanFromDivider(divider)
  const position = tableCellPosition(editor, divider.cell)
  const chain = editor.chain()
  if (position !== null) {
    chain.setTextSelection(position)
  }
  chain.focus()

  if (divider.axis === 'row') {
    if (divider.insert === 'before') {
      chain.addRowBefore()
    } else {
      chain.addRowAfter()
    }
  } else if (divider.insert === 'before') {
    chain.addColumnBefore()
  } else {
    chain.addColumnAfter()
  }

  const didInsert = chain.run()
  if (didInsert && nextColumnWidths) {
    applyTableColumnWidths(editor, nextColumnWidths)
  }

  return didInsert
}

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
    let nextIndicator = table ? tableHoverIndicatorFromTable(surface, table) : null
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
    tableDivider,
    tableHoverIndicator
  }
}

export function TableDividerControls({
  divider,
  editor,
  onChanged,
  onKeepVisible,
  onRequestHide
}: {
  divider: TableDividerGeometry
  editor: Editor
  onChanged: (divider: TableDividerGeometry) => void
  onKeepVisible: () => void
  onRequestHide: () => void
}) {
  const isRowDivider = divider.axis === 'row'
  const label = isRowDivider ? 'Insert row' : 'Insert column'

  return (
    <div className="table-divider-layer" contentEditable={false}>
      <div
        className={`table-divider-line table-divider-line-${divider.axis}`}
        style={{
          height: divider.lineHeight === undefined ? undefined : `${divider.lineHeight}px`,
          left: `${divider.lineLeft}px`,
          top: `${divider.lineTop}px`,
          width: divider.lineWidth === undefined ? undefined : `${divider.lineWidth}px`
        }}
      />
      <button
        className={`table-divider-insert table-divider-insert-${divider.axis}`}
        type="button"
        aria-label={label}
        title={label}
        style={{
          left: `${divider.handleLeft}px`,
          top: `${divider.handleTop}px`
        }}
        onClick={(event) => {
          event.preventDefault()
          event.stopPropagation()
          if (runTableDividerInsert(editor, divider)) {
            onChanged(divider)
          }
        }}
        onPointerEnter={onKeepVisible}
        onPointerLeave={onRequestHide}
      >
        <Plus size={14} strokeWidth={2.5} />
      </button>
    </div>
  )
}

export function TableHoverIndicators({
  geometry,
  onDividerHandleEnter,
  onDividerHandleLeave,
  onKeepVisible,
  onRequestHide
}: {
  geometry: TableHoverIndicatorGeometry
  onDividerHandleEnter: (divider: TableDividerGeometry) => void
  onDividerHandleLeave: () => void
  onKeepVisible: () => void
  onRequestHide: () => void
}) {
  return (
    <div className="table-hover-indicator-layer" contentEditable={false}>
      <div
        className="table-hover-column-rail"
        style={{
          left: `${geometry.tableLeft}px`,
          top: `${geometry.tableTop}px`,
          width: `${geometry.tableWidth}px`
        }}
      >
        {geometry.columns.map((column, index) => (
          <span
            key={`column-segment-${index}`}
            className="table-hover-column-segment"
            style={{
              left: `${column.offset}px`,
              width: `${column.size}px`
            }}
            onPointerEnter={onKeepVisible}
            onPointerLeave={onRequestHide}
          >
            {column.beforeDivider ? (
              <span
                className="table-hover-column-dot table-hover-column-dot-before"
                onPointerEnter={() => {
                  onKeepVisible()
                  onDividerHandleEnter(column.beforeDivider!)
                }}
                onPointerLeave={() => {
                  onDividerHandleLeave()
                  onRequestHide()
                }}
              />
            ) : null}
            {column.afterDivider ? (
              <span
                className="table-hover-column-dot table-hover-column-dot-after"
                onPointerEnter={() => {
                  onKeepVisible()
                  onDividerHandleEnter(column.afterDivider!)
                }}
                onPointerLeave={() => {
                  onDividerHandleLeave()
                  onRequestHide()
                }}
              />
            ) : null}
          </span>
        ))}
      </div>
      <div
        className="table-hover-row-rail"
        style={{
          height: `${geometry.tableHeight + TABLE_HOVER_INDICATOR_RAIL_SIZE_PX}px`,
          left: `${geometry.tableLeft}px`,
          top: `${geometry.tableTop - TABLE_HOVER_INDICATOR_RAIL_SIZE_PX}px`
        }}
      >
        {geometry.rows.map((row, index) => (
          <span
            key={`row-segment-${index}`}
            className="table-hover-row-segment"
            style={{
              height: `${row.size}px`,
              top: `${row.offset + TABLE_HOVER_INDICATOR_RAIL_SIZE_PX}px`
            }}
            onPointerEnter={onKeepVisible}
            onPointerLeave={onRequestHide}
          >
            {row.beforeDivider ? (
              <span
                className="table-hover-row-dot table-hover-row-dot-before"
                onPointerEnter={() => {
                  onKeepVisible()
                  onDividerHandleEnter(row.beforeDivider!)
                }}
                onPointerLeave={() => {
                  onDividerHandleLeave()
                  onRequestHide()
                }}
              />
            ) : null}
            {row.afterDivider ? (
              <span
                className="table-hover-row-dot table-hover-row-dot-after"
                onPointerEnter={() => {
                  onKeepVisible()
                  onDividerHandleEnter(row.afterDivider!)
                }}
                onPointerLeave={() => {
                  onDividerHandleLeave()
                  onRequestHide()
                }}
              />
            ) : null}
          </span>
        ))}
      </div>
    </div>
  )
}
