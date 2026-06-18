import type { BlockDocument } from '../block/types'
import type { ProseMirrorNode } from '../prosemirror/types'

export type DocumentContent = {
  blockSchemaVersion: string
  blockDocument: BlockDocument
  markdownText: string
  checksum?: string
}

export type DocPilotDocument = {
  documentId: string
  ownerUserId: string
  originWorkspaceId: string
  title: string
  currentVersion: string
  currentRevisionId: string
  content: DocumentContent
  createTime: number
  updateTime: number
  metadata: Record<string, unknown>
}

export type DocumentResponse = {
  document: DocPilotDocument
  prosemirror: ProseMirrorNode
}
