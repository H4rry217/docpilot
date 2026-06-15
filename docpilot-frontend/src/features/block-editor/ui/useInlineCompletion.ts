import { useCallback, useEffect, useRef } from 'react'
import type { Editor } from '@tiptap/react'
import type { Node as ProseMirrorNode, ResolvedPos } from '@tiptap/pm/model'
import type { Transaction } from '@tiptap/pm/state'
import type { BlockDocument, BlockNode, InlineNode } from '../../../entities/block/types'
import {
  completeInlineCompletion,
  type InlineCompletionBlockContext,
  type InlineCompletionShape
} from '../../inline-completion/api/inlineCompletionApi'
import { recordInlineCompletionDebug } from '../../inline-completion/model/inlineCompletionDebug'
import {
  clearInlineCompletion,
  setInlineCompletionSuggestion
} from '../model/inlineCompletion'
import { blockIdentityId } from '../model/docpilotBlockIdentity'
import type { BlockDocumentEditorSnapshot } from './BlockDocumentEditor'

const INLINE_COMPLETION_IDLE_DELAY_MS = 500
const INLINE_COMPLETION_DEFAULT_CANDIDATE_COUNT = 3
const NEARBY_BLOCK_LIMIT = 2

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

  const cancelActiveCompletion = useCallback(
    (requestSeq?: number) => {
      window.clearTimeout(timerRef.current)
      timerRef.current = undefined
      const activeRequest = activeRequestRef.current
      if (activeRequest && (requestSeq == null || activeRequest.requestSeq === requestSeq)) {
        activeRequest.controller.abort()
        activeRequestRef.current = null
      }
      if (editor) {
        clearInlineCompletion(editor, requestSeq)
      }
    },
    [editor]
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
    if (!editor.isFocused) {
      debugInlineCompletion('skipped request start', { reason: 'editor-not-focused' })
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
      if (isApplyingContentRef.current() || composingRef.current) return
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
  }, [cancelActiveCompletion, currentAnchorRange, editor, scheduleRequest])

  useEffect(() => {
    if (!editor) return
    const editorDom = editor.view.dom

    function handleCompositionStart() {
      composingRef.current = true
      cancelActiveCompletion()
    }

    function handleCompositionEnd() {
      composingRef.current = false
      scheduleRequest()
    }

    editorDom.addEventListener('compositionstart', handleCompositionStart)
    editorDom.addEventListener('compositionend', handleCompositionEnd)
    return () => {
      editorDom.removeEventListener('compositionstart', handleCompositionStart)
      editorDom.removeEventListener('compositionend', handleCompositionEnd)
    }
  }, [cancelActiveCompletion, editor, scheduleRequest])

  useEffect(() => {
    cancelActiveCompletion()
  }, [cancelActiveCompletion, contentKey])
}

function currentBlockContext(editor: Editor): InlineCompletionBlockContext | null {
  const { selection } = editor.state
  if (!selection.empty) return null
  const cursor = selection.$from
  const selected = selectedContextNode(cursor)
  if (!selected) return null
  const parent = cursor.parent
  return {
    id: selected.id,
    type: selected.type,
    text: selected.node.textContent,
    textBeforeCursor: parent.textBetween(0, cursor.parentOffset, '\n', '\n'),
    textAfterCursor: parent.textBetween(cursor.parentOffset, parent.content.size, '\n', '\n')
  }
}

function selectedContextNode(cursor: ResolvedPos): {
  id: string
  type: string
  node: ProseMirrorNode
} | null {
  const preferred = ['codeBlock', 'tableCell', 'tableHeader', 'listItem']
  for (const typeName of preferred) {
    for (let depth = cursor.depth; depth >= 0; depth -= 1) {
      const node = cursor.node(depth)
      if (node.type.name === typeName) {
        return {
          id: blockIdentityId(node.attrs),
          type: blockTypeFromProseMirror(node),
          node
        }
      }
    }
  }

  for (let depth = cursor.depth; depth >= 0; depth -= 1) {
    const node = cursor.node(depth)
    const blockId = blockIdentityId(node.attrs)
    if (blockId || node.isTextblock) {
      return {
        id: blockId,
        type: blockTypeFromProseMirror(node),
        node
      }
    }
  }
  return null
}

function blockTypeFromProseMirror(node: ProseMirrorNode): string {
  switch (node.type.name) {
    case 'paragraph':
      return 'PARAGRAPH'
    case 'heading':
      return 'HEADING'
    case 'blockquote':
      return 'BLOCK_QUOTE'
    case 'bulletList':
      return 'BULLET_LIST'
    case 'orderedList':
      return 'ORDERED_LIST'
    case 'listItem':
      return 'LIST_ITEM'
    case 'codeBlock':
      return 'CODE_BLOCK'
    case 'tableCell':
    case 'tableHeader':
      return 'TABLE_CELL'
    default:
      return node.type.name.toUpperCase()
  }
}

function headingPathForBlock(document: BlockDocument, blockId?: string): string[] {
  if (!blockId) return []
  const headingPath: string[] = []
  let found = false
  walkBlocks(document.blocks, (block) => {
    if (found) return
    if (block.id === blockId) {
      found = true
      return
    }
    if (block.type === 'HEADING') {
      const level = headingLevel(block)
      headingPath.splice(level - 1)
      headingPath[level - 1] = blockText(block)
    }
  })
  return headingPath.filter(Boolean)
}

function nearbyBlocks(document: BlockDocument, blockId?: string): InlineCompletionBlockContext[] {
  const flattened: BlockNode[] = []
  walkBlocks(document.blocks, (block) => {
    flattened.push(block)
  })
  const index = flattened.findIndex((block) => block.id === blockId)
  if (index === -1) return []
  const from = Math.max(0, index - NEARBY_BLOCK_LIMIT)
  const to = Math.min(flattened.length, index + NEARBY_BLOCK_LIMIT + 1)
  return flattened
    .slice(from, to)
    .filter((block) => block.id !== blockId)
    .map((block) => ({
      id: block.id,
      type: block.type,
      text: blockText(block),
      textBeforeCursor: '',
      textAfterCursor: ''
    }))
}

function walkBlocks(blocks: BlockNode[], visit: (block: BlockNode) => void): void {
  blocks.forEach((block) => {
    visit(block)
    walkBlocks(block.children, visit)
  })
}

function blockText(block: BlockNode): string {
  if (typeof block.attrs.text === 'string') return block.attrs.text
  if (typeof block.attrs.source === 'string') return block.attrs.source
  return inlineText(block.inlines)
}

function inlineText(inlines: InlineNode[]): string {
  return inlines.map((inline) => {
    if (inline.type === 'HARD_BREAK' || inline.type === 'SOFT_BREAK') return '\n'
    return inline.text ?? ''
  }).join('')
}

function headingLevel(block: BlockNode): number {
  const level = block.attrs.level
  if (typeof level !== 'number' || !Number.isFinite(level)) return 1
  return Math.max(1, Math.min(6, Math.trunc(level)))
}

function blockSignature(block: InlineCompletionBlockContext): string {
  return [
    block.type,
    hashString(block.textBeforeCursor),
    hashString(block.textAfterCursor)
  ].join(':')
}

function hashString(value: string): string {
  let hash = 0
  for (let index = 0; index < value.length; index += 1) {
    hash = Math.imul(31, hash) + value.charCodeAt(index) | 0
  }
  return `${value.length}:${hash}`
}

function markdownPreviewText(markdown: string, shape: InlineCompletionShape): string {
  if (shape === 'CODE_LINE') return markdown
  return decodeHtmlEntities(markdown)
    .replace(/```[\s\S]*?```/g, '')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/__([^_]+)__/g, '$1')
    .replace(/~~([^~]+)~~/g, '$1')
    .replace(/\[([^\]]+)]\([^)]*\)/g, '$1')
    .replace(/^\s*(?:>\s*)+/gm, '')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\d+[.)]\s+/gm, '')
    .trimStart()
}

function decodeHtmlEntities(text: string): string {
  return text
    .replaceAll('&gt;', '>')
    .replaceAll('&lt;', '<')
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
    .replaceAll('&amp;', '&')
}

function clampCandidateCount(value: number | undefined): number {
  if (value == null || !Number.isFinite(value)) return INLINE_COMPLETION_DEFAULT_CANDIDATE_COUNT
  return Math.max(1, Math.min(5, Math.trunc(value)))
}

function debugInlineCompletion(message: string, detail?: unknown): void {
  recordInlineCompletionDebug('runtime', message, detail)
}
