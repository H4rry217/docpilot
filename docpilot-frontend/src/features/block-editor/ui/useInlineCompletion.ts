import { useCallback, useEffect, useRef } from 'react'
import type { Editor } from '@tiptap/react'
import type { Transaction } from '@tiptap/pm/state'
import { completeInlineCompletion } from '@/features/inline-completion/api/inlineCompletionApi'
import { recordInlineCompletionDebug } from '@/features/inline-completion/model/inlineCompletionDebug'
import {
  clearInlineCompletion,
  setInlineCompletionSuggestion
} from '../model/inlineCompletion'
import type { BlockDocumentEditorSnapshot } from './BlockDocumentEditor'
import {
  activeElementDebugName,
  blockSignature,
  clampCandidateCount,
  currentBlockContext,
  headingPathForBlock,
  isEditorInteractionFocused,
  markdownPreviewText,
  nearbyBlocks
} from './inlineCompletionRuntime'

const INLINE_COMPLETION_IDLE_DELAY_MS = 500

export type InlineCompletionRuntimeSettings = {
  enabled: boolean
  idleDelayMs: number
  candidateCount: number
}

export type InlineCompletionEditorContext = {
  workspaceId?: string
  documentId?: string
  clientVersion?: string
}

type UseInlineCompletionOptions = {
  editor: Editor | null
  context?: InlineCompletionEditorContext
  settings?: InlineCompletionRuntimeSettings
  contentKey?: string
  getSnapshot: () => BlockDocumentEditorSnapshot | null
  isApplyingContent: () => boolean
}

type RequestAnchor = {
  requestSeq: number
  documentId: string
  from: number
  to: number
  blockId: string
  signature: string
}

type AnchorCheck =
  | {
      current: true
      from: number
      to: number
      drift?: {
        anchorFrom: number
        anchorTo: number
        currentFrom: number
        currentTo: number
      }
    }
  | {
      current: false
      reason: string
      detail?: Record<string, unknown>
    }

type ActiveRequest = {
  requestSeq: number
  anchor: RequestAnchor
  controller: AbortController
}

export function useInlineCompletion({
  editor,
  context,
  settings,
  contentKey,
  getSnapshot,
  isApplyingContent
}: UseInlineCompletionOptions): void {
  debugInlineCompletion('hook render', {
    hasEditor: Boolean(editor),
    workspaceId: context?.workspaceId,
    documentId: context?.documentId,
    contentKey
  })

  const timerRef = useRef<number | undefined>(undefined)
  const activeRequestRef = useRef<ActiveRequest | null>(null)
  const requestSeqRef = useRef(0)
  const composingRef = useRef(false)
  const isApplyingContentRef = useRef(isApplyingContent)
  const enabled = settings?.enabled ?? true
  const idleDelayMs = settings?.idleDelayMs ?? INLINE_COMPLETION_IDLE_DELAY_MS
  const candidateCount = clampCandidateCount(settings?.candidateCount)

  useEffect(() => {
    isApplyingContentRef.current = isApplyingContent
  }, [isApplyingContent])

  const stopPendingCompletion = useCallback(
    (requestSeq?: number) => {
      window.clearTimeout(timerRef.current)
      timerRef.current = undefined
      const activeRequest = activeRequestRef.current
      if (activeRequest && (requestSeq == null || activeRequest.requestSeq === requestSeq)) {
        activeRequest.controller.abort()
        activeRequestRef.current = null
      }
    },
    []
  )

  const cancelActiveCompletion = useCallback(
    (requestSeq?: number) => {
      stopPendingCompletion(requestSeq)
      if (editor) {
        clearInlineCompletion(editor, requestSeq)
      }
    },
    [editor, stopPendingCompletion]
  )

  const currentAnchorRange = useCallback(
    (anchor: RequestAnchor): AnchorCheck => {
      if (!editor) {
        return { current: false, reason: 'editor-missing' }
      }
      if (context?.documentId !== anchor.documentId) {
        return {
          current: false,
          reason: 'document-changed',
          detail: {
            anchorDocumentId: anchor.documentId,
            currentDocumentId: context?.documentId
          }
        }
      }
      const selection = editor.state.selection
      if (!selection.empty) {
        return {
          current: false,
          reason: 'selection-not-empty',
          detail: {
            currentFrom: selection.from,
            currentTo: selection.to
          }
        }
      }
      const blockContext = currentBlockContext(editor)
      if (!blockContext) {
        return { current: false, reason: 'block-context-missing' }
      }
      const currentBlockId = blockContext.id ?? ''
      if (anchor.blockId && currentBlockId && currentBlockId !== anchor.blockId) {
        return {
          current: false,
          reason: 'block-id-changed',
          detail: {
            anchorBlockId: anchor.blockId,
            currentBlockId
          }
        }
      }
      const currentSignature = blockSignature(blockContext)
      if (currentSignature !== anchor.signature) {
        return {
          current: false,
          reason: 'block-text-changed',
          detail: {
            anchorSignature: anchor.signature,
            currentSignature
          }
        }
      }
      const drift = selection.from !== anchor.from || selection.to !== anchor.to
        ? {
            anchorFrom: anchor.from,
            anchorTo: anchor.to,
            currentFrom: selection.from,
            currentTo: selection.to
          }
        : undefined
      return {
        current: true,
        from: selection.from,
        to: selection.to,
        drift
      }
    },
    [context?.documentId, editor]
  )

  const startRequest = useCallback(() => {
    if (!editor) {
      debugInlineCompletion('skipped request start', { reason: 'editor-missing' })
      return
    }
    if (!enabled) {
      debugInlineCompletion('skipped request start', { reason: 'inline-completion-disabled' })
      return
    }
    if (activeRequestRef.current) {
      debugInlineCompletion('skipped request start', {
        reason: 'request-already-active',
        requestSeq: activeRequestRef.current.requestSeq
      })
      return
    }
    if (!context?.workspaceId || !context.documentId) {
      debugInlineCompletion('skipped request start', {
        reason: 'context-missing',
        workspaceId: context?.workspaceId,
        documentId: context?.documentId
      })
      return
    }
    if (composingRef.current) {
      debugInlineCompletion('skipped request start', { reason: 'composition-active' })
      return
    }
    if (!isEditorInteractionFocused(editor)) {
      debugInlineCompletion('skipped request start', {
        reason: 'editor-not-focused',
        activeElement: activeElementDebugName(editor)
      })
      return
    }
    if (!editor.state.selection.empty) {
      debugInlineCompletion('skipped request start', {
        reason: 'selection-not-empty',
        from: editor.state.selection.from,
        to: editor.state.selection.to
      })
      return
    }

    const blockContext = currentBlockContext(editor)
    if (!blockContext) {
      debugInlineCompletion('skipped request start', {
        reason: 'block-context-missing',
        from: editor.state.selection.from,
        to: editor.state.selection.to
      })
      return
    }
    const snapshot = getSnapshot()
    if (!snapshot) {
      debugInlineCompletion('skipped request start', { reason: 'snapshot-missing' })
      return
    }

    const requestSeq = requestSeqRef.current + 1
    requestSeqRef.current = requestSeq
    cancelActiveCompletion()

    const anchor: RequestAnchor = {
      requestSeq,
      documentId: context.documentId,
      from: editor.state.selection.from,
      to: editor.state.selection.to,
      blockId: blockContext.id ?? '',
      signature: blockSignature(blockContext)
    }
    const controller = new AbortController()
    activeRequestRef.current = {
      requestSeq,
      anchor,
      controller
    }
    debugInlineCompletion('request started', {
      requestSeq,
      workspaceId: context.workspaceId,
      documentId: context.documentId,
      from: anchor.from,
      to: anchor.to,
      blockId: anchor.blockId,
      signature: anchor.signature
    })

    void completeInlineCompletion(
      {
        workspaceId: context.workspaceId,
        documentId: context.documentId,
        cursor: {
          from: anchor.from,
          to: anchor.to
        },
        currentBlock: blockContext,
        headingPath: headingPathForBlock(snapshot.blockDocument, blockContext.id),
        nearbyBlocks: nearbyBlocks(snapshot.blockDocument, blockContext.id),
        trigger: 'IDLE',
        clientVersion: context.clientVersion ?? 'web-0.1.0',
        candidateCount
      },
      controller.signal
    ).then((response) => {
      const activeRequest = activeRequestRef.current
      if (!activeRequest || activeRequest.requestSeq !== requestSeq) {
        debugInlineCompletion('discarded stale complete response', {
          requestSeq,
          reason: 'request-replaced',
          candidateCount: response.candidates.length
        })
        return
      }
      const anchorCheck = currentAnchorRange(anchor)
      if (!anchorCheck.current) {
        debugInlineCompletion('discarded stale complete response', {
          requestSeq,
          candidateCount: response.candidates.length,
          diagnostics: response.diagnostics,
          ...anchorCheck
        })
        return
      }
      if (anchorCheck.drift) {
        debugInlineCompletion('accepted complete response after cursor position drift', { requestSeq, ...anchorCheck.drift })
      }
      const candidates = response.candidates
        .filter((candidate) => candidate.markdown)
        .map((candidate, index) => ({
          index,
          markdown: candidate.markdown,
          previewText: candidate.previewText || markdownPreviewText(candidate.markdown, response.shape)
        }))
      if (!candidates.length) {
        debugInlineCompletion('completed with empty candidates', {
          requestSeq,
          shape: response.shape,
          diagnostics: response.diagnostics
        })
        return
      }
      setInlineCompletionSuggestion(editor, {
        requestSeq,
        from: anchorCheck.from,
        to: anchorCheck.to,
        shape: response.shape,
        candidates,
        selectedIndex: 0,
        menuOpen: false
      })
      debugInlineCompletion('rendered ghost text from complete response', {
        requestSeq,
        from: anchorCheck.from,
        to: anchorCheck.to,
        shape: response.shape,
        candidateCount: candidates.length,
        previewText: candidates[0]?.previewText,
        diagnostics: response.diagnostics
      })
      if (activeRequestRef.current?.requestSeq === requestSeq) {
        activeRequestRef.current = null
      }
    }).catch((error: unknown) => {
      if (error instanceof DOMException && error.name === 'AbortError') {
        debugInlineCompletion('complete request aborted', { requestSeq })
        return
      }
      debugInlineCompletion('complete request failed', error)
      const activeRequest = activeRequestRef.current
      if (activeRequest?.requestSeq === requestSeq) {
        cancelActiveCompletion(requestSeq)
      }
    }).finally(() => {
      if (activeRequestRef.current?.requestSeq === requestSeq) {
        debugInlineCompletion('complete request finished without response', { requestSeq })
        activeRequestRef.current = null
      }
    })
  }, [candidateCount, cancelActiveCompletion, context?.clientVersion, context?.documentId, context?.workspaceId, currentAnchorRange, editor, enabled, getSnapshot])

  const scheduleRequest = useCallback(() => {
    if (!enabled) {
      cancelActiveCompletion()
      return
    }
    window.clearTimeout(timerRef.current)
    timerRef.current = window.setTimeout(startRequest, idleDelayMs)
    debugInlineCompletion('scheduled request', { delayMs: idleDelayMs })
  }, [cancelActiveCompletion, enabled, idleDelayMs, startRequest])

  useEffect(() => {
    if (!enabled) {
      cancelActiveCompletion()
    }
  }, [cancelActiveCompletion, enabled])

  useEffect(() => {
    if (!editor) return
    const editorDom = editor.view.dom
    debugInlineCompletion('hook attached', {
      workspaceId: context?.workspaceId,
      documentId: context?.documentId,
      contentKey
    })

    function handleEditorActivity(
      source: string,
      transaction?: Transaction,
      options: { cancelActive?: boolean; forceSchedule?: boolean } = {}
    ) {
      const docChanged = transaction?.docChanged ?? source === 'dom-input'
      const selectionSet = transaction?.selectionSet ?? false
      if (!docChanged && !selectionSet && !options.forceSchedule) return
      debugInlineCompletion('editor activity observed', {
        source,
        docChanged,
        selectionSet,
        forceSchedule: Boolean(options.forceSchedule),
        cancelActive: options.cancelActive !== false,
        applyingContent: isApplyingContentRef.current(),
        composing: composingRef.current
      })
      if (isApplyingContentRef.current() || composingRef.current) {
        stopPendingCompletion()
        debugInlineCompletion('skipped request schedule', {
          source,
          reason: composingRef.current ? 'composition-active' : 'applying-content'
        })
        return
      }
      const activeRequest = activeRequestRef.current
      if (activeRequest && !docChanged && selectionSet) {
        const anchorCheck = currentAnchorRange(activeRequest.anchor)
        if (anchorCheck.current) {
          debugInlineCompletion('ignored selection activity while request anchor is current', {
            source,
            requestSeq: activeRequest.requestSeq,
            from: anchorCheck.from,
            to: anchorCheck.to
          })
          return
        }
      }
      // Cursor movement is not an edit: it invalidates any old suggestion but must not start a new idle completion.
      if (!docChanged && selectionSet && !options.forceSchedule) {
        if (options.cancelActive !== false) {
          cancelActiveCompletion()
        } else {
          window.clearTimeout(timerRef.current)
          timerRef.current = undefined
        }
        debugInlineCompletion('skipped request schedule', { source, reason: 'selection-only' })
        return
      }
      if (options.cancelActive !== false) {
        cancelActiveCompletion()
      } else {
        window.clearTimeout(timerRef.current)
        timerRef.current = undefined
      }
      scheduleRequest()
    }

    function handleTransaction({ transaction }: { transaction: Transaction }) {
      handleEditorActivity('transaction', transaction)
    }

    function handleUpdate({ transaction }: { transaction: Transaction }) {
      handleEditorActivity('update', transaction)
    }

    function handleSelectionUpdate({ transaction }: { transaction: Transaction }) {
      handleEditorActivity('selectionUpdate', transaction)
    }

    function handleDomInput() {
      handleEditorActivity('dom-input')
    }

    editor.on('transaction', handleTransaction)
    editor.on('update', handleUpdate)
    editor.on('selectionUpdate', handleSelectionUpdate)
    editorDom.addEventListener('input', handleDomInput)
    return () => {
      editor.off('transaction', handleTransaction)
      editor.off('update', handleUpdate)
      editor.off('selectionUpdate', handleSelectionUpdate)
      editorDom.removeEventListener('input', handleDomInput)
    }
  }, [cancelActiveCompletion, currentAnchorRange, editor, scheduleRequest, stopPendingCompletion])

  useEffect(() => {
    if (!editor) return
    const editorDom = editor.view.dom

    function handleCompositionStart() {
      composingRef.current = true
      stopPendingCompletion()
      debugInlineCompletion('composition started')
    }

    function handleCompositionEnd() {
      composingRef.current = false
      debugInlineCompletion('composition ended')
      scheduleRequest()
    }

    editorDom.addEventListener('compositionstart', handleCompositionStart)
    editorDom.addEventListener('compositionend', handleCompositionEnd)
    return () => {
      editorDom.removeEventListener('compositionstart', handleCompositionStart)
      editorDom.removeEventListener('compositionend', handleCompositionEnd)
    }
  }, [editor, scheduleRequest, stopPendingCompletion])

  useEffect(() => {
    cancelActiveCompletion()
  }, [cancelActiveCompletion, contentKey])
}

function debugInlineCompletion(message: string, detail?: unknown): void {
  recordInlineCompletionDebug('runtime', message, detail)
}
