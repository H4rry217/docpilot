import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { Compartment, EditorState } from '@codemirror/state'
import {
  EditorView,
  drawSelection,
  highlightActiveLine,
  highlightActiveLineGutter,
  highlightSpecialChars,
  keymap,
  lineNumbers
} from '@codemirror/view'
import type { NodeViewProps } from '@tiptap/react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { syntaxExtension } from './codeSyntaxExtension'

const CODE_KEYMAP = [...defaultKeymap, ...historyKeymap, indentWithTab]

function textFromNode(props: NodeViewProps): string {
  return props.node.textContent
}

export function useCodeMirrorNodeView({
  language,
  props
}: {
  language: string
  props: NodeViewProps
}) {
  const propsRef = useRef(props)
  const editorHostRef = useRef<HTMLDivElement | null>(null)
  const codeMirrorRef = useRef<EditorView | null>(null)
  const languageCompartmentRef = useRef(new Compartment())
  const applyingExternalChangeRef = useRef(false)
  const [codeText, setCodeText] = useState(() => textFromNode(props))

  propsRef.current = props

  function syncCodeTextToProseMirror(text: string) {
    const activeProps = propsRef.current
    const position = activeProps.getPos()
    if (typeof position !== 'number') return
    const editorView = activeProps.editor.view
    const from = position + 1
    const to = position + activeProps.node.nodeSize - 1
    editorView.dispatch(editorView.state.tr.insertText(text, from, to))
  }

  const baseExtensions = useMemo(() => [
    lineNumbers(),
    highlightSpecialChars(),
    history(),
    drawSelection(),
    highlightActiveLine(),
    highlightActiveLineGutter(),
    keymap.of(CODE_KEYMAP),
    EditorState.tabSize.of(2),
    EditorView.updateListener.of((update) => {
      if (!update.docChanged || applyingExternalChangeRef.current) return
      const nextText = update.state.doc.toString()
      setCodeText(nextText)
      syncCodeTextToProseMirror(nextText)
    }),
    EditorView.domEventHandlers({
      click: (event) => {
        event.stopPropagation()
        return false
      },
      keydown: (event) => {
        event.stopPropagation()
        return false
      },
      mousedown: (event) => {
        event.stopPropagation()
        return false
      }
    })
  ], [])

  useEffect(() => {
    const host = editorHostRef.current
    if (!host) return

    const view = new EditorView({
      parent: host,
      state: EditorState.create({
        doc: textFromNode(propsRef.current),
        extensions: [
          ...baseExtensions,
          languageCompartmentRef.current.of(syntaxExtension(language))
        ]
      })
    })
    codeMirrorRef.current = view

    return () => {
      view.destroy()
      codeMirrorRef.current = null
    }
  }, [baseExtensions])

  useEffect(() => {
    const view = codeMirrorRef.current
    if (!view) return
    view.dispatch({
      effects: languageCompartmentRef.current.reconfigure(syntaxExtension(language))
    })
  }, [language])

  useEffect(() => {
    const host = editorHostRef.current
    const view = codeMirrorRef.current
    if (!host || !view || view.dom.parentElement === host) return
    host.appendChild(view.dom)
  })

  useEffect(() => {
    const view = codeMirrorRef.current
    if (!view) return
    const nextText = textFromNode(props)
    const currentText = view.state.doc.toString()
    setCodeText(nextText)
    if (currentText === nextText) return
    applyingExternalChangeRef.current = true
    view.dispatch({
      changes: {
        from: 0,
        to: currentText.length,
        insert: nextText
      }
    })
    applyingExternalChangeRef.current = false
  }, [props])

  return {
    codeMirrorRef,
    codeText,
    editorHostRef
  }
}
