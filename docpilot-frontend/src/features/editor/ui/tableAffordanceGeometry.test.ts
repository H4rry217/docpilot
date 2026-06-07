import { describe, expect, it } from 'vitest'
import {
  closestTableDivider,
  tableIndicatorSelectionRangeFromRect,
  tableDividerSignature,
  tableSelectionToolbarFromGeometry,
  visibleTableClientRect
} from './tableAffordanceGeometry'
import type { TableDividerGeometry, TableHoverIndicatorGeometry } from './tableAffordanceTypes'

function setRect(element: Element, rect: Partial<DOMRect>) {
  element.getBoundingClientRect = () => ({
    bottom: rect.bottom ?? 0,
    height: rect.height ?? (rect.bottom ?? 0) - (rect.top ?? 0),
    left: rect.left ?? 0,
    right: rect.right ?? 0,
    toJSON: () => ({}),
    top: rect.top ?? 0,
    width: rect.width ?? (rect.right ?? 0) - (rect.left ?? 0),
    x: rect.left ?? 0,
    y: rect.top ?? 0
  })
}

function divider(overrides: Partial<TableDividerGeometry> = {}): TableDividerGeometry {
  return {
    axis: 'column',
    cell: document.createElement('td'),
    handleLeft: 10,
    handleTop: 20,
    insert: 'after',
    lineLeft: 10,
    lineTop: 30,
    lineHeight: 100,
    ...overrides
  }
}

describe('table affordance geometry', () => {
  it('clips table bounds to the visible table wrapper', () => {
    const wrapper = document.createElement('div')
    wrapper.className = 'tableWrapper'
    const table = document.createElement('table')
    wrapper.append(table)
    document.body.append(wrapper)

    setRect(wrapper, { left: 20, top: 10, right: 80, bottom: 60 })
    setRect(table, { left: 0, top: 0, right: 100, bottom: 100 })

    expect(visibleTableClientRect(table)).toEqual({
      left: 20,
      top: 10,
      right: 80,
      bottom: 60
    })

    wrapper.remove()
  })

  it('creates stable divider signatures and finds the closest divider', () => {
    const reference = divider({ handleLeft: 12, handleTop: 20 })
    const far = divider({ handleLeft: 80, handleTop: 20 })
    const close = divider({ handleLeft: 14, handleTop: 20 })
    const row = divider({ axis: 'row', handleLeft: 12, handleTop: 20 })
    const geometry: TableHoverIndicatorGeometry = {
      columns: [{ cell: close.cell, index: 0, offset: 0, size: 40, span: 1, afterDivider: far, beforeDivider: close }],
      rows: [{ cell: row.cell, index: 0, offset: 0, size: 24, span: 1, afterDivider: row }],
      tableHeight: 100,
      tableLeft: 10,
      tableTop: 20,
      tableWidth: 100
    }

    expect(tableDividerSignature(reference)).toBe('column:after:12:20:10:30:100:0')
    expect(closestTableDivider(geometry, reference)).toBe(close)
  })

  it('positions selection toolbars for selected rows before selected columns', () => {
    const cell = document.createElement('td')
    const geometry: TableHoverIndicatorGeometry = {
      columns: [{ cell, index: 0, offset: 30, selected: true, size: 40, span: 1 }],
      rows: [{ cell, index: 0, offset: 50, selected: true, size: 24, span: 1 }],
      tableHeight: 100,
      tableLeft: 20,
      tableTop: 80,
      tableWidth: 120
    }

    expect(tableSelectionToolbarFromGeometry(geometry)).toEqual({ left: 18, top: 86 })
    expect(tableSelectionToolbarFromGeometry({ ...geometry, rows: [{ ...geometry.rows[0], selected: false }] }))
      .toEqual({ left: 48, top: 32 })
  })

  it('keeps row and column indicator selection states distinct', () => {
    const rect = { bottom: 1, left: 0, right: 4, top: 0 }

    expect(tableIndicatorSelectionRangeFromRect(rect, { rowSelection: true, columnSelection: false }))
      .toEqual({ rows: { from: 0, to: 0 }, columns: undefined })
    expect(tableIndicatorSelectionRangeFromRect(rect, { rowSelection: false, columnSelection: true }))
      .toEqual({ rows: undefined, columns: { from: 0, to: 3 } })
    expect(tableIndicatorSelectionRangeFromRect(rect, { rowSelection: false, columnSelection: false }))
      .toEqual({ rows: { from: 0, to: 0 }, columns: { from: 0, to: 3 } })
  })
})
