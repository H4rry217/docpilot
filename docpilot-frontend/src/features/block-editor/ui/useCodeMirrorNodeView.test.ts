import { Editor } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import { describe, expect, it } from 'vitest'
import { replaceCodeBlockTextInEditor } from './useCodeMirrorNodeView'

describe('replaceCodeBlockTextInEditor', () => {
  it('replaces code block text through the current ProseMirror state range', () => {
    const editor = new Editor({
      extensions: [StarterKit],
      content: {
        type: 'doc',
        content: [
          {
            type: 'codeBlock',
            attrs: { language: 'sql' }
          }
        ]
      }
    })
    const getPos = () => 0

    expect(replaceCodeBlockTextInEditor(editor.view, getPos, 'a')).toBe(true)
    expect(editor.getJSON().content?.[0]).toMatchObject({
      type: 'codeBlock',
      content: [{ type: 'text', text: 'a' }]
    })

    expect(replaceCodeBlockTextInEditor(editor.view, getPos, 'ab')).toBe(true)
    expect(editor.getJSON().content?.[0]).toMatchObject({
      type: 'codeBlock',
      content: [{ type: 'text', text: 'ab' }]
    })

    editor.destroy()
  })

  it('deletes existing code block text when CodeMirror becomes empty', () => {
    const editor = new Editor({
      extensions: [StarterKit],
      content: {
        type: 'doc',
        content: [
          {
            type: 'codeBlock',
            content: [{ type: 'text', text: 'SELECT 1;' }]
          }
        ]
      }
    })

    expect(replaceCodeBlockTextInEditor(editor.view, () => 0, '')).toBe(true)
    expect(editor.getJSON().content?.[0]).toMatchObject({
      type: 'codeBlock'
    })
    expect(editor.getJSON().content?.[0].content).toBeUndefined()

    editor.destroy()
  })
})
