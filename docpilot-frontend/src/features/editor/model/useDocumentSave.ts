import { useCallback, useEffect, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import type { BlockDocument } from '@/entities/block/types'
import type { DocPilotDocument, DocumentResponse } from '@/entities/document/types'
import { blockDocumentForSave } from '@/features/block-editor/model/proseMirrorToBlockDocument'
import { createClientMutationId } from '@/shared/id/clientMutationId'
import { saveDocumentContent } from '../api/documentApi'
import {
  createDocumentSaveQueue,
  type DocumentSaveRequest
} from './documentSaveQueue'

const AUTOSAVE_DELAY_MS = 5000

export type SaveState = 'idle' | 'dirty' | 'saving' | 'saved' | 'error'

type PendingAutosave = DocumentSaveRequest

type SnapshotForSave = {
  blockDocument: BlockDocument
}

export function saveStateKey(saveState: SaveState) {
  switch (saveState) {
    case 'dirty':
      return 'editor.dirty'
    case 'saving':
      return 'editor.saving'
    case 'saved':
      return 'editor.saved'
    case 'error':
      return 'editor.saveFailed'
    case 'idle':
    default:
      return 'editor.saveIdle'
  }
}

export function useDocumentSave({
  documentId,
  getSnapshot,
  onSavedDocumentData,
  onOutlineFromDocument,
  translateSaveFailed
}: {
  documentId?: string
  getSnapshot: () => SnapshotForSave | null | undefined
  onSavedDocumentData: (response: DocumentResponse) => void
  onOutlineFromDocument: (blockDocument: BlockDocument) => void
  translateSaveFailed: (error: unknown) => string
}) {
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [saveError, setSaveError] = useState<string | null>(null)
  const documentIdRef = useRef<string | undefined>(undefined)
  const versionRef = useRef<string | null>(null)
  const pendingAutosaveRef = useRef<PendingAutosave | null>(null)
  const autosaveTimerRef = useRef<number | undefined>(undefined)
  const saveQueueRef = useRef<ReturnType<typeof createDocumentSaveQueue> | null>(null)

  documentIdRef.current = documentId

  const saveMutation = useMutation({
    mutationFn: saveDocumentContent,
    onMutate: (input) => {
      if (input.documentId !== documentIdRef.current) return
      setSaveError(null)
      setSaveState('saving')
    },
    onSuccess: (response) => {
      const savedDocumentId = response.document.documentId
      const queuedFollowUp = saveQueueRef.current?.finishSave(savedDocumentId, response.document.currentVersion) ?? false

      onSavedDocumentData(response)
      if (savedDocumentId !== documentIdRef.current) return
      versionRef.current = response.document.currentVersion
      setSaveError(null)
      if (!queuedFollowUp) {
        setSaveState('saved')
      }
    },
    onError: (error, input) => {
      saveQueueRef.current?.failSave(input.documentId)
      if (input.documentId !== documentIdRef.current) return
      setSaveState('error')
      setSaveError(translateSaveFailed(error))
    }
  })
  const saveMutationRef = useRef(saveMutation)
  saveMutationRef.current = saveMutation

  if (!saveQueueRef.current) {
    saveQueueRef.current = createDocumentSaveQueue((payload) => {
      saveMutationRef.current.mutate({
        documentId: payload.documentId,
        blockDocument: blockDocumentForSave(payload.blockDocument),
        baseVersion: payload.baseVersion,
        clientMutationId: createClientMutationId()
      })
    })
  }

  const saveBlockDocument = useCallback(
    (blockDocument: BlockDocument, expectedDocumentId = documentIdRef.current) => {
      const activeDocumentId = documentIdRef.current
      if (!activeDocumentId || activeDocumentId !== expectedDocumentId) return

      saveQueueRef.current?.requestSave({
        documentId: activeDocumentId,
        blockDocument
      })
    },
    []
  )

  const queueAutosave = useCallback((blockDocument: BlockDocument) => {
    const queuedDocumentId = documentIdRef.current
    if (!queuedDocumentId) return

    setSaveError(null)
    setSaveState('dirty')

    const pendingAutosave = {
      documentId: queuedDocumentId,
      blockDocument
    }
    pendingAutosaveRef.current = pendingAutosave
    window.clearTimeout(autosaveTimerRef.current)
    autosaveTimerRef.current = window.setTimeout(() => {
      if (pendingAutosaveRef.current !== pendingAutosave) return
      pendingAutosaveRef.current = null
      saveQueueRef.current?.requestSave(pendingAutosave)
    }, AUTOSAVE_DELAY_MS)
  }, [])

  const flushPendingAutosave = useCallback(() => {
    const pendingAutosave = pendingAutosaveRef.current
    if (!pendingAutosave) return
    pendingAutosaveRef.current = null
    window.clearTimeout(autosaveTimerRef.current)
    saveQueueRef.current?.requestSave(pendingAutosave)
  }, [])

  const saveNow = useCallback(() => {
    const snapshot = getSnapshot()
    if (!snapshot) return
    window.clearTimeout(autosaveTimerRef.current)
    pendingAutosaveRef.current = null
    saveBlockDocument(snapshot.blockDocument)
  }, [getSnapshot, saveBlockDocument])

  const registerLoadedDocument = useCallback((loadedDocument: DocPilotDocument) => {
    saveQueueRef.current?.setVersion(loadedDocument.documentId, loadedDocument.currentVersion)
    if (loadedDocument.documentId !== documentIdRef.current) return

    versionRef.current = loadedDocument.currentVersion
    if (saveQueueRef.current?.hasPendingSave(loadedDocument.documentId)) return

    setSaveError(null)
    setSaveState('saved')
    onOutlineFromDocument(loadedDocument.content.blockDocument)
  }, [onOutlineFromDocument])

  const getDocumentVersion = useCallback(() => versionRef.current, [])

  useEffect(() => {
    return () => {
      if (documentIdRef.current === documentId) {
        documentIdRef.current = undefined
      }
      flushPendingAutosave()
    }
  }, [documentId, flushPendingAutosave])

  useEffect(() => {
    versionRef.current = documentId ? saveQueueRef.current?.getVersion(documentId) ?? null : null
    setSaveError(null)
    setSaveState('idle')
  }, [documentId])

  return {
    flushPendingAutosave,
    getDocumentVersion,
    isSaving: saveMutation.isPending,
    queueAutosave,
    registerLoadedDocument,
    saveError,
    saveNow,
    saveState
  }
}
