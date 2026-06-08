import { FilePlus2, FolderPlus, Pencil, Trash2 } from 'lucide-react'
import {
  ContextMenuContent,
  ContextMenuGroup,
  ContextMenuItem,
  ContextMenuSeparator
} from '@/components/ui/context-menu'
import { WORKSPACE_NODE_TYPE, type WorkspaceTreeNode } from '../../../entities/workspace/types'
import { useI18n } from '../../../shared/i18n'

export function WorkspaceContextMenu({
  node,
  rootNodeId,
  onCreateDocument,
  onCreateFolder,
  onDeleteNode,
  onRenameNode
}: {
  node?: WorkspaceTreeNode
  rootNodeId?: string
  onCreateDocument?: (parentNode?: WorkspaceTreeNode) => void
  onCreateFolder?: (parentNode?: WorkspaceTreeNode) => void
  onDeleteNode?: (node: WorkspaceTreeNode) => void
  onRenameNode?: (node: WorkspaceTreeNode) => void
}) {
  const { t } = useI18n()
  const contextNodeIsFolder = !node || node.nodeType === WORKSPACE_NODE_TYPE.FOLDER
  const contextNodeIsRoot = Boolean(node && node.nodeId === rootNodeId)
  const showNodeActions = Boolean(node && !contextNodeIsRoot)

  return (
    <ContextMenuContent>
      {contextNodeIsFolder ? (
        <ContextMenuGroup>
          <ContextMenuItem disabled={!onCreateDocument} onSelect={() => onCreateDocument?.(node)}>
            <FilePlus2 />
            <span>{t('workspace.newDocument')}</span>
          </ContextMenuItem>
          <ContextMenuItem disabled={!onCreateFolder} onSelect={() => onCreateFolder?.(node)}>
            <FolderPlus />
            <span>{t('workspace.newFolder')}</span>
          </ContextMenuItem>
        </ContextMenuGroup>
      ) : null}
      {contextNodeIsFolder && showNodeActions ? <ContextMenuSeparator /> : null}
      {showNodeActions ? (
        <ContextMenuGroup>
          <ContextMenuItem disabled={!onRenameNode} onSelect={() => node && onRenameNode?.(node)}>
            <Pencil />
            <span>{t('workspace.rename')}</span>
          </ContextMenuItem>
          <ContextMenuItem disabled={!onDeleteNode} variant="destructive" onSelect={() => node && onDeleteNode?.(node)}>
            <Trash2 />
            <span>{t('workspace.delete')}</span>
          </ContextMenuItem>
        </ContextMenuGroup>
      ) : null}
    </ContextMenuContent>
  )
}
