import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRef, type ReactElement } from 'react'
import type { BlockDocument } from '@/entities/block/types'
import { I18nProvider } from '@/shared/i18n'
import {
  BlockDocumentEditor,
  type BlockDocumentEditorHandle,
  type BlockDocumentEditorSnapshot,
  type BlockDocumentEditorSnapshotSource
} from '..'

const paragraphDocument: BlockDocument = {
  schemaVersion: 'docpilot-block/2',
  blocks: [
    {
      id: 'paragraph1',
      type: 'PARAGRAPH',
      attrs: {},
      inlines: [
        {
          type: 'TEXT',
          text: 'Hello editor',
          attrs: {},
          marks: []
        }
      ],
      children: []
    }
  ],
  metadata: {}
}

const twoParagraphDocument: BlockDocument = {
  schemaVersion: 'docpilot-block/2',
  blocks: [
    {
      id: 'paragraph1',
      type: 'PARAGRAPH',
      attrs: {},
      inlines: [
        {
          type: 'TEXT',
          text: 'First editor',
          attrs: {},
          marks: []
        }
      ],
      children: []
    },
    {
      id: 'paragraph2',
      type: 'PARAGRAPH',
      attrs: {},
      inlines: [
        {
          type: 'TEXT',
          text: 'Second editor',
          attrs: {},
          marks: []
        }
      ],
      children: []
    }
  ],
  metadata: {}
}

const headingDocument: BlockDocument = {
  schemaVersion: 'docpilot-block/2',
  blocks: [
    {
      id: 'heading1',
      type: 'HEADING',
      attrs: { level: 1 },
      inlines: [
        {
          type: 'TEXT',
          text: 'Heading block',
          attrs: {},
          marks: []
        }
      ],
      children: []
    }
  ],
  metadata: {}
}

const emptyParagraphDocument: BlockDocument = {
  schemaVersion: 'docpilot-block/2',
  blocks: [
    {
      id: 'empty-paragraph',
      type: 'PARAGRAPH',
      attrs: {},
      inlines: [],
      children: []
    }
  ],
  metadata: {}
}

const listDocument: BlockDocument = {
  schemaVersion: 'docpilot-block/2',
  blocks: [
    {
      id: 'list-1',
      type: 'BULLET_LIST',
      attrs: {},
      inlines: [],
      children: [
        {
          id: 'item-1',
          type: 'LIST_ITEM',
          attrs: {},
          inlines: [],
          children: [
            {
              id: 'item-paragraph-1',
              type: 'PARAGRAPH',
              attrs: {},
              inlines: [
                {
                  type: 'TEXT',
                  text: 'Nested task',
                  attrs: {},
                  marks: []
                }
              ],
              children: []
            }
          ]
        }
      ]
    }
  ],
  metadata: {}
}

beforeEach(() => {
  window.localStorage.removeItem('docpilot.locale')
})

afterEach(() => {
  cleanup()
})

describe('BlockDocumentEditor', () => {
  it('loads a block document and emits an equivalent snapshot', async () => {
    const onSnapshotChange = vi.fn<
      (snapshot: BlockDocumentEditorSnapshot, source: BlockDocumentEditorSnapshotSource) => void
    >()

    renderEditor(
      <BlockDocumentEditor
        contentKey="document-1"
        blockDocument={paragraphDocument}
        onSnapshotChange={onSnapshotChange}
      />
    )

    await waitFor(() => {
      expect(onSnapshotChange).toHaveBeenCalledWith(
        expect.objectContaining({
          blockDocument: expect.objectContaining({
            schemaVersion: 'docpilot-block/2',
            blocks: expect.arrayContaining([
              expect.objectContaining({
                id: 'paragraph1',
                type: 'PARAGRAPH',
                inlines: expect.arrayContaining([
                  expect.objectContaining({ text: 'Hello editor' })
                ])
              })
            ])
          })
        }),
        'load'
      )
    })
  })

  it('keeps debug mode scoped to the block editor surface', () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="document-1"
        debugMode
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    expect(container.querySelector('.block-editor-surface')).toHaveClass('is-debug-mode')
  })

  it('applies an external block document through the editor handle', async () => {
    const editorRef = createRef<BlockDocumentEditorHandle>()
    const onSnapshotChange = vi.fn<
      (snapshot: BlockDocumentEditorSnapshot, source: BlockDocumentEditorSnapshotSource) => void
    >()
    const nextDocument: BlockDocument = {
      ...paragraphDocument,
      blocks: [
        {
          ...paragraphDocument.blocks[0],
          inlines: [
            {
              type: 'TEXT',
              text: 'Programmatic update',
              attrs: {},
              marks: []
            }
          ]
        }
      ]
    }

    renderEditor(
      <BlockDocumentEditor
        ref={editorRef}
        contentKey="document-1"
        blockDocument={paragraphDocument}
        onSnapshotChange={onSnapshotChange}
      />
    )

    await waitFor(() => {
      expect(editorRef.current?.getSnapshot()).not.toBeNull()
    })

    act(() => {
      editorRef.current?.setBlockDocument(nextDocument)
    })

    await waitFor(() => {
      expect(onSnapshotChange).toHaveBeenCalledWith(
        expect.objectContaining({
          blockDocument: expect.objectContaining({
            blocks: expect.arrayContaining([
              expect.objectContaining({
                id: 'paragraph1',
                inlines: expect.arrayContaining([
                  expect.objectContaining({ text: 'Programmatic update' })
                ])
              })
            ])
          })
        }),
        'programmatic'
      )
    })
  })

  it('opens the empty block insert menu and inserts a heading', async () => {
    const onSnapshotChange = vi.fn<
      (snapshot: BlockDocumentEditorSnapshot, source: BlockDocumentEditorSnapshotSource) => void
    >()
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="empty-document"
        blockDocument={emptyParagraphDocument}
        onSnapshotChange={onSnapshotChange}
      />
    )

    await revealBlockHandle(container, 'empty-paragraph')
    await openBlockMenu('插入块')
    fireEvent.click(await screen.findByText('一级标题'))

    await waitFor(() => {
      const latestSnapshot = latestSnapshotFrom(onSnapshotChange)
      expect(latestSnapshot?.blockDocument.blocks[0]).toEqual(expect.objectContaining({
        type: 'HEADING'
      }))
    })
  })

  it('opens the paragraph context menu and converts the block type', async () => {
    const onSnapshotChange = vi.fn<
      (snapshot: BlockDocumentEditorSnapshot, source: BlockDocumentEditorSnapshotSource) => void
    >()
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="paragraph-context"
        blockDocument={paragraphDocument}
        onSnapshotChange={onSnapshotChange}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    await openBlockMenu('正文块菜单')

    expect(await screen.findByText('正文')).toBeInTheDocument()
    expect(screen.getByText('转换为一级标题')).toBeInTheDocument()

    fireEvent.click(screen.getByText('转换为一级标题'))

    await waitFor(() => {
      const latestSnapshot = latestSnapshotFrom(onSnapshotChange)
      expect(latestSnapshot?.blockDocument.blocks[0]).toEqual(expect.objectContaining({
        type: 'HEADING'
      }))
    })
  })

  it('localizes block handle menus in English', async () => {
    window.localStorage.setItem('docpilot.locale', 'en-US')
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="paragraph-context-en"
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    await openBlockMenu('Paragraph block menu')

    expect(await screen.findByText('Paragraph')).toBeInTheDocument()
    expect(screen.getByText('Convert to heading 1')).toBeInTheDocument()
    expect(screen.queryByText('Cut')).not.toBeInTheDocument()
    expect(screen.queryByText('Copy')).not.toBeInTheDocument()
    expect(screen.getByText('Add below')).toBeInTheDocument()
  })

  it('uses a fixed context handle icon while highlighting the current block type in the menu', async () => {
    window.localStorage.setItem('docpilot.locale', 'en-US')
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="heading-context-icon"
        blockDocument={headingDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'heading1')
    const trigger = screen.getByRole('button', { name: 'Heading 1 block menu' })

    expect(trigger.querySelector('.lucide-list')).toBeInTheDocument()
    expect(trigger.querySelector('.lucide-heading-1')).not.toBeInTheDocument()

    await openBlockMenu('Heading 1 block menu')

    expect(screen.getByRole('button', { name: 'Heading 1' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('labels list item handles as list context actions', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="list-context"
        blockDocument={listDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'item-1')

    expect(screen.getByRole('button', { name: '列表块菜单' })).toBeInTheDocument()
  })

  it('keeps the block handle visible when the pointer moves onto it', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="handle-hover"
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    const trigger = container.querySelector('.block-affordance-trigger') as HTMLElement

    fireEvent.pointerMove(trigger, {
      clientX: 16,
      clientY: 16
    })

    expect(container.querySelector('.block-affordance-trigger')).toBe(trigger)
  })

  it('opens the block menu from handle hover without toggling closed on pointer down', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="handle-hover-menu"
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    const trigger = screen.getByRole('button', { name: '正文块菜单' })

    fireEvent.pointerEnter(trigger, {
      pointerType: 'mouse'
    })

    await waitFor(() => {
      expect(trigger).toHaveAttribute('aria-expanded', 'true')
    })

    fireEvent.pointerDown(trigger, {
      button: 0,
      ctrlKey: false,
      pointerType: 'mouse'
    })

    expect(trigger).toHaveAttribute('aria-expanded', 'true')
  })

  it('keeps the block handle visible while crossing the gap before it', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="handle-gap-hover"
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    const surface = container.querySelector('.block-editor-surface') as HTMLElement
    const trigger = container.querySelector('.block-affordance-trigger') as HTMLElement

    surface.dispatchEvent(new MouseEvent('pointermove', {
      bubbles: true,
      clientX: 32,
      clientY: 14
    }))

    expect(container.querySelector('.block-affordance-trigger')).toBe(trigger)
  })

  it('renders a matching block highlight while the handle is visible', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="handle-highlight"
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    const surface = container.querySelector('.block-editor-surface') as HTMLElement
    const blockElement = container.querySelector(blockIdSelector('paragraph1')) as HTMLElement

    await waitFor(() => {
      expect(blockElement).toHaveClass('docpilot-block-affordance-highlighted')
    })

    surface.dispatchEvent(new MouseEvent('pointerleave', {
      bubbles: false
    }))

    await waitFor(() => {
      expect(blockElement).toHaveClass('docpilot-block-affordance-highlighted')
      expect(container.querySelector('.block-affordance-trigger')).toBeInTheDocument()
    })
  })

  it('moves the block highlight with hover instead of snapping back to the cursor block', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="handle-highlight-hover-switch"
        blockDocument={twoParagraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph2')
    const firstBlock = container.querySelector(blockIdSelector('paragraph1')) as HTMLElement
    const secondBlock = container.querySelector(blockIdSelector('paragraph2')) as HTMLElement

    await waitFor(() => {
      expect(secondBlock).toHaveClass('docpilot-block-affordance-highlighted')
    })

    fireEvent.pointerMove(firstBlock, {
      clientX: 24,
      clientY: 48
    })

    await waitFor(() => {
      expect(firstBlock).toHaveClass('docpilot-block-affordance-highlighted')
      expect(secondBlock).not.toHaveClass('docpilot-block-affordance-highlighted')
    })
  })

  it('omits clipboard actions and adds a paragraph below without losing block identities', async () => {
    const onSnapshotChange = vi.fn<
      (snapshot: BlockDocumentEditorSnapshot, source: BlockDocumentEditorSnapshotSource) => void
    >()
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="paragraph-actions"
        blockDocument={paragraphDocument}
        onSnapshotChange={onSnapshotChange}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    await openBlockMenu('正文块菜单')
    expect(screen.queryByText('剪切')).not.toBeInTheDocument()
    expect(screen.queryByText('复制')).not.toBeInTheDocument()
    fireEvent.click(await screen.findByText('在下方添加'))

    await waitFor(() => {
      const latestSnapshot = latestSnapshotFrom(onSnapshotChange)
      expect(latestSnapshot?.blockDocument.blocks).toHaveLength(2)
      expect(latestSnapshot?.blockDocument.blocks[1]).toEqual(expect.objectContaining({
        type: 'PARAGRAPH'
      }))
      expect(latestSnapshot?.blockDocument.blocks[1]?.id).toEqual(expect.any(String))
      expect(latestSnapshot?.blockDocument.blocks[1]?.id).not.toBe('')
    })
  })
})

function renderEditor(element: ReactElement) {
  return render(<I18nProvider>{element}</I18nProvider>)
}

async function revealBlockHandle(container: HTMLElement, blockId: string): Promise<void> {
  ensureElementFromPoint()
  ensureProseMirrorGeometry()

  await waitFor(() => {
    expect(container.querySelector(blockIdSelector(blockId))).toBeInTheDocument()
  })

  const blockElement = container.querySelector(blockIdSelector(blockId)) as HTMLElement
  fireEvent.pointerMove(blockElement, {
    clientX: 24,
    clientY: 24
  })

  await waitFor(() => {
    expect(container.querySelector('.block-affordance-trigger')).toBeInTheDocument()
  })
}

async function openBlockMenu(name: string): Promise<void> {
  const trigger = screen.getByRole('button', { name })
  fireEvent.pointerEnter(trigger, {
    pointerType: 'mouse'
  })

  await waitFor(() => {
    expect(trigger).toHaveAttribute('aria-expanded', 'true')
  })
}

function latestSnapshotFrom(
  onSnapshotChange: {
    mock: {
      calls: Array<[BlockDocumentEditorSnapshot, BlockDocumentEditorSnapshotSource]>
    }
  }
): BlockDocumentEditorSnapshot | null {
  const call = onSnapshotChange.mock.calls.at(-1)
  return call?.[0] ?? null
}

function blockIdSelector(blockId: string): string {
  return `[data-block-id="${blockId.replaceAll('\\', '\\\\').replaceAll('"', '\\"')}"]`
}

function ensureElementFromPoint(): void {
  if (typeof document.elementFromPoint === 'function') return

  Object.defineProperty(document, 'elementFromPoint', {
    configurable: true,
    value: () => null
  })
}

function ensureProseMirrorGeometry(): void {
  const zeroRect = {
    x: 0,
    y: 0,
    width: 0,
    height: 0,
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    toJSON: () => ({})
  } as DOMRect
  const getClientRects = () => {
    const rects = [] as unknown as DOMRectList & { item: (index: number) => DOMRect | null }
    rects.item = () => null
    return rects
  }
  const getBoundingClientRect = () => zeroRect
  const rangePrototype = Range.prototype as unknown as {
    getClientRects?: () => DOMRectList
    getBoundingClientRect?: () => DOMRect
  }
  const textPrototype = Text.prototype as unknown as {
    getClientRects?: () => DOMRectList
    getBoundingClientRect?: () => DOMRect
  }

  if (typeof rangePrototype.getClientRects !== 'function') {
    Object.defineProperty(Range.prototype, 'getClientRects', {
      configurable: true,
      value: getClientRects
    })
  }

  if (typeof rangePrototype.getBoundingClientRect !== 'function') {
    Object.defineProperty(Range.prototype, 'getBoundingClientRect', {
      configurable: true,
      value: getBoundingClientRect
    })
  }

  if (typeof textPrototype.getClientRects !== 'function') {
    Object.defineProperty(Text.prototype, 'getClientRects', {
      configurable: true,
      value: getClientRects
    })
  }

  if (typeof textPrototype.getBoundingClientRect !== 'function') {
    Object.defineProperty(Text.prototype, 'getBoundingClientRect', {
      configurable: true,
      value: getBoundingClientRect
    })
  }
}
