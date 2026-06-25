import { EditorState } from '@codemirror/state'
import { EditorView } from '@codemirror/view'
import { afterEach, describe, expect, it } from 'vitest'
import { syntaxExtension } from './codeSyntaxExtension'

let activeView: EditorView | null = null

afterEach(() => {
  activeView?.destroy()
  activeView = null
})

describe('syntaxExtension', () => {
  it('highlights uppercase SQL keywords and line comments', () => {
    const parent = document.createElement('div')
    activeView = new EditorView({
      parent,
      state: EditorState.create({
        doc: 'SELECT id FROM users -- active records',
        extensions: [syntaxExtension('sql')]
      })
    })

    expect(parent.querySelectorAll('.cm-dp-token-keyword')).toHaveLength(2)
    expect(parent.querySelector('.cm-dp-token-comment')).toHaveTextContent('-- active records')
  })
})
