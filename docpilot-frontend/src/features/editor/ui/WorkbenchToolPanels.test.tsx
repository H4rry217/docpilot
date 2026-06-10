import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { BlockDocument } from '../../../entities/block/types'
import { I18nProvider } from '../../../shared/i18n'
import {
  TOOL_PANEL_LAYOUT_STORAGE_KEY,
  defaultToolPanelLayout,
  useToolPanelLayout,
  type ToolPanelLayoutState,
  type UseToolPanelLayoutResult
} from '../model/toolPanelLayout'
import { useDocumentOperationsConsoleState } from './DocumentOperationsConsole'
import { WorkbenchToolPanels } from './WorkbenchToolPanels'

const testBlockDocument: BlockDocument = {
  schemaVersion: 'docpilot-block/2',
  metadata: {},
  blocks: [
    {
      id: 'block-1',
      type: 'PARAGRAPH',
      attrs: {},
      inlines: [
        {
          type: 'TEXT',
          text: 'keyword text',
          attrs: {},
          marks: []
        }
      ],
      children: []
    }
  ]
}

function LiveWorkbenchHarness({
  getBlockDocument,
  onApplyBlockDocument
}: {
  getBlockDocument?: () => BlockDocument | null | undefined
  onApplyBlockDocument?: (blockDocument: BlockDocument) => void
}) {
  const layout = useToolPanelLayout()
  const consoleController = useDocumentOperationsConsoleState()

  return (
    <WorkbenchToolPanels
      consoleController={consoleController}
      getBlockDocument={getBlockDocument}
      getDocumentVersion={() => 'v1'}
      layout={layout}
      onApplyBlockDocument={onApplyBlockDocument}
    />
  )
}

function renderLiveWorkbench(props: Parameters<typeof LiveWorkbenchHarness>[0] = {}) {
  return render(
    <I18nProvider>
      <LiveWorkbenchHarness {...props} />
    </I18nProvider>
  )
}

function FakeWorkbenchHarness({
  layout
}: {
  layout: UseToolPanelLayoutResult
}) {
  const consoleController = useDocumentOperationsConsoleState()

  return (
    <WorkbenchToolPanels
      consoleController={consoleController}
      getBlockDocument={() => testBlockDocument}
      getDocumentVersion={() => 'v1'}
      layout={layout}
    />
  )
}

function renderFakeWorkbench(layout: UseToolPanelLayoutResult) {
  return render(
    <I18nProvider>
      <FakeWorkbenchHarness layout={layout} />
    </I18nProvider>
  )
}

function createLayout(overrides: Partial<ToolPanelLayoutState> = {}): UseToolPanelLayoutResult {
  const state = {
    ...defaultToolPanelLayout({ width: 1200, height: 800 }),
    ...overrides,
    placements: {
      ...defaultToolPanelLayout({ width: 1200, height: 800 }).placements,
      ...overrides.placements
    },
    floatingRects: {
      ...defaultToolPanelLayout({ width: 1200, height: 800 }).floatingRects,
      ...overrides.floatingRects
    }
  }

  return {
    state,
    dockPanel: vi.fn(),
    floatPanel: vi.fn(),
    toggleBottomCollapsed: vi.fn(),
    setBottomHeight: vi.fn(),
    setFloatingRect: vi.fn()
  }
}

function dispatchPointerEvent(
  target: Element | Node | Window | Document,
  type: string,
  init: { clientX?: number; clientY?: number } = {}
) {
  const event = new Event(type, { bubbles: true, cancelable: true })
  Object.defineProperty(event, 'clientX', { configurable: true, value: init.clientX ?? 0 })
  Object.defineProperty(event, 'clientY', { configurable: true, value: init.clientY ?? 0 })
  fireEvent(target, event)
}

beforeEach(() => {
  window.localStorage.setItem('docpilot.locale', 'en-US')
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: {
      writeText: vi.fn()
    }
  })
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  window.localStorage.removeItem('docpilot.locale')
  window.localStorage.removeItem(TOOL_PANEL_LAYOUT_STORAGE_KEY)
  document.body.className = ''
})

describe('WorkbenchToolPanels', () => {
  it('renders document operations docked at the bottom by default', () => {
    renderLiveWorkbench()

    expect(screen.getByRole('region', { name: 'Document operations panel' })).toBeInTheDocument()
    expect(screen.getByText('Document Operations')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open as floating window' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Collapse operations panel' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Resize operations panel' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Dock to bottom' })).not.toBeInTheDocument()
  })

  it('floats and docks the panel while preserving command history', () => {
    renderLiveWorkbench({ getBlockDocument: () => testBlockDocument })

    const commandInput = screen.getByRole<HTMLInputElement>('textbox', { name: 'Command input' })
    fireEvent.change(commandInput, { target: { value: 'searchText keyword' } })
    fireEvent.submit(commandInput.closest('form') as HTMLFormElement)
    expect(screen.getByText('Query complete: 1 match(es).')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Open as floating window' }))
    expect(screen.queryByRole('button', { name: 'Resize operations panel' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Dock to bottom' })).toBeInTheDocument()
    expect(screen.getByText('searchText keyword')).toBeInTheDocument()
    expect(screen.getByText('keyword text')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Dock to bottom' }))
    expect(screen.getByRole('button', { name: 'Open as floating window' })).toBeInTheDocument()
    expect(screen.getByText('searchText keyword')).toBeInTheDocument()
  })

  it('runs document operations from the floating window', () => {
    const onApplyBlockDocument = vi.fn()
    renderLiveWorkbench({
      getBlockDocument: () => testBlockDocument,
      onApplyBlockDocument
    })

    fireEvent.click(screen.getByRole('button', { name: 'Open as floating window' }))
    const commandInput = screen.getByRole<HTMLInputElement>('textbox', { name: 'Command input' })
    fireEvent.change(commandInput, { target: { value: 'insertBlock after block-1 inserted block' } })
    fireEvent.submit(commandInput.closest('form') as HTMLFormElement)

    expect(screen.getByText('Applied: 1 patch(es), 0 diagnostic(s).')).toBeInTheDocument()
    expect(onApplyBlockDocument).toHaveBeenCalledTimes(1)
  })

  it('opens the operation composer from the panel title bar', () => {
    renderLiveWorkbench({ getBlockDocument: () => testBlockDocument })

    fireEvent.click(screen.getByRole('button', { name: 'Open operation composer' }))

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Document Operation Composer')).toBeInTheDocument()
  })

  it('shows block-type-specific composer fields for structured block templates', () => {
    renderLiveWorkbench({ getBlockDocument: () => testBlockDocument })

    fireEvent.click(screen.getByRole('button', { name: 'Open operation composer' }))
    fireEvent.change(screen.getByLabelText('Block type'), {
      target: { value: 'TABLE' }
    })

    expect(screen.queryByLabelText('Text')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Rows')).toBeInTheDocument()
    expect(screen.getByLabelText('Columns')).toBeInTheDocument()
    expect(screen.getByLabelText('Header row')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Rows'), { target: { value: '3' } })
    fireEvent.change(screen.getByLabelText('Columns'), { target: { value: '3' } })
    const blockJson = screen.getByLabelText<HTMLTextAreaElement>('Block JSON')

    expect(blockJson.value).toContain('"type": "TABLE"')
    expect(blockJson.value).toContain('new-block-cell-3-3')
    expect(blockJson.value).toContain('"header": true')

    fireEvent.change(screen.getByLabelText('Block type'), {
      target: { value: 'CODE_BLOCK' }
    })

    expect(screen.getByLabelText('Code language')).toBeInTheDocument()
    expect(screen.getByLabelText('Code')).toBeInTheDocument()
    expect(screen.queryByLabelText('Rows')).not.toBeInTheDocument()
  })

  it('allows editing a canonical raw block type in the composer', () => {
    renderLiveWorkbench({ getBlockDocument: () => testBlockDocument })

    fireEvent.click(screen.getByRole('button', { name: 'Open operation composer' }))
    fireEvent.change(screen.getByLabelText('Block type'), {
      target: { value: '__RAW_BLOCK_TYPE__' }
    })

    const rawTypeInput = screen.getByLabelText<HTMLSelectElement>('Raw block type')
    expect(rawTypeInput.value).toBe('PARAGRAPH')

    fireEvent.change(rawTypeInput, { target: { value: 'EXTENSION_BLOCK' } })
    const blockJson = screen.getByLabelText<HTMLTextAreaElement>('Block JSON')

    expect(blockJson.value).toContain('"type": "EXTENSION_BLOCK"')
    expect(screen.queryByLabelText('Text')).not.toBeInTheDocument()
  })

  it('routes bottom resize, floating drag, and floating resize through layout actions', () => {
    const bottomLayout = createLayout({ bottomHeight: 300 })
    const { unmount } = renderFakeWorkbench(bottomLayout)

    dispatchPointerEvent(screen.getByRole('button', { name: 'Resize operations panel' }), 'pointerdown', { clientY: 300 })
    dispatchPointerEvent(window, 'pointermove', { clientY: 250 })
    expect(bottomLayout.setBottomHeight).toHaveBeenCalledWith(350)
    dispatchPointerEvent(window, 'pointerup')
    unmount()

    const floatingLayout = createLayout({
      activeBottomPanelId: null,
      placements: { documentOperations: 'floating' },
      floatingRects: {
        documentOperations: { x: 100, y: 80, width: 720, height: 420 }
      }
    })
    renderFakeWorkbench(floatingLayout)

    const floatingPanel = screen.getByRole('region', { name: 'Document operations panel' })
    const header = floatingPanel.querySelector('header') as HTMLElement
    dispatchPointerEvent(header, 'pointerdown', { clientX: 200, clientY: 120 })
    dispatchPointerEvent(window, 'pointermove', { clientX: 240, clientY: 150 })
    expect(floatingLayout.setFloatingRect).toHaveBeenCalledWith('documentOperations', {
      x: 140,
      y: 110,
      width: 720,
      height: 420
    })
    dispatchPointerEvent(window, 'pointerup')

    dispatchPointerEvent(screen.getByRole('button', { name: 'Resize floating window' }), 'pointerdown', { clientX: 820, clientY: 500 })
    dispatchPointerEvent(window, 'pointermove', { clientX: 860, clientY: 540 })
    expect(floatingLayout.setFloatingRect).toHaveBeenCalledWith('documentOperations', {
      x: 100,
      y: 80,
      width: 760,
      height: 460
    })
    dispatchPointerEvent(window, 'pointerup')
  })
})
