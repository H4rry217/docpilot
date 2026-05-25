import type { DocumentResponse, DocumentVisibility } from '../../../entities/document/types'
import { postJson } from '../../../shared/api/http'

export function getDocument(documentId: string): Promise<DocumentResponse> {
  return postJson('/document/get', { documentId })
}

export function createDocument(input: {
  title: string
  markdown: string
  visibility?: DocumentVisibility
}): Promise<DocumentResponse> {
  return postJson('/document/create', input)
}

export function saveDocumentContent(input: {
  documentId: string
  markdown: string
  expectedVersion: number
}): Promise<DocumentResponse> {
  return postJson('/document/content/save', input)
}
