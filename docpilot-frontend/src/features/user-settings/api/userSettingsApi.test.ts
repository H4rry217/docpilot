import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  getUserSettings,
  saveUserSettings,
  userSettingsToMap,
  USER_SETTING_KEYS
} from './userSettingsApi'

describe('user settings API', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
  })

  it('gets selected user settings', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({
        code: 0,
        msg: 'OK',
        data: {
          settings: [
            {
              key: USER_SETTING_KEYS.appLocale,
              value: 'en-US',
              source: 'USER',
              description: 'Interface language'
            }
          ]
        }
      }), { status: 200 })
    )

    const response = await getUserSettings([USER_SETTING_KEYS.appLocale])

    expect(userSettingsToMap(response).get(USER_SETTING_KEYS.appLocale)?.value).toBe('en-US')
    expect(fetchMock).toHaveBeenCalledWith('/api/user/settings/get', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ keys: [USER_SETTING_KEYS.appLocale] })
    })
  })

  it('saves typed user setting values', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 0, msg: 'OK', data: { settings: [] } }), { status: 200 })
    )

    await saveUserSettings({
      [USER_SETTING_KEYS.inlineCompletionEnabled]: false,
      [USER_SETTING_KEYS.inlineCompletionIdleDelayMs]: 800,
      [USER_SETTING_KEYS.inlineCompletionCandidateCount]: 4
    })

    expect(fetchMock).toHaveBeenCalledWith('/api/user/settings/save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        values: {
          [USER_SETTING_KEYS.inlineCompletionEnabled]: false,
          [USER_SETTING_KEYS.inlineCompletionIdleDelayMs]: 800,
          [USER_SETTING_KEYS.inlineCompletionCandidateCount]: 4
        }
      })
    })
  })
})
