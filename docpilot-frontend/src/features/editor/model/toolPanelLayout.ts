import { useCallback, useEffect, useState } from 'react'

export type ToolPanelId = 'documentOperations'
export type ToolPanelPlacement = 'bottom' | 'floating'

export type ToolPanelRect = {
  x: number
  y: number
  width: number
  height: number
}

export type ToolPanelLayoutState = {
  activeBottomPanelId: ToolPanelId | null
  bottomCollapsed: boolean
  bottomHeight: number
  placements: Record<ToolPanelId, ToolPanelPlacement>
  floatingRects: Record<ToolPanelId, ToolPanelRect>
}

export type UseToolPanelLayoutResult = {
  state: ToolPanelLayoutState
  dockPanel: (panelId: ToolPanelId) => void
  floatPanel: (panelId: ToolPanelId) => void
  toggleBottomCollapsed: () => void
  setBottomHeight: (height: number) => void
  setFloatingRect: (panelId: ToolPanelId, nextRect: ToolPanelRect | ((rect: ToolPanelRect) => ToolPanelRect)) => void
}

type ViewportSize = {
  width: number
  height: number
}

export const TOOL_PANEL_LAYOUT_STORAGE_KEY = 'docpilot.layout.toolPanels'
export const TOOL_PANEL_BOTTOM_DEFAULT_HEIGHT = 300
export const TOOL_PANEL_BOTTOM_MIN_HEIGHT = 152
export const TOOL_PANEL_BOTTOM_MAX_HEIGHT = 520
export const TOOL_PANEL_BOTTOM_COLLAPSED_HEIGHT = 36

const VIEWPORT_FALLBACK: ViewportSize = { width: 1440, height: 900 }
const VIEWPORT_MARGIN = 12
const FLOATING_DEFAULT_WIDTH = 720
const FLOATING_DEFAULT_HEIGHT = 420
const FLOATING_MIN_WIDTH = 440
const FLOATING_MIN_HEIGHT = 260
const FLOATING_MAX_WIDTH = 1100
const FLOATING_MAX_HEIGHT = 760

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function finiteNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function isToolPanelId(value: unknown): value is ToolPanelId {
  return value === 'documentOperations'
}

function isToolPanelPlacement(value: unknown): value is ToolPanelPlacement {
  return value === 'bottom' || value === 'floating'
}

function readViewportSize(): ViewportSize {
  if (typeof window === 'undefined') return VIEWPORT_FALLBACK

  return {
    width: Math.max(window.innerWidth || 0, 320),
    height: Math.max(window.innerHeight || 0, 320)
  }
}

function defaultFloatingRect(viewport: ViewportSize): ToolPanelRect {
  const width = Math.min(FLOATING_DEFAULT_WIDTH, viewport.width - VIEWPORT_MARGIN * 2)
  const height = Math.min(FLOATING_DEFAULT_HEIGHT, viewport.height - VIEWPORT_MARGIN * 2)

  return {
    x: Math.max(VIEWPORT_MARGIN, viewport.width - width - 32),
    y: Math.max(VIEWPORT_MARGIN, viewport.height - height - 72),
    width,
    height
  }
}

export function clampToolPanelBottomHeight(
  height: number,
  viewport: ViewportSize = readViewportSize()
): number {
  const maxHeight = Math.max(
    TOOL_PANEL_BOTTOM_MIN_HEIGHT,
    Math.min(TOOL_PANEL_BOTTOM_MAX_HEIGHT, viewport.height - 180)
  )
  return clamp(finiteNumber(height) ?? TOOL_PANEL_BOTTOM_DEFAULT_HEIGHT, TOOL_PANEL_BOTTOM_MIN_HEIGHT, maxHeight)
}

export function clampToolPanelFloatingRect(
  rect: ToolPanelRect,
  viewport: ViewportSize = readViewportSize()
): ToolPanelRect {
  const width = clamp(
    finiteNumber(rect.width) ?? FLOATING_DEFAULT_WIDTH,
    FLOATING_MIN_WIDTH,
    Math.min(FLOATING_MAX_WIDTH, viewport.width - VIEWPORT_MARGIN * 2)
  )
  const height = clamp(
    finiteNumber(rect.height) ?? FLOATING_DEFAULT_HEIGHT,
    FLOATING_MIN_HEIGHT,
    Math.min(FLOATING_MAX_HEIGHT, viewport.height - VIEWPORT_MARGIN * 2)
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

export function defaultToolPanelLayout(viewport: ViewportSize = readViewportSize()): ToolPanelLayoutState {
  return {
    activeBottomPanelId: 'documentOperations',
    bottomCollapsed: false,
    bottomHeight: TOOL_PANEL_BOTTOM_DEFAULT_HEIGHT,
    placements: {
      documentOperations: 'bottom'
    },
    floatingRects: {
      documentOperations: defaultFloatingRect(viewport)
    }
  }
}

function readStoredLayout(): unknown {
  try {
    const stored = globalThis.localStorage?.getItem(TOOL_PANEL_LAYOUT_STORAGE_KEY)
    return stored ? JSON.parse(stored) : null
  } catch {
    return null
  }
}

function writeStoredLayout(state: ToolPanelLayoutState): void {
  try {
    globalThis.localStorage?.setItem(TOOL_PANEL_LAYOUT_STORAGE_KEY, JSON.stringify(state))
  } catch {
    // localStorage can be unavailable in private or restricted browser contexts.
  }
}

export function resolveToolPanelLayoutState(
  value: unknown,
  viewport: ViewportSize = readViewportSize()
): ToolPanelLayoutState {
  const fallback = defaultToolPanelLayout(viewport)
  if (!value || typeof value !== 'object') return fallback

  const candidate = value as Partial<ToolPanelLayoutState>
  const placement = isToolPanelPlacement(candidate.placements?.documentOperations)
    ? candidate.placements.documentOperations
    : fallback.placements.documentOperations
  const activeBottomPanelId = isToolPanelId(candidate.activeBottomPanelId)
    ? candidate.activeBottomPanelId
    : placement === 'bottom'
      ? 'documentOperations'
      : null

  return {
    activeBottomPanelId: placement === 'bottom' ? activeBottomPanelId : null,
    bottomCollapsed: typeof candidate.bottomCollapsed === 'boolean' ? candidate.bottomCollapsed : fallback.bottomCollapsed,
    bottomHeight: clampToolPanelBottomHeight(
      finiteNumber(candidate.bottomHeight) ?? fallback.bottomHeight,
      viewport
    ),
    placements: {
      documentOperations: placement
    },
    floatingRects: {
      documentOperations: candidate.floatingRects?.documentOperations
        ? clampToolPanelFloatingRect(candidate.floatingRects.documentOperations, viewport)
        : fallback.floatingRects.documentOperations
    }
  }
}

export function useToolPanelLayout(): UseToolPanelLayoutResult {
  const [state, setState] = useState(() => resolveToolPanelLayoutState(readStoredLayout()))

  useEffect(() => {
    writeStoredLayout(state)
  }, [state])

  useEffect(() => {
    function handleResize() {
      const viewport = readViewportSize()
      setState((current) => resolveToolPanelLayoutState(current, viewport))
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const dockPanel = useCallback((panelId: ToolPanelId) => {
    setState((current) => ({
      ...current,
      activeBottomPanelId: panelId,
      placements: {
        ...current.placements,
        [panelId]: 'bottom'
      }
    }))
  }, [])

  const floatPanel = useCallback((panelId: ToolPanelId) => {
    setState((current) => ({
      ...current,
      activeBottomPanelId: current.activeBottomPanelId === panelId ? null : current.activeBottomPanelId,
      placements: {
        ...current.placements,
        [panelId]: 'floating'
      },
      floatingRects: {
        ...current.floatingRects,
        [panelId]: clampToolPanelFloatingRect(current.floatingRects[panelId])
      }
    }))
  }, [])

  const toggleBottomCollapsed = useCallback(() => {
    setState((current) => ({ ...current, bottomCollapsed: !current.bottomCollapsed }))
  }, [])

  const setBottomHeight = useCallback((height: number) => {
    setState((current) => ({ ...current, bottomHeight: clampToolPanelBottomHeight(height) }))
  }, [])

  const setFloatingRect = useCallback((
    panelId: ToolPanelId,
    nextRect: ToolPanelRect | ((rect: ToolPanelRect) => ToolPanelRect)
  ) => {
    setState((current) => {
      const resolvedRect = typeof nextRect === 'function' ? nextRect(current.floatingRects[panelId]) : nextRect
      return {
        ...current,
        floatingRects: {
          ...current.floatingRects,
          [panelId]: clampToolPanelFloatingRect(resolvedRect)
        }
      }
    })
  }, [])

  return {
    state,
    dockPanel,
    floatPanel,
    toggleBottomCollapsed,
    setBottomHeight,
    setFloatingRect
  }
}
