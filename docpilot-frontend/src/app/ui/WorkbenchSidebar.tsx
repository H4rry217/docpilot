import { Files, Settings } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import type { Workspace, WorkspaceTreeNode } from '../../entities/workspace/types'
import { WorkspaceTree } from '../../features/workspace-tree/ui/WorkspaceTree'
import { useI18n } from '../../shared/i18n'
import { useResizableWidth } from '../../shared/ui/useResizableWidth'
import './WorkbenchSidebar.css'

export type SidebarMode = 'files'

const docpilotLogoMarkUrl = new URL('../../assets/brand/docpilot-logo-mark.svg', import.meta.url).href

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
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          className={cn(
            'relative size-[38px] text-muted-foreground hover:bg-[#F3F4F6] hover:text-foreground [&_svg]:size-[18px]',
            active && expanded && 'bg-[#F5F8FF] text-[#3D6EFF] hover:bg-[#EEF4FF] hover:text-[#3D6EFF] before:absolute before:left-0.5 before:top-1/2 before:h-4 before:w-0.5 before:-translate-y-1/2 before:rounded-full before:bg-[#4F7CFF]',
            active && !expanded && 'bg-transparent text-muted-foreground hover:bg-[#F3F4F6] hover:text-[#3D6EFF]'
          )}
          type="button"
          aria-label={label}
          aria-expanded={active ? expanded : false}
          variant="ghost"
          size="icon"
          onClick={onClick}
        >
          {icon}
        </Button>
      </TooltipTrigger>
      <TooltipContent side="right">{label}</TooltipContent>
    </Tooltip>
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
      <aside className="workbench-activity-bar flex min-w-0 flex-col justify-between p-1.5" aria-label="DocPilot">
        <div className="grid gap-2">
          <div className="flex h-[38px] items-center justify-center" aria-hidden="true">
            <img className="size-6 select-none" src={docpilotLogoMarkUrl} alt="" draggable={false} />
          </div>
          <ActivityButton
            active={mode === 'files'}
            expanded={expanded}
            icon={<Files />}
            label={t('activity.files')}
            onClick={() => onModeChange('files')}
          />
        </div>
        <div className="grid gap-2">
          <ActivityButton
            active={false}
            expanded={false}
            icon={<Settings />}
            label={t('activity.settings')}
            onClick={onOpenSettings}
          />
        </div>
      </aside>

      <aside className={cn('workbench-sidebar-panel relative min-w-0 overflow-visible', !expanded && 'is-collapsed overflow-hidden')} aria-hidden={!expanded}>
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
              className="absolute inset-y-0 -right-[5px] z-50 w-2.5 cursor-col-resize border-0 bg-transparent p-0 after:absolute after:inset-y-0 after:left-1 after:w-0.5 after:rounded-full after:bg-primary after:opacity-0 after:transition-opacity hover:after:opacity-100 focus-visible:outline-none focus-visible:after:opacity-100"
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
