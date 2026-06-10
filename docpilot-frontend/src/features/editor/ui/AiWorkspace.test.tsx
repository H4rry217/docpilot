import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TooltipProvider } from '@/components/ui/tooltip'
import { I18nProvider } from '../../../shared/i18n'
import {
  defaultAiWorkspaceLayout,
  type AiWorkspaceLayoutState,
  type UseAiWorkspaceLayoutResult
} from '../model/aiWorkspaceLayout'
import { AI_WORKSPACE_FLOATING_MORPH_MS, AiWorkspace } from './AiWorkspace'

function createLayout(overrides: Partial<AiWorkspaceLayoutState> = {}): UseAiWorkspaceLayoutResult {
  const defaults = defaultAiWorkspaceLayout({ width: 1200, height: 800 })
  const state = {
    ...defaults,
    ...overrides,
    floatingRect: {
      ...defaults.floatingRect,
      ...overrides.floatingRect
    },
    orbPosition: {
      ...defaults.orbPosition,
      ...overrides.orbPosition
    }
  }

  return {
    state,
    dock: vi.fn(),
    undock: vi.fn(),
    minimize: vi.fn(),
    restore: vi.fn(),
    setDockWidth: vi.fn(),
    setFloatingRect: vi.fn(),
    setOrbPosition: vi.fn()
  }
}

function renderWorkspace(layout: UseAiWorkspaceLayoutResult) {
  window.localStorage.setItem('docpilot.locale', 'en-US')
  render(
    <I18nProvider>
      <TooltipProvider>
        <AiWorkspace layout={layout} />
      </TooltipProvider>
    </I18nProvider>
  )
}

function StatefulWorkspace({ initialState }: { initialState: AiWorkspaceLayoutState }) {
  const [state, setState] = useState(initialState)
  const layout: UseAiWorkspaceLayoutResult = {
    state,
    dock: () => setState((current) => ({ ...current, dockMode: 'docked', minimized: false })),
    undock: () => setState((current) => ({ ...current, dockMode: 'floating', minimized: false })),
    minimize: () => setState((current) => ({ ...current, minimized: true })),
    restore: () => setState((current) => ({ ...current, minimized: false })),
    setDockWidth: (dockWidth) => setState((current) => ({ ...current, dockWidth })),
    setFloatingRect: (nextRect) => setState((current) => ({
      ...current,
      floatingRect: typeof nextRect === 'function' ? nextRect(current.floatingRect) : nextRect
    })),
    setOrbPosition: (nextPosition) => setState((current) => ({
      ...current,
      orbPosition: typeof nextPosition === 'function' ? nextPosition(current.orbPosition) : nextPosition
    }))
  }

  return <AiWorkspace layout={layout} />
}

function renderStatefulWorkspace(initialState: AiWorkspaceLayoutState) {
  window.localStorage.setItem('docpilot.locale', 'en-US')
  render(
    <I18nProvider>
      <TooltipProvider>
        <StatefulWorkspace initialState={initialState} />
      </TooltipProvider>
    </I18nProvider>
  )
}

function dispatchPointer(target: EventTarget, type: string, clientX: number, clientY = 0) {
  target.dispatchEvent(new MouseEvent(type, {
    bubbles: true,
    cancelable: true,
    clientX,
    clientY
  }))
}

afterEach(() => {
  vi.useRealTimers()
  cleanup()
  window.localStorage.removeItem('docpilot.locale')
  document.body.className = ''
})

describe('AiWorkspace', () => {
  it('renders the expanded docked panel by default', () => {
    const layout = createLayout()

    renderWorkspace(layout)

    const panel = screen.getByRole('complementary', { name: 'AI chat' })
    expect(panel).toBeInTheDocument()
    expect(panel.className).toContain('row-end-3')
    expect(screen.queryByText('AI Status')).not.toBeInTheDocument()
    expect(screen.queryByText('Review')).not.toBeInTheDocument()
    expect(screen.getByText('I can help organize documents, explain changes, and draft rewrite suggestions.')).toBeInTheDocument()
    const assistantMessage = screen.getByTestId('chat-message-assistant-intro')
    expect(assistantMessage.className).not.toContain('rounded')
    expect(assistantMessage.className).not.toContain('border')
    expect(assistantMessage.className).not.toContain('bg-')
    expect(screen.getByTestId('chat-message-user-question').querySelector('svg')).toBeNull()
    const composer = screen.getByTestId('chat-composer')
    const sendButton = screen.getByTestId('chat-send-button')
    expect(composer).toContainElement(sendButton)
    expect(composer.className).toContain('border')
    expect(within(composer).getByRole('textbox').className).toContain('border-0')
    expect(within(composer).getByRole('textbox').className).toContain('focus-visible:ring-0')
    expect(sendButton.className).toContain('absolute')

    const header = screen.getByLabelText('AI chat window controls')
    expect(header.querySelector('[data-ai-workspace-brand="true"]')).toBeInTheDocument()
    const controls = within(header).getAllByRole('button')
    expect(controls.map((control) => control.getAttribute('aria-label'))).toEqual(['Switch to floating window', 'Minimize'])
  })

  it('restores from the docked minimized rail', () => {
    const layout = createLayout({ minimized: true })

    renderWorkspace(layout)
    fireEvent.click(screen.getByRole('button', { name: 'Restore AI chat' }))

    expect(layout.restore).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('complementary', { name: 'AI chat' })).not.toBeInTheDocument()
  })

  it('switches floating panels back to docked mode', () => {
    const layout = createLayout({ dockMode: 'floating' })

    renderWorkspace(layout)
    expect(screen.getByLabelText('AI chat window controls').querySelector('[data-ai-workspace-brand="true"]')).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Dock to right side' }))

    expect(layout.dock).toHaveBeenCalledTimes(1)
  })

  it('animates floating panels into the minimized orb before minimizing', () => {
    vi.useFakeTimers()
    const layout = createLayout({ dockMode: 'floating' })

    renderWorkspace(layout)
    const controls = within(screen.getByRole('complementary')).getAllByRole('button')

    fireEvent.click(controls[1])

    expect(layout.minimize).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('ai-workspace-floating-morph')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(AI_WORKSPACE_FLOATING_MORPH_MS)
    })

    expect(layout.minimize).toHaveBeenCalledTimes(1)
  })

  it('toggles docked and floating workspace states with Ctrl+Enter', () => {
    const dockedLayout = createLayout()

    renderWorkspace(dockedLayout)
    fireEvent.keyDown(window, { key: 'Enter', ctrlKey: true })

    expect(dockedLayout.minimize).toHaveBeenCalledTimes(1)
    cleanup()

    vi.useFakeTimers()
    const floatingLayout = createLayout({
      dockMode: 'floating',
      minimized: true,
      orbPosition: { x: 900, y: 650 }
    })

    renderWorkspace(floatingLayout)
    fireEvent.keyDown(window, { key: 'Enter', ctrlKey: true })

    expect(floatingLayout.restore).not.toHaveBeenCalled()
    expect(screen.getByTestId('ai-workspace-floating-morph')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(AI_WORKSPACE_FLOATING_MORPH_MS)
    })

    expect(floatingLayout.restore).toHaveBeenCalledTimes(1)
  })

  it('focuses the chat input when Ctrl+Enter restores a minimized docked workspace', () => {
    renderStatefulWorkspace({
      ...defaultAiWorkspaceLayout({ width: 1200, height: 800 }),
      minimized: true
    })

    fireEvent.keyDown(window, { key: 'Enter', ctrlKey: true })

    expect(screen.getByRole('textbox')).toHaveFocus()
  })

  it('focuses the chat input when Ctrl+Enter restores a minimized floating workspace', () => {
    vi.useFakeTimers()
    renderStatefulWorkspace({
      ...defaultAiWorkspaceLayout({ width: 1200, height: 800 }),
      dockMode: 'floating',
      minimized: true,
      orbPosition: { x: 900, y: 650 }
    })

    fireEvent.keyDown(window, { key: 'Enter', ctrlKey: true })

    act(() => {
      vi.advanceTimersByTime(AI_WORKSPACE_FLOATING_MORPH_MS)
    })
    act(() => {
      vi.advanceTimersByTime(32)
    })

    expect(screen.getByRole('textbox')).toHaveFocus()
  })

  it('drags floating panels from the title bar', () => {
    const layout = createLayout({
      dockMode: 'floating',
      floatingRect: { x: 100, y: 80, width: 360, height: 560 }
    })

    renderWorkspace(layout)
    dispatchPointer(screen.getByLabelText('AI chat window controls'), 'pointerdown', 120, 100)
    dispatchPointer(window, 'pointermove', 150, 130)
    dispatchPointer(window, 'pointerup', 150, 130)

    expect(layout.setFloatingRect).toHaveBeenCalledWith({
      x: 130,
      y: 110,
      width: 360,
      height: 560
    })
  })

  it('undocks docked panels when dragged out from the title bar', () => {
    const layout = createLayout({
      dockWidth: 340,
      floatingRect: { x: 640, y: 120, width: 420, height: 560 }
    })

    renderWorkspace(layout)
    const header = screen.getByRole('complementary').querySelector('header')
    expect(header).not.toBeNull()

    dispatchPointer(header as HTMLElement, 'pointerdown', 1000, 40)
    act(() => {
      dispatchPointer(window, 'pointermove', 970, 48)
    })

    const panel = screen.getByTestId('ai-workspace-panel')
    expect(panel.className).toContain('ring-blue-300')
    expect(screen.queryByTestId('ai-workspace-detach-preview')).not.toBeInTheDocument()

    act(() => {
      dispatchPointer(window, 'pointerup', 970, 48)
    })

    expect(layout.setFloatingRect).toHaveBeenCalledWith(expect.objectContaining({
      width: 420,
      height: 560
    }))
    expect(layout.setDockWidth).not.toHaveBeenCalled()
    expect(layout.undock).toHaveBeenCalledTimes(1)
    expect(panel.className).not.toContain('ring-blue-300')
  })

  it('docks floating panels when dragged to the right edge', () => {
    const layout = createLayout({
      dockMode: 'floating',
      floatingRect: { x: 100, y: 80, width: 360, height: 560 }
    })

    renderWorkspace(layout)
    const header = screen.getByRole('complementary').querySelector('header')
    expect(header).not.toBeNull()

    dispatchPointer(header as HTMLElement, 'pointerdown', 120, 100)
    act(() => {
      dispatchPointer(window, 'pointermove', 900, 100)
    })

    const panel = screen.getByTestId('ai-workspace-panel')
    expect(panel.className).toContain('ring-blue-300')
    expect(screen.queryByTestId('ai-workspace-dock-snap-preview')).not.toBeInTheDocument()

    act(() => {
      dispatchPointer(window, 'pointerup', 900, 100)
    })

    expect(layout.setFloatingRect).toHaveBeenCalledWith({
      x: 880,
      y: 80,
      width: 360,
      height: 560
    })
    expect(layout.setDockWidth).not.toHaveBeenCalled()
    expect(layout.dock).toHaveBeenCalledTimes(1)
    expect(panel.className).not.toContain('ring-blue-300')
  })

  it('resizes docked and floating panels with their handles', () => {
    const dockedLayout = createLayout({ dockWidth: 340 })
    renderWorkspace(dockedLayout)

    dispatchPointer(screen.getByLabelText('Resize AI chat sidebar'), 'pointerdown', 400)
    dispatchPointer(window, 'pointermove', 360)
    dispatchPointer(window, 'pointerup', 360)

    expect(dockedLayout.setDockWidth).toHaveBeenCalledWith(380)
    cleanup()

    const floatingLayout = createLayout({
      dockMode: 'floating',
      floatingRect: { x: 100, y: 80, width: 360, height: 560 }
    })
    renderWorkspace(floatingLayout)

    dispatchPointer(screen.getByLabelText('Resize AI chat window'), 'pointerdown', 460, 640)
    dispatchPointer(window, 'pointermove', 500, 690)
    dispatchPointer(window, 'pointerup', 500, 690)

    expect(floatingLayout.setFloatingRect).toHaveBeenCalledWith({
      x: 100,
      y: 80,
      width: 400,
      height: 610
    })
  })

  it('keeps floating minimized orbs draggable before restoring on click', () => {
    const layout = createLayout({
      dockMode: 'floating',
      minimized: true,
      orbPosition: { x: 900, y: 650 }
    })

    renderWorkspace(layout)
    const orb = screen.getByRole('button', { name: 'Restore AI chat' })

    dispatchPointer(orb, 'pointerdown', 910, 660)
    dispatchPointer(window, 'pointermove', 940, 690)
    dispatchPointer(window, 'pointerup', 940, 690)

    expect(layout.setOrbPosition).toHaveBeenCalledWith({ x: 930, y: 680 })

    fireEvent.click(orb)
    expect(layout.restore).not.toHaveBeenCalled()

    vi.useFakeTimers()
    fireEvent.click(orb)
    expect(layout.restore).not.toHaveBeenCalled()
    expect(screen.getByTestId('ai-workspace-floating-morph')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(AI_WORKSPACE_FLOATING_MORPH_MS)
    })

    expect(layout.restore).toHaveBeenCalledTimes(1)
  })
})
