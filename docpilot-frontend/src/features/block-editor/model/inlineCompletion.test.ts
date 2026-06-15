import { Editor } from '@tiptap/core'
import { describe, expect, it } from 'vitest'
import { editorExtensions } from './extensions'
import {
  clearInlineCompletion,
  getInlineCompletionSuggestion,
  setInlineCompletionSuggestion
} from './inlineCompletion'

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

function pressKey(editor: Editor, key: string): boolean {
  const event = new KeyboardEvent('keydown', {
    key,
    bubbles: true,
    cancelable: true
  })
  return Boolean(editor.view.someProp('handleKeyDown', (handler) => handler(editor.view, event)))
}

describe('DocpilotInlineCompletion', () => {
  it('accepts inline markdown on Tab', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: '<p>Hello </p>'
    })
    const position = findTextPosition(editor, 'Hello') + 'Hello '.length
    editor.commands.setTextSelection(position)
    setInlineCompletionSuggestion(editor, {
      requestSeq: 1,
      from: position,
      to: position,
      markdown: '**重要**',
      previewText: '重要',
      shape: 'SENTENCE'
    })

    expect(pressKey(editor, 'Tab')).toBe(true)
    expect(editor.getHTML()).toContain('<strong>重要</strong>')
    expect(getInlineCompletionSuggestion(editor)).toBeNull()
    editor.destroy()
  })

  it('normalizes escaped blockquote markers before accepting inline completion', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: '<p>Hello </p>'
    })
    const position = findTextPosition(editor, 'Hello') + 'Hello '.length
    editor.commands.setTextSelection(position)
    setInlineCompletionSuggestion(editor, {
      requestSeq: 1,
      from: position,
      to: position,
      markdown: '&gt; &gt; &gt; nested quote',
      previewText: 'nested quote',
      shape: 'SENTENCE'
    })

    expect(pressKey(editor, 'Tab')).toBe(true)
    expect(editor.getText()).toContain('nested quote')
    expect(editor.getText()).not.toContain('&gt;')
    expect(editor.getText()).not.toContain('> > >')
    editor.destroy()
  })

  it('accepts block markdown for list items', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: '<p>Items:</p>'
    })
    const position = findTextPosition(editor, 'Items:') + 'Items:'.length
    editor.commands.setTextSelection(position)
    setInlineCompletionSuggestion(editor, {
      requestSeq: 1,
      from: position,
      to: position,
      markdown: '- 新项目',
      previewText: '新项目',
      shape: 'LIST_ITEM'
    })

    expect(pressKey(editor, 'Tab')).toBe(true)
    expect(editor.getJSON()).toEqual(
      expect.objectContaining({
        content: expect.arrayContaining([
          expect.objectContaining({ type: 'bulletList' })
        ])
      })
    )
    expect(editor.getText()).toContain('新项目')
    editor.destroy()
  })

  it('keeps code line completions as raw text', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: {
        type: 'doc',
        content: [
          {
            type: 'codeBlock',
            content: [{ type: 'text', text: 'const value = ' }]
          }
        ]
      }
    })
    const position = findTextPosition(editor, 'const value = ') + 'const value = '.length
    editor.commands.setTextSelection(position)
    setInlineCompletionSuggestion(editor, {
      requestSeq: 1,
      from: position,
      to: position,
      markdown: '`literal`',
      previewText: '`literal`',
      shape: 'CODE_LINE'
    })

    expect(pressKey(editor, 'Tab')).toBe(true)
    expect(editor.getText()).toContain('const value = `literal`')
    editor.destroy()
  })

  it('clears suggestions on Escape and leaves Tab alone without a suggestion', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: '<p>Hello</p>'
    })
    const position = findTextPosition(editor, 'Hello') + 'Hello'.length
    editor.commands.setTextSelection(position)
    setInlineCompletionSuggestion(editor, {
      requestSeq: 1,
      from: position,
      to: position,
      markdown: ' world',
      previewText: ' world',
      shape: 'SENTENCE'
    })

    expect(pressKey(editor, 'Escape')).toBe(true)
    expect(getInlineCompletionSuggestion(editor)).toBeNull()
    clearInlineCompletion(editor)
    expect(pressKey(editor, 'Tab')).toBe(false)
    editor.destroy()
  })
})
