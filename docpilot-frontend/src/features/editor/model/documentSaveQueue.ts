import type { BlockDocument } from '@/entities/block/types'

export type DocumentSaveRequest = {
  blockDocument: BlockDocument
  documentId: string
}

export type DocumentSaveSubmission = DocumentSaveRequest & {
  baseVersion: string
}

export function createDocumentSaveQueue(
  submit: (submission: DocumentSaveSubmission) => void
) {
  const inFlightDocumentIds = new Set<string>()
  const queuedSaveByDocumentId = new Map<string, DocumentSaveRequest>()
  const versionByDocumentId = new Map<string, string>()

  function submitWithCurrentVersion(request: DocumentSaveRequest): boolean {
    const baseVersion = versionByDocumentId.get(request.documentId)
    if (baseVersion == null) return false

    inFlightDocumentIds.add(request.documentId)
    submit({
      ...request,
      baseVersion
    })
    return true
  }

  return {
    failSave(documentId: string) {
      inFlightDocumentIds.delete(documentId)
      queuedSaveByDocumentId.delete(documentId)
    },

    finishSave(documentId: string, nextVersion: string): boolean {
      inFlightDocumentIds.delete(documentId)
      versionByDocumentId.set(documentId, nextVersion)

      const queuedSave = queuedSaveByDocumentId.get(documentId)
      if (!queuedSave) return false

      queuedSaveByDocumentId.delete(documentId)
      return submitWithCurrentVersion(queuedSave)
    },

    getVersion(documentId: string): string | null {
      return versionByDocumentId.get(documentId) ?? null
    },

    hasPendingSave(documentId: string): boolean {
      return inFlightDocumentIds.has(documentId) || queuedSaveByDocumentId.has(documentId)
    },

    requestSave(request: DocumentSaveRequest): boolean {
      if (inFlightDocumentIds.has(request.documentId)) {
        queuedSaveByDocumentId.set(request.documentId, request)
        return false
      }

      return submitWithCurrentVersion(request)
    },

    setVersion(documentId: string, version: string) {
      versionByDocumentId.set(documentId, version)
    }
  }
}
