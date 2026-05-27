import { useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { UserInformation } from '../entities/user/types'
import type { WorkspaceTreeNode } from '../entities/workspace/types'
import { getCurrentUser, type AuthSession } from '../features/auth/api/authApi'
import { AuthScreen } from '../features/auth/ui/AuthScreen'
import { ProfileControls } from '../features/auth/ui/ProfileControls'
import { DocumentEditor } from '../features/editor/ui/DocumentEditor'
import { useWorkspaceTree } from '../features/workspace-tree/model/useWorkspaceTree'
import { WorkspaceTree } from '../features/workspace-tree/ui/WorkspaceTree'

type AuthStatus = 'checking' | 'anonymous' | 'authenticated'

export function App() {
  const [selectedNode, setSelectedNode] = useState<WorkspaceTreeNode | undefined>()
  const [authStatus, setAuthStatus] = useState<AuthStatus>('checking')
  const [currentUser, setCurrentUser] = useState<UserInformation | undefined>()
  const queryClient = useQueryClient()
  const workspaceTree = useWorkspaceTree(selectedNode?.documentId, authStatus === 'authenticated')

  function clearAuth() {
    globalThis.localStorage?.removeItem('docpilot.auth.token')
    setCurrentUser(undefined)
    setSelectedNode(undefined)
    setAuthStatus('anonymous')
    queryClient.clear()
  }

  function handleAuthenticated(session: AuthSession) {
    globalThis.localStorage?.setItem('docpilot.auth.token', session.token)
    setCurrentUser(session.user)
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
    if (!selectedNode && workspaceTree.selectedDocumentNode) {
      setSelectedNode(workspaceTree.selectedDocumentNode)
    }
  }, [selectedNode, workspaceTree.selectedDocumentNode])

  if (authStatus === 'checking') {
    return (
      <main className="auth-shell">
        <div className="auth-loading">正在进入 DocPilot</div>
      </main>
    )
  }

  if (authStatus === 'anonymous' || !currentUser) {
    return <AuthScreen onAuthenticated={handleAuthenticated} />
  }

  return (
    <div className="app-shell">
      <WorkspaceTree
        nodes={workspaceTree.tree}
        selectedDocumentId={selectedNode?.documentId}
        onSelectDocument={setSelectedNode}
        profileSlot={
          <ProfileControls user={currentUser} onUserChange={setCurrentUser} onLogout={clearAuth} />
        }
      />

      <DocumentEditor workspace={workspaceTree.workspace} documentNode={selectedNode} />
    </div>
  )
}
