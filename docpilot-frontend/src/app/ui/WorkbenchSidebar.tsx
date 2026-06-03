import { Files, Settings } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Workspace, WorkspaceTreeNode } from '../../entities/workspace/types'
import { WorkspaceTree } from '../../features/workspace-tree/ui/WorkspaceTree'
import { useI18n } from '../../shared/i18n'
import { useResizableWidth } from '../../shared/ui/useResizableWidth'
import '../../shared/ui/ResizablePanel.css'
import './WorkbenchSidebar.css'

export type SidebarMode = 'files'

export const WORKSPACE_SIDEBAR_DEFAULT_WIDTH = 200
export const WORKSPACE_SIDEBAR_MIN_WIDTH = 180
export const WORKSPACE_SIDEBAR_MAX_WIDTH = 360

type ActivityButtonProps = {
  active: boolean
  expanded: boolean
  icon: ReactNode
  label: string
  onClick: () => void
}

function ActivityButton({ active, expanded, icon, label, onClick }: ActivityButtonProps) {
  return (
    <button
      className={`activity-button ${active ? 'active' : ''}`}
      type="button"
      aria-label={label}
      aria-expanded={active ? expanded : false}
      title={label}
      onClick={onClick}
    >
      {icon}
    </button>
  )
}

function SidebarPanel({
  selectedNode,
  workspace,
  workspaces,
  selectedWorkspaceId,
  tree,
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
}: {
  selectedNode?: WorkspaceTreeNode
  workspace?: Workspace
  workspaces: Workspace[]
  selectedWorkspaceId?: string
  tree: WorkspaceTreeNode[]
  onSelectWorkspace: (workspace: Workspace) => void
  onCreateWorkspace: () => void
  onRenameWorkspace: (workspace: Workspace) => void
  onDeleteWorkspace: (workspace: Workspace) => void
  onSelectNode: (node: WorkspaceTreeNode) => void
  onCreateFolder: (parentNode?: WorkspaceTreeNode) => void
  onCreateDocument: (parentNode?: WorkspaceTreeNode) => void
  onUploadMarkdownFiles: (files: File[], parentNode?: WorkspaceTreeNode) => void
  onRenameNode: (node: WorkspaceTreeNode) => void
  onDeleteNode: (node: WorkspaceTreeNode) => void
  uploadMessage?: string
}) {
  return (
    <WorkspaceTree
      nodes={tree}
      workspaces={workspaces}
      workspaceName={workspace?.name}
      selectedWorkspaceId={selectedWorkspaceId}
      rootNodeId={workspace?.rootNodeId}
      onSelectWorkspace={onSelectWorkspace}
      onCreateWorkspace={onCreateWorkspace}
      onRenameWorkspace={onRenameWorkspace}
      onDeleteWorkspace={onDeleteWorkspace}
      selectedNodeId={selectedNode?.nodeId}
      selectedDocumentId={selectedNode?.documentId}
      onSelectNode={onSelectNode}
      onCreateFolder={onCreateFolder}
      onCreateDocument={onCreateDocument}
      onUploadMarkdownFiles={onUploadMarkdownFiles}
      onRenameNode={onRenameNode}
      onDeleteNode={onDeleteNode}
      uploadMessage={uploadMessage}
    />
  )
}

export function WorkbenchSidebar({
  mode,
  expanded,
  width,
  selectedNode,
  workspace,
  workspaces,
  selectedWorkspaceId,
  tree,
  onModeChange,
  onWidthChange,
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
  uploadMessage,
  onOpenSettings
}: {
  mode: SidebarMode
  expanded: boolean
  width: number
  selectedNode?: WorkspaceTreeNode
  workspace?: Workspace
  workspaces: Workspace[]
  selectedWorkspaceId?: string
  tree: WorkspaceTreeNode[]
  onModeChange: (mode: SidebarMode) => void
  onWidthChange: (width: number) => void
  onSelectWorkspace: (workspace: Workspace) => void
  onCreateWorkspace: () => void
  onRenameWorkspace: (workspace: Workspace) => void
  onDeleteWorkspace: (workspace: Workspace) => void
  onSelectNode: (node: WorkspaceTreeNode) => void
  onCreateFolder: (parentNode?: WorkspaceTreeNode) => void
  onCreateDocument: (parentNode?: WorkspaceTreeNode) => void
  onUploadMarkdownFiles: (files: File[], parentNode?: WorkspaceTreeNode) => void
  onRenameNode: (node: WorkspaceTreeNode) => void
  onDeleteNode: (node: WorkspaceTreeNode) => void
  uploadMessage?: string
  onOpenSettings: () => void
}) {
  const { t } = useI18n()
  const startResize = useResizableWidth({
    width,
    min: WORKSPACE_SIDEBAR_MIN_WIDTH,
    max: WORKSPACE_SIDEBAR_MAX_WIDTH,
    direction: 'left-panel',
    onChange: onWidthChange
  })

  return (
    <>
      <aside className="activity-bar" aria-label="DocPilot">
        <div className="activity-bar-main">
          <ActivityButton
            active={mode === 'files'}
            expanded={expanded}
            icon={<Files size={18} />}
            label={t('activity.files')}
            onClick={() => onModeChange('files')}
          />
        </div>
        <div className="activity-bar-bottom">
          <ActivityButton
            active={false}
            expanded={false}
            icon={<Settings size={18} />}
            label={t('activity.settings')}
            onClick={onOpenSettings}
          />
        </div>
      </aside>

      <aside className={`workspace-sidebar ${expanded ? '' : 'collapsed'}`} aria-hidden={!expanded}>
        {expanded ? (
          <>
            <SidebarPanel
              selectedNode={selectedNode}
              workspace={workspace}
              workspaces={workspaces}
              selectedWorkspaceId={selectedWorkspaceId}
              tree={tree}
              onSelectWorkspace={onSelectWorkspace}
              onCreateWorkspace={onCreateWorkspace}
              onRenameWorkspace={onRenameWorkspace}
              onDeleteWorkspace={onDeleteWorkspace}
              onSelectNode={onSelectNode}
              onCreateFolder={onCreateFolder}
              onCreateDocument={onCreateDocument}
              onUploadMarkdownFiles={onUploadMarkdownFiles}
              onRenameNode={onRenameNode}
              onDeleteNode={onDeleteNode}
              uploadMessage={uploadMessage}
            />
            <button
              className="resize-handle resize-handle-left"
              type="button"
              aria-label="Resize sidebar"
              onPointerDown={startResize}
            />
          </>
        ) : null}
      </aside>
    </>
  )
}
