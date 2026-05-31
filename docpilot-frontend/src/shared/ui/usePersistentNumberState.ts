import { useCallback, useState, type Dispatch, type SetStateAction } from 'react'

type PersistentNumberStateOptions = {
  storageKey: string
  defaultValue: number
  min: number
  max: number
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function readStoredNumber(storageKey: string): number | null {
  try {
    const stored = globalThis.localStorage?.getItem(storageKey)
    if (stored == null) return null
    const value = Number(stored)
    return Number.isFinite(value) ? value : null
  } catch {
    return null
  }
}

function writeStoredNumber(storageKey: string, value: number): void {
  try {
    globalThis.localStorage?.setItem(storageKey, String(value))
  } catch {
    // localStorage can be unavailable in private or restricted browser contexts.
  }
}

export function usePersistentNumberState({
  storageKey,
  defaultValue,
  min,
  max
}: PersistentNumberStateOptions): [number, Dispatch<SetStateAction<number>>] {
  const [value, setValue] = useState(() => clamp(readStoredNumber(storageKey) ?? defaultValue, min, max))

  const setPersistentValue = useCallback<Dispatch<SetStateAction<number>>>(
    (nextValue) => {
      setValue((currentValue) => {
        const resolvedValue = typeof nextValue === 'function' ? nextValue(currentValue) : nextValue
        const clampedValue = clamp(resolvedValue, min, max)
        writeStoredNumber(storageKey, clampedValue)
        return clampedValue
      })
    },
    [max, min, storageKey]
  )

  return [value, setPersistentValue]
}
