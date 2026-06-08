import { useEffect, useState, type MouseEvent } from 'react'
import type { Workspace, WorkspaceTreeNode } from '../../../entities/workspace/types'
import { useI18n } from '../../../shared/i18n'
import { WorkspaceContextMenu, type WorkspaceContextMenuState } from './WorkspaceContextMenu'
import { WorkspaceSwitcher } from './WorkspaceSwitcher'
import { WorkspaceTreeNodeRow } from './WorkspaceTreeNodeRow'
import './WorkspaceTree.css'
import { useWorkspaceTreeDnd } from './useWorkspaceTreeDnd'

export type WorkspaceTreeProps = {
  nodes: WorkspaceTreeNode[]
  workspaces?: Workspace[]
  workspaceName?: string
  selectedWorkspaceId?: string
  rootNodeId?: string
  selectedNodeId?: string
  selectedDocumentId?: string
  onSelectWorkspace?: (workspace: Workspace) => void
  onCreateWorkspace?: () => void
  onRenameWorkspace?: (workspace: Workspace) => void
  onDeleteWorkspace?: (workspace: Workspace) => void
  onSelectNode: (node: WorkspaceTreeNode) => void
  onCreateFolder?: (parentNode?: WorkspaceTreeNode) => void
  onCreateDocument?: (parentNode?: WorkspaceTreeNode) => void
  onUploadMarkdownFiles?: (files: File[], parentNode?: WorkspaceTreeNode) => void
  onRenameNode?: (node: WorkspaceTreeNode) => void
  onDeleteNode?: (node: WorkspaceTreeNode) => void
  uploadMessage?: string
}

export function WorkspaceTree({
  nodes,
  workspaces = [],
  workspaceName,
  selectedWorkspaceId,
  rootNodeId,
  selectedNodeId,
  selectedDocumentId,
  onSelectWorkspace,
  onCreateWorkspace,
  onRenameWorkspace,
  onDeleteWorkspace,
  onSelectNode,
  onCreateFolder,
  onCreateDocument,
  onUploadMarkdownFiles,
  onRenameNode,
  onDeleteNode,
  uploadMessage
}: WorkspaceTreeProps) {
  const { t } = useI18n()
  const [collapsedFolderIds, setCollapsedFolderIds] = useState<Set<string>>(() => new Set())
  const [contextMenu, setContextMenu] = useState<WorkspaceContextMenuState>()
  const [showWorkspaceList, setShowWorkspaceList] = useState(false)
  const dnd = useWorkspaceTreeDnd({ onUploadMarkdownFiles })
  const currentWorkspaceName = workspaceName ?? t('sidebar.workspace')

  useEffect(() => {
    if (!contextMenu) return

    function closeContextMenu() {
      setContextMenu(undefined)
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') closeContextMenu()
    }

    document.addEventListener('click', closeContextMenu)
    document.addEventListener('contextmenu', closeContextMenu)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('click', closeContextMenu)
      document.removeEventListener('contextmenu', closeContextMenu)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [contextMenu])

  function toggleFolder(node: WorkspaceTreeNode) {
    setCollapsedFolderIds((current) => {
      const next = new Set(current)
      if (next.has(node.nodeId)) {
        next.delete(node.nodeId)
      } else {
        next.add(node.nodeId)
      }
      return next
    })
  }

  function isFolderExpanded(node: WorkspaceTreeNode) {
    return !collapsedFolderIds.has(node.nodeId)
  }

  function openContextMenu(event: MouseEvent, node?: WorkspaceTreeNode) {
    event.preventDefault()
    event.stopPropagation()
    setContextMenu({
      node,
      x: Math.max(8, Math.min(event.clientX, window.innerWidth - 144)),
      y: Math.max(8, Math.min(event.clientY, window.innerHeight - 84))
    })
  }

  if (showWorkspaceList) {
    return (
      <WorkspaceSwitcher
        currentWorkspaceName={currentWorkspaceName}
        rootDropTarget={dnd.rootDropTarget}
        selectedWorkspaceId={selectedWorkspaceId}
        showWorkspaceList={showWorkspaceList}
        workspaces={workspaces}
        onCreateWorkspace={onCreateWorkspace}
        onDeleteWorkspace={onDeleteWorkspace}
        onHideWorkspaceList={() => setShowWorkspaceList(false)}
        onOpenContextMenu={openContextMenu}
        onRenameWorkspace={onRenameWorkspace}
        onRootDragLeave={dnd.handleRootDragLeave}
        onRootDragOver={dnd.handleRootDragOver}
        onRootDrop={dnd.handleRootDrop}
        onSelectWorkspace={onSelectWorkspace}
        onShowWorkspaceList={() => setShowWorkspaceList(true)}
      />
    )
  }

  return (
    <div
      className="workspace-panel"
      onContextMenu={(event) => openContextMenu(event)}
      onDragOver={dnd.handlePanelDragOver}
      onDragLeave={dnd.handlePanelDragLeave}
      onDrop={dnd.handlePanelDrop}
    >
      <WorkspaceSwitcher
        currentWorkspaceName={currentWorkspaceName}
        rootDropTarget={dnd.rootDropTarget}
        selectedWorkspaceId={selectedWorkspaceId}
        showWorkspaceList={showWorkspaceList}
        workspaces={workspaces}
        onCreateWorkspace={onCreateWorkspace}
        onDeleteWorkspace={onDeleteWorkspace}
        onHideWorkspaceList={() => setShowWorkspaceList(false)}
        onOpenContextMenu={openContextMenu}
        onRenameWorkspace={onRenameWorkspace}
        onRootDragLeave={dnd.handleRootDragLeave}
        onRootDragOver={dnd.handleRootDragOver}
        onRootDrop={dnd.handleRootDrop}
        onSelectWorkspace={onSelectWorkspace}
        onShowWorkspaceList={() => setShowWorkspaceList(true)}
      />

      <div className="tree-list">
        {nodes.length > 0 ? (
          nodes.map((node) => (
            <WorkspaceTreeNodeRow
              key={node.nodeId}
              node={node}
              depth={0}
              expanded={isFolderExpanded(node)}
              selectedNodeId={selectedNodeId}
              selectedDocumentId={selectedDocumentId}
              onSelectNode={onSelectNode}
              onToggleFolder={toggleFolder}
              isFolderExpanded={isFolderExpanded}
              onOpenContextMenu={openContextMenu}
              dragTargetNodeId={dnd.dragTargetNodeId}
              onMarkdownDragOver={dnd.handleMarkdownDragOver}
              onMarkdownDragLeave={dnd.handleMarkdownDragLeave}
              onMarkdownDrop={dnd.handleMarkdownDrop}
            />
          ))
        ) : (
          <div className="sidebar-empty">{t('workspace.empty')}</div>
        )}
      </div>
      {uploadMessage ? <div className="workspace-upload-message">{uploadMessage}</div> : null}
      {contextMenu ? (
        <WorkspaceContextMenu
          contextMenu={contextMenu}
          rootNodeId={rootNodeId}
          onClose={() => setContextMenu(undefined)}
          onCreateDocument={onCreateDocument}
          onCreateFolder={onCreateFolder}
          onDeleteNode={onDeleteNode}
          onRenameNode={onRenameNode}
        />
      ) : null}
    </div>
  )
}
