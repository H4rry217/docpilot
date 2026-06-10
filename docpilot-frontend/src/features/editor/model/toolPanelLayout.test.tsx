import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  TOOL_PANEL_BOTTOM_DEFAULT_HEIGHT,
  TOOL_PANEL_BOTTOM_MIN_HEIGHT,
  TOOL_PANEL_LAYOUT_STORAGE_KEY,
  defaultToolPanelLayout,
  resolveToolPanelLayoutState,
  useToolPanelLayout,
  type ToolPanelLayoutState
} from './toolPanelLayout'

function setViewport(width: number, height: number) {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: width })
  Object.defineProperty(window, 'innerHeight', { configurable: true, value: height })
}

function LayoutProbe({ onState }: { onState: (state: ToolPanelLayoutState) => void }) {
  const layout = useToolPanelLayout()
  onState(layout.state)

  return (
    <>
      <button type="button" onClick={() => layout.floatPanel('documentOperations')}>float</button>
      <button type="button" onClick={() => layout.dockPanel('documentOperations')}>dock</button>
      <button type="button" onClick={layout.toggleBottomCollapsed}>collapse</button>
      <button type="button" onClick={() => layout.setBottomHeight(9999)}>height</button>
    </>
  )
}

afterEach(() => {
  cleanup()
  window.localStorage.removeItem(TOOL_PANEL_LAYOUT_STORAGE_KEY)
  setViewport(1024, 768)
})

describe('toolPanelLayout', () => {
  it('defaults document operations to an expanded bottom panel', () => {
    const state = defaultToolPanelLayout({ width: 1200, height: 800 })

    expect(state.activeBottomPanelId).toBe('documentOperations')
    expect(state.bottomCollapsed).toBe(false)
    expect(state.bottomHeight).toBe(TOOL_PANEL_BOTTOM_DEFAULT_HEIGHT)
    expect(state.placements.documentOperations).toBe('bottom')
  })

  it('clamps invalid persisted values back into viewport-safe bounds', () => {
    const state = resolveToolPanelLayoutState(
      {
        activeBottomPanelId: 'not-a-panel',
        bottomCollapsed: 'yes',
        bottomHeight: -40,
        placements: { documentOperations: 'elsewhere' },
        floatingRects: {
          documentOperations: { x: -200, y: 9999, width: 10, height: 4000 }
        }
      },
      { width: 800, height: 600 }
    )

    expect(state.activeBottomPanelId).toBe('documentOperations')
    expect(state.bottomCollapsed).toBe(false)
    expect(state.bottomHeight).toBe(TOOL_PANEL_BOTTOM_MIN_HEIGHT)
    expect(state.placements.documentOperations).toBe('bottom')
    expect(state.floatingRects.documentOperations).toEqual({
      x: 12,
      y: 12,
      width: 440,
      height: 576
    })
  })

  it('moves document operations between bottom and floating placements', () => {
    const onState = vi.fn()
    render(<LayoutProbe onState={onState} />)

    fireEvent.click(screen.getByRole('button', { name: 'float' }))
    expect(onState).toHaveBeenLastCalledWith(expect.objectContaining({
      activeBottomPanelId: null,
      placements: { documentOperations: 'floating' }
    }))

    fireEvent.click(screen.getByRole('button', { name: 'dock' }))
    expect(onState).toHaveBeenLastCalledWith(expect.objectContaining({
      activeBottomPanelId: 'documentOperations',
      placements: { documentOperations: 'bottom' }
    }))
  })

  it('persists bottom collapsed and height settings', () => {
    setViewport(900, 700)
    render(<LayoutProbe onState={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'collapse' }))
    fireEvent.click(screen.getByRole('button', { name: 'height' }))

    const stored = JSON.parse(window.localStorage.getItem(TOOL_PANEL_LAYOUT_STORAGE_KEY) ?? '{}') as ToolPanelLayoutState
    expect(stored.bottomCollapsed).toBe(true)
    expect(stored.bottomHeight).toBe(520)
  })

  it('falls back to defaults when localStorage contains invalid JSON', () => {
    setViewport(1000, 700)
    window.localStorage.setItem(TOOL_PANEL_LAYOUT_STORAGE_KEY, '{bad-json')
    const onState = vi.fn()

    render(<LayoutProbe onState={onState} />)

    expect(onState).toHaveBeenLastCalledWith(expect.objectContaining({
      activeBottomPanelId: 'documentOperations',
      bottomCollapsed: false,
      bottomHeight: TOOL_PANEL_BOTTOM_DEFAULT_HEIGHT
    }))
  })
})
