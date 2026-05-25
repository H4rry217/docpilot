export type WorkspaceState = 'ACTIVE' | 'DELETED'
export type WorkspaceNodeState = 'ACTIVE' | 'DELETED'
export type WorkspaceNodeType = 'FOLDER' | 'DOCUMENT'

export type Workspace = {
  workspaceId: string
  ownerUserId: string
  name: string
  rootNodeId: string
  state: WorkspaceState
  createTime: string
  updateTime: string
  metadata: Record<string, unknown>
}

export type WorkspaceNode = {
  nodeId: string
  workspaceId: string
  parentNodeId?: string
  type: WorkspaceNodeType
  name: string
  documentId?: string
  sortOrder: number
  state: WorkspaceNodeState
  createTime: string
  updateTime: string
  metadata: Record<string, unknown>
}

export type WorkspaceTreeNode = WorkspaceNode & {
  children: WorkspaceTreeNode[]
}
