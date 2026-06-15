import { Editor } from '@tiptap/core'
import { describe, expect, it } from 'vitest'
import type { InlineCompletionShape } from '../../inline-completion/api/inlineCompletionApi'
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

function pressAltKey(editor: Editor, key: string): boolean {
  const event = new KeyboardEvent('keydown', {
    key,
    altKey: true,
    bubbles: true,
    cancelable: true
  })
  return Boolean(editor.view.someProp('handleKeyDown', (handler) => handler(editor.view, event)))
}

function singleCandidateSuggestion(input: {
  from: number
  to: number
  markdown: string
  previewText: string
  shape: InlineCompletionShape
}) {
  return {
    requestSeq: 1,
    from: input.from,
    to: input.to,
    shape: input.shape,
    candidates: [
      {
        index: 0,
        markdown: input.markdown,
        previewText: input.previewText
      }
    ],
    selectedIndex: 0,
    menuOpen: false
  }
}

describe('DocpilotInlineCompletion', () => {
  it('accepts inline markdown on Tab', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: '<p>Hello </p>'
    })
    const position = findTextPosition(editor, 'Hello') + 'Hello '.length
    editor.commands.setTextSelection(position)
    setInlineCompletionSuggestion(editor, singleCandidateSuggestion({
      from: position,
      to: position,
      markdown: '**important**',
      previewText: 'important',
      shape: 'SENTENCE'
    }))

    expect(pressKey(editor, 'Tab')).toBe(true)
    expect(editor.getHTML()).toContain('<strong>important</strong>')
    expect(getInlineCompletionSuggestion(editor)).toBeNull()
    editor.destroy()
  })

  it('accepts the current suggestion on Enter', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: '<p>Hello </p>'
    })
    const position = findTextPosition(editor, 'Hello') + 'Hello '.length
    editor.commands.setTextSelection(position)
    setInlineCompletionSuggestion(editor, singleCandidateSuggestion({
      from: position,
      to: position,
      markdown: ' world',
      previewText: ' world',
      shape: 'SHORT'
    }))

    expect(pressKey(editor, 'Enter')).toBe(true)
    expect(editor.getText()).toContain('Hello world')
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
    setInlineCompletionSuggestion(editor, singleCandidateSuggestion({
      from: position,
      to: position,
      markdown: '&gt; &gt; &gt; nested quote',
      previewText: 'nested quote',
      shape: 'SENTENCE'
    }))

    expect(pressKey(editor, 'Tab')).toBe(true)
    expect(editor.getText()).toContain('nested quote')
    expect(editor.getText()).not.toContain('&gt;')
    expect(editor.getText()).not.toContain('> > >')
    editor.destroy()
  })

  it('places block markdown suggestions after the current heading even when shape is short', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: '<h1>银杏索引</h1>'
    })
    const position = findTextPosition(editor, '银杏索引') + '银杏索引'.length
    editor.commands.setTextSelection(position)
    setInlineCompletionSuggestion(editor, singleCandidateSuggestion({
      from: position,
      to: position,
      markdown: '## 简介\n\n银杏索引是一个高效的文档检索工具。',
      previewText: '简介\n\n银杏索引是一个高效的文档检索工具。',
      shape: 'SHORT'
    }))

    const widget = editor.view.dom.querySelector('.docpilot-inline-completion-widget')
    expect(widget?.classList.contains('is-block')).toBe(true)
    expect(editor.view.dom.querySelector('.docpilot-inline-completion-ghost-heading')?.textContent)
      .toBe('简介')
    expect(pressKey(editor, 'Tab')).toBe(true)
    expect(editor.getJSON().content?.[0]).toEqual(
      expect.objectContaining({
        type: 'heading',
        content: [expect.objectContaining({ text: '银杏索引' })]
      })
    )
    expect(editor.getJSON().content?.[1]).toEqual(
      expect.objectContaining({
        type: 'heading',
        content: [expect.objectContaining({ text: '简介' })]
      })
    )
    expect(editor.getText()).toContain('银杏索引是一个高效的文档检索工具。')
    editor.destroy()
  })

  it('accepts block markdown for list items', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: '<p>Items:</p>'
    })
    const position = findTextPosition(editor, 'Items:') + 'Items:'.length
    editor.commands.setTextSelection(position)
    setInlineCompletionSuggestion(editor, singleCandidateSuggestion({
      from: position,
      to: position,
      markdown: '- new item',
      previewText: 'new item',
      shape: 'LIST_ITEM'
    }))

    expect(pressKey(editor, 'Tab')).toBe(true)
    expect(editor.getJSON()).toEqual(
      expect.objectContaining({
        content: expect.arrayContaining([
          expect.objectContaining({ type: 'bulletList' })
        ])
      })
    )
    expect(editor.getText()).toContain('new item')
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
    setInlineCompletionSuggestion(editor, singleCandidateSuggestion({
      from: position,
      to: position,
      markdown: '`literal`',
      previewText: '`literal`',
      shape: 'CODE_LINE'
    }))

    expect(pressKey(editor, 'Tab')).toBe(true)
    expect(editor.getText()).toContain('const value = `literal`')
    editor.destroy()
  })

  it('opens the candidate menu with Alt arrow keys and cycles with plain arrow keys', () => {
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
      shape: 'SENTENCE',
      candidates: [
        { index: 0, markdown: ' first', previewText: ' first' },
        { index: 1, markdown: ' second', previewText: ' second' },
        { index: 2, markdown: ' third', previewText: ' third' }
      ],
      selectedIndex: 0,
      menuOpen: false
    })

    expect(pressAltKey(editor, 'ArrowDown')).toBe(true)
    expect(getInlineCompletionSuggestion(editor)).toEqual(
      expect.objectContaining({
        selectedIndex: 1,
        menuOpen: true
      })
    )
    expect(pressKey(editor, 'ArrowDown')).toBe(true)
    expect(getInlineCompletionSuggestion(editor)?.selectedIndex).toBe(2)
    expect(pressKey(editor, 'ArrowUp')).toBe(true)
    expect(getInlineCompletionSuggestion(editor)?.selectedIndex).toBe(1)
    expect(pressKey(editor, 'Tab')).toBe(true)
    expect(editor.getText()).toContain('Hello second')
    editor.destroy()
  })

  it('clears suggestions on Escape and leaves Tab alone without a suggestion', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: '<p>Hello</p>'
    })
    const position = findTextPosition(editor, 'Hello') + 'Hello'.length
    editor.commands.setTextSelection(position)
    setInlineCompletionSuggestion(editor, singleCandidateSuggestion({
      from: position,
      to: position,
      markdown: ' world',
      previewText: ' world',
      shape: 'SENTENCE'
    }))

    expect(pressKey(editor, 'Escape')).toBe(true)
    expect(getInlineCompletionSuggestion(editor)).toBeNull()
    clearInlineCompletion(editor)
    expect(pressKey(editor, 'Tab')).toBe(false)
    expect(pressAltKey(editor, 'ArrowDown')).toBe(false)
    editor.destroy()
  })
})
