import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { html } from '@codemirror/lang-html'
import { bracketMatching, defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { EditorState } from '@codemirror/state'
import {
  drawSelection,
  EditorView,
  highlightActiveLine,
  highlightSpecialChars,
  keymap,
  lineNumbers
} from '@codemirror/view'
import { useEffect, useRef } from 'react'

export type HtmlSourceEditorProps = {
  source: string
  onChange: (source: string) => void
}

export function HtmlSourceEditor({ source, onChange }: HtmlSourceEditorProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const viewRef = useRef<EditorView | null>(null)
  const onChangeRef = useRef(onChange)

  useEffect(() => {
    onChangeRef.current = onChange
  }, [onChange])

  useEffect(() => {
    if (!containerRef.current) return

    const view = new EditorView({
      parent: containerRef.current,
      state: EditorState.create({
        doc: source,
        extensions: [
          lineNumbers(),
          highlightSpecialChars(),
          history(),
          drawSelection(),
          highlightActiveLine(),
          bracketMatching(),
          html(),
          syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
          keymap.of([indentWithTab, ...defaultKeymap, ...historyKeymap]),
          EditorView.lineWrapping,
          EditorView.updateListener.of((update) => {
            if (update.docChanged) {
              onChangeRef.current(update.state.doc.toString())
            }
          })
        ]
      })
    })

    viewRef.current = view
    return () => {
      view.destroy()
      viewRef.current = null
    }
  }, [])

  useEffect(() => {
    const view = viewRef.current
    if (!view) return
    const current = view.state.doc.toString()
    if (current === source) return
    view.dispatch({
      changes: { from: 0, to: current.length, insert: source }
    })
  }, [source])

  return <div ref={containerRef} className="html-source-editor" contentEditable={false} />
}
