import { FilePlus2, FolderPlus, Pencil, Trash2 } from 'lucide-react'
import { WORKSPACE_NODE_TYPE, type WorkspaceTreeNode } from '../../../entities/workspace/types'
import { useI18n } from '../../../shared/i18n'

export type WorkspaceContextMenuState = {
  node?: WorkspaceTreeNode
  x: number
  y: number
}

export function WorkspaceContextMenu({
  contextMenu,
  rootNodeId,
  onClose,
  onCreateDocument,
  onCreateFolder,
  onDeleteNode,
  onRenameNode
}: {
  contextMenu: WorkspaceContextMenuState
  rootNodeId?: string
  onClose: () => void
  onCreateDocument?: (parentNode?: WorkspaceTreeNode) => void
  onCreateFolder?: (parentNode?: WorkspaceTreeNode) => void
  onDeleteNode?: (node: WorkspaceTreeNode) => void
  onRenameNode?: (node: WorkspaceTreeNode) => void
}) {
  const { t } = useI18n()
  const contextNodeIsFolder = !contextMenu.node || contextMenu.node.nodeType === WORKSPACE_NODE_TYPE.FOLDER
  const contextNodeIsRoot = Boolean(contextMenu.node && contextMenu.node.nodeId === rootNodeId)
  const showNodeActions = Boolean(contextMenu.node && !contextNodeIsRoot)

  function handleCreateAction(action?: (parentNode?: WorkspaceTreeNode) => void) {
    if (!action) return
    action(contextMenu.node)
    onClose()
  }

  function handleNodeAction(action?: (node: WorkspaceTreeNode) => void) {
    if (!action || !contextMenu.node) return
    action(contextMenu.node)
    onClose()
  }

  return (
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
  )
}
