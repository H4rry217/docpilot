import { ChevronDown, FileText, Folder, FolderOpen } from 'lucide-react'
import type { WorkspaceTreeNode } from '../../../entities/workspace/types'

export type WorkspaceTreeProps = {
  nodes: WorkspaceTreeNode[]
  selectedDocumentId?: string
  onSelectDocument: (node: WorkspaceTreeNode) => void
}

function TreeNode({
  node,
  depth,
  selectedDocumentId,
  onSelectDocument
}: {
  node: WorkspaceTreeNode
  depth: number
  selectedDocumentId?: string
  onSelectDocument: (node: WorkspaceTreeNode) => void
}) {
  const isFolder = node.type === 'FOLDER'
  const isSelected = node.documentId === selectedDocumentId
  const paddingLeft = 12 + depth * 18

  return (
    <div>
      <button
        className={`tree-row ${isSelected ? 'selected' : ''}`}
        style={{ paddingLeft }}
        type="button"
        onClick={() => {
          if (!isFolder) onSelectDocument(node)
        }}
      >
        {isFolder ? (
          <>
            <ChevronDown size={13} className="tree-chevron" />
            {depth === 0 ? <FolderOpen size={15} /> : <Folder size={15} />}
          </>
        ) : (
          <>
            <span className="tree-chevron" />
            <FileText size={14} />
          </>
        )}
        <span>{node.name}</span>
      </button>
      {isFolder
        ? node.children.map((child) => (
            <TreeNode
              key={child.nodeId}
              node={child}
              depth={depth + 1}
              selectedDocumentId={selectedDocumentId}
              onSelectDocument={onSelectDocument}
            />
          ))
        : null}
    </div>
  )
}

export function WorkspaceTree({ nodes, selectedDocumentId, onSelectDocument }: WorkspaceTreeProps) {
  return (
    <aside className="workspace-panel">
      <div className="tree-list">
        {nodes.map((node) => (
          <TreeNode
            key={node.nodeId}
            node={node}
            depth={0}
            selectedDocumentId={selectedDocumentId}
            onSelectDocument={onSelectDocument}
          />
        ))}
      </div>
    </aside>
  )
}
