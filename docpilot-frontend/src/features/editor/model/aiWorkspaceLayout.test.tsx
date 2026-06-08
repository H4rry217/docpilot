import { render } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  AI_WORKSPACE_DOCK_DEFAULT_WIDTH,
  AI_WORKSPACE_DOCK_MIN_WIDTH,
  AI_WORKSPACE_FLOATING_MAX_WIDTH,
  AI_WORKSPACE_FLOATING_MIN_WIDTH,
  AI_WORKSPACE_LAYOUT_STORAGE_KEY,
  AI_WORKSPACE_ORB_SIZE,
  clampAiWorkspaceRect,
  defaultAiWorkspaceLayout,
  resolveAiWorkspaceLayoutState,
  useAiWorkspaceLayout,
  type AiWorkspaceLayoutState
} from './aiWorkspaceLayout'

function setViewport(width: number, height: number) {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: width })
  Object.defineProperty(window, 'innerHeight', { configurable: true, value: height })
}

function LayoutProbe({ onState }: { onState: (state: AiWorkspaceLayoutState) => void }) {
  const layout = useAiWorkspaceLayout()
  onState(layout.state)
  return null
}

afterEach(() => {
  window.localStorage.removeItem(AI_WORKSPACE_LAYOUT_STORAGE_KEY)
  setViewport(1024, 768)
})

describe('aiWorkspaceLayout', () => {
  it('defaults to an expanded docked workspace', () => {
    const state = defaultAiWorkspaceLayout({ width: 1200, height: 800 })

    expect(state.dockMode).toBe('docked')
    expect(state.minimized).toBe(false)
    expect(state.dockWidth).toBe(AI_WORKSPACE_DOCK_DEFAULT_WIDTH)
    expect(state.floatingRect.x).toBe(816)
    expect(state.orbPosition.x).toBe(1128)
  })

  it('clamps invalid persisted values back into viewport-safe bounds', () => {
    const state = resolveAiWorkspaceLayoutState(
      {
        dockMode: 'elsewhere',
        minimized: 'yes',
        dockWidth: -50,
        floatingRect: { x: -400, y: 9000, width: 10, height: 4000 },
        orbPosition: { x: 9999, y: -200 }
      },
      { width: 800, height: 600 }
    )

    expect(state.dockMode).toBe('docked')
    expect(state.minimized).toBe(false)
    expect(state.dockWidth).toBe(AI_WORKSPACE_DOCK_MIN_WIDTH)
    expect(state.floatingRect).toEqual({
      x: 12,
      y: 12,
      width: AI_WORKSPACE_FLOATING_MIN_WIDTH,
      height: 576
    })
    expect(state.orbPosition).toEqual({
      x: 800 - AI_WORKSPACE_ORB_SIZE - 12,
      y: 12
    })
  })

  it('allows floating windows to grow wider while staying inside the viewport', () => {
    expect(clampAiWorkspaceRect(
      { x: 900, y: 80, width: 2000, height: 560 },
      { width: 1200, height: 800 }
    )).toEqual({
      x: 328,
      y: 80,
      width: AI_WORKSPACE_FLOATING_MAX_WIDTH,
      height: 560
    })

    expect(clampAiWorkspaceRect(
      { x: 100, y: 80, width: 2000, height: 560 },
      { width: 720, height: 800 }
    ).width).toBe(696)
  })

  it('falls back to defaults when localStorage contains invalid JSON', () => {
    setViewport(1000, 700)
    window.localStorage.setItem(AI_WORKSPACE_LAYOUT_STORAGE_KEY, '{bad-json')
    const onState = vi.fn()

    render(<LayoutProbe onState={onState} />)

    expect(onState).toHaveBeenLastCalledWith(expect.objectContaining({
      dockMode: 'docked',
      minimized: false,
      dockWidth: AI_WORKSPACE_DOCK_DEFAULT_WIDTH
    }))
  })
})
