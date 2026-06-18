import { ChevronDown, ChevronRight, FileText, Folder, FolderOpen } from 'lucide-react'
import type { DragEvent } from 'react'
import { Button } from '@/components/ui/button'
import { ContextMenu, ContextMenuTrigger } from '@/components/ui/context-menu'
import { cn } from '@/lib/utils'
import { WORKSPACE_NODE_TYPE, type WorkspaceTreeNode } from '@/entities/workspace/types'
import { WorkspaceContextMenu } from './WorkspaceContextMenu'

export function WorkspaceTreeNodeRow({
  node,
  depth,
  expanded,
  selectedNodeId,
  selectedDocumentId,
  rootNodeId,
  onSelectNode,
  onToggleFolder,
  isFolderExpanded,
  onCreateFolder,
  onCreateDocument,
  onRenameNode,
  onDeleteNode,
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
  rootNodeId?: string
  onSelectNode: (node: WorkspaceTreeNode) => void
  onToggleFolder: (node: WorkspaceTreeNode) => void
  isFolderExpanded: (node: WorkspaceTreeNode) => boolean
  onCreateFolder?: (parentNode?: WorkspaceTreeNode) => void
  onCreateDocument?: (parentNode?: WorkspaceTreeNode) => void
  onRenameNode?: (node: WorkspaceTreeNode) => void
  onDeleteNode?: (node: WorkspaceTreeNode) => void
  dragTargetNodeId?: string
  onMarkdownDragOver: (event: DragEvent, node: WorkspaceTreeNode) => void
  onMarkdownDragLeave: (event: DragEvent, node: WorkspaceTreeNode) => void
  onMarkdownDrop: (event: DragEvent, node: WorkspaceTreeNode) => void
}) {
  const isFolder = node.nodeType === WORKSPACE_NODE_TYPE.FOLDER
  const isSelected = node.nodeId === selectedNodeId || Boolean(node.documentId && node.documentId === selectedDocumentId)
  const isDropTarget = isFolder && dragTargetNodeId === node.nodeId
  const paddingLeft = 10 + depth * 16

  return (
    <div className="min-w-0">
      <ContextMenu>
        <ContextMenuTrigger asChild>
          <div
            className={cn(
              'flex min-h-7 min-w-0 items-center overflow-hidden rounded-md pr-1 transition-colors',
              isSelected && 'selected',
              isSelected && 'bg-primary/10 text-primary',
              isDropTarget && 'bg-primary/10 shadow-[inset_3px_0_0_var(--primary)]'
            )}
            style={{ paddingLeft }}
            onContextMenu={(event) => event.stopPropagation()}
            onDragOver={isFolder ? (event) => onMarkdownDragOver(event, node) : undefined}
            onDragLeave={isFolder ? (event) => onMarkdownDragLeave(event, node) : undefined}
            onDrop={isFolder ? (event) => onMarkdownDrop(event, node) : undefined}
          >
            {isFolder ? (
              <Button
                className="size-6 shrink-0 text-muted-foreground"
                type="button"
                variant="ghost"
                size="icon-xs"
                aria-label={`${expanded ? 'Collapse' : 'Expand'} ${node.name}`}
                aria-expanded={expanded}
                onClick={(event) => {
                  event.stopPropagation()
                  onToggleFolder(node)
                }}
              >
                {expanded ? <ChevronDown /> : <ChevronRight />}
              </Button>
            ) : (
              <span className="size-6 shrink-0" />
            )}
            <Button
              className={cn(
                'h-7 min-w-0 flex-1 justify-start truncate px-1.5 text-muted-foreground hover:bg-transparent',
                isSelected && 'font-medium text-primary'
              )}
              type="button"
              variant="ghost"
              size="sm"
              aria-label={node.name}
              aria-current={isSelected ? 'true' : undefined}
              title={node.name}
              onClick={() => onSelectNode(node)}
            >
              {isFolder ? (
                expanded ? <FolderOpen data-icon="inline-start" /> : <Folder data-icon="inline-start" />
              ) : (
                <FileText data-icon="inline-start" />
              )}
              <span className="tree-row-name truncate">{node.name}</span>
            </Button>
          </div>
        </ContextMenuTrigger>
        <WorkspaceContextMenu
          node={node}
          rootNodeId={rootNodeId}
          onCreateDocument={onCreateDocument}
          onCreateFolder={onCreateFolder}
          onDeleteNode={onDeleteNode}
          onRenameNode={onRenameNode}
        />
      </ContextMenu>
      {isFolder && expanded
        ? node.children.map((child) => (
            <WorkspaceTreeNodeRow
              key={child.nodeId}
              node={child}
              depth={depth + 1}
              expanded={isFolderExpanded(child)}
              selectedNodeId={selectedNodeId}
              selectedDocumentId={selectedDocumentId}
              rootNodeId={rootNodeId}
              onSelectNode={onSelectNode}
              onToggleFolder={onToggleFolder}
              isFolderExpanded={isFolderExpanded}
              onCreateFolder={onCreateFolder}
              onCreateDocument={onCreateDocument}
              onRenameNode={onRenameNode}
              onDeleteNode={onDeleteNode}
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
