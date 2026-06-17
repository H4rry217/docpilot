import type { UserInformation } from '../../../entities/user/types'
import { postJson } from '../../../shared/api/http'

export type AuthSession = {
  token: string
  user: UserInformation
}

export type AuthLoginFlow = 'PASSWORD_FORM' | 'HOST_TOKEN' | 'REMOTE_USER'

export type AuthAccountAction =
  | 'REGISTER'
  | 'CHANGE_PASSWORD'
  | 'CHANGE_DISPLAY_NAME'

export type AuthProviderCapabilities = {
  loginFlows?: AuthLoginFlow[]
  accountActions?: AuthAccountAction[]
  supportsPasswordLogin?: boolean
  supportsRegistration?: boolean
  supportsPasswordChange?: boolean
  supportsDisplayNameChange?: boolean
  supportsHostToken?: boolean
}

export type AuthConfig = {
  providerId: string
  capabilities: AuthProviderCapabilities
}

export function getAuthConfig(): Promise<AuthConfig> {
  return postJson('/auth/config', {})
}

export function hasLoginFlow(capabilities: AuthProviderCapabilities, flow: AuthLoginFlow): boolean {
  if (Array.isArray(capabilities.loginFlows)) {
    return capabilities.loginFlows.includes(flow)
  }
  if (flow === 'PASSWORD_FORM') return capabilities.supportsPasswordLogin ?? true
  if (flow === 'HOST_TOKEN') return capabilities.supportsHostToken ?? false
  return false
}

export function hasAccountAction(capabilities: AuthProviderCapabilities, action: AuthAccountAction): boolean {
  if (Array.isArray(capabilities.accountActions)) {
    return capabilities.accountActions.includes(action)
  }
  if (action === 'REGISTER') return capabilities.supportsRegistration ?? true
  if (action === 'CHANGE_PASSWORD') return capabilities.supportsPasswordChange ?? true
  if (action === 'CHANGE_DISPLAY_NAME') return capabilities.supportsDisplayNameChange ?? true
  return false
}

export function register(input: {
  email: string
  password: string
  displayName?: string
}): Promise<UserInformation> {
  return postJson('/auth/register', input)
}

export function login(input: { email: string; password: string }): Promise<AuthSession> {
  return postJson('/auth/login', input)
}

export function getCurrentUser(): Promise<UserInformation> {
  return postJson('/auth/me', {})
}

export function changePassword(input: {
  currentPassword: string
  newPassword: string
}): Promise<void> {
  return postJson('/auth/password/change', input)
}

export function changeDisplayName(input: { displayName: string }): Promise<UserInformation> {
  return postJson('/auth/display-name/change', input)
}
