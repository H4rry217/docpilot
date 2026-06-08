import { ChevronDown, ChevronRight, FileText, Folder, FolderOpen } from 'lucide-react'
import type { DragEvent, MouseEvent } from 'react'
import { WORKSPACE_NODE_TYPE, type WorkspaceTreeNode } from '../../../entities/workspace/types'

export function WorkspaceTreeNodeRow({
  node,
  depth,
  expanded,
  selectedNodeId,
  selectedDocumentId,
  onSelectNode,
  onToggleFolder,
  isFolderExpanded,
  onOpenContextMenu,
  dragTargetNodeId,
  onMarkdownDragOver,
  onMarkdownDragLeave,
  onMarkdownDrop
}: {
  node: WorkspaceTreeNode
  depth: number
  expanded: boolean
  selectedNodeId?: string
  selectedDocumentId?: string
  onSelectNode: (node: WorkspaceTreeNode) => void
  onToggleFolder: (node: WorkspaceTreeNode) => void
  isFolderExpanded: (node: WorkspaceTreeNode) => boolean
  onOpenContextMenu: (event: MouseEvent, node: WorkspaceTreeNode) => void
  dragTargetNodeId?: string
  onMarkdownDragOver: (event: DragEvent, node: WorkspaceTreeNode) => void
  onMarkdownDragLeave: (event: DragEvent, node: WorkspaceTreeNode) => void
  onMarkdownDrop: (event: DragEvent, node: WorkspaceTreeNode) => void
}) {
  const isFolder = node.nodeType === WORKSPACE_NODE_TYPE.FOLDER
  const isSelected = node.nodeId === selectedNodeId || Boolean(node.documentId && node.documentId === selectedDocumentId)
  const isDropTarget = isFolder && dragTargetNodeId === node.nodeId
  const paddingLeft = 12 + depth * 18

  return (
    <div className="tree-node">
      <div
        className={`tree-row-wrap ${isSelected ? 'selected' : ''} ${isDropTarget ? 'drop-target' : ''}`}
        style={{ paddingLeft }}
        onContextMenu={(event) => onOpenContextMenu(event, node)}
        onDragOver={isFolder ? (event) => onMarkdownDragOver(event, node) : undefined}
        onDragLeave={isFolder ? (event) => onMarkdownDragLeave(event, node) : undefined}
        onDrop={isFolder ? (event) => onMarkdownDrop(event, node) : undefined}
      >
        {isFolder ? (
          <button
            className="tree-toggle"
            type="button"
            aria-label={`${expanded ? 'Collapse' : 'Expand'} ${node.name}`}
            aria-expanded={expanded}
            onClick={(event) => {
              event.stopPropagation()
              onToggleFolder(node)
            }}
          >
            {expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
          </button>
        ) : (
          <span className="tree-toggle tree-toggle-placeholder" />
        )}
        <button
          className="tree-row"
          type="button"
          aria-label={node.name}
          aria-current={isSelected ? 'true' : undefined}
          title={node.name}
          onClick={() => onSelectNode(node)}
        >
          {isFolder ? (
            expanded ? <FolderOpen size={15} /> : <Folder size={15} />
          ) : (
            <FileText size={14} />
          )}
          <span className="tree-row-name">{node.name}</span>
        </button>
      </div>
      {isFolder && expanded
        ? node.children.map((child) => (
            <WorkspaceTreeNodeRow
              key={child.nodeId}
              node={child}
              depth={depth + 1}
              expanded={isFolderExpanded(child)}
              selectedNodeId={selectedNodeId}
              selectedDocumentId={selectedDocumentId}
              onSelectNode={onSelectNode}
              onToggleFolder={onToggleFolder}
              isFolderExpanded={isFolderExpanded}
              onOpenContextMenu={onOpenContextMenu}
              dragTargetNodeId={dragTargetNodeId}
              onMarkdownDragOver={onMarkdownDragOver}
              onMarkdownDragLeave={onMarkdownDragLeave}
              onMarkdownDrop={onMarkdownDrop}
            />
          ))
        : null}
    </div>
  )
}
