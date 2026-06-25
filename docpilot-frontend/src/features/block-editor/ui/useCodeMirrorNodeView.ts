import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { Compartment, EditorState as CodeMirrorEditorState } from '@codemirror/state'
import {
  EditorView,
  drawSelection,
  highlightActiveLine,
  highlightActiveLineGutter,
  highlightSpecialChars,
  keymap,
  lineNumbers
} from '@codemirror/view'
import type { EditorState as ProseMirrorEditorState } from '@tiptap/pm/state'
import type { EditorView as ProseMirrorEditorView } from '@tiptap/pm/view'
import type { NodeViewProps } from '@tiptap/react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { syntaxExtension } from './codeSyntaxExtension'

const CODE_KEYMAP = [...defaultKeymap, ...historyKeymap, indentWithTab]

type CodeBlockContentRange = {
  from: number
  text: string
  to: number
}

function textFromNode(props: NodeViewProps): string {
  return props.node.textContent
}

export function codeBlockContentRange(
  state: ProseMirrorEditorState,
  getPos: NodeViewProps['getPos']
): CodeBlockContentRange | null {
  let position: unknown
  try {
    position = getPos()
  } catch {
    return null
  }
  if (typeof position !== 'number') return null
  const node = state.doc.nodeAt(position)
  if (node?.type.name !== 'codeBlock') return null
  return {
    from: position + 1,
    text: node.textContent,
    to: position + node.nodeSize - 1
  }
}

export function codeBlockTextFromEditorState(
  state: ProseMirrorEditorState,
  getPos: NodeViewProps['getPos']
): string | null {
  return codeBlockContentRange(state, getPos)?.text ?? null
}

export function replaceCodeBlockTextInEditor(
  editorView: ProseMirrorEditorView,
  getPos: NodeViewProps['getPos'],
  text: string
): boolean {
  const range = codeBlockContentRange(editorView.state, getPos)
  if (!range || range.text === text) return false
  const transaction = text
    ? editorView.state.tr.insertText(text, range.from, range.to)
    : editorView.state.tr.delete(range.from, range.to)
  editorView.dispatch(transaction)
  return true
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
    replaceCodeBlockTextInEditor(activeProps.editor.view, activeProps.getPos, text)
  }

  const baseExtensions = useMemo(() => [
    lineNumbers(),
    highlightSpecialChars(),
    history(),
    drawSelection(),
    highlightActiveLine(),
    highlightActiveLineGutter(),
    keymap.of(CODE_KEYMAP),
    CodeMirrorEditorState.tabSize.of(2),
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
      state: CodeMirrorEditorState.create({
        doc: codeBlockTextFromEditorState(propsRef.current.editor.view.state, propsRef.current.getPos) ?? textFromNode(propsRef.current),
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
    const nextText = codeBlockTextFromEditorState(props.editor.view.state, props.getPos) ?? textFromNode(props)
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
