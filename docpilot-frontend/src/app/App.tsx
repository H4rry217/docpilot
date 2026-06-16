import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import type { AuthSession } from '../features/auth/api/authApi'
import { AuthScreen } from '../features/auth/ui/AuthScreen'
import { DocumentEditor } from '../features/editor/ui/DocumentEditor'
import { useI18n } from '../shared/i18n'
import { useAppDialogs } from './model/useAppDialogs'
import { useAuthSession } from './model/useAuthSession'
import { useCloudUserSettings } from './model/useCloudUserSettings'
import { useDeveloperSettings } from './model/useDeveloperSettings'
import { useDocumentOutlineState } from './model/useDocumentOutlineState'
import { useWorkspaceShell } from './model/useWorkspaceShell'
import { WorkbenchSidebar } from './ui/WorkbenchSidebar'
import { SettingsDialog } from './ui/SettingsDialog'

export function App() {
  const { locale, setLocale, t } = useI18n()
  const queryClient = useQueryClient()
  const [settingsOpen, setSettingsOpen] = useState(false)
  const dialogs = useAppDialogs()
  const developerSettings = useDeveloperSettings()
  const authSession = useAuthSession()
  const workspaceShell = useWorkspaceShell({
    authStatus: authSession.authStatus,
    requestText: dialogs.requestText,
    requestConfirm: dialogs.requestConfirm
  })
  const outlineState = useDocumentOutlineState(workspaceShell.selectedNode?.documentId)
  const { authStatus, currentUser, setCurrentUser, clearAuth, handleAuthenticated: storeAuthenticated } = authSession
  const cloudUserSettings = useCloudUserSettings({
    userId: currentUser?.userId,
    locale,
    setLocale,
    developerMode: developerSettings.developerMode,
    setDeveloperMode: developerSettings.setDeveloperMode
  })
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
      <main className="grid h-screen w-screen place-items-center bg-muted/40">
        <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
          <Spinner />
          <span>{t('app.loading')}</span>
        </div>
      </main>
    )
  }

  if (authStatus === 'anonymous' || !currentUser) {
    return (
      <AuthScreen
        authConfig={authSession.authConfig}
        onAuthenticated={handleAuthenticated}
        onRetryHostAuth={authSession.refreshHostAuth}
      />
    )
  }

  return (
    <div
      className="grid h-screen w-screen overflow-hidden bg-background"
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
        developerMode={developerSettings.developerMode}
        inlineCompletionSettings={cloudUserSettings.inlineCompletion}
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
          locale={locale}
          developerMode={developerSettings.developerMode}
          inlineCompletion={cloudUserSettings.inlineCompletion}
          settingsSaving={cloudUserSettings.saving}
          settingsError={cloudUserSettings.error}
          onClose={() => setSettingsOpen(false)}
          onLocaleChange={cloudUserSettings.setLocale}
          onDeveloperModeChange={cloudUserSettings.setDeveloperMode}
          onInlineCompletionEnabledChange={cloudUserSettings.setInlineCompletionEnabled}
          onInlineCompletionIdleDelayChange={cloudUserSettings.setInlineCompletionIdleDelayMs}
          onInlineCompletionCandidateCountChange={cloudUserSettings.setInlineCompletionCandidateCount}
          onInlineCompletionMaxOutputTokensChange={cloudUserSettings.setInlineCompletionMaxOutputTokens}
          onLogout={clearAuthenticatedSession}
          onUserChange={setCurrentUser}
          authCapabilities={authSession.authConfig?.capabilities}
        />
      ) : null}
      {activeDialog?.type === 'prompt' ? (
        <Dialog open onOpenChange={(open) => {
          if (!open) dialogs.handleDialogCancel()
        }}>
          <DialogContent>
            <form className="grid gap-4" onSubmit={(event: FormEvent) => dialogs.handleDialogSubmit(event)}>
              <DialogHeader>
                <DialogTitle>{activeDialog.title}</DialogTitle>
              </DialogHeader>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="app-dialog-value">{activeDialog.label}</FieldLabel>
                  <Input
                    id="app-dialog-value"
                    autoFocus
                    value={activeDialog.value}
                    onChange={(event) => dialogs.handleDialogValueChange(event.target.value)}
                  />
                </Field>
              </FieldGroup>
              <DialogFooter>
                <Button type="button" variant="outline" onClick={dialogs.handleDialogCancel}>
                  {t('dialog.cancel')}
                </Button>
                <Button type="submit" disabled={!activeDialog.value.trim()}>
                  {activeDialog.confirmLabel}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      ) : null}
      {activeDialog?.type === 'confirm' ? (
        <AlertDialog open onOpenChange={(open) => {
          if (!open) dialogs.handleDialogCancel()
        }}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>{activeDialog.title}</AlertDialogTitle>
              <AlertDialogDescription>{activeDialog.message}</AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel onClick={dialogs.handleDialogCancel}>{t('dialog.cancel')}</AlertDialogCancel>
              <AlertDialogAction
                variant={activeDialog.danger ? 'destructive' : 'default'}
                onClick={dialogs.handleDialogConfirm}
              >
                {activeDialog.confirmLabel}
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      ) : null}
    </div>
  )
}
