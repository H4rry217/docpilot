import { Extension, type Editor } from '@tiptap/core'
import type { ResolvedPos } from '@tiptap/pm/model'
import { TextSelection, type Selection } from '@tiptap/pm/state'

type TableCellContext = {
  before: number
  end: number
  start: number
}

function tableCellContext($pos: ResolvedPos): TableCellContext | null {
  for (let depth = $pos.depth; depth > 0; depth -= 1) {
    const role = $pos.node(depth).type.spec.tableRole
    if (role === 'cell' || role === 'header_cell') {
      return {
        before: $pos.before(depth),
        start: $pos.start(depth),
        end: $pos.end(depth)
      }
    }
  }

  return null
}

function currentTableCellContentSelection(editor: Editor): Selection | null {
  const { doc, selection } = editor.state
  const fromCell = tableCellContext(selection.$from)
  const toCell = tableCellContext(selection.$to)
  if (!fromCell || !toCell || fromCell.before !== toCell.before) {
    return null
  }

  const nextSelection = TextSelection.between(
    doc.resolve(fromCell.start),
    doc.resolve(fromCell.end),
    1
  )
  if (nextSelection.from < fromCell.start || nextSelection.to > fromCell.end) {
    return null
  }

  return nextSelection
}

export function selectCurrentTableCellContent(editor: Editor): boolean {
  const nextSelection = currentTableCellContentSelection(editor)
  if (!nextSelection) return false

  editor.view.dispatch(
    editor.state.tr
      .setSelection(nextSelection)
      .scrollIntoView()
  )
  return true
}

export const DocpilotTableCellSelection = Extension.create({
  name: 'docpilotTableCellSelection',
  priority: 1000,

  addKeyboardShortcuts() {
    return {
      'Mod-a': () => selectCurrentTableCellContent(this.editor)
    }
  }
})
