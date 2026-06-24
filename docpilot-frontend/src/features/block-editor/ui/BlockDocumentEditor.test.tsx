import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRef, type ReactElement } from 'react'
import type { Transaction } from '@tiptap/pm/state'
import { EditorView } from '@tiptap/pm/view'
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

const emptyHeadingDocument: BlockDocument = {
  schemaVersion: 'docpilot-block/2',
  blocks: [
    {
      id: 'empty-heading',
      type: 'HEADING',
      attrs: { level: 1 },
      inlines: [],
      children: []
    }
  ],
  metadata: {}
}

const codeBlockDocument: BlockDocument = {
  schemaVersion: 'docpilot-block/2',
  blocks: [
    {
      id: 'code-1',
      type: 'CODE_BLOCK',
      attrs: {
        language: 'javascript',
        text: 'const answer = 42;'
      },
      inlines: [],
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

const headingWithEmptyParagraphDocument: BlockDocument = {
  schemaVersion: 'docpilot-block/2',
  blocks: [
    {
      id: 'heading-before-empty',
      type: 'HEADING',
      attrs: { level: 6 },
      inlines: [
        {
          type: 'TEXT',
          text: 'Heading before empty',
          attrs: {},
          marks: []
        }
      ],
      children: []
    },
    {
      id: 'empty-paragraph-after-heading',
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

const nestedListDocument: BlockDocument = {
  schemaVersion: 'docpilot-block/2',
  blocks: [
    {
      id: 'outer-list',
      type: 'ORDERED_LIST',
      attrs: {},
      inlines: [],
      children: [
        {
          id: 'outer-item',
          type: 'LIST_ITEM',
          attrs: {},
          inlines: [],
          children: [
            {
              id: 'outer-paragraph',
              type: 'PARAGRAPH',
              attrs: {},
              inlines: [
                {
                  type: 'TEXT',
                  text: '第三项',
                  attrs: {},
                  marks: []
                }
              ],
              children: []
            },
            {
              id: 'inner-list',
              type: 'ORDERED_LIST',
              attrs: {},
              inlines: [],
              children: [
                {
                  id: 'inner-item',
                  type: 'LIST_ITEM',
                  attrs: {},
                  inlines: [],
                  children: [
                    {
                      id: 'inner-paragraph',
                      type: 'PARAGRAPH',
                      attrs: {},
                      inlines: [
                        {
                          type: 'TEXT',
                          text: "d'wd'w'qdd'wq1",
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

  it('shows type-specific ghost text for the selected empty heading without saving it', async () => {
    const onSnapshotChange = vi.fn<
      (snapshot: BlockDocumentEditorSnapshot, source: BlockDocumentEditorSnapshotSource) => void
    >()
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="empty-heading-placeholder"
        blockDocument={emptyHeadingDocument}
        onSnapshotChange={onSnapshotChange}
      />
    )

    const editorElement = container.querySelector('.prose-editor') as HTMLElement
    fireEvent.focus(editorElement)

    await waitFor(() => {
      const heading = container.querySelector(blockIdSelector('empty-heading'))
      expect(heading).toHaveClass('docpilot-empty-textblock')
      expect(heading).toHaveAttribute('data-placeholder', 'H1')
    })

    await waitFor(() => {
      const latestSnapshot = latestSnapshotFrom(onSnapshotChange)
      expect(latestSnapshot?.blockDocument.blocks[0]).toEqual(expect.objectContaining({
        attrs: { level: 1 },
        children: [],
        id: 'empty-heading',
        inlines: [],
        type: 'HEADING'
      }))
    })
  })

  it('keeps code block content visible in debug mode', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="code-debug"
        debugMode
        blockDocument={codeBlockDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await waitFor(() => {
      const codeBlock = container.querySelector('[data-block-id="code-1"]')
      expect(codeBlock).toHaveClass('code-block-node')
      expect(codeBlock?.querySelector('.cm-content')).toHaveTextContent('const answer = 42;')
    })
  })

  it('selects a code block when marquee starts from the code gutter', async () => {
    const dispatchSpy = vi.spyOn(EditorView.prototype, 'dispatch')
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="code-marquee"
        blockDocument={codeBlockDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    ensureProseMirrorGeometry(container)

    await waitFor(() => {
      expect(container.querySelector('[data-block-id="code-1"] .cm-gutters')).toBeInTheDocument()
    })

    const surface = container.querySelector('.block-editor-surface') as HTMLElement
    const editorElement = container.querySelector('.prose-editor') as HTMLElement
    const codeBlock = container.querySelector('[data-block-id="code-1"]') as HTMLElement
    const gutters = codeBlock.querySelector('.cm-gutters') as HTMLElement

    setElementRect(surface, rect(0, 0, 900, 600))
    setElementSize(surface, 900, 600)
    setElementRect(editorElement, rect(100, 0, 760, 400))
    setElementRect(codeBlock, rect(100, 40, 760, 120))

    dispatchPointerEvent(gutters, 'pointerdown', {
      button: 0,
      clientX: 122,
      clientY: 62,
      pointerId: 1
    })
    dispatchPointerEvent(window, 'pointermove', {
      clientX: 720,
      clientY: 150,
      pointerId: 1
    })
    await act(async () => {
      await new Promise<void>((resolve) => {
        window.requestAnimationFrame(() => resolve())
      })
    })

    await waitFor(() => {
      expect(codeBlock.closest('.node-codeBlock')).toHaveClass('docpilot-block-selected')
    })

    const callsBeforePointerUp = dispatchSpy.mock.calls.length
    dispatchPointerEvent(window, 'pointerup', {
      clientX: 720,
      clientY: 150,
      pointerId: 1
    })
    await act(async () => {
      await new Promise<void>((resolve) => {
        window.requestAnimationFrame(() => resolve())
      })
    })

    const pointerUpTransactions = dispatchSpy.mock.calls
      .slice(callsBeforePointerUp)
      .map(([transaction]) => transaction as Transaction)
    expect(pointerUpTransactions.some((transaction) => transaction.scrolledIntoView)).toBe(false)
    dispatchSpy.mockRestore()
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

  it('uses a fixed context handle icon while keeping command menu items stateless', async () => {
    window.localStorage.setItem('docpilot.locale', 'en-US')
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="heading-context-icon"
        blockDocument={headingDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'heading1')
    const trigger = blockMenuTrigger('Heading 1 block menu')

    expect(trigger.querySelector('.lucide-list')).toBeInTheDocument()
    expect(trigger.querySelector('.lucide-heading-1')).not.toBeInTheDocument()

    await openBlockMenu('Heading 1 block menu')

    expect(screen.getByRole('button', { name: 'Heading 1' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('menuitem', { name: 'Convert to heading 1' })).not.toHaveAttribute('aria-current')
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

    expect(blockMenuTrigger('列表块菜单')).toBeInTheDocument()
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
    const trigger = blockMenuTrigger('正文块菜单')

    fireEvent.pointerEnter(trigger, {
      pointerType: 'mouse'
    })

    expect(trigger).not.toHaveAttribute('aria-expanded', 'true')

    await waitFor(() => {
      expect(trigger).toHaveAttribute('aria-expanded', 'true')
    }, { timeout: 1600 })

    fireEvent.pointerDown(trigger, {
      button: 0,
      ctrlKey: false,
      pointerType: 'mouse'
    })

    expect(trigger).toHaveAttribute('aria-expanded', 'true')
  })

  it('opens the block menu immediately from a menu button click', async () => {
    window.localStorage.setItem('docpilot.locale', 'en-US')
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="handle-click-menu"
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    const trigger = blockMenuTrigger('Paragraph block menu')

    fireEvent.click(trigger)

    expect(trigger).toHaveAttribute('aria-expanded', 'true')
  })

  it('keeps the drag grip separate from the hover menu trigger', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="handle-grip-menu"
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    const trigger = blockMenuTrigger('正文块菜单')
    const grip = document.querySelector<HTMLElement>('.block-affordance-grip')

    expect(grip).toBeInTheDocument()
    fireEvent.pointerEnter(grip as HTMLElement, {
      pointerType: 'mouse'
    })

    expect(trigger).not.toHaveAttribute('aria-expanded', 'true')
  })

  it('keeps the menu and highlight stable when moving from menu button to drag grip', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="handle-trigger-to-grip"
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    const blockElement = container.querySelector(blockIdSelector('paragraph1')) as HTMLElement
    const handle = hoverVisibleHandle()

    await waitFor(() => {
      expect(blockElement).toHaveClass('docpilot-block-affordance-highlighted')
    })

    const trigger = container.querySelector('.block-affordance-trigger') as HTMLButtonElement
    const grip = document.querySelector<HTMLElement>('.block-affordance-grip')

    expect(grip).toBeInTheDocument()
    fireEvent.pointerEnter(trigger, {
      pointerType: 'mouse'
    })

    await waitFor(() => {
      expect(trigger).toHaveAttribute('aria-expanded', 'true')
    }, { timeout: 1600 })

    fireEvent.pointerLeave(trigger, {
      pointerType: 'mouse',
      relatedTarget: grip
    })
    fireEvent.pointerEnter(grip as HTMLElement, {
      pointerType: 'mouse',
      relatedTarget: trigger
    })

    await waitFor(() => {
      expect(trigger).toHaveAttribute('aria-expanded', 'true')
      expect(blockElement).toHaveClass('docpilot-block-affordance-highlighted')
    })

    fireEvent.pointerLeave(handle, {
      pointerType: 'mouse'
    })
  })

  it('sizes the drag handle container before portal content is measured', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="handle-stable-size"
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    const dragHandle = document.querySelector<HTMLElement>('.block-affordance-drag-handle')

    expect(dragHandle).toHaveStyle({
      height: '28px',
      width: '52px'
    })
    expect(dragHandle).toHaveAttribute('data-affordance-kind', 'context')
  })

  it('keeps the insert handle container sized to the plus button', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="insert-handle-stable-size"
        blockDocument={emptyParagraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'empty-paragraph')
    const dragHandle = document.querySelector<HTMLElement>('.block-affordance-drag-handle')

    expect(dragHandle).toHaveStyle({
      height: '28px',
      width: '28px'
    })
    expect(dragHandle).toHaveAttribute('data-affordance-kind', 'insert')
  })

  it('clears stale block highlights when opening the empty block insert menu', async () => {
    window.localStorage.setItem('docpilot.locale', 'en-US')
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="insert-menu-single-highlight"
        blockDocument={headingWithEmptyParagraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    ensureElementFromPoint()
    ensureProseMirrorGeometry(container)

    const surface = container.querySelector('.block-editor-surface') as HTMLElement
    const editorElement = container.querySelector('.prose-editor') as HTMLElement
    const heading = container.querySelector(blockIdSelector('heading-before-empty')) as HTMLElement
    const emptyParagraph = container.querySelector(blockIdSelector('empty-paragraph-after-heading')) as HTMLElement

    setElementRect(surface, rect(0, 0, 900, 600))
    setElementSize(surface, 900, 600)
    setElementRect(editorElement, rect(100, 0, 760, 400))
    setElementRect(heading, rect(100, 40, 760, 32))
    setElementRect(emptyParagraph, rect(100, 100, 760, 32))

    dispatchPointerEvent(surface, 'pointerdown', {
      button: 0,
      clientX: 10,
      clientY: 44,
      pointerId: 1
    })
    dispatchPointerEvent(window, 'pointermove', {
      clientX: 880,
      clientY: 68,
      pointerId: 1
    })
    await act(async () => {
      await new Promise<void>((resolve) => {
        window.requestAnimationFrame(() => resolve())
      })
    })

    await waitFor(() => {
      expect(heading).toHaveClass('docpilot-block-selected')
    })

    dispatchPointerEvent(window, 'pointerup', {
      clientX: 880,
      clientY: 68,
      pointerId: 1
    })

    hoverBlockHandle(container, 'empty-paragraph-after-heading')
    await waitFor(() => {
      expect(blockMenuTrigger('Insert block')).toBeInTheDocument()
    })

    const handle = hoverVisibleHandle()
    await waitFor(() => {
      expect(heading).not.toHaveClass('docpilot-block-selected')
      expect(heading).not.toHaveClass('docpilot-block-affordance-highlighted')
    })

    await openBlockMenu('Insert block')
    const trigger = blockMenuTrigger('Insert block')

    await waitFor(() => {
      expect(trigger).toHaveAttribute('aria-expanded', 'true')
      expect(emptyParagraph).toHaveClass('docpilot-block-affordance-highlighted')
      expect(heading).not.toHaveClass('docpilot-block-selected')
      expect(heading).not.toHaveClass('docpilot-block-affordance-highlighted')
    })

    fireEvent.pointerLeave(handle, {
      pointerType: 'mouse'
    })
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

  it('keeps the current handle reachable while crossing the left approach corridor', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="handle-approach-corridor"
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    const editorElement = container.querySelector('.prose-editor') as HTMLElement
    const dragHandle = document.querySelector<HTMLElement>('.block-affordance-drag-handle')

    expect(dragHandle).toBeInTheDocument()
    setElementRect(dragHandle as HTMLElement, rect(38, 16, 52, 28))
    expect((dragHandle as HTMLElement).getBoundingClientRect().width).toBe(52)

    fireEvent.mouseMove(editorElement, {
      clientX: 150,
      clientY: 24
    })

    await waitFor(() => {
      expect(dragHandle).toHaveProperty('draggable', false)
    })

    fireEvent.mouseEnter(dragHandle as HTMLElement, {
      clientX: 52,
      clientY: 24
    })

    await waitFor(() => {
      expect(dragHandle).toHaveProperty('draggable', true)
    })
  })

  it('keeps nested list handles on the hovered item instead of the parent item', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="nested-list-handle"
        blockDocument={nestedListDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    ensureElementFromPoint()
    ensureProseMirrorGeometry(container)

    await waitFor(() => {
      expect(container.querySelector(blockIdSelector('inner-item'))).toBeInTheDocument()
    })

    const outerItem = container.querySelector(blockIdSelector('outer-item')) as HTMLElement
    const innerItem = container.querySelector(blockIdSelector('inner-item')) as HTMLElement
    const innerParagraph = container.querySelector(blockIdSelector('inner-paragraph')) as HTMLElement

    setElementRect(outerItem, rect(100, 20, 760, 140))
    setElementRect(innerItem, rect(150, 118, 710, 32))
    setElementRect(innerParagraph, rect(150, 118, 710, 32))

    hoverBlockHandle(container, 'inner-item')

    await waitFor(() => {
      expect(document.querySelector('.block-affordance-trigger')).toBeInTheDocument()
    })

    const handle = hoverVisibleHandle()

    await waitFor(() => {
      expect(innerItem).toHaveClass('docpilot-block-affordance-highlighted')
      expect(outerItem).not.toHaveClass('docpilot-block-affordance-highlighted')
    })

    fireEvent.pointerLeave(handle, {
      pointerType: 'mouse'
    })
  })

  it('delays the block highlight until the handle is hovered briefly', async () => {
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="handle-highlight"
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    const blockElement = container.querySelector(blockIdSelector('paragraph1')) as HTMLElement

    expect(blockElement).not.toHaveClass('docpilot-block-affordance-highlighted')
    const handle = hoverVisibleHandle()
    expect(blockElement).not.toHaveClass('docpilot-block-affordance-highlighted')

    await waitFor(() => {
      expect(blockElement).toHaveClass('docpilot-block-affordance-highlighted')
    })

    fireEvent.pointerLeave(handle, {
      pointerType: 'mouse'
    })

    await waitFor(() => {
      expect(blockElement).not.toHaveClass('docpilot-block-affordance-highlighted')
      expect(container.querySelector('.block-affordance-trigger')).toBeInTheDocument()
    })
  })

  it('moves the delayed block highlight with handle hover instead of snapping back to the cursor block', async () => {
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

    const secondHandle = hoverVisibleHandle()
    await waitFor(() => {
      expect(secondBlock).toHaveClass('docpilot-block-affordance-highlighted')
    })

    fireEvent.pointerLeave(secondHandle, {
      pointerType: 'mouse'
    })
    await waitFor(() => {
      expect(secondBlock).not.toHaveClass('docpilot-block-affordance-highlighted')
    })

    hoverBlockHandle(container, 'paragraph1')

    fireEvent.mouseMove(firstBlock, {
      clientX: 24,
      clientY: 48
    })

    expect(firstBlock).not.toHaveClass('docpilot-block-affordance-highlighted')
    const firstHandle = hoverVisibleHandle()

    await waitFor(() => {
      expect(firstBlock).toHaveClass('docpilot-block-affordance-highlighted')
      expect(secondBlock).not.toHaveClass('docpilot-block-affordance-highlighted')
    })

    fireEvent.pointerLeave(firstHandle, {
      pointerType: 'mouse'
    })
  })

  it('closes an open block menu before activating a different block handle', async () => {
    window.localStorage.setItem('docpilot.locale', 'en-US')
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="handle-open-menu-switch"
        blockDocument={twoParagraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    await revealBlockHandle(container, 'paragraph1')
    const firstBlock = container.querySelector(blockIdSelector('paragraph1')) as HTMLElement
    const secondBlock = container.querySelector(blockIdSelector('paragraph2')) as HTMLElement
    hoverVisibleHandle()
    await openBlockMenu('Paragraph block menu')

    await waitFor(() => {
      expect(screen.getByRole('menuitem', { name: 'Add below' })).toBeInTheDocument()
      expect(firstBlock).toHaveClass('docpilot-block-affordance-highlighted')
    })

    hoverBlockHandle(container, 'paragraph2')

    await waitFor(() => {
      expect(screen.queryByRole('menuitem', { name: 'Add below' })).not.toBeInTheDocument()
      expect(firstBlock).not.toHaveClass('docpilot-block-affordance-highlighted')
    })

    const secondHandle = hoverVisibleHandle()
    await waitFor(() => {
      expect(secondBlock).toHaveClass('docpilot-block-affordance-highlighted')
      expect(firstBlock).not.toHaveClass('docpilot-block-affordance-highlighted')
    })

    fireEvent.pointerLeave(secondHandle, {
      pointerType: 'mouse'
    })
  })

  it('omits clipboard actions and adds a paragraph below without losing block identities or leaving stale highlights', async () => {
    window.localStorage.setItem('docpilot.locale', 'en-US')
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
    await openBlockMenu('Paragraph block menu')
    const originalBlock = container.querySelector(blockIdSelector('paragraph1')) as HTMLElement

    expect(screen.queryByText('Cut')).not.toBeInTheDocument()
    expect(screen.queryByText('Copy')).not.toBeInTheDocument()
    expect(originalBlock).toHaveClass('docpilot-block-affordance-highlighted')

    fireEvent.click(await screen.findByText('Add below'))

    await waitFor(() => {
      const latestSnapshot = latestSnapshotFrom(onSnapshotChange)
      expect(latestSnapshot?.blockDocument.blocks).toHaveLength(2)
      expect(latestSnapshot?.blockDocument.blocks[1]).toEqual(expect.objectContaining({
        type: 'PARAGRAPH'
      }))
      expect(latestSnapshot?.blockDocument.blocks[1]?.id).toEqual(expect.any(String))
      expect(latestSnapshot?.blockDocument.blocks[1]?.id).not.toBe('')
    })

    const latestSnapshot = latestSnapshotFrom(onSnapshotChange)
    const addedBlockId = latestSnapshot?.blockDocument.blocks[1]?.id
    expect(addedBlockId).toEqual(expect.any(String))

    await waitFor(() => {
      expect(screen.queryByRole('menuitem', { name: 'Add below' })).not.toBeInTheDocument()
      expect(originalBlock).not.toHaveClass('docpilot-block-affordance-highlighted')
    })

    await revealBlockHandle(container, addedBlockId as string)
    const addedBlock = container.querySelector(blockIdSelector(addedBlockId as string)) as HTMLElement
    const handle = hoverVisibleHandle()

    await waitFor(() => {
      expect(addedBlock).toHaveClass('docpilot-block-affordance-highlighted')
      expect(originalBlock).not.toHaveClass('docpilot-block-affordance-highlighted')
    })

    fireEvent.pointerLeave(handle, {
      pointerType: 'mouse'
    })
  })

  it('clears the list block menu highlight after adding below', async () => {
    window.localStorage.setItem('docpilot.locale', 'en-US')
    const onSnapshotChange = vi.fn<
      (snapshot: BlockDocumentEditorSnapshot, source: BlockDocumentEditorSnapshotSource) => void
    >()
    const { container } = renderEditor(
      <BlockDocumentEditor
        contentKey="list-add-below-highlight"
        blockDocument={listDocument}
        onSnapshotChange={onSnapshotChange}
      />
    )

    await revealBlockHandle(container, 'item-1')
    hoverVisibleHandle()
    await openBlockMenu('List block menu')

    const listItem = container.querySelector(blockIdSelector('item-1')) as HTMLElement
    await waitFor(() => {
      expect(listItem).toHaveClass('docpilot-block-affordance-highlighted')
    })

    fireEvent.click(await screen.findByRole('menuitem', { name: 'Add below' }))

    await waitFor(() => {
      expect(screen.queryByRole('menuitem', { name: 'Add below' })).not.toBeInTheDocument()
      expect(listItem).not.toHaveClass('docpilot-block-affordance-highlighted')
      expect(highlightedBlocks(container)).toHaveLength(0)
    })

    const latestSnapshot = latestSnapshotFrom(onSnapshotChange)
    const blockIds = latestSnapshot ? blockDocumentIds(latestSnapshot.blockDocument) : []
    expect(blockIds.length).toBeGreaterThan(3)
    expect(new Set(blockIds).size).toBe(blockIds.length)
  })
})

function renderEditor(element: ReactElement) {
  return render(<I18nProvider>{element}</I18nProvider>)
}

async function revealBlockHandle(container: HTMLElement, blockId: string): Promise<void> {
  ensureElementFromPoint()
  ensureProseMirrorGeometry(container)

  await waitFor(() => {
    expect(container.querySelector(blockIdSelector(blockId))).toBeInTheDocument()
  })

  hoverBlockHandle(container, blockId)

  await waitFor(() => {
    expect(document.querySelector('.block-affordance-trigger')).toBeInTheDocument()
  })
}

function hoverBlockHandle(container: HTMLElement, blockId: string): void {
  const blockElement = container.querySelector(blockIdSelector(blockId)) as HTMLElement
  const blockRect = blockElement.getBoundingClientRect()
  setPointTarget(blockElement)
  fireEvent.mouseMove(blockElement, {
    clientX: blockRect.left + Math.min(18, Math.max(6, blockRect.width / 2)),
    clientY: blockRect.top + Math.min(18, Math.max(6, blockRect.height / 2))
  })
}

function hoverVisibleHandle(): HTMLElement {
  const handle = document.querySelector('.block-affordance-layer') as HTMLElement | null
  if (!handle) {
    throw new Error('Unable to find visible block affordance handle')
  }

  fireEvent.pointerEnter(handle, {
    pointerType: 'mouse'
  })
  return handle
}

async function openBlockMenu(name: string): Promise<void> {
  const trigger = blockMenuTrigger(name)
  fireEvent.pointerEnter(trigger, {
    pointerType: 'mouse'
  })

  await waitFor(() => {
    expect(trigger).toHaveAttribute('aria-expanded', 'true')
  }, { timeout: 1600 })
}

function blockMenuTrigger(name: string): HTMLButtonElement {
  const trigger = Array.from(document.querySelectorAll<HTMLButtonElement>('button.block-affordance-trigger'))
    .find((button) => button.getAttribute('aria-label') === name)
  if (!trigger) {
    throw new Error(`Unable to find block menu trigger "${name}"`)
  }
  return trigger
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

function blockDocumentIds(blockDocument: BlockDocument): string[] {
  const ids: string[] = []
  const visit = (blocks: BlockDocument['blocks']) => {
    blocks.forEach((block) => {
      ids.push(block.id)
      visit(block.children)
    })
  }

  visit(blockDocument.blocks)
  return ids
}

function highlightedBlocks(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>('.docpilot-block-affordance-highlighted'))
}

function blockIdSelector(blockId: string): string {
  return `[data-block-id="${blockId.replaceAll('\\', '\\\\').replaceAll('"', '\\"')}"]`
}

function ensureElementFromPoint(): void {
  Object.defineProperty(document, 'elementFromPoint', {
    configurable: true,
    value: () => pointTarget
  })
  Object.defineProperty(document, 'elementsFromPoint', {
    configurable: true,
    value: () => pointTarget ? elementAncestors(pointTarget) : []
  })
}

let pointTarget: Element | null = null

function setPointTarget(target: Element | null): void {
  pointTarget = target
}

function elementAncestors(target: Element): Element[] {
  const elements: Element[] = []
  let current: Element | null = target
  while (current) {
    elements.push(current)
    current = current.parentElement
  }
  return elements
}

function ensureProseMirrorGeometry(container: HTMLElement): void {
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

  const editorElement = container.querySelector('.prose-editor') as HTMLElement | null
  if (editorElement) {
    setElementRect(editorElement, rect(100, 0, 760, 400))
    Array.from(editorElement.querySelectorAll<HTMLElement>('[data-block-id]')).forEach((element, index) => {
      setElementRect(element, rect(100, 20 + (index * 42), 760, 32))
    })
  }
}

function setElementRect(element: HTMLElement, value: DOMRect): void {
  Object.defineProperty(element, 'getBoundingClientRect', {
    configurable: true,
    value: () => value
  })
}

function setElementSize(element: HTMLElement, width: number, height: number): void {
  Object.defineProperty(element, 'clientWidth', {
    configurable: true,
    value: width
  })
  Object.defineProperty(element, 'clientHeight', {
    configurable: true,
    value: height
  })
}

function dispatchPointerEvent(
  target: EventTarget,
  type: string,
  init: {
    button?: number
    clientX?: number
    clientY?: number
    pointerId?: number
  }
): void {
  const event = new Event(type, {
    bubbles: true,
    cancelable: true
  })
  Object.defineProperties(event, {
    button: { configurable: true, value: init.button ?? 0 },
    clientX: { configurable: true, value: init.clientX ?? 0 },
    clientY: { configurable: true, value: init.clientY ?? 0 },
    pointerId: { configurable: true, value: init.pointerId ?? 1 }
  })
  target.dispatchEvent(event)
}

function rect(left: number, top: number, width: number, height: number): DOMRect {
  return {
    x: left,
    y: top,
    width,
    height,
    top,
    right: left + width,
    bottom: top + height,
    left,
    toJSON: () => ({})
  } as DOMRect
}
