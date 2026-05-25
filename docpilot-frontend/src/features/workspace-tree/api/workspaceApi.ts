import type { Workspace, WorkspaceNode } from '../../../entities/workspace/types'
import { postJson } from '../../../shared/api/http'

export type WorkspaceListResponse = {
  workspaces: Workspace[]
}

export type WorkspaceNodeListResponse = {
  nodes: WorkspaceNode[]
}

export function listWorkspaces(): Promise<WorkspaceListResponse> {
  return postJson('/workspace/list', {})
}

export function listWorkspaceNodes(workspaceId: string, parentNodeId: string): Promise<WorkspaceNodeListResponse> {
  return postJson('/workspace/node/list', {
    workspaceId,
    parentNodeId
  })
}
