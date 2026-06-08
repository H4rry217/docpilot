import { useCallback, useEffect, useState } from 'react'

export type AiWorkspaceDockMode = 'docked' | 'floating'

export type AiWorkspacePoint = {
  x: number
  y: number
}

export type AiWorkspaceRect = AiWorkspacePoint & {
  width: number
  height: number
}

export type AiWorkspaceLayoutState = {
  dockMode: AiWorkspaceDockMode
  minimized: boolean
  dockWidth: number
  floatingRect: AiWorkspaceRect
  orbPosition: AiWorkspacePoint
}

export type UseAiWorkspaceLayoutResult = {
  state: AiWorkspaceLayoutState
  dock: () => void
  undock: () => void
  minimize: () => void
  restore: () => void
  setDockWidth: (width: number) => void
  setFloatingRect: (nextRect: AiWorkspaceRect | ((rect: AiWorkspaceRect) => AiWorkspaceRect)) => void
  setOrbPosition: (nextPosition: AiWorkspacePoint | ((position: AiWorkspacePoint) => AiWorkspacePoint)) => void
}

type ViewportSize = {
  width: number
  height: number
}

export const AI_WORKSPACE_LAYOUT_STORAGE_KEY = 'docpilot.layout.aiWorkspace'
const VIEWPORT_FALLBACK: ViewportSize = { width: 1440, height: 900 }
const VIEWPORT_MARGIN = 12

export const AI_WORKSPACE_DOCK_DEFAULT_WIDTH = 340
export const AI_WORKSPACE_DOCK_MIN_WIDTH = 280
export const AI_WORKSPACE_DOCK_MAX_WIDTH = 520

export const AI_WORKSPACE_FLOATING_DEFAULT_WIDTH = 360
export const AI_WORKSPACE_FLOATING_DEFAULT_HEIGHT = 560
export const AI_WORKSPACE_FLOATING_MIN_WIDTH = 300
export const AI_WORKSPACE_FLOATING_MIN_HEIGHT = 360
export const AI_WORKSPACE_FLOATING_MAX_WIDTH = 860
export const AI_WORKSPACE_FLOATING_MAX_HEIGHT = 760

export const AI_WORKSPACE_ORB_SIZE = 48

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function finiteNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function readViewportSize(): ViewportSize {
  if (typeof window === 'undefined') return VIEWPORT_FALLBACK

  return {
    width: Math.max(window.innerWidth || 0, 320),
    height: Math.max(window.innerHeight || 0, 320)
  }
}

function isDockMode(value: unknown): value is AiWorkspaceDockMode {
  return value === 'docked' || value === 'floating'
}

function defaultFloatingRect(viewport: ViewportSize): AiWorkspaceRect {
  const width = Math.min(AI_WORKSPACE_FLOATING_DEFAULT_WIDTH, viewport.width - VIEWPORT_MARGIN * 2)
  const height = Math.min(AI_WORKSPACE_FLOATING_DEFAULT_HEIGHT, viewport.height - VIEWPORT_MARGIN * 2)

  return {
    x: Math.max(VIEWPORT_MARGIN, viewport.width - width - 24),
    y: Math.max(VIEWPORT_MARGIN, viewport.height - height - 24),
    width,
    height
  }
}

function defaultOrbPosition(viewport: ViewportSize): AiWorkspacePoint {
  return {
    x: Math.max(VIEWPORT_MARGIN, viewport.width - AI_WORKSPACE_ORB_SIZE - 24),
    y: Math.max(VIEWPORT_MARGIN, viewport.height - AI_WORKSPACE_ORB_SIZE - 24)
  }
}

export function defaultAiWorkspaceLayout(viewport: ViewportSize = readViewportSize()): AiWorkspaceLayoutState {
  return {
    dockMode: 'docked',
    minimized: false,
    dockWidth: AI_WORKSPACE_DOCK_DEFAULT_WIDTH,
    floatingRect: defaultFloatingRect(viewport),
    orbPosition: defaultOrbPosition(viewport)
  }
}

export function clampDockWidth(width: number): number {
  return clamp(width, AI_WORKSPACE_DOCK_MIN_WIDTH, AI_WORKSPACE_DOCK_MAX_WIDTH)
}

export function clampAiWorkspaceRect(rect: AiWorkspaceRect, viewport: ViewportSize = readViewportSize()): AiWorkspaceRect {
  const width = clamp(
    finiteNumber(rect.width) ?? AI_WORKSPACE_FLOATING_DEFAULT_WIDTH,
    AI_WORKSPACE_FLOATING_MIN_WIDTH,
    Math.min(AI_WORKSPACE_FLOATING_MAX_WIDTH, viewport.width - VIEWPORT_MARGIN * 2)
  )
  const height = clamp(
    finiteNumber(rect.height) ?? AI_WORKSPACE_FLOATING_DEFAULT_HEIGHT,
    AI_WORKSPACE_FLOATING_MIN_HEIGHT,
    Math.min(AI_WORKSPACE_FLOATING_MAX_HEIGHT, viewport.height - VIEWPORT_MARGIN * 2)
  )
  const maxX = Math.max(VIEWPORT_MARGIN, viewport.width - width - VIEWPORT_MARGIN)
  const maxY = Math.max(VIEWPORT_MARGIN, viewport.height - height - VIEWPORT_MARGIN)

  return {
    x: clamp(finiteNumber(rect.x) ?? maxX, VIEWPORT_MARGIN, maxX),
    y: clamp(finiteNumber(rect.y) ?? maxY, VIEWPORT_MARGIN, maxY),
    width,
    height
  }
}

export function clampAiWorkspaceOrbPosition(
  position: AiWorkspacePoint,
  viewport: ViewportSize = readViewportSize()
): AiWorkspacePoint {
  const maxX = Math.max(VIEWPORT_MARGIN, viewport.width - AI_WORKSPACE_ORB_SIZE - VIEWPORT_MARGIN)
  const maxY = Math.max(VIEWPORT_MARGIN, viewport.height - AI_WORKSPACE_ORB_SIZE - VIEWPORT_MARGIN)

  return {
    x: clamp(finiteNumber(position.x) ?? maxX, VIEWPORT_MARGIN, maxX),
    y: clamp(finiteNumber(position.y) ?? maxY, VIEWPORT_MARGIN, maxY)
  }
}

function readStoredLayout(): unknown {
  try {
    const stored = globalThis.localStorage?.getItem(AI_WORKSPACE_LAYOUT_STORAGE_KEY)
    return stored ? JSON.parse(stored) : null
  } catch {
    return null
  }
}

function writeStoredLayout(state: AiWorkspaceLayoutState): void {
  try {
    globalThis.localStorage?.setItem(AI_WORKSPACE_LAYOUT_STORAGE_KEY, JSON.stringify(state))
  } catch {
    // localStorage can be unavailable in private or restricted browser contexts.
  }
}

export function resolveAiWorkspaceLayoutState(
  value: unknown,
  viewport: ViewportSize = readViewportSize()
): AiWorkspaceLayoutState {
  const fallback = defaultAiWorkspaceLayout(viewport)

  if (!value || typeof value !== 'object') return fallback

  const candidate = value as Partial<AiWorkspaceLayoutState>
  const floatingRect = candidate.floatingRect && typeof candidate.floatingRect === 'object'
    ? clampAiWorkspaceRect(candidate.floatingRect as AiWorkspaceRect, viewport)
    : fallback.floatingRect
  const orbPosition = candidate.orbPosition && typeof candidate.orbPosition === 'object'
    ? clampAiWorkspaceOrbPosition(candidate.orbPosition as AiWorkspacePoint, viewport)
    : fallback.orbPosition

  return {
    dockMode: isDockMode(candidate.dockMode) ? candidate.dockMode : fallback.dockMode,
    minimized: typeof candidate.minimized === 'boolean' ? candidate.minimized : fallback.minimized,
    dockWidth: clampDockWidth(finiteNumber(candidate.dockWidth) ?? fallback.dockWidth),
    floatingRect,
    orbPosition
  }
}

export function useAiWorkspaceLayout(): UseAiWorkspaceLayoutResult {
  const [state, setState] = useState(() => resolveAiWorkspaceLayoutState(readStoredLayout()))

  useEffect(() => {
    writeStoredLayout(state)
  }, [state])

  useEffect(() => {
    function handleResize() {
      const viewport = readViewportSize()
      setState((current) => resolveAiWorkspaceLayoutState(current, viewport))
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const dock = useCallback(() => {
    setState((current) => ({ ...current, dockMode: 'docked', minimized: false }))
  }, [])

  const undock = useCallback(() => {
    setState((current) => ({
      ...current,
      dockMode: 'floating',
      minimized: false,
      floatingRect: clampAiWorkspaceRect(current.floatingRect)
    }))
  }, [])

  const minimize = useCallback(() => {
    setState((current) => ({ ...current, minimized: true }))
  }, [])

  const restore = useCallback(() => {
    setState((current) => ({ ...current, minimized: false }))
  }, [])

  const setDockWidth = useCallback((width: number) => {
    setState((current) => ({ ...current, dockWidth: clampDockWidth(width) }))
  }, [])

  const setFloatingRect = useCallback((nextRect: AiWorkspaceRect | ((rect: AiWorkspaceRect) => AiWorkspaceRect)) => {
    setState((current) => {
      const resolvedRect = typeof nextRect === 'function' ? nextRect(current.floatingRect) : nextRect
      return { ...current, floatingRect: clampAiWorkspaceRect(resolvedRect) }
    })
  }, [])

  const setOrbPosition = useCallback((nextPosition: AiWorkspacePoint | ((position: AiWorkspacePoint) => AiWorkspacePoint)) => {
    setState((current) => {
      const resolvedPosition = typeof nextPosition === 'function' ? nextPosition(current.orbPosition) : nextPosition
      return { ...current, orbPosition: clampAiWorkspaceOrbPosition(resolvedPosition) }
    })
  }, [])

  return {
    state,
    dock,
    undock,
    minimize,
    restore,
    setDockWidth,
    setFloatingRect,
    setOrbPosition
  }
}
