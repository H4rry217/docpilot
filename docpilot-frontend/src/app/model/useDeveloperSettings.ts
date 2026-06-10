import { useCallback, useState } from 'react'

const DEVELOPER_MODE_STORAGE_KEY = 'docpilot.developerMode'

function initialDeveloperMode(): boolean {
  return globalThis.localStorage?.getItem(DEVELOPER_MODE_STORAGE_KEY) === 'true'
}

export function useDeveloperSettings() {
  const [developerMode, setDeveloperModeState] = useState(initialDeveloperMode)

  const setDeveloperMode = useCallback((enabled: boolean) => {
    setDeveloperModeState(enabled)
    globalThis.localStorage?.setItem(DEVELOPER_MODE_STORAGE_KEY, enabled ? 'true' : 'false')
  }, [])

  return {
    developerMode,
    setDeveloperMode
  }
}
