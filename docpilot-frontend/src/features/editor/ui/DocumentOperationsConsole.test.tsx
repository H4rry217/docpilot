import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { BlockDocument } from '../../../entities/block/types'
import { I18nProvider } from '../../../shared/i18n'
import {
  DocumentOperationsConsole,
  useDocumentOperationsConsoleState,
  type DocumentOperationsConsoleProps
} from './DocumentOperationsConsole'
import { DocumentOperationComposer } from './DocumentOperationComposer'

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
    },
    {
      id: 'block-2',
      type: 'PARAGRAPH',
      attrs: {},
      inlines: [
        {
          type: 'TEXT',
          text: 'another keyword',
          attrs: {},
          marks: []
        }
      ],
      children: []
    },
    {
      id: 'block-3',
      type: 'PARAGRAPH',
      attrs: {},
      inlines: [
        {
          type: 'TEXT',
          text: 'keyword in the middle',
          attrs: {},
          marks: []
        }
      ],
      children: []
    },
    {
      id: 'block-4',
      type: 'PARAGRAPH',
      attrs: {},
      inlines: [
        {
          type: 'TEXT',
          text: 'last keyword',
          attrs: {},
          marks: []
        }
      ],
      children: []
    }
  ]
}

beforeEach(() => {
  window.localStorage.setItem('docpilot.locale', 'en-US')
  Element.prototype.scrollIntoView = vi.fn()
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
})

function ConsoleHarness({
  getBlockDocument,
  getDocumentVersion,
  onApplyBlockDocument,
  onJumpToBlock
}: Partial<DocumentOperationsConsoleProps>) {
  const controller = useDocumentOperationsConsoleState()

  return (
    <>
      <DocumentOperationsConsole
        controller={controller}
        getBlockDocument={getBlockDocument}
        getDocumentVersion={getDocumentVersion}
        onApplyBlockDocument={onApplyBlockDocument}
        onJumpToBlock={onJumpToBlock}
      />
      <DocumentOperationComposer
        open={controller.composerOpen}
        initialBlockId={controller.composerInitialBlockId}
        getBlockDocument={getBlockDocument}
        getDocumentVersion={getDocumentVersion}
        onApplyBlockDocument={onApplyBlockDocument}
        onOpenChange={controller.setComposerOpen}
      />
    </>
  )
}

function renderConsole(props: Partial<DocumentOperationsConsoleProps> = {}) {
  return render(
    <I18nProvider>
      <ConsoleHarness {...props} />
    </I18nProvider>
  )
}

describe('DocumentOperationsConsole', () => {
  it('renders the operation UI and opens command suggestions only after slash', () => {
    renderConsole()

    expect(screen.getByPlaceholderText('Document operation command')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Document Operations' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Terminal' })).not.toBeInTheDocument()
    expect(screen.queryByText('Terminal 1')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'New terminal' })).not.toBeInTheDocument()
    expect(screen.queryByText('Document operations console ready.')).not.toBeInTheDocument()
    expect(screen.queryByText('docpilot:ops $')).not.toBeInTheDocument()

    const commandInput = screen.getByRole<HTMLInputElement>('textbox', { name: 'Command input' })
    fireEvent.focus(commandInput)

    expect(screen.queryByText('Available operations')).not.toBeInTheDocument()
    expect(screen.queryByText('searchText <text>')).not.toBeInTheDocument()

    const guideButton = screen.getByRole('button', { name: 'Available operations' })
    fireEvent.click(guideButton)

    expect(screen.getByText('Available operations')).toBeInTheDocument()
    expect(screen.getByText('Search text in the document')).toBeInTheDocument()

    fireEvent.click(guideButton)

    expect(screen.queryByText('Available operations')).not.toBeInTheDocument()

    fireEvent.change(commandInput, { target: { value: '/' } })

    expect(screen.getByText('Available operations')).toBeInTheDocument()
    expect(screen.getByText('Search text in the document')).toBeInTheDocument()
    expect(screen.getByText('searchText <text>')).toBeInTheDocument()

    fireEvent.change(commandInput, { target: { value: 'searchText title' } })
    fireEvent.submit(commandInput.closest('form') as HTMLFormElement)

    expect(screen.getByText('Submitted')).toBeInTheDocument()
    expect(screen.getByText('searchText title')).toBeInTheDocument()
    expect(screen.getByText('The current document snapshot is not ready, so the command was only recorded.')).toBeInTheDocument()
  })

  it('autocompletes a short command and shows localized query feedback', async () => {
    const onJumpToBlock = vi.fn()

    renderConsole({
      getBlockDocument: () => testBlockDocument,
      onJumpToBlock
    })

    const commandInput = screen.getByRole<HTMLInputElement>('textbox', { name: 'Command input' })
    fireEvent.focus(commandInput)

    expect(screen.queryByText('Available operations')).not.toBeInTheDocument()

    fireEvent.change(commandInput, { target: { value: '/' } })

    expect(screen.getByText('Available operations')).toBeInTheDocument()
    expect(screen.getByText('Search text in the document')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Choose searchText' }))

    expect(commandInput).toHaveValue('searchText <text>')
    expect(screen.queryByText('{"type":"searchText","text":"keyword","limit":10}')).not.toBeInTheDocument()
    await waitFor(() => {
      expect(screen.queryByText('Available operations')).not.toBeInTheDocument()
    })

    fireEvent.change(commandInput, { target: { value: 'searchText keyword' } })
    fireEvent.submit(commandInput.closest('form') as HTMLFormElement)

    expect(commandInput).toHaveValue('')
    expect(screen.getByText('Submitted')).toBeInTheDocument()
    expect(screen.getByText('Query complete: 4 match(es).')).toBeInTheDocument()
    const blockJumpButton = screen.getByRole('button', { name: 'Jump to block block-1' })
    expect(blockJumpButton).toHaveTextContent(/block-1.*0-7/)
    expect(screen.getByText('keyword text')).toBeInTheDocument()

    fireEvent.click(blockJumpButton)
    expect(onJumpToBlock).toHaveBeenCalledWith('block-1')

    const copyBlockButton = screen.getByRole('button', { name: 'Copy block ID block-1' })
    fireEvent.click(copyBlockButton)
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('block-1')
    expect(screen.getByRole('button', { name: 'Copied block ID block-1' })).toBeInTheDocument()

    const collapseResultsButton = screen.getByRole('button', { name: 'Collapse results' })
    expect(collapseResultsButton).toHaveAttribute('aria-expanded', 'true')

    fireEvent.click(collapseResultsButton)
    expect(screen.getByRole('button', { name: 'Expand results' })).toHaveAttribute('aria-expanded', 'false')

  })

  it('keeps the latest short result entry expanded and collapses older results', () => {
    const { container } = renderConsole({
      getBlockDocument: () => testBlockDocument
    })

    const commandInput = screen.getByRole<HTMLInputElement>('textbox', { name: 'Command input' })
    fireEvent.change(commandInput, { target: { value: 'searchText keyword' } })
    fireEvent.submit(commandInput.closest('form') as HTMLFormElement)

    expect(screen.getByText('keyword text')).toBeInTheDocument()

    fireEvent.change(commandInput, { target: { value: 'getBlockContext block-2' } })
    fireEvent.submit(commandInput.closest('form') as HTMLFormElement)

    const commandEntries = Array.from(container.querySelectorAll<HTMLElement>('.document-operations-entry'))
    expect(commandEntries).toHaveLength(2)
    expect(within(commandEntries[0]).getByRole('button', { name: 'Expand results' }))
      .toHaveAttribute('aria-expanded', 'false')
    expect(within(commandEntries[0]).queryByText('keyword text')).not.toBeInTheDocument()
    expect(within(commandEntries[1]).getByRole('button', { name: 'Collapse results' }))
      .toHaveAttribute('aria-expanded', 'true')
    expect(within(commandEntries[1]).getByText('another keyword')).toBeInTheDocument()
  })

  it('applies document operations to the current block document', () => {
    const onApplyBlockDocument = vi.fn()

    renderConsole({
      getBlockDocument: () => testBlockDocument,
      onApplyBlockDocument
    })

    const commandInput = screen.getByRole<HTMLInputElement>('textbox', { name: 'Command input' })
    fireEvent.change(commandInput, { target: { value: 'insertBlock after block-1 inserted block' } })
    fireEvent.submit(commandInput.closest('form') as HTMLFormElement)

    expect(screen.getByText('Applied: 1 patch(es), 0 diagnostic(s).')).toBeInTheDocument()
    expect(onApplyBlockDocument).toHaveBeenCalledTimes(1)
    expect(onApplyBlockDocument.mock.calls[0][0].blocks[1]).toEqual(
      expect.objectContaining({
        id: 'new-block',
        type: 'PARAGRAPH',
        inlines: expect.arrayContaining([
          expect.objectContaining({ text: 'inserted block' })
        ])
      })
    )
  })

  it('generates a unique id for insertBlock shorthand commands', () => {
    const onApplyBlockDocument = vi.fn()
    const documentWithDefaultId: BlockDocument = {
      ...testBlockDocument,
      blocks: [
        testBlockDocument.blocks[0],
        {
          id: 'new-block',
          type: 'PARAGRAPH',
          attrs: {},
          inlines: [],
          children: []
        },
        ...testBlockDocument.blocks.slice(1)
      ]
    }

    renderConsole({
      getBlockDocument: () => documentWithDefaultId,
      onApplyBlockDocument
    })

    const commandInput = screen.getByRole<HTMLInputElement>('textbox', { name: 'Command input' })
    fireEvent.change(commandInput, { target: { value: 'insertBlock after block-1 inserted block' } })
    fireEvent.submit(commandInput.closest('form') as HTMLFormElement)

    expect(screen.getByText('Applied: 1 patch(es), 0 diagnostic(s).')).toBeInTheDocument()
    expect(onApplyBlockDocument).toHaveBeenCalledTimes(1)
    expect(onApplyBlockDocument.mock.calls[0][0].blocks[1]).toEqual(
      expect.objectContaining({
        id: 'new-block-2',
        type: 'PARAGRAPH'
      })
    )
  })

  it('opens the operation composer from a result block and applies attrs updates', () => {
    const onApplyBlockDocument = vi.fn()

    renderConsole({
      getBlockDocument: () => testBlockDocument,
      onApplyBlockDocument
    })

    const commandInput = screen.getByRole<HTMLInputElement>('textbox', { name: 'Command input' })
    fireEvent.change(commandInput, { target: { value: 'searchText keyword' } })
    fireEvent.submit(commandInput.closest('form') as HTMLFormElement)

    fireEvent.click(screen.getByRole('button', { name: 'Build operation for block block-1' }))

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Document Operation Composer')).toBeInTheDocument()
    expect(screen.getByLabelText('Target blockId')).toHaveValue('block-1')

    fireEvent.change(screen.getByLabelText('Attrs JSON'), {
      target: { value: '{ "tone": "note" }' }
    })

    fireEvent.click(screen.getByRole('button', { name: 'Preview' }))
    expect(screen.getByText('Preview complete: 1 patch(es), 0 diagnostic(s).')).toBeInTheDocument()
    expect(onApplyBlockDocument).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Apply' }))
    expect(screen.getByText('Applied: 1 patch(es), 0 diagnostic(s).')).toBeInTheDocument()
    expect(onApplyBlockDocument).toHaveBeenCalledTimes(1)
    expect(onApplyBlockDocument.mock.calls[0][0].blocks[0].attrs).toEqual({ tone: 'note' })
  })

  it('keeps invalid composer JSON from applying', () => {
    const onApplyBlockDocument = vi.fn()

    renderConsole({
      getBlockDocument: () => testBlockDocument,
      onApplyBlockDocument
    })

    fireEvent.change(screen.getByRole<HTMLInputElement>('textbox', { name: 'Command input' }), {
      target: { value: 'searchText keyword' }
    })
    fireEvent.submit(screen.getByRole<HTMLInputElement>('textbox', { name: 'Command input' }).closest('form') as HTMLFormElement)
    fireEvent.click(screen.getByRole('button', { name: 'Build operation for block block-1' }))
    fireEvent.change(screen.getByLabelText('Operation'), {
      target: { value: 'updateBlockAttrs' }
    })
    fireEvent.change(screen.getByLabelText('Target blockId'), {
      target: { value: 'block-1' }
    })
    fireEvent.change(screen.getByLabelText('Attrs JSON'), {
      target: { value: '{' }
    })

    fireEvent.click(screen.getByRole('button', { name: 'Apply' }))

    expect(screen.getByText('JSON could not be parsed.')).toBeInTheDocument()
    expect(onApplyBlockDocument).not.toHaveBeenCalled()
  })

  it('filters suggestions after slash and inserts an editable parameter template with Tab navigation', async () => {
    renderConsole()

    const commandInput = screen.getByRole<HTMLInputElement>('textbox', { name: 'Command input' })
    fireEvent.focus(commandInput)
    fireEvent.change(commandInput, { target: { value: 'replace' } })

    expect(screen.queryByText('replaceText')).not.toBeInTheDocument()
    expect(screen.queryByText('replaceBlock')).not.toBeInTheDocument()

    fireEvent.change(commandInput, { target: { value: '/replace' } })

    expect(screen.getByText('replaceText')).toBeInTheDocument()
    expect(screen.getByText('replaceBlock')).toBeInTheDocument()

    const scrollIntoView = vi.mocked(Element.prototype.scrollIntoView)
    scrollIntoView.mockClear()
    fireEvent.keyDown(commandInput, { key: 'ArrowDown' })

    expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' })

    fireEvent.keyDown(commandInput, { key: 'ArrowUp' })
    fireEvent.keyDown(commandInput, { key: 'Tab' })

    expect(commandInput).toHaveValue('replaceText <blockId> <old> => <new>')

    await waitFor(() => {
      expect(commandInput.selectionStart).toBe('replaceText '.length)
      expect(commandInput.selectionEnd).toBe('replaceText <blockId>'.length)
    })

    fireEvent.keyDown(commandInput, { key: 'Tab' })

    expect(commandInput.selectionStart).toBe('replaceText <blockId> '.length)
    expect(commandInput.selectionEnd).toBe('replaceText <blockId> <old>'.length)
  })
})
