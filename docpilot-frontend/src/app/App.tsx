import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { AuthSession } from '../features/auth/api/authApi'
import { AuthScreen } from '../features/auth/ui/AuthScreen'
import { DocumentEditor } from '../features/editor/ui/DocumentEditor'
import { useI18n } from '../shared/i18n'
import { useAppDialogs } from './model/useAppDialogs'
import { useAuthSession } from './model/useAuthSession'
import { useDocumentOutlineState } from './model/useDocumentOutlineState'
import { useWorkspaceShell } from './model/useWorkspaceShell'
import { WorkbenchSidebar } from './ui/WorkbenchSidebar'
import { SettingsDialog } from './ui/SettingsDialog'
import './App.css'

export function App() {
  const { t } = useI18n()
  const queryClient = useQueryClient()
  const [settingsOpen, setSettingsOpen] = useState(false)
  const dialogs = useAppDialogs()
  const authSession = useAuthSession()
  const workspaceShell = useWorkspaceShell({
    authStatus: authSession.authStatus,
    requestText: dialogs.requestText,
    requestConfirm: dialogs.requestConfirm
  })
  const outlineState = useDocumentOutlineState(workspaceShell.selectedNode?.documentId)
  const { authStatus, currentUser, setCurrentUser, clearAuth, handleAuthenticated: storeAuthenticated } = authSession
  const { resetWorkspaceSelection } = workspaceShell
  const activeDialog = dialogs.dialog

  const clearAuthenticatedSession = useCallback(() => {
    clearAuth()
    resetWorkspaceSelection()
    queryClient.clear()
  }, [clearAuth, queryClient, resetWorkspaceSelection])

  const handleAuthenticated = useCallback((session: AuthSession) => {
    storeAuthenticated(session)
    resetWorkspaceSelection()
    queryClient.clear()
  }, [queryClient, resetWorkspaceSelection, storeAuthenticated])

  useEffect(() => {
    const handleInvalidAuth = () => clearAuthenticatedSession()
    window.addEventListener('docpilot.auth.invalid', handleInvalidAuth)
    return () => window.removeEventListener('docpilot.auth.invalid', handleInvalidAuth)
  }, [clearAuthenticatedSession])

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
    <div
      className="app-shell"
      style={{ gridTemplateColumns: `48px ${workspaceShell.sidebarExpanded ? workspaceShell.sidebarWidth : 0}px minmax(0, 1fr)` }}
    >
      <WorkbenchSidebar
        mode={workspaceShell.activeSidebarMode}
        expanded={workspaceShell.sidebarExpanded}
        width={workspaceShell.sidebarWidth}
        selectedNode={workspaceShell.selectedNode}
        workspace={workspaceShell.workspace}
        workspaces={workspaceShell.workspaces}
        selectedWorkspaceId={workspaceShell.activeWorkspaceId}
        tree={workspaceShell.tree}
        onModeChange={workspaceShell.handleSidebarModeChange}
        onWidthChange={workspaceShell.setSidebarWidth}
        onSelectWorkspace={workspaceShell.handleSelectWorkspace}
        onCreateWorkspace={workspaceShell.handleCreateWorkspace}
        onRenameWorkspace={workspaceShell.handleRenameWorkspace}
        onDeleteWorkspace={workspaceShell.handleDeleteWorkspace}
        onSelectNode={workspaceShell.setSelectedNode}
        onCreateFolder={workspaceShell.handleCreateFolder}
        onCreateDocument={workspaceShell.handleCreateDocument}
        onUploadMarkdownFiles={workspaceShell.handleUploadMarkdownFiles}
        onRenameNode={workspaceShell.handleRenameNode}
        onDeleteNode={workspaceShell.handleDeleteNode}
        uploadMessage={workspaceShell.workspaceUploadMessage}
        onOpenSettings={() => setSettingsOpen(true)}
      />

      <DocumentEditor
        workspace={workspaceShell.workspace}
        documentNode={workspaceShell.selectedNode}
        outline={outlineState.documentOutline}
        activeOutlineId={outlineState.activeOutlineId}
        outlineJumpRequest={outlineState.outlineJumpRequest}
        onOutlineChange={outlineState.setDocumentOutline}
        onSelectOutlineItem={outlineState.handleSelectOutlineItem}
      />
      {settingsOpen ? (
        <SettingsDialog
          user={currentUser}
          onClose={() => setSettingsOpen(false)}
          onLogout={clearAuthenticatedSession}
          onUserChange={setCurrentUser}
        />
      ) : null}
      {activeDialog ? (
        <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) dialogs.handleDialogCancel()
        }}>
          <form
            className={`app-dialog ${activeDialog.type === 'confirm' && activeDialog.danger ? 'danger' : ''}`}
            role="dialog"
            aria-modal="true"
            onSubmit={(event: FormEvent) => dialogs.handleDialogSubmit(event)}
          >
            <header className="app-dialog-header">
              <strong>{activeDialog.title}</strong>
            </header>
            <div className="app-dialog-body">
              {activeDialog.type === 'prompt' ? (
                <label className="app-dialog-field">
                  <span>{activeDialog.label}</span>
                  <input autoFocus value={activeDialog.value} onChange={(event) => dialogs.handleDialogValueChange(event.target.value)} />
                </label>
              ) : (
                <p>{activeDialog.message}</p>
              )}
            </div>
            <div className="app-dialog-actions">
              <button type="button" className="app-dialog-cancel" onClick={dialogs.handleDialogCancel}>
                {t('dialog.cancel')}
              </button>
              <button
                type="submit"
                className={`app-dialog-confirm ${activeDialog.type === 'confirm' && activeDialog.danger ? 'danger' : ''}`}
                disabled={activeDialog.type === 'prompt' && !activeDialog.value.trim()}
              >
                {activeDialog.confirmLabel}
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </div>
  )
}
