import { postJson } from '../../../shared/api/http'

export const USER_SETTING_KEYS = {
  appLocale: 'app.locale',
  appDeveloperMode: 'app.developerMode',
  inlineCompletionEnabled: 'inlineCompletion.enabled',
  inlineCompletionIdleDelayMs: 'inlineCompletion.idleDelayMs',
  inlineCompletionCandidateCount: 'inlineCompletion.candidateCount',
  inlineCompletionMaxOutputTokensShort: 'inlineCompletion.maxOutputTokens.short',
  inlineCompletionMaxOutputTokensSentence: 'inlineCompletion.maxOutputTokens.sentence',
  inlineCompletionMaxOutputTokensParagraph: 'inlineCompletion.maxOutputTokens.paragraph',
  inlineCompletionMaxOutputTokensListItem: 'inlineCompletion.maxOutputTokens.listItem',
  inlineCompletionMaxOutputTokensTableCell: 'inlineCompletion.maxOutputTokens.tableCell',
  inlineCompletionMaxOutputTokensCodeLine: 'inlineCompletion.maxOutputTokens.codeLine'
} as const

export type UserSettingKey = typeof USER_SETTING_KEYS[keyof typeof USER_SETTING_KEYS]

export type UserSettingSource = 'USER' | 'DEFAULT'

export type UserSettingValue = string | boolean | number

export type UserSetting = {
  key: UserSettingKey
  value: UserSettingValue
  source: UserSettingSource
  description: string
}

export type UserSettingsResponse = {
  settings: UserSetting[]
}

export function getUserSettings(keys?: UserSettingKey[]): Promise<UserSettingsResponse> {
  return postJson('/user/settings/get', keys?.length ? { keys } : {})
}

export function saveUserSettings(values: Partial<Record<UserSettingKey, UserSettingValue>>): Promise<UserSettingsResponse> {
  return postJson('/user/settings/save', { values })
}

export function removeUserSettings(keys: UserSettingKey[]): Promise<UserSettingsResponse> {
  return postJson('/user/settings/remove', { keys })
}

export function userSettingsToMap(response: UserSettingsResponse): Map<UserSettingKey, UserSetting> {
  return new Map(response.settings.map((setting) => [setting.key, setting]))
}
