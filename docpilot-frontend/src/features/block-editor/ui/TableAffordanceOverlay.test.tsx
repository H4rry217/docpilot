import { cleanup, render, screen } from '@testing-library/react'
import type { Editor } from '@tiptap/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TableHoverIndicators } from './TableAffordanceOverlay'
import type { TableHoverIndicatorGeometry } from './tableAffordanceTypes'

afterEach(() => {
  cleanup()
})

function hoverGeometry(): TableHoverIndicatorGeometry {
  const cell = document.createElement('td')
  return {
    columns: [{ cell, index: 0, offset: 0, size: 80, span: 1 }],
    rows: [{ cell, index: 0, offset: 0, size: 40, span: 1 }],
    tableHeight: 40,
    tableLeft: 20,
    tableTop: 30,
    tableWidth: 80
  }
}

function editorStub(): Editor {
  return {
    view: {
      dom: document.createElement('div')
    }
  } as unknown as Editor
}

describe('TableHoverIndicators', () => {
  it('keeps row and column indicators rendered when the table block is selected', () => {
    render(
      <div className="block-editor-surface has-selected-table-block">
        <TableHoverIndicators
          editor={editorStub()}
          geometry={hoverGeometry()}
          onDividerHandleEnter={vi.fn()}
          onDividerHandleLeave={vi.fn()}
          onKeepVisible={vi.fn()}
          onRequestHide={vi.fn()}
          onSelectColumn={vi.fn()}
          onSelectRow={vi.fn()}
        />
      </div>
    )

    expect(screen.getByLabelText('Select column')).toBeInTheDocument()
    expect(screen.getByLabelText('Select row')).toBeInTheDocument()
  })
})
