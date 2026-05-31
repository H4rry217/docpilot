import { Bot, Files, ListTree, Search, Settings } from 'lucide-react'
import { type ReactNode } from 'react'
import type { UserInformation } from '../../entities/user/types'
import type { Workspace, WorkspaceTreeNode } from '../../entities/workspace/types'
import { ProfileControls } from '../../features/auth/ui/ProfileControls'
import { WorkspaceTree } from '../../features/workspace-tree/ui/WorkspaceTree'
import { useI18n, type Locale } from '../../shared/i18n'
import { useResizableWidth } from '../../shared/ui/useResizableWidth'

export type SidebarMode = 'files' | 'outline' | 'search' | 'agent' | 'settings'

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
  mode,
  currentUser,
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
  onRenameNode,
  onDeleteNode,
  onUserChange,
  onLogout
}: {
  mode: SidebarMode
  currentUser: UserInformation
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
  onRenameNode: (node: WorkspaceTreeNode) => void
  onDeleteNode: (node: WorkspaceTreeNode) => void
  onUserChange: (user: UserInformation) => void
  onLogout: () => void
}) {
  const { locale, setLocale, t } = useI18n()
  const selectedName = selectedNode?.name ?? t('sidebar.noDocument')

  if (mode === 'files') {
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
        onRenameNode={onRenameNode}
        onDeleteNode={onDeleteNode}
      />
    )
  }

  if (mode === 'outline') {
    return (
      <section className="sidebar-panel">
        <header className="sidebar-panel-header">
          <span>{t('activity.outline')}</span>
          <strong>{t('sidebar.outlineTitle')}</strong>
        </header>
        <div className="outline-list">
          <button type="button" className="outline-row active">
            <span>H1</span>
            <strong>{selectedName}</strong>
          </button>
          <button type="button" className="outline-row">
            <span>H2</span>
            <strong>{t('sidebar.outlineHeading')}</strong>
          </button>
          <button type="button" className="outline-row">
            <span>¶</span>
            <strong>{t('sidebar.outlineParagraph')}</strong>
          </button>
          <button type="button" className="outline-row">
            <span>{'{}'}</span>
            <strong>{t('sidebar.outlineHtml')}</strong>
          </button>
        </div>
        <p className="sidebar-note">{t('sidebar.outlineIntro')}</p>
      </section>
    )
  }

  if (mode === 'search') {
    return (
      <section className="sidebar-panel">
        <header className="sidebar-panel-header">
          <span>{t('activity.search')}</span>
          <strong>{t('sidebar.searchTitle')}</strong>
        </header>
        <label className="sidebar-search">
          <Search size={14} />
          <input placeholder={t('sidebar.searchPlaceholder')} />
        </label>
        <p className="sidebar-note">{t('sidebar.searchHint')}</p>
      </section>
    )
  }

  if (mode === 'agent') {
    return (
      <section className="sidebar-panel">
        <header className="sidebar-panel-header">
          <span>{t('activity.agent')}</span>
          <strong>{t('sidebar.agentTitle')}</strong>
        </header>
        <div className="agent-sidebar-card is-live">
          <span>{t('sidebar.agentIdle')}</span>
          <strong>{selectedName}</strong>
        </div>
        <div className="agent-sidebar-card">
          <strong>{t('sidebar.agentReview')}</strong>
          <span>{t('sidebar.agentReviewDesc')}</span>
        </div>
        <div className="agent-sidebar-card">
          <strong>{t('sidebar.agentApply')}</strong>
          <span>{t('sidebar.agentApplyDesc')}</span>
        </div>
      </section>
    )
  }

  return (
    <section className="sidebar-panel">
      <header className="sidebar-panel-header">
        <span>{t('activity.settings')}</span>
        <strong>{t('sidebar.settingsTitle')}</strong>
      </header>
      <label className="locale-field">
        <span>{t('sidebar.languageTitle')}</span>
        <select value={locale} onChange={(event) => setLocale(event.target.value as Locale)}>
          <option value="zh-CN">中文</option>
          <option value="en-US">English</option>
        </select>
      </label>
      <ProfileControls user={currentUser} onUserChange={onUserChange} onLogout={onLogout} />
    </section>
  )
}

export function WorkbenchSidebar({
  mode,
  expanded,
  width,
  currentUser,
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
  onRenameNode,
  onDeleteNode,
  onUserChange,
  onLogout
}: {
  mode: SidebarMode
  expanded: boolean
  width: number
  currentUser: UserInformation
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
  onRenameNode: (node: WorkspaceTreeNode) => void
  onDeleteNode: (node: WorkspaceTreeNode) => void
  onUserChange: (user: UserInformation) => void
  onLogout: () => void
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
          <ActivityButton
            active={mode === 'outline'}
            expanded={expanded}
            icon={<ListTree size={18} />}
            label={t('activity.outline')}
            onClick={() => onModeChange('outline')}
          />
          <ActivityButton
            active={mode === 'search'}
            expanded={expanded}
            icon={<Search size={18} />}
            label={t('activity.search')}
            onClick={() => onModeChange('search')}
          />
          <ActivityButton
            active={mode === 'agent'}
            expanded={expanded}
            icon={<Bot size={18} />}
            label={t('activity.agent')}
            onClick={() => onModeChange('agent')}
          />
        </div>
        <div className="activity-bar-bottom">
          <ActivityButton
            active={mode === 'settings'}
            expanded={expanded}
            icon={<Settings size={18} />}
            label={t('activity.settings')}
            onClick={() => onModeChange('settings')}
          />
        </div>
      </aside>

      <aside className={`workspace-sidebar ${expanded ? '' : 'collapsed'}`} aria-hidden={!expanded}>
        {expanded ? (
          <>
            <SidebarPanel
              mode={mode}
              currentUser={currentUser}
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
              onRenameNode={onRenameNode}
              onDeleteNode={onDeleteNode}
              onUserChange={onUserChange}
              onLogout={onLogout}
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
