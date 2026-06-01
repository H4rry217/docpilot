import { Bot, ChevronDown, ChevronRight, Files, ListTree, Search, Settings } from 'lucide-react'
import { Fragment, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { DocumentOutlineItem } from '../../entities/block/outline'
import type { Workspace, WorkspaceTreeNode } from '../../entities/workspace/types'
import { WorkspaceTree } from '../../features/workspace-tree/ui/WorkspaceTree'
import { useI18n } from '../../shared/i18n'
import { useResizableWidth } from '../../shared/ui/useResizableWidth'
import '../../shared/ui/ResizablePanel.css'
import './WorkbenchSidebar.css'

export type SidebarMode = 'files' | 'outline' | 'search' | 'agent'

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

type OutlineTreeItem = DocumentOutlineItem & {
  children: OutlineTreeItem[]
}

function buildOutlineTree(outline: DocumentOutlineItem[]): OutlineTreeItem[] {
  const roots: OutlineTreeItem[] = []
  const stack: OutlineTreeItem[] = []

  outline.forEach((item) => {
    const treeItem: OutlineTreeItem = { ...item, children: [] }

    while (stack.length && stack[stack.length - 1].level >= treeItem.level) {
      stack.pop()
    }

    const parent = stack[stack.length - 1]
    if (parent) {
      parent.children.push(treeItem)
    } else {
      roots.push(treeItem)
    }

    stack.push(treeItem)
  })

  return roots
}

function outlineItemFromTreeItem(item: OutlineTreeItem): DocumentOutlineItem {
  return {
    id: item.id,
    level: item.level,
    title: item.title,
    headingIndex: item.headingIndex
  }
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
  selectedNode,
  outline,
  activeOutlineId,
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
  onSelectOutlineItem,
  uploadMessage
}: {
  mode: SidebarMode
  selectedNode?: WorkspaceTreeNode
  outline: DocumentOutlineItem[]
  activeOutlineId?: string
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
  onSelectOutlineItem: (item: DocumentOutlineItem) => void
  uploadMessage?: string
}) {
  const { t } = useI18n()
  const selectedName = selectedNode?.name ?? t('sidebar.noDocument')
  const hasSelectedDocument = Boolean(selectedNode?.documentId)
  const [collapsedOutlineIds, setCollapsedOutlineIds] = useState<Set<string>>(() => new Set())
  const outlineTree = useMemo(() => buildOutlineTree(outline), [outline])

  useEffect(() => {
    const outlineIds = new Set(outline.map((item) => item.id))
    setCollapsedOutlineIds((current) => {
      const next = new Set([...current].filter((id) => outlineIds.has(id)))
      return next.size === current.size ? current : next
    })
  }, [outline])

  function toggleOutlineItem(id: string) {
    setCollapsedOutlineIds((current) => {
      const next = new Set(current)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  function renderOutlineItems(items: OutlineTreeItem[], depth = 0): ReactNode {
    return items.map((item) => {
      const title = item.title || t('sidebar.outlineUntitled')
      const hasChildren = item.children.length > 0
      const collapsed = collapsedOutlineIds.has(item.id)
      const active = activeOutlineId === item.id

      return (
        <Fragment key={`${item.id}-${item.headingIndex}`}>
          <div
            className={`outline-row outline-level-${item.level} ${active ? 'active' : ''}`}
            style={{ paddingLeft: `${2 + depth * 14}px` }}
            title={title}
          >
            {hasChildren ? (
              <button
                type="button"
                className="outline-toggle"
                aria-label={collapsed ? t('sidebar.outlineExpand', { title }) : t('sidebar.outlineCollapse', { title })}
                aria-expanded={!collapsed}
                onClick={() => toggleOutlineItem(item.id)}
              >
                {collapsed ? <ChevronRight size={13} /> : <ChevronDown size={13} />}
              </button>
            ) : (
              <span className="outline-toggle-spacer" />
            )}
            <button
              type="button"
              className="outline-title"
              onClick={() => onSelectOutlineItem(outlineItemFromTreeItem(item))}
            >
              {title}
            </button>
          </div>
          {hasChildren && !collapsed ? renderOutlineItems(item.children, depth + 1) : null}
        </Fragment>
      )
    })
  }

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
        onUploadMarkdownFiles={onUploadMarkdownFiles}
        onRenameNode={onRenameNode}
        onDeleteNode={onDeleteNode}
        uploadMessage={uploadMessage}
      />
    )
  }

  if (mode === 'outline') {
    return (
      <section className="sidebar-panel">
        {outlineTree.length ? (
          <div className="outline-list" aria-label={t('sidebar.outlineTitle')}>
            {renderOutlineItems(outlineTree)}
          </div>
        ) : (
          <p className="sidebar-note">
            {hasSelectedDocument ? t('sidebar.outlineEmpty') : t('sidebar.outlineIntro')}
          </p>
        )}
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

export function WorkbenchSidebar({
  mode,
  expanded,
  width,
  selectedNode,
  outline,
  activeOutlineId,
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
  onSelectOutlineItem,
  uploadMessage,
  onOpenSettings
}: {
  mode: SidebarMode
  expanded: boolean
  width: number
  selectedNode?: WorkspaceTreeNode
  outline: DocumentOutlineItem[]
  activeOutlineId?: string
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
  onSelectOutlineItem: (item: DocumentOutlineItem) => void
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
              mode={mode}
              selectedNode={selectedNode}
              outline={outline}
              activeOutlineId={activeOutlineId}
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
              onSelectOutlineItem={onSelectOutlineItem}
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
