import { ChevronDown, ChevronRight, FilePlus2, FileText, Folder, FolderOpen, FolderPlus, Pencil, Trash2 } from 'lucide-react'
import { useEffect, useState, type MouseEvent } from 'react'
import { WORKSPACE_NODE_TYPE, WORKSPACE_TYPE, type Workspace, type WorkspaceTreeNode } from '../../../entities/workspace/types'
import { useI18n } from '../../../shared/i18n'
import './WorkspaceTree.css'

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
  onRenameNode?: (node: WorkspaceTreeNode) => void
  onDeleteNode?: (node: WorkspaceTreeNode) => void
}

type ContextMenuState = {
  node?: WorkspaceTreeNode
  x: number
  y: number
}

function TreeNode({
  node,
  depth,
  expanded,
  selectedNodeId,
  selectedDocumentId,
  onSelectNode,
  onToggleFolder,
  isFolderExpanded,
  onOpenContextMenu
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
}) {
  const isFolder = node.nodeType === WORKSPACE_NODE_TYPE.FOLDER
  const isSelected = node.nodeId === selectedNodeId || Boolean(node.documentId && node.documentId === selectedDocumentId)
  const paddingLeft = 12 + depth * 18

  return (
    <div className="tree-node">
      <div
        className={`tree-row-wrap ${isSelected ? 'selected' : ''}`}
        style={{ paddingLeft }}
        onContextMenu={(event) => onOpenContextMenu(event, node)}
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
            <TreeNode
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
            />
          ))
        : null}
    </div>
  )
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
  onRenameNode,
  onDeleteNode
}: WorkspaceTreeProps) {
  const { t } = useI18n()
  const [collapsedFolderIds, setCollapsedFolderIds] = useState<Set<string>>(() => new Set())
  const [contextMenu, setContextMenu] = useState<ContextMenuState>()
  const [showWorkspaceList, setShowWorkspaceList] = useState(false)

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

  function handleCreateAction(action?: (parentNode?: WorkspaceTreeNode) => void) {
    if (!contextMenu || !action) return
    action(contextMenu.node)
    setContextMenu(undefined)
  }

  function handleNodeAction(action?: (node: WorkspaceTreeNode) => void) {
    if (!contextMenu || !action) return
    if (!contextMenu.node) return
    action(contextMenu.node)
    setContextMenu(undefined)
  }

  function selectWorkspace(workspace: Workspace) {
    onSelectWorkspace?.(workspace)
    setShowWorkspaceList(false)
  }

  const contextNodeIsFolder = !contextMenu?.node || contextMenu.node.nodeType === WORKSPACE_NODE_TYPE.FOLDER
  const contextNodeIsRoot = Boolean(contextMenu?.node && contextMenu.node.nodeId === rootNodeId)
  const showNodeActions = Boolean(contextMenu?.node && !contextNodeIsRoot)

  if (showWorkspaceList) {
    return (
      <div className="workspace-panel">
        <button className="workspace-card" type="button" onClick={() => setShowWorkspaceList(false)}>
          <span className="workspace-card-icon">
            <FolderOpen size={16} />
          </span>
          <span className="workspace-card-copy">
            <strong>{t('workspace.listTitle')}</strong>
          </span>
          <ChevronDown size={14} />
        </button>

        <div className="workspace-list-header">
          <span>{t('workspace.listTitle')}</span>
          <button
            type="button"
            aria-label={t('workspace.newWorkspace')}
            title={t('workspace.newWorkspace')}
            onClick={() => onCreateWorkspace?.()}
          >
            <FolderPlus size={14} />
          </button>
        </div>
        <div className="workspace-list">
          {workspaces.length > 0 ? (
            workspaces.map((workspace) => {
              const isActive = workspace.workspaceId === selectedWorkspaceId
              const canManageWorkspace = workspace.type !== WORKSPACE_TYPE.PERSONAL
              return (
                <div key={workspace.workspaceId} className={`workspace-list-row ${isActive ? 'active' : ''} ${canManageWorkspace ? 'has-actions' : ''}`}>
                  <button
                    className="workspace-list-select"
                    type="button"
                    aria-current={isActive ? 'true' : undefined}
                    title={workspace.name}
                    onClick={() => selectWorkspace(workspace)}
                  >
                    <span className="workspace-list-icon">
                      {isActive ? <FolderOpen size={15} /> : <Folder size={15} />}
                    </span>
                    <span>{workspace.name}</span>
                  </button>
                  {canManageWorkspace ? (
                    <button
                      className="workspace-list-action"
                      type="button"
                      aria-label={`${t('workspace.renameWorkspace')} ${workspace.name}`}
                      title={t('workspace.renameWorkspace')}
                      onClick={() => onRenameWorkspace?.(workspace)}
                    >
                      <Pencil size={13} />
                    </button>
                  ) : null}
                  {canManageWorkspace ? (
                    <button
                      className="workspace-list-action danger"
                      type="button"
                      aria-label={`${t('workspace.deleteWorkspace')} ${workspace.name}`}
                      title={t('workspace.deleteWorkspace')}
                      onClick={() => onDeleteWorkspace?.(workspace)}
                    >
                      <Trash2 size={13} />
                    </button>
                  ) : null}
                </div>
              )
            })
          ) : (
            <div className="sidebar-empty">{t('workspace.emptyWorkspaces')}</div>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="workspace-panel" onContextMenu={(event) => openContextMenu(event)}>
      <button
        className="workspace-card"
        type="button"
        title={workspaceName ?? t('sidebar.workspace')}
        onClick={() => setShowWorkspaceList(true)}
        onContextMenu={(event) => openContextMenu(event)}
      >
        <span className="workspace-card-icon">
          <FolderOpen size={16} />
        </span>
        <span className="workspace-card-copy">
          <strong>{workspaceName ?? t('sidebar.workspace')}</strong>
        </span>
        <ChevronRight size={14} />
      </button>

      <div className="tree-list">
        {nodes.length > 0 ? (
          nodes.map((node) => (
            <TreeNode
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
            />
          ))
        ) : (
          <div className="sidebar-empty">{t('workspace.empty')}</div>
        )}
      </div>
      {contextMenu ? (
        <div className="workspace-context-menu" style={{ left: contextMenu.x, top: contextMenu.y }} role="menu">
          {contextNodeIsFolder ? (
            <>
              <button type="button" role="menuitem" onClick={() => handleCreateAction(onCreateDocument)}>
                <FilePlus2 size={13} />
                <span>{t('workspace.newDocument')}</span>
              </button>
              <button type="button" role="menuitem" onClick={() => handleCreateAction(onCreateFolder)}>
                <FolderPlus size={13} />
                <span>{t('workspace.newFolder')}</span>
              </button>
            </>
          ) : null}
          {contextNodeIsFolder && showNodeActions ? <div className="workspace-context-menu-separator" /> : null}
          {showNodeActions ? (
            <>
              <button type="button" role="menuitem" onClick={() => handleNodeAction(onRenameNode)}>
                <Pencil size={13} />
                <span>{t('workspace.rename')}</span>
              </button>
              <button type="button" role="menuitem" className="danger" onClick={() => handleNodeAction(onDeleteNode)}>
                <Trash2 size={13} />
                <span>{t('workspace.delete')}</span>
              </button>
            </>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
