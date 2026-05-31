export const WORKSPACE_TYPE = {
  PERSONAL: 1,
  CUSTOM: 2
} as const

export const WORKSPACE_NODE_TYPE = {
  FOLDER: 1,
  RESOURCE: 2
} as const

export const WORKSPACE_RESOURCE_TYPE = {
  DOCUMENT: 1,
  IMAGE: 2,
  FILE: 3,
  LINK: 4
} as const

export type WorkspaceType = (typeof WORKSPACE_TYPE)[keyof typeof WORKSPACE_TYPE]
export type WorkspaceNodeType = (typeof WORKSPACE_NODE_TYPE)[keyof typeof WORKSPACE_NODE_TYPE]
export type WorkspaceResourceType = (typeof WORKSPACE_RESOURCE_TYPE)[keyof typeof WORKSPACE_RESOURCE_TYPE]

export type Workspace = {
  workspaceId: string
  ownerUserId: string
  name: string
  type: WorkspaceType
  rootNodeId: string
  settings: Record<string, unknown>
  createTime: string
  updateTime: string
}

export type WorkspaceNode = {
  nodeId: string
  workspaceId: string
  parentNodeId?: string
  ancestors: string[]
  nodeType: WorkspaceNodeType
  resourceType?: WorkspaceResourceType
  name: string
  documentId?: string
  storage?: Record<string, unknown>
  mimeType?: string
  size?: string
  checksum?: string
  createTime: string
  updateTime: string
  metadata: Record<string, unknown>
}

export type WorkspaceTreeNode = WorkspaceNode & {
  children: WorkspaceTreeNode[]
}
