import { ChevronDown, ChevronRight, Folder, FolderOpen, FolderPlus, Pencil, Trash2 } from 'lucide-react'
import type { DragEvent } from 'react'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
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
  onRenameWorkspace?: (workspace: Workspace) => void
  onRootDragLeave: (event: DragEvent) => void
  onRootDragOver: (event: DragEvent) => void
  onRootDrop: (event: DragEvent) => void
  onSelectWorkspace?: (workspace: Workspace) => void
  onShowWorkspaceList: () => void
}) {
  const { t } = useI18n()
  const cardClassName = cn(
    'mx-2 grid min-h-10 grid-cols-[1.75rem_minmax(0,1fr)_auto] items-center gap-2 rounded-lg border bg-card px-2 py-1.5 text-left text-card-foreground transition-colors hover:bg-muted',
    rootDropTarget && 'border-primary bg-primary/5 shadow-[inset_3px_0_0_var(--primary)]'
  )

  if (!showWorkspaceList) {
    return (
      <button
        className={cardClassName}
        type="button"
        title={currentWorkspaceName}
        onClick={onShowWorkspaceList}
        onDragOver={onRootDragOver}
        onDragLeave={onRootDragLeave}
        onDrop={onRootDrop}
      >
        <span className="inline-flex size-7 items-center justify-center rounded-md bg-primary/10 text-primary">
          <FolderOpen />
        </span>
        <span className="min-w-0 truncate text-sm font-medium">{currentWorkspaceName}</span>
        <ChevronRight className="text-muted-foreground" />
      </button>
    )
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <button className={cardClassName} type="button" onClick={onHideWorkspaceList}>
        <span className="inline-flex size-7 items-center justify-center rounded-md bg-primary/10 text-primary">
          <FolderOpen />
        </span>
        <span className="min-w-0 truncate text-sm font-medium">{t('workspace.listTitle')}</span>
        <ChevronDown className="text-muted-foreground" />
      </button>

      <div className="mx-2 mt-3 flex min-h-7 items-center justify-between">
        <span className="text-xs font-medium text-muted-foreground">{t('workspace.listTitle')}</span>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              aria-label={t('workspace.newWorkspace')}
              onClick={() => onCreateWorkspace?.()}
            >
              <FolderPlus />
            </Button>
          </TooltipTrigger>
          <TooltipContent>{t('workspace.newWorkspace')}</TooltipContent>
        </Tooltip>
      </div>

      <ScrollArea className="min-h-0 flex-1">
        <div className="mx-2 grid gap-1.5 pb-2">
          {workspaces.length > 0 ? (
            workspaces.map((workspace) => {
              const isActive = workspace.workspaceId === selectedWorkspaceId
              const canManageWorkspace = workspace.type !== WORKSPACE_TYPE.PERSONAL
              return (
                <div
                  key={workspace.workspaceId}
                  className={cn(
                    'group grid min-h-9 grid-cols-[minmax(0,1fr)] items-center gap-1 rounded-lg border bg-card p-1 transition-colors hover:bg-muted',
                    canManageWorkspace && 'grid-cols-[minmax(0,1fr)_1.75rem_1.75rem]',
                    isActive && 'border-primary/30 bg-primary/5 shadow-[inset_3px_0_0_var(--primary)]'
                  )}
                >
                  <button
                    className={cn(
                      'flex min-h-7 min-w-0 items-center gap-2 rounded-md px-1.5 text-left text-sm text-muted-foreground outline-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50',
                      isActive && 'font-medium text-primary'
                    )}
                    type="button"
                    aria-current={isActive ? 'true' : undefined}
                    title={workspace.name}
                    onClick={() => {
                      onSelectWorkspace?.(workspace)
                      onHideWorkspaceList()
                    }}
                  >
                    <span className="inline-flex size-6 shrink-0 items-center justify-center rounded-md border bg-muted text-primary">
                      {isActive ? <FolderOpen /> : <Folder />}
                    </span>
                    <span className="truncate">{workspace.name}</span>
                  </button>
                  {canManageWorkspace ? (
                    <Button
                      className="workspace-list-action opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100"
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label={`${t('workspace.renameWorkspace')} ${workspace.name}`}
                      title={t('workspace.renameWorkspace')}
                      onClick={() => onRenameWorkspace?.(workspace)}
                    >
                      <Pencil />
                    </Button>
                  ) : null}
                  {canManageWorkspace ? (
                    <Button
                      className="workspace-list-action danger opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100"
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label={`${t('workspace.deleteWorkspace')} ${workspace.name}`}
                      title={t('workspace.deleteWorkspace')}
                      onClick={() => onDeleteWorkspace?.(workspace)}
                    >
                      <Trash2 />
                    </Button>
                  ) : null}
                </div>
              )
            })
          ) : (
            <div className="px-3 py-4 text-sm font-medium text-muted-foreground">{t('workspace.emptyWorkspaces')}</div>
          )}
        </div>
      </ScrollArea>
    </div>
  )
}
