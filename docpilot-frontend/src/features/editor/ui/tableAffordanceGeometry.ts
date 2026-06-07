import { CellSelection, columnResizingPluginKey, findTable, TableMap } from '@tiptap/pm/tables'
import type { ResolvedPos } from '@tiptap/pm/model'
import type { Selection } from '@tiptap/pm/state'
import type { Editor } from '@tiptap/react'
import {
  TABLE_DIVIDER_GUTTER_PX,
  TABLE_DIVIDER_HOTSPOT_RADIUS_PX,
  TABLE_HOVER_INDICATOR_DOT_ANCHOR_OUTSET_PX
} from './tableAffordanceConstants'
import type {
  Point,
  Rect,
  TableDividerGeometry,
  TableHoverIndicatorGeometry,
  TableIndicatorSelectionRange,
  TableSelectionToolbarGeometry
} from './tableAffordanceTypes'

export function pointFromClientPoint(point: Point, element: HTMLElement): Point {
  const rect = element.getBoundingClientRect()
  return {
    x: point.x - rect.left,
    y: point.y - rect.top
  }
}

export function rectContainsPoint(rect: Rect, point: Point, inset = 0): boolean {
  return point.x >= rect.left - inset
    && point.x <= rect.right + inset
    && point.y >= rect.top - inset
    && point.y <= rect.bottom + inset
}

export function visibleTableClientRect(table: HTMLTableElement): Rect | null {
  const tableRect = table.getBoundingClientRect()
  const wrapperRect = table.closest<HTMLElement>('.tableWrapper')?.getBoundingClientRect() ?? tableRect
  const left = Math.max(tableRect.left, wrapperRect.left)
  const right = Math.min(tableRect.right, wrapperRect.right)
  const top = Math.max(tableRect.top, wrapperRect.top)
  const bottom = Math.min(tableRect.bottom, wrapperRect.bottom)

  if (right <= left || bottom <= top) return null
  return { left, top, right, bottom }
}

export function tableFromPoint(editorDom: HTMLElement, point: Point): HTMLTableElement | null {
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

export function isTableDividerHotspotPoint(
  point: Point,
  surface: HTMLElement | null,
  divider: TableDividerGeometry
): boolean {
  if (!surface) return false

  const element = document.elementFromPoint(point.x, point.y)
  if (element instanceof HTMLElement && (
    element.closest('.table-divider-layer')
    || element.closest('.table-hover-indicator-layer')
  )) {
    return true
  }

  const surfacePoint = pointFromClientPoint(point, surface)
  return Math.abs(surfacePoint.x - divider.handleLeft) <= TABLE_DIVIDER_HOTSPOT_RADIUS_PX
    && Math.abs(surfacePoint.y - divider.handleTop) <= TABLE_DIVIDER_HOTSPOT_RADIUS_PX
}

export function tableDomFromTableResult(editor: Editor, tablePos: number): HTMLTableElement | null {
  const tableDom = editor.view.nodeDOM(tablePos)
  if (tableDom instanceof HTMLTableElement) return tableDom
  if (tableDom instanceof HTMLElement) {
    return tableDom.matches('table') ? tableDom as HTMLTableElement : tableDom.querySelector('table')
  }
  return null
}

function isCellSelectionLike(selection: Selection): selection is Selection & {
  $anchorCell: ResolvedPos
  $headCell: ResolvedPos
} {
  return '$anchorCell' in selection && '$headCell' in selection
}

type TableSelectionRect = {
  bottom: number
  left: number
  right: number
  top: number
}

export function tableIndicatorSelectionRangeFromRect(
  rect: TableSelectionRect,
  {
    columnSelection,
    rowSelection
  }: {
    columnSelection: boolean
    rowSelection: boolean
  }
): TableIndicatorSelectionRange {
  return {
    columns: rect.left < rect.right && (!rowSelection || columnSelection)
      ? { from: rect.left, to: rect.right - 1 }
      : undefined,
    rows: rect.top < rect.bottom && (!columnSelection || rowSelection)
      ? { from: rect.top, to: rect.bottom - 1 }
      : undefined
  }
}

export function tableSelectionRangeForTable(editor: Editor, table: HTMLTableElement): TableIndicatorSelectionRange | null {
  const selection = editor.state.selection
  if (!isCellSelectionLike(selection)) return null

  const tableResult = findTable(selection.$anchorCell)
  if (!tableResult) return null
  const selectedTable = tableDomFromTableResult(editor, tableResult.pos)
  if (selectedTable !== table) return null

  const map = TableMap.get(tableResult.node)
  const rect = map.rectBetween(
    selection.$anchorCell.pos - tableResult.start,
    selection.$headCell.pos - tableResult.start
  )
  const rowSelection = selection instanceof CellSelection && selection.isRowSelection()
  const columnSelection = selection instanceof CellSelection && selection.isColSelection()

  return tableIndicatorSelectionRangeFromRect(rect, { columnSelection, rowSelection })
}

export function tableHoverIndicatorFromTable(
  editor: Editor,
  surface: HTMLElement,
  table: HTMLTableElement
): TableHoverIndicatorGeometry | null {
  const surfaceRect = surface.getBoundingClientRect()
  const tableRect = table.getBoundingClientRect()
  const wrapperRect = table.closest<HTMLElement>('.tableWrapper')?.getBoundingClientRect() ?? tableRect
  const firstRow = table.rows.item(0)
  if (!firstRow) return null

  const visibleLeft = Math.max(tableRect.left, wrapperRect.left)
  const visibleRight = Math.min(tableRect.right, wrapperRect.right)
  const visibleWidth = visibleRight - visibleLeft
  if (visibleWidth <= 0) return null

  const tableLeft = visibleLeft - surfaceRect.left
  const tableTop = tableRect.top - surfaceRect.top
  const tableHeight = tableRect.height
  const columnHandleTop = tableTop - TABLE_HOVER_INDICATOR_DOT_ANCHOR_OUTSET_PX
  const rowHandleLeft = tableLeft - TABLE_HOVER_INDICATOR_DOT_ANCHOR_OUTSET_PX
  const selectionRange = tableSelectionRangeForTable(editor, table)

  const columns = Array.from(firstRow.cells).flatMap((cell, index) => {
    const span = Math.max(cell.colSpan || 1, 1)
    const currentColumnIndex = index
    const rect = cell.getBoundingClientRect()
    const clippedLeft = Math.max(rect.left, visibleLeft)
    const clippedRight = Math.min(rect.right, visibleRight)
    const size = clippedRight - clippedLeft
    if (size <= 0) return []
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
      lineLeft: right,
      lineTop: tableTop,
      lineHeight: tableHeight
    } : undefined
    const beforeDivider: TableDividerGeometry | undefined = index === 0 && isLeftDividerVisible ? {
      axis: 'column',
      cell,
      handleLeft: left,
      handleTop: columnHandleTop,
      insert: 'before',
      lineLeft: left,
      lineTop: tableTop,
      lineHeight: tableHeight
    } : undefined

    return [{
      afterDivider,
      beforeDivider,
      cell,
      index: currentColumnIndex,
      offset,
      selected: Boolean(selectionRange?.columns
        && currentColumnIndex >= selectionRange.columns.from
        && currentColumnIndex <= selectionRange.columns.to),
      size,
      span
    }]
  })

  const rows = Array.from(table.rows).map((row, index) => {
    const rect = row.getBoundingClientRect()
    const cell = row.cells.item(0)
    const size = rect.height
    const offset = rect.top - tableRect.top
    const afterDivider: TableDividerGeometry = {
      axis: 'row',
      cell: cell ?? firstRow.cells.item(0)!,
      handleLeft: rowHandleLeft,
      handleTop: rect.bottom - surfaceRect.top,
      insert: 'after',
      lineLeft: tableLeft,
      lineTop: rect.bottom - surfaceRect.top,
      lineWidth: visibleWidth
    }
    const beforeDivider: TableDividerGeometry | undefined = index === 0 ? {
      axis: 'row',
      cell: cell ?? firstRow.cells.item(0)!,
      handleLeft: rowHandleLeft,
      handleTop: rect.top - surfaceRect.top,
      insert: 'before',
      lineLeft: tableLeft,
      lineTop: rect.top - surfaceRect.top,
      lineWidth: visibleWidth
    } : undefined

    return {
      afterDivider,
      beforeDivider,
      cell: cell ?? firstRow.cells.item(0)!,
      index,
      offset,
      selected: Boolean(selectionRange?.rows
        && index >= selectionRange.rows.from
        && index <= selectionRange.rows.to),
      size,
      span: 1
    }
  })

  return {
    columns,
    rows,
    tableHeight,
    tableLeft,
    tableTop,
    tableWidth: visibleWidth
  }
}

export function tableHoverIndicatorFromPoint(editor: Editor, surface: HTMLElement, point: Point): TableHoverIndicatorGeometry | null {
  const table = tableFromPoint(editor.view.dom, point)
  return table ? tableHoverIndicatorFromTable(editor, surface, table) : null
}

export function tableFromEditorSelection(editor: Editor): HTMLTableElement | null {
  const tableResult = findTable(editor.state.selection.$from)
  if (!tableResult) return null
  return tableDomFromTableResult(editor, tableResult.pos)
}

export function isTableColumnResizing(editor: Editor): boolean {
  const resizeState = columnResizingPluginKey.getState(editor.state)
  return Boolean(resizeState && typeof resizeState.activeHandle === 'number' && resizeState.activeHandle > -1)
}

export function tableDividerSignature(divider: TableDividerGeometry): string {
  return [
    divider.axis,
    divider.insert,
    Math.round(divider.handleLeft),
    Math.round(divider.handleTop),
    Math.round(divider.lineLeft),
    Math.round(divider.lineTop),
    Math.round(divider.lineHeight ?? 0),
    Math.round(divider.lineWidth ?? 0)
  ].join(':')
}

export function tableDividersFromIndicator(geometry: TableHoverIndicatorGeometry): TableDividerGeometry[] {
  return [
    ...geometry.columns.flatMap((column) => [column.beforeDivider, column.afterDivider].filter(Boolean)),
    ...geometry.rows.flatMap((row) => [row.beforeDivider, row.afterDivider].filter(Boolean))
  ] as TableDividerGeometry[]
}

export function closestTableDivider(
  geometry: TableHoverIndicatorGeometry,
  reference: TableDividerGeometry
): TableDividerGeometry | null {
  return tableDividersFromIndicator(geometry)
    .filter((divider) => divider.axis === reference.axis)
    .reduce<TableDividerGeometry | null>((closest, divider) => {
      if (!closest) return divider
      const deltaLeft = Math.abs(divider.handleLeft - reference.handleLeft)
      const deltaTop = Math.abs(divider.handleTop - reference.handleTop)
      const insertPenalty = divider.insert === reference.insert ? 0 : 1000
      const score = deltaLeft + deltaTop + insertPenalty
      const closestScore = Math.abs(closest.handleLeft - reference.handleLeft)
        + Math.abs(closest.handleTop - reference.handleTop)
        + (closest.insert === reference.insert ? 0 : 1000)
      return score < closestScore ? divider : closest
    }, null)
}

export function tableSelectionToolbarFromGeometry(geometry: TableHoverIndicatorGeometry): TableSelectionToolbarGeometry | null {
  const selectedRows = geometry.rows.filter((row) => row.selected)
  if (selectedRows.length) {
    const firstRow = selectedRows[0]
    return {
      left: Math.max(8, geometry.tableLeft - 2),
      top: Math.max(8, geometry.tableTop + firstRow.offset - 44)
    }
  }

  const selectedColumns = geometry.columns.filter((column) => column.selected)
  if (selectedColumns.length) {
    const firstColumn = selectedColumns[0]
    return {
      left: Math.max(8, geometry.tableLeft + firstColumn.offset - 2),
      top: Math.max(8, geometry.tableTop - 48)
    }
  }

  return null
}
