import type { JSONContent } from '@tiptap/core'
import { type Editor, EditorContent, useEditor } from '@tiptap/react'
import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useRef
} from 'react'
import type { BlockDocument } from '../../../entities/block/types'
import { blockDocumentToProseMirrorJson } from '../model/blockDocumentToProseMirror'
import { editorExtensions } from '../model/extensions'
import { proseMirrorJsonToBlockDocument } from '../model/proseMirrorToBlockDocument'

export type BlockDocumentEditorSnapshot = {
  blockDocument: BlockDocument
  proseMirrorJson: JSONContent
}

export type BlockDocumentEditorSnapshotSource = 'load' | 'edit' | 'programmatic'

export type InsertHtmlBlockInput = {
  id: string
  title: string
  source: string
  displayMode: 'fixed' | 'auto'
  fixedHeightPx: number
  allowScripts: boolean
}

export type BlockDocumentEditorHandle = {
  getSnapshot: () => BlockDocumentEditorSnapshot | null
  insertHtmlBlock: (input: InsertHtmlBlockInput) => BlockDocumentEditorSnapshot | null
  setBlockDocument: (blockDocument: BlockDocument) => BlockDocumentEditorSnapshot | null
  setProseMirrorJson: (proseMirrorJson: JSONContent) => BlockDocumentEditorSnapshot | null
}

export type BlockDocumentEditorProps = {
  contentKey?: string
  blockDocument?: BlockDocument
  proseMirrorFallback?: JSONContent
  onSnapshotChange: (
    snapshot: BlockDocumentEditorSnapshot,
    source: BlockDocumentEditorSnapshotSource
  ) => void
}

function snapshotFromEditor(editor: Editor): BlockDocumentEditorSnapshot {
  const proseMirrorJson = editor.getJSON()
  return {
    proseMirrorJson,
    blockDocument: proseMirrorJsonToBlockDocument(proseMirrorJson)
  }
}

export const BlockDocumentEditor = forwardRef<BlockDocumentEditorHandle, BlockDocumentEditorProps>(
  function BlockDocumentEditor(
    { contentKey, blockDocument, proseMirrorFallback, onSnapshotChange },
    ref
  ) {
    const applyingContentRef = useRef(false)
    const lastAppliedContentKeyRef = useRef<string | undefined>(undefined)

    const emitSnapshot = useCallback(
      (activeEditor: Editor, source: BlockDocumentEditorSnapshotSource) => {
        const snapshot = snapshotFromEditor(activeEditor)
        onSnapshotChange(snapshot, source)
        return snapshot
      },
      [onSnapshotChange]
    )

    const editor = useEditor({
      extensions: editorExtensions,
      content: '',
      editorProps: {
        attributes: {
          class: 'prose-editor',
          spellcheck: 'false'
        }
      },
      onUpdate: ({ editor: activeEditor }) => {
        if (applyingContentRef.current) return
        emitSnapshot(activeEditor, 'edit')
      }
    })

    const applyProseMirrorJson = useCallback(
      (activeEditor: Editor, proseMirrorJson: JSONContent, source: BlockDocumentEditorSnapshotSource) => {
        applyingContentRef.current = true
        activeEditor.commands.setContent(proseMirrorJson, false)
        applyingContentRef.current = false
        return emitSnapshot(activeEditor, source)
      },
      [emitSnapshot]
    )

    useImperativeHandle(
      ref,
      () => ({
        getSnapshot: () => (editor ? snapshotFromEditor(editor) : null),
        insertHtmlBlock: (input) => {
          if (!editor) return null
          editor
            .chain()
            .focus()
            .insertContent({
              type: 'docpilotHtmlBlock',
              attrs: input
            })
            .run()
          return snapshotFromEditor(editor)
        },
        setBlockDocument: (nextBlockDocument) => {
          if (!editor) return null
          return applyProseMirrorJson(editor, blockDocumentToProseMirrorJson(nextBlockDocument), 'programmatic')
        },
        setProseMirrorJson: (nextProseMirrorJson) => {
          if (!editor) return null
          return applyProseMirrorJson(editor, nextProseMirrorJson, 'programmatic')
        }
      }),
      [applyProseMirrorJson, editor]
    )

    useEffect(() => {
      if (!editor || !contentKey || lastAppliedContentKeyRef.current === contentKey) return
      const nextContent = blockDocument ? blockDocumentToProseMirrorJson(blockDocument) : proseMirrorFallback
      if (!nextContent) return
      applyProseMirrorJson(editor, nextContent, 'load')
      lastAppliedContentKeyRef.current = contentKey
    }, [applyProseMirrorJson, blockDocument, contentKey, editor, proseMirrorFallback])

    return <EditorContent editor={editor} className="editor-content" />
  }
)
