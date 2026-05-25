import { useEffect, useState } from 'react'
import type { WorkspaceTreeNode } from '../entities/workspace/types'
import { DocumentEditor } from '../features/editor/ui/DocumentEditor'
import { useWorkspaceTree } from '../features/workspace-tree/model/useWorkspaceTree'
import { WorkspaceTree } from '../features/workspace-tree/ui/WorkspaceTree'

export function App() {
  const [selectedNode, setSelectedNode] = useState<WorkspaceTreeNode | undefined>()
  const workspaceTree = useWorkspaceTree(selectedNode?.documentId)

  useEffect(() => {
    if (!selectedNode && workspaceTree.selectedDocumentNode) {
      setSelectedNode(workspaceTree.selectedDocumentNode)
    }
  }, [selectedNode, workspaceTree.selectedDocumentNode])

  return (
    <div className="app-shell">
      <WorkspaceTree
        nodes={workspaceTree.tree}
        selectedDocumentId={selectedNode?.documentId}
        onSelectDocument={setSelectedNode}
      />

      <DocumentEditor workspace={workspaceTree.workspace} documentNode={selectedNode} />
    </div>
  )
}
