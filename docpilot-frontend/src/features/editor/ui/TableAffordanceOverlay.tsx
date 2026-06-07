import type { Editor } from '@tiptap/react'
import {
  Bold,
  Code2,
  Combine,
  Highlighter,
  Italic,
  Plus,
  Strikethrough,
  Trash2,
  Underline
} from 'lucide-react'
import {
  useEffect,
  useState,
  type ReactNode
} from 'react'
import { TABLE_HOVER_INDICATOR_RAIL_SIZE_PX } from './tableAffordanceConstants'
import {
  runTableDividerInsert,
  runTableToolbarDelete
} from './tableAffordanceCommands'
import { tableSelectionToolbarFromGeometry } from './tableAffordanceGeometry'
import type {
  TableDividerAxis,
  TableDividerGeometry,
  TableHoverIndicatorGeometry,
  TableHoverIndicatorSegment,
  TableSelectionToolbarGeometry
} from './tableAffordanceTypes'
import './TableAffordances.css'

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

function TableSelectionToolbarButton({
  active,
  children,
  danger,
  onClick,
  onPreviewChange,
  title
}: {
  active?: boolean
  children: ReactNode
  danger?: boolean
  onClick: () => void
  onPreviewChange?: (active: boolean) => void
  title: string
}) {
  return (
    <button
      className={`table-selection-toolbar-button ${active ? 'is-active' : ''} ${danger ? 'is-danger' : ''}`}
      type="button"
      aria-label={title}
      aria-pressed={active === undefined ? undefined : active}
      title={title}
      onClick={(event) => {
        event.preventDefault()
        event.stopPropagation()
        onClick()
      }}
      onBlur={() => onPreviewChange?.(false)}
      onFocus={() => onPreviewChange?.(true)}
      onPointerEnter={() => onPreviewChange?.(true)}
      onPointerLeave={() => onPreviewChange?.(false)}
      onPointerDown={(event) => {
        event.preventDefault()
        event.stopPropagation()
      }}
    >
      {children}
    </button>
  )
}

function TableSelectionToolbar({
  editor,
  geometry,
  onDeletePreviewChange,
  onKeepVisible,
  onRequestHide
}: {
  editor: Editor
  geometry: TableSelectionToolbarGeometry
  onDeletePreviewChange: (active: boolean) => void
  onKeepVisible: () => void
  onRequestHide: () => void
}) {
  const iconSize = 15

  return (
    <div
      className="table-selection-toolbar"
      contentEditable={false}
      style={{
        left: `${geometry.left}px`,
        top: `${geometry.top}px`
      }}
      onPointerEnter={onKeepVisible}
      onPointerLeave={onRequestHide}
    >
      <TableSelectionToolbarButton
        active={editor.isActive('bold')}
        title="Bold"
        onClick={() => editor.chain().focus().toggleBold().run()}
      >
        <Bold size={iconSize} strokeWidth={2.4} />
      </TableSelectionToolbarButton>
      <TableSelectionToolbarButton
        active={editor.isActive('strike')}
        title="Strikethrough"
        onClick={() => editor.chain().focus().toggleStrike().run()}
      >
        <Strikethrough size={iconSize} strokeWidth={2.4} />
      </TableSelectionToolbarButton>
      <TableSelectionToolbarButton
        active={editor.isActive('italic')}
        title="Italic"
        onClick={() => editor.chain().focus().toggleItalic().run()}
      >
        <Italic size={iconSize} strokeWidth={2.4} />
      </TableSelectionToolbarButton>
      <TableSelectionToolbarButton
        active={editor.isActive('underline')}
        title="Underline"
        onClick={() => editor.chain().focus().toggleMark('underline').run()}
      >
        <Underline size={iconSize} strokeWidth={2.4} />
      </TableSelectionToolbarButton>
      <TableSelectionToolbarButton
        active={editor.isActive('code')}
        title="Code"
        onClick={() => editor.chain().focus().toggleCode().run()}
      >
        <Code2 size={iconSize} strokeWidth={2.4} />
      </TableSelectionToolbarButton>
      <TableSelectionToolbarButton
        active={editor.isActive('highlight')}
        title="Highlight"
        onClick={() => editor.chain().focus().toggleMark('highlight').run()}
      >
        <Highlighter size={iconSize} strokeWidth={2.4} />
      </TableSelectionToolbarButton>
      <span className="table-selection-toolbar-divider" />
      <TableSelectionToolbarButton
        title="Merge cells"
        onClick={() => editor.chain().focus().mergeCells().run()}
      >
        <Combine size={iconSize} strokeWidth={2.4} />
      </TableSelectionToolbarButton>
      <TableSelectionToolbarButton
        danger
        title="Delete selection"
        onPreviewChange={onDeletePreviewChange}
        onClick={() => runTableToolbarDelete(editor)}
      >
        <Trash2 size={iconSize} strokeWidth={2.4} />
      </TableSelectionToolbarButton>
    </div>
  )
}

export function TableHoverIndicators({
  editor,
  geometry,
  onDividerHandleEnter,
  onDividerHandleLeave,
  onKeepVisible,
  onRequestHide,
  onSelectColumn,
  onSelectRow
}: {
  editor: Editor
  geometry: TableHoverIndicatorGeometry
  onDividerHandleEnter: (divider: TableDividerGeometry) => void
  onDividerHandleLeave: () => void
  onKeepVisible: () => void
  onRequestHide: () => void
  onSelectColumn: (column: TableHoverIndicatorSegment) => void
  onSelectRow: (row: TableHoverIndicatorSegment) => void
}) {
  const toolbarGeometry = tableSelectionToolbarFromGeometry(geometry)
  const hasSelectionToolbar = toolbarGeometry !== null
  const [deletePreviewActive, setDeletePreviewActive] = useState(false)

  useEffect(() => {
    editor.view.dom.classList.toggle('table-delete-preview', deletePreviewActive)

    return () => {
      editor.view.dom.classList.remove('table-delete-preview')
    }
  }, [deletePreviewActive, editor])

  useEffect(() => {
    if (!hasSelectionToolbar) {
      setDeletePreviewActive(false)
    }
  }, [hasSelectionToolbar])

  return (
    <div
      className={`table-hover-indicator-layer ${deletePreviewActive ? 'is-delete-preview' : ''}`}
      contentEditable={false}
    >
      {toolbarGeometry ? (
        <TableSelectionToolbar
          editor={editor}
          geometry={toolbarGeometry}
          onDeletePreviewChange={setDeletePreviewActive}
          onKeepVisible={onKeepVisible}
          onRequestHide={onRequestHide}
        />
      ) : null}
      <div
        className="table-hover-column-rail"
        style={{
          left: `${geometry.tableLeft}px`,
          top: `${geometry.tableTop}px`,
          width: `${geometry.tableWidth}px`
        }}
      >
        {geometry.columns.map((column, index) => (
          <button
            key={`column-segment-${index}`}
            className={`table-hover-column-segment ${column.selected ? 'is-selected' : ''}`}
            type="button"
            aria-label="Select column"
            title="Select column"
            style={{
              left: `${column.offset}px`,
              width: `${column.size}px`
            }}
            onClick={(event) => {
              event.preventDefault()
              event.stopPropagation()
              onSelectColumn(column)
            }}
            onPointerEnter={onKeepVisible}
            onPointerLeave={onRequestHide}
            onPointerDown={(event) => {
              event.preventDefault()
              event.stopPropagation()
            }}
          >
            {column.beforeDivider ? (
              <span
                className="table-hover-column-dot table-hover-column-dot-before"
                onClick={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                }}
                onPointerEnter={() => {
                  onKeepVisible()
                  onDividerHandleEnter(column.beforeDivider!)
                }}
                onPointerLeave={() => {
                  onDividerHandleLeave()
                  onRequestHide()
                }}
                onPointerDown={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                }}
              />
            ) : null}
            {column.afterDivider ? (
              <span
                className="table-hover-column-dot table-hover-column-dot-after"
                onClick={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                }}
                onPointerEnter={() => {
                  onKeepVisible()
                  onDividerHandleEnter(column.afterDivider!)
                }}
                onPointerLeave={() => {
                  onDividerHandleLeave()
                  onRequestHide()
                }}
                onPointerDown={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                }}
              />
            ) : null}
          </button>
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
          <button
            key={`row-segment-${index}`}
            className={`table-hover-row-segment ${row.selected ? 'is-selected' : ''}`}
            type="button"
            aria-label="Select row"
            title="Select row"
            style={{
              height: `${row.size}px`,
              top: `${row.offset + TABLE_HOVER_INDICATOR_RAIL_SIZE_PX}px`
            }}
            onClick={(event) => {
              event.preventDefault()
              event.stopPropagation()
              onSelectRow(row)
            }}
            onPointerEnter={onKeepVisible}
            onPointerLeave={onRequestHide}
            onPointerDown={(event) => {
              event.preventDefault()
              event.stopPropagation()
            }}
          >
            {row.beforeDivider ? (
              <span
                className="table-hover-row-dot table-hover-row-dot-before"
                onClick={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                }}
                onPointerEnter={() => {
                  onKeepVisible()
                  onDividerHandleEnter(row.beforeDivider!)
                }}
                onPointerLeave={() => {
                  onDividerHandleLeave()
                  onRequestHide()
                }}
                onPointerDown={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                }}
              />
            ) : null}
            {row.afterDivider ? (
              <span
                className="table-hover-row-dot table-hover-row-dot-after"
                onClick={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                }}
                onPointerEnter={() => {
                  onKeepVisible()
                  onDividerHandleEnter(row.afterDivider!)
                }}
                onPointerLeave={() => {
                  onDividerHandleLeave()
                  onRequestHide()
                }}
                onPointerDown={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                }}
              />
            ) : null}
          </button>
        ))}
      </div>
    </div>
  )
}

export type {
  TableDividerAxis,
  TableDividerGeometry,
  TableHoverIndicatorGeometry,
  TableHoverIndicatorSegment
}
