import { useCallback, useEffect, useState } from 'react'
import type { UserInformation } from '../../entities/user/types'
import {
  getAuthConfig,
  getCurrentUser,
  type AuthConfig,
  type AuthSession
} from '../../features/auth/api/authApi'

export type AuthStatus = 'checking' | 'anonymous' | 'authenticated'

type HostTokenBridge = {
  // Host containers can expose a fresh token through window.DocPilotHost.getToken().
  getToken?: () => Promise<string | null | undefined> | string | null | undefined
}

declare global {
  interface Window {
    DocPilotHost?: HostTokenBridge
    __DOCPILOT_AUTH_TOKEN__?: string
  }
}

export function useAuthSession() {
  const [authStatus, setAuthStatus] = useState<AuthStatus>('checking')
  const [currentUser, setCurrentUser] = useState<UserInformation | undefined>()
  const [authConfig, setAuthConfig] = useState<AuthConfig | undefined>()

  const clearAuth = useCallback(() => {
    globalThis.localStorage?.removeItem('docpilot.auth.token')
    setCurrentUser(undefined)
    setAuthStatus('anonymous')
  }, [])

  const authenticateWithToken = useCallback(async (token: string) => {
    globalThis.localStorage?.setItem('docpilot.auth.token', token)
    const user = await getCurrentUser()
    setCurrentUser(user)
    setAuthStatus('authenticated')
    return user
  }, [])

  const handleAuthenticated = useCallback((session: AuthSession) => {
    globalThis.localStorage?.setItem('docpilot.auth.token', session.token)
    setCurrentUser(session.user)
    setAuthStatus('authenticated')
  }, [])

  const refreshHostAuth = useCallback(async () => {
    setAuthStatus('checking')
    try {
      const token = await readInjectedAuthToken()
      if (!token) {
        clearAuth()
        return false
      }
      await authenticateWithToken(token)
      return true
    } catch {
      clearAuth()
      return false
    }
  }, [authenticateWithToken, clearAuth])

  useEffect(() => {
    let cancelled = false

    async function initializeAuth() {
      try {
        const config = await getAuthConfig()
        if (cancelled) return
        setAuthConfig(config)

        let token = globalThis.localStorage?.getItem('docpilot.auth.token') ?? ''
        if (!token && config.capabilities.supportsHostToken) {
          token = await readInjectedAuthToken()
        }
        if (cancelled) return
        if (!token) {
          setAuthStatus('anonymous')
          return
        }

        globalThis.localStorage?.setItem('docpilot.auth.token', token)
        const user = await getCurrentUser()
        if (cancelled) return
        setCurrentUser(user)
        setAuthStatus('authenticated')
      } catch {
        if (cancelled) return
        clearAuth()
      }
    }

    void initializeAuth()

    return () => {
      cancelled = true
    }
  }, [authenticateWithToken, clearAuth])

  return {
    authStatus,
    authConfig,
    currentUser,
    setCurrentUser,
    clearAuth,
    handleAuthenticated,
    refreshHostAuth
  }
}

async function readInjectedAuthToken(): Promise<string> {
  // Prefer an active host bridge over passive globals or URL bootstrap tokens.
  const bridgeToken = await globalThis.window?.DocPilotHost?.getToken?.()
  if (typeof bridgeToken === 'string' && bridgeToken.trim()) {
    return bridgeToken.trim()
  }

  const globalToken = globalThis.window?.__DOCPILOT_AUTH_TOKEN__
  if (typeof globalToken === 'string' && globalToken.trim()) {
    return globalToken.trim()
  }

  const windowLocation = globalThis.window?.location
  if (!windowLocation) return ''
  const url = new URL(windowLocation.href)
  const token = url.searchParams.get('docpilot_auth_token') ?? url.searchParams.get('docpilot_token') ?? ''
  if (!token.trim()) return ''

  // URL tokens are bootstrap-only and should not remain in browser history.
  url.searchParams.delete('docpilot_auth_token')
  url.searchParams.delete('docpilot_token')
  globalThis.window?.history?.replaceState(null, '', url.toString())
  return token.trim()
}
