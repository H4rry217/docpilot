import { ChevronDown, ChevronRight, Folder, FolderOpen, FolderPlus, Pencil, Trash2 } from 'lucide-react'
import type { DragEvent, MouseEvent } from 'react'
import { WORKSPACE_TYPE, type Workspace } from '../../../entities/workspace/types'
import { useI18n } from '../../../shared/i18n'

export function WorkspaceSwitcher({
  currentWorkspaceName,
  rootDropTarget,
  selectedWorkspaceId,
  showWorkspaceList,
  workspaces,
  onCreateWorkspace,
  onDeleteWorkspace,
  onHideWorkspaceList,
  onOpenContextMenu,
  onRenameWorkspace,
  onRootDragLeave,
  onRootDragOver,
  onRootDrop,
  onSelectWorkspace,
  onShowWorkspaceList
}: {
  currentWorkspaceName: string
  rootDropTarget: boolean
  selectedWorkspaceId?: string
  showWorkspaceList: boolean
  workspaces: Workspace[]
  onCreateWorkspace?: () => void
  onDeleteWorkspace?: (workspace: Workspace) => void
  onHideWorkspaceList: () => void
  onOpenContextMenu: (event: MouseEvent) => void
  onRenameWorkspace?: (workspace: Workspace) => void
  onRootDragLeave: (event: DragEvent) => void
  onRootDragOver: (event: DragEvent) => void
  onRootDrop: (event: DragEvent) => void
  onSelectWorkspace?: (workspace: Workspace) => void
  onShowWorkspaceList: () => void
}) {
  const { t } = useI18n()

  if (!showWorkspaceList) {
    return (
      <button
        className={`workspace-card ${rootDropTarget ? 'drop-target' : ''}`}
        type="button"
        title={currentWorkspaceName}
        onClick={onShowWorkspaceList}
        onContextMenu={onOpenContextMenu}
        onDragOver={onRootDragOver}
        onDragLeave={onRootDragLeave}
        onDrop={onRootDrop}
      >
        <span className="workspace-card-icon">
          <FolderOpen size={16} />
        </span>
        <span className="workspace-card-copy">
          <strong>{currentWorkspaceName}</strong>
        </span>
        <ChevronRight size={14} />
      </button>
    )
  }

  return (
    <div className="workspace-panel">
      <button className="workspace-card" type="button" onClick={onHideWorkspaceList}>
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
                  onClick={() => {
                    onSelectWorkspace?.(workspace)
                    onHideWorkspaceList()
                  }}
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
