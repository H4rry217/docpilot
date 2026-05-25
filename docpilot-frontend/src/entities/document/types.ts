import type { BlockDocument } from '../block/types'
import type { ProseMirrorNode } from '../prosemirror/types'

export type DocumentVisibility = 'PRIVATE' | 'LINK_READ'
export type DocumentState = 'ACTIVE' | 'DELETED'

export type DocPilotDocument = {
  documentId: string
  ownerUserId: string
  title: string
  markdown: string
  blockDocument: BlockDocument
  visibility: DocumentVisibility
  state: DocumentState
  version: number
  createTime: string
  updateTime: string
  metadata: Record<string, unknown>
}

export type DocumentResponse = {
  document: DocPilotDocument
  prosemirror: ProseMirrorNode
}
