import { useState } from 'react'
import { FileUp } from 'lucide-react'
import { ContextMenu, ContextMenuTrigger } from '@/components/ui/context-menu'
import { ScrollArea } from '@/components/ui/scroll-area'
import type { Workspace, WorkspaceTreeNode } from '@/entities/workspace/types'
import { useI18n } from '@/shared/i18n'
import { WorkspaceContextMenu } from './WorkspaceContextMenu'
import { WorkspaceSwitcher } from './WorkspaceSwitcher'
import { WorkspaceTreeNodeRow } from './WorkspaceTreeNodeRow'
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

function WorkspaceUploadToast({ message }: { message: string }) {
  return (
    <div className="px-3 pb-1 pt-2">
      <div
        className="flex min-h-9 items-center gap-2 rounded-md border border-[#d7e5ff] bg-[#f3f7ff] px-2.5 py-2 text-[12px] leading-5 text-[#40516a] shadow-[0_8px_22px_rgb(37_99_235/0.08)]"
        role="status"
        aria-live="polite"
      >
        <span className="grid size-5 shrink-0 place-items-center rounded bg-white text-[#4f7cff] shadow-[inset_0_0_0_1px_rgb(79_124_255/0.16)]">
          <FileUp className="size-3.5" aria-hidden="true" />
        </span>
        <span className="min-w-0 flex-1 text-pretty break-words">{message}</span>
      </div>
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
  onUploadMarkdownFiles,
  onRenameNode,
  onDeleteNode,
  uploadMessage
}: WorkspaceTreeProps) {
  const { t } = useI18n()
  const [collapsedFolderIds, setCollapsedFolderIds] = useState<Set<string>>(() => new Set())
  const [showWorkspaceList, setShowWorkspaceList] = useState(false)
  const dnd = useWorkspaceTreeDnd({ onUploadMarkdownFiles })
  const currentWorkspaceName = workspaceName ?? t('sidebar.workspace')

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

  return (
    <ContextMenu>
      <ContextMenuTrigger asChild>
        <div
          className="relative flex h-full min-h-0 min-w-0 flex-col overflow-hidden py-3"
          onDragOver={showWorkspaceList ? undefined : dnd.handlePanelDragOver}
          onDragLeave={showWorkspaceList ? undefined : dnd.handlePanelDragLeave}
          onDrop={showWorkspaceList ? undefined : dnd.handlePanelDrop}
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
            onRenameWorkspace={onRenameWorkspace}
            onRootDragLeave={dnd.handleRootDragLeave}
            onRootDragOver={dnd.handleRootDragOver}
            onRootDrop={dnd.handleRootDrop}
            onSelectWorkspace={onSelectWorkspace}
            onShowWorkspaceList={() => setShowWorkspaceList(true)}
          />

          {!showWorkspaceList ? (
            <>
              <ScrollArea className="min-h-0 flex-1">
                <div className="grid gap-0.5 px-1 py-2">
                  {nodes.length > 0 ? (
                    nodes.map((node) => (
                      <WorkspaceTreeNodeRow
                        key={node.nodeId}
                        node={node}
                        depth={0}
                        expanded={isFolderExpanded(node)}
                        selectedNodeId={selectedNodeId}
                        selectedDocumentId={selectedDocumentId}
                        rootNodeId={rootNodeId}
                        onSelectNode={onSelectNode}
                        onToggleFolder={toggleFolder}
                        isFolderExpanded={isFolderExpanded}
                        onCreateFolder={onCreateFolder}
                        onCreateDocument={onCreateDocument}
                        onRenameNode={onRenameNode}
                        onDeleteNode={onDeleteNode}
                        dragTargetNodeId={dnd.dragTargetNodeId}
                        onMarkdownDragOver={dnd.handleMarkdownDragOver}
                        onMarkdownDragLeave={dnd.handleMarkdownDragLeave}
                        onMarkdownDrop={dnd.handleMarkdownDrop}
                      />
                    ))
                  ) : (
                    <div className="px-3 py-4 text-sm font-medium text-muted-foreground">{t('workspace.empty')}</div>
                  )}
                </div>
              </ScrollArea>
              {uploadMessage ? (
                <WorkspaceUploadToast message={uploadMessage} />
              ) : null}
            </>
          ) : null}
        </div>
      </ContextMenuTrigger>
      <WorkspaceContextMenu
        rootNodeId={rootNodeId}
        onCreateDocument={onCreateDocument}
        onCreateFolder={onCreateFolder}
        onDeleteNode={onDeleteNode}
        onRenameNode={onRenameNode}
      />
    </ContextMenu>
  )
}
