import { CellSelection, findCellPos, findTable, TableMap } from '@tiptap/pm/tables'
import type { Editor } from '@tiptap/react'
import { TABLE_DEFAULT_COLUMN_WIDTH_PX } from '../model/tableConstants'
import type {
  TableDividerAxis,
  TableDividerGeometry,
  TableHoverIndicatorSegment
} from './tableAffordanceTypes'

export function tableCellPosition(editor: Editor, cell: HTMLTableCellElement): number | null {
  const rect = cell.getBoundingClientRect()
  const position = editor.view.posAtCoords({
    left: rect.left + Math.min(8, Math.max(rect.width / 2, 1)),
    top: rect.top + Math.min(8, Math.max(rect.height / 2, 1))
  })?.pos
  return typeof position === 'number' ? position : null
}

export function tableCellResolvedPosition(editor: Editor, cell: HTMLTableCellElement) {
  const position = tableCellPosition(editor, cell)
  if (position === null) return null

  return findCellPos(editor.state.doc, position) ?? null
}

export function runTableIndicatorSelect(
  editor: Editor,
  axis: TableDividerAxis,
  segment: TableHoverIndicatorSegment
): boolean {
  const $cell = tableCellResolvedPosition(editor, segment.cell)
  if (!$cell) return false

  const tableResult = findTable($cell)
  if (!tableResult) return false

  const table = tableResult.node
  const map = TableMap.get(table)
  if (!map.width || !map.height) return false

  const startIndex = Math.max(0, segment.index)
  let anchorCell: number
  let headCell: number
  let selection: CellSelection

  if (axis === 'column') {
    const startColumn = Math.min(startIndex, map.width - 1)
    const endColumn = Math.min(startIndex + Math.max(segment.span, 1) - 1, map.width - 1)
    anchorCell = map.positionAt(map.height - 1, endColumn, table)
    headCell = map.positionAt(0, startColumn, table)
    selection = CellSelection.colSelection(
      editor.state.doc.resolve(tableResult.start + anchorCell),
      editor.state.doc.resolve(tableResult.start + headCell)
    )
  } else {
    const rowIndex = Math.min(startIndex, map.height - 1)
    anchorCell = map.positionAt(rowIndex, map.width - 1, table)
    headCell = map.positionAt(rowIndex, 0, table)
    selection = CellSelection.rowSelection(
      editor.state.doc.resolve(tableResult.start + anchorCell),
      editor.state.doc.resolve(tableResult.start + headCell)
    )
  }

  editor.view.dispatch(editor.state.tr.setSelection(selection))
  editor.view.focus()
  return true
}

export function runTableToolbarDelete(editor: Editor): boolean {
  const selection = editor.state.selection
  const chain = editor.chain().focus()
  if (selection instanceof CellSelection && selection.isRowSelection() && selection.isColSelection()) {
    return chain.deleteTable().run()
  }
  if (selection instanceof CellSelection && selection.isRowSelection()) {
    return chain.deleteRow().run()
  }
  if (selection instanceof CellSelection && selection.isColSelection()) {
    return chain.deleteColumn().run()
  }

  return chain.deleteTable().run()
}

export function columnWidthsFromTableDom(table: HTMLTableElement): number[] {
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

export function columnInsertIndexFromDivider(divider: TableDividerGeometry, firstRow: HTMLTableRowElement): number {
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

export function insertedColumnWidthPlanFromDivider(divider: TableDividerGeometry): number[] | null {
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

export function applyTableColumnWidths(editor: Editor, columnWidths: number[]): boolean {
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

export function runTableDividerInsert(editor: Editor, divider: TableDividerGeometry): boolean {
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
