import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import type { DocumentOutlineItem, DocumentOutlineJumpRequest } from '../entities/block/outline'
import type { UserInformation } from '../entities/user/types'
import { WORKSPACE_NODE_TYPE, WORKSPACE_RESOURCE_TYPE, WORKSPACE_TYPE, type Workspace, type WorkspaceTreeNode } from '../entities/workspace/types'
import { getCurrentUser, type AuthSession } from '../features/auth/api/authApi'
import { AuthScreen } from '../features/auth/ui/AuthScreen'
import { DocumentEditor } from '../features/editor/ui/DocumentEditor'
import { createDocument } from '../features/editor/api/documentApi'
import { useWorkspaceTree } from '../features/workspace-tree/model/useWorkspaceTree'
import {
  documentTitleFromMarkdownFileName,
  isMarkdownFileName,
  uniqueMarkdownNodeName
} from '../features/workspace-tree/model/markdownUpload'
import {
  createFolder,
  createWorkspace,
  deleteNode,
  deleteWorkspace,
  ensureDefaultWorkspace,
  listWorkspaces,
  renameNode,
  renameWorkspace
} from '../features/workspace-tree/api/workspaceApi'
import { useI18n } from '../shared/i18n'
import { usePersistentNumberState } from '../shared/ui/usePersistentNumberState'
import {
  WorkbenchSidebar,
  WORKSPACE_SIDEBAR_DEFAULT_WIDTH,
  WORKSPACE_SIDEBAR_MAX_WIDTH,
  WORKSPACE_SIDEBAR_MIN_WIDTH,
  type SidebarMode
} from './ui/WorkbenchSidebar'
import { SettingsDialog } from './ui/SettingsDialog'
import './App.css'

type AuthStatus = 'checking' | 'anonymous' | 'authenticated'

type PromptDialogState = {
  type: 'prompt'
  title: string
  label: string
  value: string
  confirmLabel: string
  resolve: (value?: string) => void
}

type ConfirmDialogState = {
  type: 'confirm'
  title: string
  message: string
  confirmLabel: string
  danger?: boolean
  resolve: (confirmed: boolean) => void
}

type AppDialogState = PromptDialogState | ConfirmDialogState

export function App() {
  const { t } = useI18n()
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | undefined>()
  const [selectedNode, setSelectedNode] = useState<WorkspaceTreeNode | undefined>()
  const [documentOutline, setDocumentOutline] = useState<DocumentOutlineItem[]>([])
  const [activeOutlineId, setActiveOutlineId] = useState<string | undefined>()
  const [outlineJumpRequest, setOutlineJumpRequest] = useState<DocumentOutlineJumpRequest | undefined>()
  const [workspaceUploadMessage, setWorkspaceUploadMessage] = useState<string | undefined>()
  const [activeSidebarMode, setActiveSidebarMode] = useState<SidebarMode>('files')
  const [sidebarExpanded, setSidebarExpanded] = useState(true)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [sidebarWidth, setSidebarWidth] = usePersistentNumberState({
    storageKey: 'docpilot.layout.sidebarWidth',
    defaultValue: WORKSPACE_SIDEBAR_DEFAULT_WIDTH,
    min: WORKSPACE_SIDEBAR_MIN_WIDTH,
    max: WORKSPACE_SIDEBAR_MAX_WIDTH
  })
  const [authStatus, setAuthStatus] = useState<AuthStatus>('checking')
  const [currentUser, setCurrentUser] = useState<UserInformation | undefined>()
  const [dialog, setDialog] = useState<AppDialogState | undefined>()
  const uploadMessageTimerRef = useRef<number | undefined>(undefined)
  const queryClient = useQueryClient()
  const workspaceTree = useWorkspaceTree(selectedWorkspaceId, selectedNode?.documentId, authStatus === 'authenticated')
  const workspaceListQuery = useQuery({
    queryKey: ['workspaces'],
    enabled: authStatus === 'authenticated',
    queryFn: async () => {
      await ensureDefaultWorkspace()
      return listWorkspaces()
    }
  })
  const workspaces = workspaceListQuery.data?.workspaces ?? []
  const activeWorkspaceId = selectedWorkspaceId ?? workspaceTree.workspace?.workspaceId

  function requestText(input: { title: string; label: string; defaultValue: string; confirmLabel: string }): Promise<string | undefined> {
    return new Promise((resolve) => {
      setDialog({
        type: 'prompt',
        title: input.title,
        label: input.label,
        value: input.defaultValue,
        confirmLabel: input.confirmLabel,
        resolve
      })
    })
  }

  function requestConfirm(input: { title: string; message: string; confirmLabel: string; danger?: boolean }): Promise<boolean> {
    return new Promise((resolve) => {
      setDialog({
        type: 'confirm',
        title: input.title,
        message: input.message,
        confirmLabel: input.confirmLabel,
        danger: input.danger,
        resolve
      })
    })
  }

  function handleDialogCancel() {
    if (!dialog) return
    if (dialog.type === 'prompt') {
      dialog.resolve(undefined)
    } else {
      dialog.resolve(false)
    }
    setDialog(undefined)
  }

  function handleDialogValueChange(value: string) {
    setDialog((current) => (current?.type === 'prompt' ? { ...current, value } : current))
  }

  function handleDialogSubmit(event: FormEvent) {
    event.preventDefault()
    if (!dialog) return

    if (dialog.type === 'prompt') {
      const value = dialog.value.trim()
      if (!value) return
      dialog.resolve(value)
    } else {
      dialog.resolve(true)
    }

    setDialog(undefined)
  }

  function clearAuth() {
    globalThis.localStorage?.removeItem('docpilot.auth.token')
    setCurrentUser(undefined)
    setSelectedWorkspaceId(undefined)
    setSelectedNode(undefined)
    setAuthStatus('anonymous')
    queryClient.clear()
  }

  function handleAuthenticated(session: AuthSession) {
    globalThis.localStorage?.setItem('docpilot.auth.token', session.token)
    setCurrentUser(session.user)
    setSelectedWorkspaceId(undefined)
    setSelectedNode(undefined)
    setAuthStatus('authenticated')
    queryClient.clear()
  }

  useEffect(() => {
    let cancelled = false
    const token = globalThis.localStorage?.getItem('docpilot.auth.token')
    if (!token) {
      setAuthStatus('anonymous')
      return
    }

    getCurrentUser()
      .then((user) => {
        if (cancelled) return
        setCurrentUser(user)
        setAuthStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        clearAuth()
      })

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const handleInvalidAuth = () => clearAuth()
    window.addEventListener('docpilot.auth.invalid', handleInvalidAuth)
    return () => window.removeEventListener('docpilot.auth.invalid', handleInvalidAuth)
  })

  useEffect(() => {
    return () => window.clearTimeout(uploadMessageTimerRef.current)
  }, [])

  useEffect(() => {
    if (!selectedNode && workspaceTree.selectedDocumentNode) {
      setSelectedNode(workspaceTree.selectedDocumentNode)
    }
  }, [selectedNode, workspaceTree.selectedDocumentNode])

  useEffect(() => {
    setActiveOutlineId(undefined)
    setOutlineJumpRequest(undefined)
  }, [selectedNode?.documentId])

  async function refreshWorkspaceQueries() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['workspaces'] }),
      queryClient.invalidateQueries({ queryKey: ['workspace-tree'] })
    ])
  }

  async function handleCreateFolder(parentNode?: WorkspaceTreeNode) {
    const workspace = workspaceTree.workspace
    if (!workspace) return
    const name = await requestText({
      title: t('workspace.newFolder'),
      label: t('prompt.folderName'),
      defaultValue: t('prompt.folderDefault'),
      confirmLabel: t('workspace.newFolder')
    })
    if (!name) return
    await createFolder({
      workspaceId: workspace.workspaceId,
      parentNodeId: parentNode?.nodeId ?? workspace.rootNodeId,
      name
    })
    await queryClient.invalidateQueries({ queryKey: ['workspace-tree'] })
  }

  async function handleSelectWorkspace(workspace: Workspace) {
    if (workspace.workspaceId === activeWorkspaceId) return
    setSelectedWorkspaceId(workspace.workspaceId)
    setSelectedNode(undefined)
  }

  async function handleCreateWorkspace() {
    const name = await requestText({
      title: t('workspace.newWorkspace'),
      label: t('prompt.workspaceName'),
      defaultValue: t('prompt.workspaceDefault'),
      confirmLabel: t('workspace.newWorkspace')
    })
    if (!name) return
    const workspace = await createWorkspace({ name })
    setSelectedWorkspaceId(workspace.workspaceId)
    setSelectedNode(undefined)
    await refreshWorkspaceQueries()
  }

  async function handleRenameWorkspace(workspace: Workspace) {
    if (workspace.type === WORKSPACE_TYPE.PERSONAL) return

    const name = await requestText({
      title: t('workspace.renameWorkspace'),
      label: t('prompt.workspaceName'),
      defaultValue: workspace.name,
      confirmLabel: t('workspace.renameWorkspace')
    })
    if (!name || name === workspace.name) return
    await renameWorkspace({ workspaceId: workspace.workspaceId, name })
    await refreshWorkspaceQueries()
  }

  async function handleDeleteWorkspace(workspace: Workspace) {
    if (workspace.type === WORKSPACE_TYPE.PERSONAL) return

    const confirmed = await requestConfirm({
      title: t('workspace.deleteWorkspace'),
      message: t('prompt.deleteWorkspace', { name: workspace.name }),
      confirmLabel: t('workspace.deleteWorkspace'),
      danger: true
    })
    if (!confirmed) return

    const fallbackWorkspace = workspaces.find((candidate) => candidate.workspaceId !== workspace.workspaceId)
    await deleteWorkspace({ workspaceId: workspace.workspaceId })
    if (workspace.workspaceId === activeWorkspaceId) {
      setSelectedNode(undefined)
      if (fallbackWorkspace) {
        setSelectedWorkspaceId(fallbackWorkspace.workspaceId)
      } else {
        const ensuredWorkspace = await ensureDefaultWorkspace()
        setSelectedWorkspaceId(ensuredWorkspace.workspaceId)
      }
    }
    await refreshWorkspaceQueries()
  }

  async function handleCreateDocument(parentNode?: WorkspaceTreeNode) {
    const workspace = workspaceTree.workspace
    if (!workspace) return
    const title = await requestText({
      title: t('workspace.newDocument'),
      label: t('prompt.documentTitle'),
      defaultValue: t('prompt.documentDefault'),
      confirmLabel: t('workspace.newDocument')
    })
    if (!title) return
    const nodeName = title.endsWith('.md') ? title : `${title}.md`
    const parentNodeId = parentNode?.nodeId ?? workspace.rootNodeId
    const response = await createDocument({
      workspaceId: workspace.workspaceId,
      parentNodeId,
      title,
      nodeName,
      markdown: `# ${title}`
    })
    setSelectedNode({
      nodeId: `pending-${response.document.documentId}`,
      workspaceId: workspace.workspaceId,
      parentNodeId,
      ancestors: parentNode ? [...parentNode.ancestors, parentNode.nodeId] : [workspace.rootNodeId],
      nodeType: WORKSPACE_NODE_TYPE.RESOURCE,
      resourceType: WORKSPACE_RESOURCE_TYPE.DOCUMENT,
      name: nodeName,
      documentId: response.document.documentId,
      createTime: response.document.createTime,
      updateTime: response.document.updateTime,
      metadata: {},
      children: []
    })
    await queryClient.invalidateQueries({ queryKey: ['workspace-tree'] })
  }

  function showWorkspaceUploadMessage(message: string) {
    window.clearTimeout(uploadMessageTimerRef.current)
    setWorkspaceUploadMessage(message)
    uploadMessageTimerRef.current = window.setTimeout(() => {
      setWorkspaceUploadMessage(undefined)
    }, 3600)
  }

  async function handleUploadMarkdownFiles(files: File[], parentNode?: WorkspaceTreeNode) {
    const workspace = workspaceTree.workspace
    if (!workspace) return

    const markdownFiles = files.filter((file) => isMarkdownFileName(file.name))
    if (!markdownFiles.length) {
      showWorkspaceUploadMessage(t('workspace.uploadMarkdownOnly'))
      return
    }

    const rootNode = workspaceTree.tree.find((node) => node.nodeId === workspace.rootNodeId)
    const targetChildren = parentNode?.children ?? rootNode?.children ?? workspaceTree.tree
    const usedNames = new Set(targetChildren.map((child) => child.name.toLowerCase()))
    const parentNodeId = parentNode?.nodeId ?? workspace.rootNodeId
    const parentAncestors = parentNode ? [...parentNode.ancestors, parentNode.nodeId] : [workspace.rootNodeId]
    let uploadedNode: WorkspaceTreeNode | undefined
    let uploadedCount = 0

    showWorkspaceUploadMessage(t('workspace.uploadingMarkdown', { count: String(markdownFiles.length) }))

    try {
      for (const file of markdownFiles) {
        const nodeName = uniqueMarkdownNodeName(file.name, usedNames)
        const markdown = await file.text()
        const response = await createDocument({
          workspaceId: workspace.workspaceId,
          parentNodeId,
          title: documentTitleFromMarkdownFileName(nodeName),
          nodeName,
          markdown
        })

        uploadedCount += 1
        uploadedNode = {
          nodeId: `pending-${response.document.documentId}`,
          workspaceId: workspace.workspaceId,
          parentNodeId,
          ancestors: parentAncestors,
          nodeType: WORKSPACE_NODE_TYPE.RESOURCE,
          resourceType: WORKSPACE_RESOURCE_TYPE.DOCUMENT,
          name: nodeName,
          documentId: response.document.documentId,
          createTime: response.document.createTime,
          updateTime: response.document.updateTime,
          metadata: {},
          children: []
        }
      }

      if (uploadedNode) {
        setSelectedNode(uploadedNode)
      }
      await queryClient.invalidateQueries({ queryKey: ['workspace-tree'] })
      showWorkspaceUploadMessage(t('workspace.uploadMarkdownDone', { count: String(uploadedCount) }))
    } catch (error) {
      await queryClient.invalidateQueries({ queryKey: ['workspace-tree'] })
      const message = error instanceof Error ? error.message : t('workspace.uploadMarkdownFailed')
      showWorkspaceUploadMessage(t('workspace.uploadMarkdownFailedWithReason', { reason: message }))
    }
  }

  async function handleRenameNode(node: WorkspaceTreeNode) {
    const name = await requestText({
      title: t('workspace.rename'),
      label: t('prompt.nodeName'),
      defaultValue: node.name,
      confirmLabel: t('workspace.rename')
    })
    if (!name || name === node.name) return
    await renameNode({ nodeId: node.nodeId, name })
    await queryClient.invalidateQueries({ queryKey: ['workspace-tree'] })
  }

  async function handleDeleteNode(node: WorkspaceTreeNode) {
    const confirmed = await requestConfirm({
      title: t('workspace.delete'),
      message: t('prompt.deleteNode', { name: node.name }),
      confirmLabel: t('workspace.delete'),
      danger: true
    })
    if (!confirmed) return
    await deleteNode({ nodeId: node.nodeId })
    if (selectedNode?.nodeId === node.nodeId || selectedNode?.documentId === node.documentId) {
      setSelectedNode(undefined)
    }
    await queryClient.invalidateQueries({ queryKey: ['workspace-tree'] })
  }

  function handleSidebarModeChange(nextMode: SidebarMode) {
    if (nextMode === activeSidebarMode) {
      setSidebarExpanded((expanded) => !expanded)
      return
    }

    setActiveSidebarMode(nextMode)
    setSidebarExpanded(true)
  }

  function handleSelectOutlineItem(item: DocumentOutlineItem) {
    setActiveOutlineId(item.id)
    setOutlineJumpRequest((current) => ({
      id: item.id,
      headingIndex: item.headingIndex,
      requestId: (current?.requestId ?? 0) + 1
    }))
  }

  if (authStatus === 'checking') {
    return (
      <main className="auth-shell">
        <div className="auth-loading">{t('app.loading')}</div>
      </main>
    )
  }

  if (authStatus === 'anonymous' || !currentUser) {
    return <AuthScreen onAuthenticated={handleAuthenticated} />
  }

  return (
    <div className="app-shell" style={{ gridTemplateColumns: `48px ${sidebarExpanded ? sidebarWidth : 0}px minmax(0, 1fr)` }}>
      <WorkbenchSidebar
        mode={activeSidebarMode}
        expanded={sidebarExpanded}
        width={sidebarWidth}
        selectedNode={selectedNode}
        outline={documentOutline}
        activeOutlineId={activeOutlineId}
        workspace={workspaceTree.workspace}
        workspaces={workspaces}
        selectedWorkspaceId={activeWorkspaceId}
        tree={workspaceTree.tree}
        onModeChange={handleSidebarModeChange}
        onWidthChange={setSidebarWidth}
        onSelectWorkspace={handleSelectWorkspace}
        onCreateWorkspace={handleCreateWorkspace}
        onRenameWorkspace={handleRenameWorkspace}
        onDeleteWorkspace={handleDeleteWorkspace}
        onSelectNode={setSelectedNode}
        onCreateFolder={handleCreateFolder}
        onCreateDocument={handleCreateDocument}
        onUploadMarkdownFiles={handleUploadMarkdownFiles}
        onRenameNode={handleRenameNode}
        onDeleteNode={handleDeleteNode}
        onSelectOutlineItem={handleSelectOutlineItem}
        uploadMessage={workspaceUploadMessage}
        onOpenSettings={() => setSettingsOpen(true)}
      />

      <DocumentEditor
        workspace={workspaceTree.workspace}
        documentNode={selectedNode}
        outlineJumpRequest={outlineJumpRequest}
        onRequestText={requestText}
        onOutlineChange={setDocumentOutline}
      />
      {settingsOpen ? (
        <SettingsDialog
          user={currentUser}
          onClose={() => setSettingsOpen(false)}
          onLogout={clearAuth}
          onUserChange={setCurrentUser}
        />
      ) : null}
      {dialog ? (
        <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) handleDialogCancel()
        }}>
          <form className={`app-dialog ${dialog.type === 'confirm' && dialog.danger ? 'danger' : ''}`} role="dialog" aria-modal="true" onSubmit={handleDialogSubmit}>
            <header className="app-dialog-header">
              <strong>{dialog.title}</strong>
            </header>
            <div className="app-dialog-body">
              {dialog.type === 'prompt' ? (
                <label className="app-dialog-field">
                  <span>{dialog.label}</span>
                  <input autoFocus value={dialog.value} onChange={(event) => handleDialogValueChange(event.target.value)} />
                </label>
              ) : (
                <p>{dialog.message}</p>
              )}
            </div>
            <div className="app-dialog-actions">
              <button type="button" className="app-dialog-cancel" onClick={handleDialogCancel}>
                {t('dialog.cancel')}
              </button>
              <button
                type="submit"
                className={`app-dialog-confirm ${dialog.type === 'confirm' && dialog.danger ? 'danger' : ''}`}
                disabled={dialog.type === 'prompt' && !dialog.value.trim()}
              >
                {dialog.confirmLabel}
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </div>
  )
}
