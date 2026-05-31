import type { Workspace, WorkspaceNode } from '../../../entities/workspace/types'
import { postJson } from '../../../shared/api/http'

export type WorkspaceListResponse = {
  workspaces: Workspace[]
}

export type WorkspaceTreeResponse = {
  workspace: Workspace
  nodes: WorkspaceNode[]
}

export function ensureDefaultWorkspace(): Promise<Workspace> {
  return postJson('/workspace/default/ensure', {})
}

export function listWorkspaces(): Promise<WorkspaceListResponse> {
  return postJson('/workspace/list', {})
}

export function createWorkspace(input: { name: string }): Promise<Workspace> {
  return postJson('/workspace/create', input)
}

export function renameWorkspace(input: { workspaceId: string; name: string }): Promise<Workspace> {
  return postJson('/workspace/rename', input)
}

export function deleteWorkspace(input: { workspaceId: string }): Promise<void> {
  return postJson('/workspace/delete', input)
}

export function getWorkspaceTree(workspaceId: string): Promise<WorkspaceTreeResponse> {
  return postJson('/workspace/tree/get', { workspaceId })
}

export function createFolder(input: { workspaceId: string; parentNodeId?: string; name: string }): Promise<WorkspaceNode> {
  return postJson('/workspace/node/create', input)
}

export function renameNode(input: { nodeId: string; name: string }): Promise<WorkspaceNode> {
  return postJson('/workspace/node/rename', input)
}

export function deleteNode(input: { nodeId: string }): Promise<void> {
  return postJson('/workspace/node/delete', input)
}
