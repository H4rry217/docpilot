import { useCallback, useEffect, useState } from 'react'
import type { UserInformation } from '../../entities/user/types'
import { getCurrentUser, type AuthSession } from '../../features/auth/api/authApi'

export type AuthStatus = 'checking' | 'anonymous' | 'authenticated'

export function useAuthSession() {
  const [authStatus, setAuthStatus] = useState<AuthStatus>('checking')
  const [currentUser, setCurrentUser] = useState<UserInformation | undefined>()

  const clearAuth = useCallback(() => {
    globalThis.localStorage?.removeItem('docpilot.auth.token')
    setCurrentUser(undefined)
    setAuthStatus('anonymous')
  }, [])

  const handleAuthenticated = useCallback((session: AuthSession) => {
    globalThis.localStorage?.setItem('docpilot.auth.token', session.token)
    setCurrentUser(session.user)
    setAuthStatus('authenticated')
  }, [])

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
  }, [clearAuth])

  return {
    authStatus,
    currentUser,
    setCurrentUser,
    clearAuth,
    handleAuthenticated
  }
}
