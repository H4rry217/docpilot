import { useQuery } from '@tanstack/react-query'
import type { Workspace, WorkspaceNode, WorkspaceTreeNode } from '../../../entities/workspace/types'
import { listWorkspaceNodes, listWorkspaces } from '../api/workspaceApi'

export type WorkspaceTreeState = {
  workspace?: Workspace
  tree: WorkspaceTreeNode[]
  selectedDocumentNode?: WorkspaceTreeNode
  isLoading: boolean
  error?: Error
}

async function loadTree(workspace: Workspace): Promise<WorkspaceTreeNode[]> {
  async function hydrate(parentNodeId: string): Promise<WorkspaceTreeNode[]> {
    const response = await listWorkspaceNodes(workspace.workspaceId, parentNodeId)
    const sorted = [...response.nodes].sort(compareNodes)
    return Promise.all(
      sorted.map(async (node) => ({
        ...node,
        children: node.type === 'FOLDER' ? await hydrate(node.nodeId) : []
      }))
    )
  }

  return hydrate(workspace.rootNodeId)
}

function compareNodes(left: WorkspaceNode, right: WorkspaceNode): number {
  if (left.type !== right.type) {
    return left.type === 'FOLDER' ? -1 : 1
  }
  if (left.sortOrder !== right.sortOrder) {
    return left.sortOrder - right.sortOrder
  }
  return left.name.localeCompare(right.name, 'zh-CN')
}

function flatten(nodes: WorkspaceTreeNode[]): WorkspaceTreeNode[] {
  return nodes.flatMap((node) => [node, ...flatten(node.children)])
}

function findDefaultDocument(nodes: WorkspaceTreeNode[], selectedDocumentId?: string): WorkspaceTreeNode | undefined {
  const documents = flatten(nodes).filter((node) => node.type === 'DOCUMENT' && node.documentId)
  return (
    documents.find((node) => node.documentId === selectedDocumentId) ??
    documents.find((node) => node.name === '西游记.md') ??
    documents[0]
  )
}

export function useWorkspaceTree(selectedDocumentId?: string, enabled = true): WorkspaceTreeState {
  const query = useQuery({
    queryKey: ['workspace-tree'],
    enabled,
    queryFn: async () => {
      const workspaceList = await listWorkspaces()
      const workspace = workspaceList.workspaces[0]
      if (!workspace) {
        return { workspace: undefined, tree: [] }
      }
      return {
        workspace,
        tree: await loadTree(workspace)
      }
    }
  })

  const tree = query.data?.tree ?? []
  return {
    workspace: query.data?.workspace,
    tree,
    selectedDocumentNode: findDefaultDocument(tree, selectedDocumentId),
    isLoading: query.isLoading,
    error: query.error instanceof Error ? query.error : undefined
  }
}
