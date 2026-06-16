import type { JSONContent } from '@tiptap/core'
import { type Editor, EditorContent, useEditor } from '@tiptap/react'
import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useRef,
  type PointerEvent as ReactPointerEvent
} from 'react'
import type { DocumentOutlineJumpRequest } from '../../../entities/block/outline'
import type { BlockDocument } from '../../../entities/block/types'
import { blockDocumentToProseMirrorJson } from '../model/blockDocumentToProseMirror'
import { editorExtensions } from '../model/extensions'
import { proseMirrorJsonToBlockDocument } from '../model/proseMirrorToBlockDocument'
import './BlockDocumentEditor.css'
import { TableDividerControls, TableHoverIndicators } from './TableAffordanceOverlay'
import { useBlockMarqueeSelection } from './useBlockMarqueeSelection'
import {
  useInlineCompletion,
  type InlineCompletionEditorContext,
  type InlineCompletionRuntimeSettings
} from './useInlineCompletion'
import { useTableAffordances } from './useTableAffordances'

export type BlockDocumentEditorSnapshot = {
  blockDocument: BlockDocument
  proseMirrorJson: JSONContent
}

export type BlockDocumentEditorSnapshotSource = 'load' | 'edit' | 'programmatic'

export type BlockDocumentEditorHandle = {
  getSnapshot: () => BlockDocumentEditorSnapshot | null
  scrollToOutlineItem: (request: DocumentOutlineJumpRequest) => void
  setBlockDocument: (blockDocument: BlockDocument) => BlockDocumentEditorSnapshot | null
}

export type BlockDocumentEditorProps = {
  contentKey?: string
  blockDocument?: BlockDocument
  debugMode?: boolean
  inlineCompletionContext?: InlineCompletionEditorContext
  inlineCompletionSettings?: InlineCompletionRuntimeSettings
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

function blockIdSelector(blockId: string): string {
  return `[data-block-id="${blockId.replaceAll('\\', '\\\\').replaceAll('"', '\\"')}"]`
}

export const BlockDocumentEditor = forwardRef<BlockDocumentEditorHandle, BlockDocumentEditorProps>(
  function BlockDocumentEditor(
    {
      contentKey,
      blockDocument,
      debugMode = false,
      inlineCompletionContext,
      inlineCompletionSettings,
      proseMirrorFallback,
      onSnapshotChange
    },
    ref
  ) {
    const applyingContentRef = useRef(false)
    const lastAppliedContentKeyRef = useRef<string | undefined>(undefined)
    const jumpHighlightTimeoutRef = useRef<number | undefined>(undefined)
    const surfaceRef = useRef<HTMLDivElement | null>(null)
    const marqueeRef = useRef<HTMLDivElement | null>(null)

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

    const getCurrentSnapshot = useCallback(
      () => (editor ? snapshotFromEditor(editor) : null),
      [editor]
    )

    useInlineCompletion({
      editor,
      context: inlineCompletionContext,
      settings: inlineCompletionSettings,
      contentKey,
      getSnapshot: getCurrentSnapshot,
      isApplyingContent: () => applyingContentRef.current
    })

    const {
      handleTableDividerChanged,
      handleTablePointerLeave,
      handleTablePointerMove,
      handleTableWheel,
      keepTableDividerControlsVisible,
      keepTableHoverIndicatorVisible,
      requestTableDividerHide,
      requestTableHoverIndicatorHide,
      resetTableAffordances,
      revealTableDividerAfterDelay,
      selectTableIndicatorSegment,
      tableDivider,
      tableHoverIndicator
    } = useTableAffordances({ editor, surfaceRef })

    const {
      blockSelectionUiState,
      clearBlockSelection,
      isBlockSelectionDragging
    } = useBlockMarqueeSelection({ editor, marqueeRef, surfaceRef })

    const surfaceClassName = [
      'block-editor-surface',
      debugMode ? 'is-debug-mode' : '',
      blockSelectionUiState.isDragging ? 'is-block-selection-dragging' : '',
      blockSelectionUiState.hasSelectedTableBlock ? 'has-selected-table-block' : ''
    ].filter(Boolean).join(' ')

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
        setBlockDocument: (nextBlockDocument) => {
          if (!editor) return null
          return applyProseMirrorJson(
            editor,
            blockDocumentToProseMirrorJson(nextBlockDocument),
            'programmatic'
          )
        },
        scrollToOutlineItem: (request) => {
          if (!editor) return
          const headings = editor.view.dom.querySelectorAll<HTMLElement>('h1, h2, h3, h4, h5, h6')
          const target = editor.view.dom.querySelector<HTMLElement>(blockIdSelector(request.id))
            ?? headings.item(request.headingIndex)
          if (!target) return

          target.scrollIntoView({ behavior: 'smooth', block: 'center' })
          window.clearTimeout(jumpHighlightTimeoutRef.current)
          target.classList.add('docpilot-block-jump-target')
          jumpHighlightTimeoutRef.current = window.setTimeout(() => {
            target.classList.remove('docpilot-block-jump-target')
          }, 1400)
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

    useEffect(() => {
      clearBlockSelection()
      resetTableAffordances()
    }, [clearBlockSelection, contentKey, resetTableAffordances])

    useEffect(() => {
      return () => window.clearTimeout(jumpHighlightTimeoutRef.current)
    }, [])

    function handleSurfacePointerMove(event: ReactPointerEvent<HTMLDivElement>) {
      if (isBlockSelectionDragging()) return
      handleTablePointerMove(event)
    }

    function handleSurfacePointerLeave() {
      handleTablePointerLeave()
    }

    return (
      <div
        ref={surfaceRef}
        className={surfaceClassName}
        onPointerLeave={handleSurfacePointerLeave}
        onPointerMove={handleSurfacePointerMove}
        onWheelCapture={handleTableWheel}
      >
        <EditorContent editor={editor} className="editor-content" />
        {editor && tableHoverIndicator ? (
          <TableHoverIndicators
            editor={editor}
            geometry={tableHoverIndicator}
            onDividerHandleEnter={revealTableDividerAfterDelay}
            onDividerHandleLeave={requestTableDividerHide}
            onKeepVisible={keepTableHoverIndicatorVisible}
            onRequestHide={requestTableHoverIndicatorHide}
            onSelectColumn={(column) => selectTableIndicatorSegment('column', column)}
            onSelectRow={(row) => selectTableIndicatorSegment('row', row)}
          />
        ) : null}
        {editor && tableDivider ? (
          <TableDividerControls
            editor={editor}
            divider={tableDivider}
            onChanged={handleTableDividerChanged}
            onKeepVisible={keepTableDividerControlsVisible}
            onRequestHide={requestTableDividerHide}
          />
        ) : null}
        <div ref={marqueeRef} className="block-selection-marquee" hidden />
      </div>
    )
  }
)
