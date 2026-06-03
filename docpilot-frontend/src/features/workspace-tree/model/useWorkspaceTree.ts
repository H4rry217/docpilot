import { useQuery } from '@tanstack/react-query'
import { WORKSPACE_NODE_TYPE, type Workspace, type WorkspaceNode, type WorkspaceTreeNode } from '../../../entities/workspace/types'
import { ensureDefaultWorkspace, getWorkspaceTree } from '../api/workspaceApi'

export type WorkspaceTreeState = {
  workspace?: Workspace
  tree: WorkspaceTreeNode[]
  isLoading: boolean
  error?: Error
}

function buildTree(nodes: WorkspaceNode[], workspace?: Workspace): WorkspaceTreeNode[] {
  const byParent = new Map<string | undefined, WorkspaceNode[]>()
  for (const node of nodes) {
    const parentKey = node.parentNodeId
    byParent.set(parentKey, [...(byParent.get(parentKey) ?? []), node])
  }

  function compareNodes(left: WorkspaceNode, right: WorkspaceNode): number {
    if (left.nodeType !== right.nodeType) {
      return left.nodeType === WORKSPACE_NODE_TYPE.FOLDER ? -1 : 1
    }
    return left.name.localeCompare(right.name, 'zh-CN')
  }

  function hydrate(parentNodeId?: string): WorkspaceTreeNode[] {
    return [...(byParent.get(parentNodeId) ?? [])].sort(compareNodes).map((node) => ({
      ...node,
      children: hydrate(node.nodeId)
    }))
  }

  const rootNodes = hydrate(undefined)
  if (rootNodes.length > 0) return rootNodes
  return workspace ? hydrate(workspace.rootNodeId) : []
}

export function useWorkspaceTree(workspaceId?: string, enabled = true): WorkspaceTreeState {
  const query = useQuery({
    queryKey: ['workspace-tree', workspaceId ?? 'default'],
    enabled,
    queryFn: async () => {
      const workspace = workspaceId ? undefined : await ensureDefaultWorkspace()
      const treeResponse = await getWorkspaceTree(workspaceId ?? workspace?.workspaceId ?? '')
      return {
        workspace: treeResponse.workspace,
        tree: buildTree(treeResponse.nodes, treeResponse.workspace)
      }
    }
  })

  const tree = query.data?.tree ?? []
  return {
    workspace: query.data?.workspace,
    tree,
    isLoading: query.isLoading,
    error: query.error instanceof Error ? query.error : undefined
  }
}
