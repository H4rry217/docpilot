import { Editor } from '@tiptap/core'
import { describe, expect, it } from 'vitest'
import { editorExtensions } from './extensions'
import { selectCurrentTableCellContent } from './docpilotTableCellSelection'

function findTextPosition(editor: Editor, text: string): number {
  let found: number | null = null
  editor.state.doc.descendants((node, position) => {
    if (!node.isText || typeof node.text !== 'string') return true
    const offset = node.text.indexOf(text)
    if (offset === -1) return true
    found = position + offset
    return false
  })

  if (found === null) {
    throw new Error(`Text not found: ${text}`)
  }
  return found
}

function selectedText(editor: Editor): string {
  const { from, to } = editor.state.selection
  return editor.state.doc.textBetween(from, to, '\n')
}

function pressCtrlA(editor: Editor): boolean {
  const event = new KeyboardEvent('keydown', {
    key: 'a',
    ctrlKey: true,
    bubbles: true,
    cancelable: true
  })

  return Boolean(editor.view.someProp('handleKeyDown', (handler) => handler(editor.view, event)))
}

describe('DocpilotTableCellSelection', () => {
  it('selects only the active table cell content on Mod-a', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: {
        type: 'doc',
        content: [
          {
            type: 'paragraph',
            content: [{ type: 'text', text: 'Before' }]
          },
          {
            type: 'table',
            content: [
              {
                type: 'tableRow',
                content: [
                  {
                    type: 'tableCell',
                    content: [
                      {
                        type: 'paragraph',
                        content: [{ type: 'text', text: 'Cell one' }]
                      }
                    ]
                  },
                  {
                    type: 'tableCell',
                    content: [
                      {
                        type: 'paragraph',
                        content: [{ type: 'text', text: 'Cell two' }]
                      }
                    ]
                  }
                ]
              }
            ]
          },
          {
            type: 'paragraph',
            content: [{ type: 'text', text: 'After' }]
          }
        ]
      }
    })

    editor.commands.setTextSelection(findTextPosition(editor, 'Cell one') + 4)
    expect(pressCtrlA(editor)).toBe(true)

    expect(selectedText(editor)).toBe('Cell one')
    expect(selectedText(editor)).not.toContain('Before')
    expect(selectedText(editor)).not.toContain('After')
    editor.destroy()
  })

  it('does not intercept Mod-a outside table cells', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: {
        type: 'doc',
        content: [
          {
            type: 'paragraph',
            content: [{ type: 'text', text: 'Outside' }]
          }
        ]
      }
    })

    editor.commands.setTextSelection(findTextPosition(editor, 'Outside') + 3)

    expect(selectCurrentTableCellContent(editor)).toBe(false)
    expect(editor.state.selection.empty).toBe(true)
    editor.destroy()
  })
})
