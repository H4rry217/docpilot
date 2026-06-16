import type { UserInformation } from '../../../entities/user/types'
import { postJson } from '../../../shared/api/http'

export type AuthSession = {
  token: string
  user: UserInformation
}

export type AuthProviderCapabilities = {
  supportsPasswordLogin: boolean
  supportsRegistration: boolean
  supportsPasswordChange: boolean
  supportsDisplayNameChange: boolean
  supportsHostToken: boolean
}

export type AuthConfig = {
  providerId: string
  capabilities: AuthProviderCapabilities
}

export function getAuthConfig(): Promise<AuthConfig> {
  return postJson('/auth/config', {})
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
