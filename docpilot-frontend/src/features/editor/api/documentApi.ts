import type { BlockDocument } from '../../../entities/block/types'
import type { DocumentResponse } from '../../../entities/document/types'
import { postJson } from '../../../shared/api/http'

export function getDocument(documentId: string): Promise<DocumentResponse> {
  return postJson('/document/get', { documentId })
}

export function createDocument(input: {
  workspaceId: string
  parentNodeId?: string
  title: string
  nodeName?: string
  blockDocument?: BlockDocument
  markdown?: string
}): Promise<DocumentResponse> {
  return postJson('/document/create', input)
}

export function saveDocumentContent(input: {
  documentId: string
  blockDocument: BlockDocument
  baseVersion: string
  clientMutationId: string
}): Promise<DocumentResponse> {
  return postJson('/document/content/save', input)
}
