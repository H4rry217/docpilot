import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { Locale } from '../../shared/i18n'
import {
  getUserSettings,
  saveUserSettings,
  userSettingsToMap,
  USER_SETTING_KEYS,
  type UserSettingKey,
  type UserSettingValue,
  type UserSettingsResponse
} from '../../features/user-settings/api/userSettingsApi'

export type InlineCompletionTokenKey =
  | 'short'
  | 'sentence'
  | 'paragraph'
  | 'listItem'
  | 'tableCell'
  | 'codeLine'

export type InlineCompletionCloudSettings = {
  enabled: boolean
  idleDelayMs: number
  candidateCount: number
  maxOutputTokens: Record<InlineCompletionTokenKey, number>
}

export type CloudUserSettingsState = {
  hydrated: boolean
  saving: boolean
  error: string | null
  inlineCompletion: InlineCompletionCloudSettings
  setLocale: (locale: Locale) => void
  setDeveloperMode: (enabled: boolean) => void
  setInlineCompletionEnabled: (enabled: boolean) => void
  setInlineCompletionIdleDelayMs: (delayMs: number) => void
  setInlineCompletionCandidateCount: (candidateCount: number) => void
  setInlineCompletionMaxOutputTokens: (key: InlineCompletionTokenKey, value: number) => void
}

type UseCloudUserSettingsOptions = {
  userId?: number
  locale: Locale
  setLocale: (locale: Locale) => void
  developerMode: boolean
  setDeveloperMode: (enabled: boolean) => void
}

const LOCALE_STORAGE_KEY = 'docpilot.locale'
const DEVELOPER_MODE_STORAGE_KEY = 'docpilot.developerMode'

const DEFAULT_INLINE_COMPLETION_SETTINGS: InlineCompletionCloudSettings = {
  enabled: true,
  idleDelayMs: 500,
  candidateCount: 3,
  maxOutputTokens: {
    short: 32,
    sentence: 64,
    paragraph: 160,
    listItem: 80,
    tableCell: 32,
    codeLine: 96
  }
}

const TOKEN_SETTING_KEYS: Record<InlineCompletionTokenKey, UserSettingKey> = {
  short: USER_SETTING_KEYS.inlineCompletionMaxOutputTokensShort,
  sentence: USER_SETTING_KEYS.inlineCompletionMaxOutputTokensSentence,
  paragraph: USER_SETTING_KEYS.inlineCompletionMaxOutputTokensParagraph,
  listItem: USER_SETTING_KEYS.inlineCompletionMaxOutputTokensListItem,
  tableCell: USER_SETTING_KEYS.inlineCompletionMaxOutputTokensTableCell,
  codeLine: USER_SETTING_KEYS.inlineCompletionMaxOutputTokensCodeLine
}

export function useCloudUserSettings({
  userId,
  locale,
  setLocale,
  developerMode,
  setDeveloperMode
}: UseCloudUserSettingsOptions): CloudUserSettingsState {
  const [hydrated, setHydrated] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [inlineCompletion, setInlineCompletion] = useState<InlineCompletionCloudSettings>(
    DEFAULT_INLINE_COMPLETION_SETTINGS
  )
  const userIdRef = useRef<number | undefined>(undefined)
  const settingsRef = useRef({
    locale,
    developerMode,
    inlineCompletion
  })

  useEffect(() => {
    settingsRef.current = {
      locale,
      developerMode,
      inlineCompletion
    }
  }, [developerMode, inlineCompletion, locale])

  const applyResponse = useCallback((response: UserSettingsResponse) => {
    const settings = userSettingsToMap(response)
    const nextLocale = stringValue(settings.get(USER_SETTING_KEYS.appLocale)?.value)
    if (isLocale(nextLocale)) {
      setLocale(nextLocale)
    }

    const nextDeveloperMode = booleanValue(settings.get(USER_SETTING_KEYS.appDeveloperMode)?.value)
    if (nextDeveloperMode != null) {
      setDeveloperMode(nextDeveloperMode)
    }

    setInlineCompletion({
      enabled: booleanValue(settings.get(USER_SETTING_KEYS.inlineCompletionEnabled)?.value)
        ?? DEFAULT_INLINE_COMPLETION_SETTINGS.enabled,
      idleDelayMs: numberValue(settings.get(USER_SETTING_KEYS.inlineCompletionIdleDelayMs)?.value)
        ?? DEFAULT_INLINE_COMPLETION_SETTINGS.idleDelayMs,
      candidateCount: numberValue(settings.get(USER_SETTING_KEYS.inlineCompletionCandidateCount)?.value)
        ?? DEFAULT_INLINE_COMPLETION_SETTINGS.candidateCount,
      maxOutputTokens: {
        short: numberValue(settings.get(USER_SETTING_KEYS.inlineCompletionMaxOutputTokensShort)?.value)
          ?? DEFAULT_INLINE_COMPLETION_SETTINGS.maxOutputTokens.short,
        sentence: numberValue(settings.get(USER_SETTING_KEYS.inlineCompletionMaxOutputTokensSentence)?.value)
          ?? DEFAULT_INLINE_COMPLETION_SETTINGS.maxOutputTokens.sentence,
        paragraph: numberValue(settings.get(USER_SETTING_KEYS.inlineCompletionMaxOutputTokensParagraph)?.value)
          ?? DEFAULT_INLINE_COMPLETION_SETTINGS.maxOutputTokens.paragraph,
        listItem: numberValue(settings.get(USER_SETTING_KEYS.inlineCompletionMaxOutputTokensListItem)?.value)
          ?? DEFAULT_INLINE_COMPLETION_SETTINGS.maxOutputTokens.listItem,
        tableCell: numberValue(settings.get(USER_SETTING_KEYS.inlineCompletionMaxOutputTokensTableCell)?.value)
          ?? DEFAULT_INLINE_COMPLETION_SETTINGS.maxOutputTokens.tableCell,
        codeLine: numberValue(settings.get(USER_SETTING_KEYS.inlineCompletionMaxOutputTokensCodeLine)?.value)
          ?? DEFAULT_INLINE_COMPLETION_SETTINGS.maxOutputTokens.codeLine
      }
    })
  }, [setDeveloperMode, setLocale])

  useEffect(() => {
    if (!userId) {
      userIdRef.current = undefined
      setHydrated(false)
      setError(null)
      return
    }
    if (userIdRef.current === userId && hydrated) return

    userIdRef.current = userId
    setHydrated(false)
    setError(null)
    let cancelled = false

    getUserSettings()
      .then(async (response) => {
        if (cancelled) return
        const migrationValues = legacyMigrationValues(response)
        if (Object.keys(migrationValues).length) {
          const migratedResponse = await saveUserSettings(migrationValues)
          if (cancelled) return
          applyResponse(migratedResponse)
        } else {
          applyResponse(response)
        }
        setHydrated(true)
      })
      .catch((caught: unknown) => {
        if (cancelled) return
        setError(errorMessage(caught))
        setHydrated(true)
      })

    return () => {
      cancelled = true
    }
  }, [applyResponse, hydrated, userId])

  const saveValues = useCallback((values: Partial<Record<UserSettingKey, UserSettingValue>>) => {
    const previous = settingsRef.current
    applyLocalValues(values, setLocale, setDeveloperMode, setInlineCompletion)
    setSaving(true)
    setError(null)
    saveUserSettings(values)
      .then(applyResponse)
      .catch((caught: unknown) => {
        setLocale(previous.locale)
        setDeveloperMode(previous.developerMode)
        setInlineCompletion(previous.inlineCompletion)
        setError(errorMessage(caught))
      })
      .finally(() => setSaving(false))
  }, [applyResponse, setDeveloperMode, setLocale])

  return useMemo(() => ({
    hydrated,
    saving,
    error,
    inlineCompletion,
    setLocale: (nextLocale) => saveValues({ [USER_SETTING_KEYS.appLocale]: nextLocale }),
    setDeveloperMode: (enabled) => saveValues({ [USER_SETTING_KEYS.appDeveloperMode]: enabled }),
    setInlineCompletionEnabled: (enabled) => saveValues({ [USER_SETTING_KEYS.inlineCompletionEnabled]: enabled }),
    setInlineCompletionIdleDelayMs: (delayMs) => saveValues({ [USER_SETTING_KEYS.inlineCompletionIdleDelayMs]: delayMs }),
    setInlineCompletionCandidateCount: (candidateCount) => saveValues({ [USER_SETTING_KEYS.inlineCompletionCandidateCount]: candidateCount }),
    setInlineCompletionMaxOutputTokens: (key, value) => saveValues({ [TOKEN_SETTING_KEYS[key]]: value })
  }), [error, hydrated, inlineCompletion, saveValues, saving])
}

function legacyMigrationValues(response: UserSettingsResponse): Partial<Record<UserSettingKey, UserSettingValue>> {
  const settings = userSettingsToMap(response)
  const values: Partial<Record<UserSettingKey, UserSettingValue>> = {}

  const localeSetting = settings.get(USER_SETTING_KEYS.appLocale)
  const storedLocale = globalThis.localStorage?.getItem(LOCALE_STORAGE_KEY)
  if (localeSetting?.source === 'DEFAULT' && isLocale(storedLocale) && storedLocale !== localeSetting.value) {
    values[USER_SETTING_KEYS.appLocale] = storedLocale
  }

  const developerModeSetting = settings.get(USER_SETTING_KEYS.appDeveloperMode)
  const storedDeveloperMode = globalThis.localStorage?.getItem(DEVELOPER_MODE_STORAGE_KEY)
  if (developerModeSetting?.source === 'DEFAULT' && storedDeveloperMode === 'true' && developerModeSetting.value !== true) {
    values[USER_SETTING_KEYS.appDeveloperMode] = true
  }
  return values
}

function applyLocalValues(
  values: Partial<Record<UserSettingKey, UserSettingValue>>,
  setLocale: (locale: Locale) => void,
  setDeveloperMode: (enabled: boolean) => void,
  setInlineCompletion: (updater: (settings: InlineCompletionCloudSettings) => InlineCompletionCloudSettings) => void
) {
  const localeValue = values[USER_SETTING_KEYS.appLocale]
  if (isLocale(localeValue)) {
    setLocale(localeValue)
  }
  const developerModeValue = values[USER_SETTING_KEYS.appDeveloperMode]
  if (typeof developerModeValue === 'boolean') {
    setDeveloperMode(developerModeValue)
  }

  setInlineCompletion((current) => {
    const next = {
      ...current,
      maxOutputTokens: { ...current.maxOutputTokens }
    }
    const enabled = values[USER_SETTING_KEYS.inlineCompletionEnabled]
    if (typeof enabled === 'boolean') next.enabled = enabled
    const idleDelayMs = values[USER_SETTING_KEYS.inlineCompletionIdleDelayMs]
    if (typeof idleDelayMs === 'number') next.idleDelayMs = idleDelayMs
    const candidateCount = values[USER_SETTING_KEYS.inlineCompletionCandidateCount]
    if (typeof candidateCount === 'number') next.candidateCount = candidateCount
    Object.entries(TOKEN_SETTING_KEYS).forEach(([tokenKey, settingKey]) => {
      const value = values[settingKey]
      if (typeof value === 'number') {
        next.maxOutputTokens[tokenKey as InlineCompletionTokenKey] = value
      }
    })
    return next
  })
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined
}

function booleanValue(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined
}

function numberValue(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function isLocale(value: unknown): value is Locale {
  return value === 'zh-CN' || value === 'en-US'
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Failed to sync user settings'
}
