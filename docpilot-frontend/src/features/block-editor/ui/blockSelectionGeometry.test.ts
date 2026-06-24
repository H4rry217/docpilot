import { Editor } from '@tiptap/core'
import { describe, expect, it } from 'vitest'
import { editorExtensions } from '../model/extensions'
import {
  blockMarqueeStartModeForTarget,
  blockSelectionDecorationsFromTargets,
  blockSelectionSignature,
  clampPointToRect,
  eventTargetElement,
  hasTableBlockTarget,
  isInteractiveSelectionTarget,
  isSelectableBlockElement,
  isPastDragStartDistance,
  rectFromPoints,
  rectsOverlap,
  selectableBlockTargets,
  shouldRenderBlockOverlay,
  targetFromElement,
  visualBlockSelectionTargets,
  type SelectableBlockTarget
} from './blockSelectionGeometry'

function target(element: HTMLElement, id: string): SelectableBlockTarget {
  return {
    element,
    hitRect: { left: 0, top: 0, right: 100, bottom: 100 },
    id,
    rect: { left: 0, top: 0, right: 100, bottom: 100 },
    selectionLeft: 0,
    selectionTop: 0,
    selectionWidth: 100,
    selectionHeight: 20
  }
}

function setRect(element: HTMLElement, rect: Pick<DOMRect, 'bottom' | 'height' | 'left' | 'right' | 'top' | 'width'>): void {
  element.getBoundingClientRect = () => ({
    ...rect,
    x: rect.left,
    y: rect.top,
    toJSON: () => rect
  }) as DOMRect
}

describe('block selection geometry', () => {
  it('builds normalized rectangles and detects overlap', () => {
    expect(rectFromPoints({ x: 10, y: 30 }, { x: 2, y: 4 })).toEqual({
      left: 2,
      top: 4,
      right: 10,
      bottom: 30
    })
    expect(rectsOverlap(
      { left: 0, top: 0, right: 10, bottom: 10 },
      { left: 10, top: 10, right: 20, bottom: 20 }
    )).toBe(true)
    expect(rectsOverlap(
      { left: 0, top: 0, right: 9, bottom: 9 },
      { left: 10, top: 10, right: 20, bottom: 20 }
    )).toBe(false)
  })

  it('clamps pointer positions to a local rectangle', () => {
    expect(clampPointToRect(
      { x: -20, y: 120 },
      { left: 0, top: 10, right: 100, bottom: 80 }
    )).toEqual({ x: 0, y: 80 })
    expect(clampPointToRect(
      { x: 40, y: 30 },
      { left: 0, top: 10, right: 100, bottom: 80 }
    )).toEqual({ x: 40, y: 30 })
  })

  it('uses the drag threshold before starting marquee selection', () => {
    expect(isPastDragStartDistance({ x: 0, y: 0 }, { x: 3, y: 4 })).toBe(false)
    expect(isPastDragStartDistance({ x: 0, y: 0 }, { x: 6, y: 0 })).toBe(true)
  })

  it('resolves text event targets to their parent element for marquee starts', () => {
    const paragraph = document.createElement('p')
    paragraph.textContent = 'Selectable text'
    const textNode = paragraph.firstChild

    expect(eventTargetElement(textNode)).toBe(paragraph)
    expect(isInteractiveSelectionTarget(paragraph)).toBe(false)
  })

  it('keeps interactive descendants out of marquee selection starts', () => {
    const button = document.createElement('button')
    const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
    button.append(icon)

    expect(eventTargetElement(icon)).toBe(icon)
    expect(isInteractiveSelectionTarget(icon)).toBe(true)
  })

  it('leaves dragging from editor content to native text selection', () => {
    const boundary = document.createElement('div')
    const editorContent = document.createElement('div')
    const paragraph = document.createElement('p')
    editorContent.className = 'editor-content'
    editorContent.append(paragraph)
    boundary.append(editorContent)

    expect(blockMarqueeStartModeForTarget({
      boundaryElement: boundary,
      button: 0,
      target: paragraph
    })).toBe('ignore')
  })

  it('starts marquee selection from a code block shell inside editor content', () => {
    const boundary = document.createElement('div')
    const editorContent = document.createElement('div')
    const codeBlock = document.createElement('div')
    const frame = document.createElement('div')
    editorContent.className = 'editor-content'
    codeBlock.className = 'code-block-node'
    frame.className = 'code-block-frame'
    codeBlock.append(frame)
    editorContent.append(codeBlock)
    boundary.append(editorContent)

    expect(blockMarqueeStartModeForTarget({
      boundaryElement: boundary,
      button: 0,
      target: frame
    })).toBe('block-with-marquee')
  })

  it('keeps code editor and code block controls out of marquee starts', () => {
    const boundary = document.createElement('div')
    const editorContent = document.createElement('div')
    const codeBlock = document.createElement('div')
    const codeEditor = document.createElement('div')
    const cmEditor = document.createElement('div')
    const cmGutters = document.createElement('div')
    const cmContent = document.createElement('div')
    const cmLine = document.createElement('div')
    const control = document.createElement('button')
    editorContent.className = 'editor-content'
    codeBlock.className = 'code-block-node'
    codeEditor.className = 'code-block-editor'
    cmEditor.className = 'cm-editor'
    cmGutters.className = 'cm-gutters'
    cmContent.className = 'cm-content'
    cmLine.className = 'cm-line'
    control.className = 'code-block-control'
    cmContent.append(cmLine)
    cmEditor.append(cmGutters, cmContent)
    codeEditor.append(cmEditor)
    codeBlock.append(codeEditor, control)
    editorContent.append(codeBlock)
    boundary.append(editorContent)

    expect(blockMarqueeStartModeForTarget({
      boundaryElement: boundary,
      button: 0,
      target: cmGutters
    })).toBe('block-with-marquee')
    expect(blockMarqueeStartModeForTarget({
      boundaryElement: boundary,
      button: 0,
      target: cmEditor
    })).toBe('ignore')
    expect(blockMarqueeStartModeForTarget({
      boundaryElement: boundary,
      button: 0,
      target: cmLine
    })).toBe('ignore')
    expect(blockMarqueeStartModeForTarget({
      boundaryElement: boundary,
      button: 0,
      target: control
    })).toBe('ignore')
  })

  it('uses a visible marquee when dragging inside the selection boundary outside editor content', () => {
    const boundary = document.createElement('div')
    const gutter = document.createElement('div')
    boundary.append(gutter)

    expect(blockMarqueeStartModeForTarget({
      boundaryElement: boundary,
      button: 0,
      target: gutter
    })).toBe('block-with-marquee')
  })

  it('ignores non-primary buttons, interactive targets, and targets outside the boundary', () => {
    const boundary = document.createElement('div')
    const button = document.createElement('button')
    const outside = document.createElement('div')
    boundary.append(button)

    expect(blockMarqueeStartModeForTarget({
      boundaryElement: boundary,
      button: 1,
      target: boundary
    })).toBe('ignore')
    expect(blockMarqueeStartModeForTarget({
      boundaryElement: boundary,
      button: 0,
      target: button
    })).toBe('ignore')
    expect(blockMarqueeStartModeForTarget({
      boundaryElement: boundary,
      button: 0,
      target: outside
    })).toBe('ignore')
  })

  it('suppresses child overlays when a visual container is selected', () => {
    const blockquote = document.createElement('blockquote')
    const paragraph = document.createElement('p')
    blockquote.append(paragraph)
    const container = target(blockquote, 'container')
    const child = target(paragraph, 'child')

    expect(shouldRenderBlockOverlay(container, [container, child])).toBe(true)
    expect(shouldRenderBlockOverlay(child, [container, child])).toBe(false)
  })

  it('treats collapsible callouts as selectable visual containers', () => {
    const details = document.createElement('details')
    details.className = 'docpilot-callout docpilot-callout-collapsible'
    const paragraph = document.createElement('p')
    details.append(paragraph)
    const container = target(details, 'details')
    const child = target(paragraph, 'child')

    expect(visualBlockSelectionTargets([container, child]).map((item) => item.id)).toEqual(['details'])
  })

  it('renders table marquee selection as one table overlay', () => {
    const wrapper = document.createElement('div')
    wrapper.className = 'tableWrapper'
    const table = document.createElement('table')
    const row = document.createElement('tr')
    const cell = document.createElement('td')
    row.append(cell)
    table.append(row)
    wrapper.append(table)
    const tableTarget = target(wrapper, 'table')
    const rowTarget = target(row, 'row')
    const cellTarget = target(cell, 'cell')

    expect(visualBlockSelectionTargets([tableTarget, rowTarget, cellTarget]).map((item) => item.id))
      .toEqual(['table'])
    expect(hasTableBlockTarget(visualBlockSelectionTargets([tableTarget, rowTarget, cellTarget]))).toBe(true)
  })

  it('keeps table selection geometry on the table box', () => {
    const editorRect = {
      bottom: 500,
      height: 500,
      left: 20,
      right: 620,
      top: 0,
      width: 600
    } as DOMRect
    const wrapper = document.createElement('div')
    wrapper.className = 'tableWrapper'
    wrapper.dataset.blockId = 'table'
    setRect(wrapper, {
      bottom: 320,
      height: 260,
      left: 80,
      right: 480,
      top: 60,
      width: 400
    })

    expect(targetFromElement(wrapper, editorRect)).toMatchObject({
      id: 'table',
      selectionHeight: 260,
      selectionLeft: 0,
      selectionTop: 0,
      selectionWidth: 400
    })
  })

  it('accepts a ProseMirror block id for table NodeView wrappers', () => {
    const editorDom = document.createElement('div')
    const wrapper = document.createElement('div')
    wrapper.className = 'tableWrapper'
    editorDom.append(wrapper)
    setRect(wrapper, {
      bottom: 320,
      height: 260,
      left: 80,
      right: 480,
      top: 60,
      width: 400
    })

    expect(wrapper.dataset.blockId).toBeUndefined()
    expect(isSelectableBlockElement(wrapper, editorDom, 'table')).toBe(true)
    expect(targetFromElement(wrapper, editorDom.getBoundingClientRect(), 'table')).toMatchObject({
      id: 'table',
      selectionHeight: 260,
      selectionLeft: 0,
      selectionTop: 0,
      selectionWidth: 400
    })
  })

  it('collects real TipTap table NodeView wrappers as selectable targets', () => {
    const element = document.createElement('div')
    document.body.append(element)
    const editor = new Editor({
      element,
      extensions: editorExtensions,
      content: {
        type: 'doc',
        content: [
          {
            type: 'table',
            attrs: { blockId: 'table1' },
            content: [
              {
                type: 'tableRow',
                attrs: { blockId: 'row1' },
                content: [
                  {
                    type: 'tableCell',
                    attrs: { blockId: 'cell1' },
                    content: [{ type: 'paragraph', attrs: { blockId: 'cellParagraph' } }]
                  }
                ]
              }
            ]
          }
        ]
      }
    })

    try {
      const editorDom = editor.view.dom as HTMLElement
      const wrapper = editorDom.querySelector<HTMLElement>('.tableWrapper')
      expect(wrapper).not.toBeNull()
      if (!wrapper) return
      expect(wrapper.dataset.blockId).toBeUndefined()
      setRect(editorDom, {
        bottom: 500,
        height: 500,
        left: 20,
        right: 620,
        top: 0,
        width: 600
      })
      setRect(wrapper, {
        bottom: 320,
        height: 260,
        left: 80,
        right: 480,
        top: 60,
        width: 400
      })

      expect(selectableBlockTargets(editor).some((item) => item.id === 'table1' && item.element === wrapper))
        .toBe(true)
    } finally {
      editor.destroy()
      element.remove()
    }
  })

  it('shrinks block selection outsets to keep adjacent blocks separated', () => {
    const previous = target(document.createElement('h2'), 'previous')
    const current = target(document.createElement('h3'), 'current')
    previous.rect = { left: 0, top: 0, right: 100, bottom: 40 }
    current.rect = { left: 0, top: 48, right: 100, bottom: 88 }

    expect(blockSelectionDecorationsFromTargets([previous, current])).toMatchObject([
      { blockId: 'previous', selectionBlockEndOutset: 1 },
      { blockId: 'current', selectionBlockStartOutset: 1 }
    ])
  })

  it('insets touching block selection backgrounds to preserve a visible gap', () => {
    const previous = target(document.createElement('li'), 'previous')
    const current = target(document.createElement('li'), 'current')
    previous.rect = { left: 0, top: 0, right: 100, bottom: 40 }
    current.rect = { left: 0, top: 40, right: 100, bottom: 80 }

    expect(blockSelectionDecorationsFromTargets([previous, current])).toMatchObject([
      { blockId: 'previous', selectionBlockEndOutset: -3 },
      { blockId: 'current', selectionBlockStartOutset: -3 }
    ])
  })

  it('creates stable selection signatures', () => {
    expect(blockSelectionSignature([
      {
        blockId: 'a',
        selectionHeight: 20.6,
        selectionLeft: 1.2,
        selectionTop: 2.4,
        selectionWidth: 100.5
      }
    ])).toBe('a:1:2:101:21')
  })
})
