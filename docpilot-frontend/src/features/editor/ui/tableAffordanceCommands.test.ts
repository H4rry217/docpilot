import type { Editor } from '@tiptap/react'
import { describe, expect, it, vi } from 'vitest'
import {
  insertedColumnWidthPlanFromDivider,
  runTableToolbarDelete
} from './tableAffordanceCommands'
import type { TableDividerGeometry } from './tableAffordanceTypes'

function setWidth(element: Element, width: number) {
  element.getBoundingClientRect = () => ({
    bottom: 20,
    height: 20,
    left: 0,
    right: width,
    toJSON: () => ({}),
    top: 0,
    width,
    x: 0,
    y: 0
  })
}

function tableDivider(insert: 'after' | 'before', cellIndex: number): TableDividerGeometry {
  const table = document.createElement('table')
  const row = document.createElement('tr')
  const first = document.createElement('td')
  const second = document.createElement('td')
  setWidth(first, 120)
  setWidth(second, 180)
  row.append(first, second)
  table.append(row)
  document.body.append(table)

  return {
    axis: 'column',
    cell: row.cells.item(cellIndex)!,
    handleLeft: 0,
    handleTop: 0,
    insert,
    lineLeft: 0,
    lineTop: 0
  }
}

describe('table affordance commands', () => {
  it('plans inserted column widths from the neighboring divider column', () => {
    const beforeFirst = tableDivider('before', 0)
    expect(insertedColumnWidthPlanFromDivider(beforeFirst)).toEqual([120, 120, 180])
    beforeFirst.cell.closest('table')?.remove()

    const afterSecond = tableDivider('after', 1)
    expect(insertedColumnWidthPlanFromDivider(afterSecond)).toEqual([120, 180, 180])
    afterSecond.cell.closest('table')?.remove()
  })

  it('does not create a column width plan for row dividers', () => {
    const rowDivider = tableDivider('after', 0)
    rowDivider.axis = 'row'

    expect(insertedColumnWidthPlanFromDivider(rowDivider)).toBeNull()
    rowDivider.cell.closest('table')?.remove()
  })

  it('falls back to deleting the table for non-cell selections', () => {
    const run = vi.fn(() => true)
    const deleteTable = vi.fn(() => ({ run }))
    const focus = vi.fn(() => ({ deleteTable }))
    const editor = {
      chain: () => ({ focus }),
      state: { selection: {} }
    } as unknown as Editor

    expect(runTableToolbarDelete(editor)).toBe(true)
    expect(deleteTable).toHaveBeenCalled()
    expect(run).toHaveBeenCalled()
  })
})
