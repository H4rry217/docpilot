import type { JSONContent } from '@tiptap/core'
import { type Editor, EditorContent, useEditor } from '@tiptap/react'
import {
  forwardRef,
  type MouseEvent as ReactMouseEvent,
  useCallback,
  useEffect,
  useImperativeHandle,
  useRef,
  type PointerEvent as ReactPointerEvent
} from 'react'
import type { DocumentOutlineJumpRequest } from '../../../entities/block/outline'
import type { BlockDocument } from '../../../entities/block/types'
import {
  deleteBlocksByIds,
  setBlockSelectionDecorations,
  type BlockSelectionDecoration
} from '../model/blockSelection'
import { blockDocumentToProseMirrorJson } from '../model/blockDocumentToProseMirror'
import { editorExtensions } from '../model/extensions'
import { proseMirrorJsonToBlockDocument } from '../model/proseMirrorToBlockDocument'
import {
  TableDividerControls,
  TableHoverIndicators,
  useTableAffordances
} from './tableAffordances'

export type BlockDocumentEditorSnapshot = {
  blockDocument: BlockDocument
  proseMirrorJson: JSONContent
}

export type BlockDocumentEditorSnapshotSource = 'load' | 'edit' | 'programmatic'

export type InsertHtmlBlockInput = {
  id: string
  title: string
  source: string
  displayMode: 'fixed' | 'auto'
  fixedHeightPx: number
  allowScripts: boolean
}

export type BlockDocumentEditorHandle = {
  getSnapshot: () => BlockDocumentEditorSnapshot | null
  insertHtmlBlock: (input: InsertHtmlBlockInput) => BlockDocumentEditorSnapshot | null
  scrollToOutlineItem: (request: DocumentOutlineJumpRequest) => void
  setBlockDocument: (blockDocument: BlockDocument) => BlockDocumentEditorSnapshot | null
  setProseMirrorJson: (proseMirrorJson: JSONContent) => BlockDocumentEditorSnapshot | null
}

export type BlockDocumentEditorProps = {
  contentKey?: string
  blockDocument?: BlockDocument
  proseMirrorFallback?: JSONContent
  onSnapshotChange: (
    snapshot: BlockDocumentEditorSnapshot,
    source: BlockDocumentEditorSnapshotSource
  ) => void
}

function snapshotFromEditor(editor: Editor): BlockDocumentEditorSnapshot {
  const proseMirrorJson = editor.getJSON()
  return {
    proseMirrorJson,
    blockDocument: proseMirrorJsonToBlockDocument(proseMirrorJson)
  }
}

function blockIdSelector(blockId: string): string {
  return `[data-block-id="${blockId.replaceAll('\\', '\\\\').replaceAll('"', '\\"')}"]`
}

type Point = {
  x: number
  y: number
}

type Rect = {
  left: number
  top: number
  right: number
  bottom: number
}

type SelectableBlockTarget = {
  id: string
  element: HTMLElement
  hitRect: Rect
  rect: Rect
  selectionLeft: number
  selectionTop?: number
  selectionWidth: number
  selectionHeight?: number
}

type DragSelectionState = {
  editor: Editor
  frame: number | null
  isSelecting: boolean
  latestClientPoint: Point
  originClientPoint: Point
  originSurfacePoint: Point
  targets: SelectableBlockTarget[]
}

const BLOCK_MARQUEE_START_DISTANCE_PX = 6
const BLOCK_SELECTION_COMPACT_END_OUTSET_PX = 2
const BLOCK_SELECTION_CLOSE_BLOCK_GAP_PX = 18

function rectFromPoints(start: Point, end: Point): Rect {
  return {
    left: Math.min(start.x, end.x),
    top: Math.min(start.y, end.y),
    right: Math.max(start.x, end.x),
    bottom: Math.max(start.y, end.y)
  }
}

function rectsOverlap(left: Rect, right: Rect): boolean {
  return left.left <= right.right
    && left.right >= right.left
    && left.top <= right.bottom
    && left.bottom >= right.top
}

function pointFromClientPoint(point: Point, element: HTMLElement): Point {
  const rect = element.getBoundingClientRect()
  return {
    x: point.x - rect.left,
    y: point.y - rect.top
  }
}

function pointFromElement(event: PointerEvent | ReactPointerEvent, element: HTMLElement): Point {
  return pointFromClientPoint({ x: event.clientX, y: event.clientY }, element)
}

function clientPointFromElementPoint(point: Point, element: HTMLElement): Point {
  const rect = element.getBoundingClientRect()
  return {
    x: rect.left + point.x,
    y: rect.top + point.y
  }
}

function isEditorContentSelectionStart(target: HTMLElement, editorDom: HTMLElement): boolean {
  if (!editorDom.contains(target)) return false
  return Boolean(target.closest('[data-block-id], .tableWrapper, table, td, th'))
}

function isInteractiveSelectionTarget(target: HTMLElement): boolean {
  return Boolean(target.closest([
    'button',
    'input',
    'select',
    'textarea',
    '[contenteditable="false"]',
    '.cm-editor',
    '.image-node',
    '.code-block-control',
    '.image-node-toolbar',
    '.image-node-caption',
    '.image-resize-handle',
    '.column-resize-handle',
    '.html-block-controls'
  ].join(',')))
}

function isSelectableBlockElement(element: HTMLElement, editorDom: HTMLElement): boolean {
  const blockId = element.dataset.blockId
  if (!blockId || !editorDom.contains(element)) return false
  const style = window.getComputedStyle(element)
  if (style.display === 'none' || style.visibility === 'hidden') return false
  const rect = element.getBoundingClientRect()
  return rect.width > 0 && rect.height > 0
}

function targetFromElement(element: HTMLElement, editorRect: DOMRect): SelectableBlockTarget | null {
  const rect = element.getBoundingClientRect()
  const blockId = element.dataset.blockId
  const blockRect = {
    left: rect.left,
    top: rect.top,
    right: rect.right,
    bottom: rect.bottom
  }

  if (!blockId || blockRect.right <= blockRect.left || blockRect.bottom <= blockRect.top) {
    return null
  }

  const imageNode = element.querySelector<HTMLElement>('.image-node')
  const imageRect = imageNode?.getBoundingClientRect()
  const selectionTop = imageRect ? imageRect.top - rect.top : undefined
  const selectionHeight = imageRect ? imageRect.height : undefined

  return {
    element,
    id: blockId,
    rect: blockRect,
    hitRect: {
      left: Math.min(editorRect.left, blockRect.left),
      top: blockRect.top,
      right: Math.max(editorRect.right, blockRect.right),
      bottom: blockRect.bottom
    },
    selectionLeft: editorRect.left - blockRect.left,
    selectionTop,
    selectionWidth: editorRect.width,
    selectionHeight
  }
}

function withBlockRowHitRects(targets: SelectableBlockTarget[], editorRect: DOMRect): SelectableBlockTarget[] {
  const sortedTargets = [...targets].sort((left, right) => {
    if (left.rect.top !== right.rect.top) return left.rect.top - right.rect.top
    return left.rect.left - right.rect.left
  })

  return sortedTargets.map((target, index) => {
    const previous = sortedTargets[index - 1]
    const next = sortedTargets[index + 1]
    const top = previous
      ? Math.max(editorRect.top, Math.min(target.rect.top, (previous.rect.bottom + target.rect.top) / 2))
      : Math.max(editorRect.top, target.rect.top)
    const bottom = next
      ? Math.min(editorRect.bottom, Math.max(target.rect.bottom, (target.rect.bottom + next.rect.top) / 2))
      : Math.min(editorRect.bottom, target.rect.bottom)

    return {
      ...target,
      hitRect: {
        left: editorRect.left,
        top,
        right: editorRect.right,
        bottom: Math.max(bottom, top)
      }
    }
  })
}

function selectableBlockTargets(editor: Editor): SelectableBlockTarget[] {
  const editorDom = editor.view.dom
  const editorRect = editorDom.getBoundingClientRect()
  const seenBlockIds = new Set<string>()
  const targets: SelectableBlockTarget[] = []

  editor.state.doc.descendants((node, position) => {
    const blockId = node.attrs.blockId
    if (typeof blockId !== 'string' || !blockId || seenBlockIds.has(blockId)) {
      return true
    }

    const domNode = editor.view.nodeDOM(position)
    if (!(domNode instanceof HTMLElement) || !isSelectableBlockElement(domNode, editorDom)) {
      return true
    }

    const target = targetFromElement(domNode, editorRect)
    if (target) {
      seenBlockIds.add(blockId)
      targets.push(target)
    }

    return true
  })

  for (const target of fallbackSelectableBlockTargets(editorDom)) {
    if (seenBlockIds.has(target.id)) continue
    seenBlockIds.add(target.id)
    targets.push(target)
  }

  return withBlockRowHitRects(targets, editorRect)
}

function fallbackSelectableBlockTargets(editorDom: HTMLElement): SelectableBlockTarget[] {
  const editorRect = editorDom.getBoundingClientRect()
  return Array.from(editorDom.querySelectorAll<HTMLElement>('[data-block-id]'))
    .filter((element) => isSelectableBlockElement(element, editorDom))
    .map((element) => targetFromElement(element, editorRect))
    .filter((target): target is SelectableBlockTarget => target !== null)
}

function isPastDragStartDistance(origin: Point, current: Point): boolean {
  const deltaX = current.x - origin.x
  const deltaY = current.y - origin.y
  return deltaX * deltaX + deltaY * deltaY >= BLOCK_MARQUEE_START_DISTANCE_PX * BLOCK_MARQUEE_START_DISTANCE_PX
}

function isVisualContainerTarget(target: SelectableBlockTarget): boolean {
  return target.element.tagName === 'BLOCKQUOTE' || target.element.tagName === 'LI'
}

function shouldRenderBlockOverlay(target: SelectableBlockTarget, targets: SelectableBlockTarget[]): boolean {
  const selectedContainerAncestor = targets.some((otherTarget) => (
    otherTarget !== target
    && isVisualContainerTarget(otherTarget)
    && otherTarget.element.contains(target.element)
  ))
  if (selectedContainerAncestor) return false

  if (isVisualContainerTarget(target)) return true

  return !targets.some((otherTarget) => (
    otherTarget !== target
    && target.element.contains(otherTarget.element)
  ))
}

export const BlockDocumentEditor = forwardRef<BlockDocumentEditorHandle, BlockDocumentEditorProps>(
  function BlockDocumentEditor(
    { contentKey, blockDocument, proseMirrorFallback, onSnapshotChange },
    ref
  ) {
    const applyingContentRef = useRef(false)
    const lastAppliedContentKeyRef = useRef<string | undefined>(undefined)
    const surfaceRef = useRef<HTMLDivElement | null>(null)
    const marqueeRef = useRef<HTMLDivElement | null>(null)
    const selectedBlockIdsRef = useRef<Set<string>>(new Set())
    const selectedVisualBlockIdsRef = useRef<Set<string>>(new Set())
    const selectedVisualSignatureRef = useRef('')
    const dragSelectionRef = useRef<DragSelectionState | null>(null)
    const suppressNextClickRef = useRef(false)

    const emitSnapshot = useCallback(
      (activeEditor: Editor, source: BlockDocumentEditorSnapshotSource) => {
        const snapshot = snapshotFromEditor(activeEditor)
        onSnapshotChange(snapshot, source)
        return snapshot
      },
      [onSnapshotChange]
    )

    const editor = useEditor({
      extensions: editorExtensions,
      content: '',
      editorProps: {
        attributes: {
          class: 'prose-editor',
          spellcheck: 'false'
        }
      },
      onUpdate: ({ editor: activeEditor }) => {
        if (applyingContentRef.current) return
        emitSnapshot(activeEditor, 'edit')
      }
    })

    const {
      handleTableDividerChanged,
      handleTablePointerLeave,
      handleTablePointerMove,
      handleTableWheel,
      keepTableDividerControlsVisible,
      keepTableHoverIndicatorVisible,
      requestTableDividerHide,
      requestTableHoverIndicatorHide,
      resetTableAffordances,
      revealTableDividerAfterDelay,
      tableDivider,
      tableHoverIndicator
    } = useTableAffordances({ editor, surfaceRef })

    const clearBlockSelection = useCallback(() => {
      selectedBlockIdsRef.current.clear()
      selectedVisualBlockIdsRef.current.clear()
      selectedVisualSignatureRef.current = ''
      if (editor) {
        setBlockSelectionDecorations(editor, [])
      }
    }, [editor])

    function selectionDecorationFromTarget(target: SelectableBlockTarget): BlockSelectionDecoration {
      return {
        blockId: target.id,
        selectionLeft: target.selectionLeft,
        selectionTop: target.selectionTop,
        selectionWidth: target.selectionWidth,
        selectionHeight: target.selectionHeight
      }
    }

    function isFixedHeightSelectionTarget(target: SelectableBlockTarget): boolean {
      return Number.isFinite(target.selectionHeight)
    }

    function blockSelectionDecorationsFromTargets(targets: SelectableBlockTarget[]): BlockSelectionDecoration[] {
      const decorations = targets.map(selectionDecorationFromTarget)

      for (let index = 1; index < targets.length; index += 1) {
        const previousTarget = targets[index - 1]
        const currentTarget = targets[index]
        const isCloseToPrevious = currentTarget.rect.top - previousTarget.rect.bottom <= BLOCK_SELECTION_CLOSE_BLOCK_GAP_PX
        if (!isCloseToPrevious) continue
        if (!isFixedHeightSelectionTarget(previousTarget) && !isFixedHeightSelectionTarget(currentTarget)) continue

        decorations[index - 1] = {
          ...decorations[index - 1],
          selectionBlockEndOutset: BLOCK_SELECTION_COMPACT_END_OUTSET_PX
        }
      }

      return decorations
    }

    function blockSelectionSignature(blockSelections: BlockSelectionDecoration[]): string {
      return blockSelections
        .map((blockSelection) => [
          blockSelection.blockId,
          Math.round(blockSelection.selectionLeft ?? 0),
          Math.round(blockSelection.selectionTop ?? 0),
          Math.round(blockSelection.selectionWidth ?? 0),
          Math.round(blockSelection.selectionHeight ?? 0)
        ].join(':'))
        .join('|')
    }

    function applyVisualBlockSelection(blockSelections: BlockSelectionDecoration[]) {
      if (!editor) return

      const nextIds = new Set(blockSelections.map((blockSelection) => blockSelection.blockId))
      const nextSignature = blockSelectionSignature(blockSelections)
      if (nextSignature === selectedVisualSignatureRef.current) {
        return
      }

      selectedVisualBlockIdsRef.current = nextIds
      selectedVisualSignatureRef.current = nextSignature
      setBlockSelectionDecorations(editor, blockSelections)
    }

    function applySelectedBlockTargets(targets: SelectableBlockTarget[]) {
      selectedBlockIdsRef.current = new Set(targets.map((target) => target.id))
      const visualTargets = targets.filter((candidate) => shouldRenderBlockOverlay(candidate, targets))
      applyVisualBlockSelection(
        blockSelectionDecorationsFromTargets(visualTargets)
      )
    }

    function setMarqueeVisible(visible: boolean) {
      const element = marqueeRef.current
      if (!element) return
      element.hidden = !visible
    }

    function updateMarqueeElement(origin: Point, current: Point) {
      const element = marqueeRef.current
      if (!element) return
      const rect = rectFromPoints(origin, current)
      element.style.transform = `translate(${rect.left}px, ${rect.top}px)`
      element.style.width = `${rect.right - rect.left}px`
      element.style.height = `${rect.bottom - rect.top}px`
    }

    const applyProseMirrorJson = useCallback(
      (activeEditor: Editor, proseMirrorJson: JSONContent, source: BlockDocumentEditorSnapshotSource) => {
        applyingContentRef.current = true
        activeEditor.commands.setContent(proseMirrorJson, false)
        applyingContentRef.current = false
        return emitSnapshot(activeEditor, source)
      },
      [emitSnapshot]
    )

    useImperativeHandle(
      ref,
      () => ({
        getSnapshot: () => (editor ? snapshotFromEditor(editor) : null),
        insertHtmlBlock: (input) => {
          if (!editor) return null
          editor
            .chain()
            .focus()
            .insertContent({
              type: 'docpilotHtmlBlock',
              attrs: input
            })
            .run()
          return snapshotFromEditor(editor)
        },
        scrollToOutlineItem: (request) => {
          if (!editor) return
          const headings = editor.view.dom.querySelectorAll<HTMLElement>('h1, h2, h3, h4, h5, h6')
          const target = editor.view.dom.querySelector<HTMLElement>(blockIdSelector(request.id))
            ?? headings.item(request.headingIndex)
          target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
        },
        setBlockDocument: (nextBlockDocument) => {
          if (!editor) return null
          return applyProseMirrorJson(editor, blockDocumentToProseMirrorJson(nextBlockDocument), 'programmatic')
        },
        setProseMirrorJson: (nextProseMirrorJson) => {
          if (!editor) return null
          return applyProseMirrorJson(editor, nextProseMirrorJson, 'programmatic')
        }
      }),
      [applyProseMirrorJson, editor]
    )

    useEffect(() => {
      if (!editor || !contentKey || lastAppliedContentKeyRef.current === contentKey) return
      const nextContent = blockDocument ? blockDocumentToProseMirrorJson(blockDocument) : proseMirrorFallback
      if (!nextContent) return
      applyProseMirrorJson(editor, nextContent, 'load')
      lastAppliedContentKeyRef.current = contentKey
    }, [applyProseMirrorJson, blockDocument, contentKey, editor, proseMirrorFallback])

    useEffect(() => {
      clearBlockSelection()
      resetTableAffordances()
    }, [clearBlockSelection, contentKey, resetTableAffordances])

    useEffect(() => {
      return () => {
        const dragState = dragSelectionRef.current
        if (dragState && dragState.frame !== null) {
          window.cancelAnimationFrame(dragState.frame)
        }
        dragSelectionRef.current = null
        suppressNextClickRef.current = false
        setMarqueeVisible(false)
        clearBlockSelection()
      }
    }, [clearBlockSelection])

    useEffect(() => {
      if (!editor) return
      const activeEditor = editor

      function handleKeyDown(event: KeyboardEvent) {
        if (event.key !== 'Backspace' && event.key !== 'Delete') return
        if (!selectedBlockIdsRef.current.size) return
        const activeElement = document.activeElement
        const surface = surfaceRef.current
        if (
          activeElement
          && activeElement !== document.body
          && surface
          && !surface.contains(activeElement)
        ) {
          return
        }

        event.preventDefault()
        if (deleteBlocksByIds(activeEditor, selectedBlockIdsRef.current)) {
          clearBlockSelection()
        }
      }

      window.addEventListener('keydown', handleKeyDown)
      return () => window.removeEventListener('keydown', handleKeyDown)
    }, [clearBlockSelection, editor])

    function shouldStartBlockMarquee(event: ReactPointerEvent<HTMLDivElement>): boolean {
      if (!editor || event.button !== 0) return false
      const target = event.target
      if (!(target instanceof HTMLElement)) return false
      if (!surfaceRef.current?.contains(target)) return false
      if (isInteractiveSelectionTarget(target)) return false
      if (isEditorContentSelectionStart(target, editor.view.dom)) return false

      return true
    }

    function handleSurfacePointerMove(event: ReactPointerEvent<HTMLDivElement>) {
      if (dragSelectionRef.current) return
      handleTablePointerMove(event)
    }

    function handleSurfacePointerLeave() {
      handleTablePointerLeave()
    }

    function applyDragSelection(clientPoint: Point) {
      const dragState = dragSelectionRef.current
      const surface = surfaceRef.current
      if (!dragState || !surface) return

      dragState.latestClientPoint = clientPoint
      const currentSurfacePoint = pointFromClientPoint(clientPoint, surface)
      updateMarqueeElement(dragState.originSurfacePoint, currentSurfacePoint)

      const selectionRect = rectFromPoints(
        clientPointFromElementPoint(dragState.originSurfacePoint, surface),
        clientPoint
      )
      dragState.targets = selectableBlockTargets(dragState.editor)
      applySelectedBlockTargets(
        dragState.targets.filter((target) => rectsOverlap(selectionRect, target.hitRect))
      )
    }

    function scheduleDragSelection(clientPoint: Point) {
      const dragState = dragSelectionRef.current
      if (!dragState) return

      dragState.latestClientPoint = clientPoint
      if (dragState.frame !== null) return

      dragState.frame = window.requestAnimationFrame(() => {
        const latestDragState = dragSelectionRef.current
        if (!latestDragState) return
        latestDragState.frame = null
        applyDragSelection(latestDragState.latestClientPoint)
      })
    }

    function handleSurfacePointerDown(event: ReactPointerEvent<HTMLDivElement>) {
      const activeEditor = editor
      if (!activeEditor) {
        if (selectedBlockIdsRef.current.size) {
          clearBlockSelection()
        }
        return
      }

      if (!shouldStartBlockMarquee(event)) {
        if (selectedBlockIdsRef.current.size) {
          clearBlockSelection()
        }
        return
      }

      const dragEditor: Editor = activeEditor
      const surfaceElement = event.currentTarget
      try {
        surfaceElement.setPointerCapture(event.pointerId)
      } catch {
        // Pointer capture is best-effort; window listeners below still handle the drag.
      }
      const originClientPoint = { x: event.clientX, y: event.clientY }
      const originSurfacePoint = pointFromElement(event, surfaceElement)
      clearBlockSelection()
      dragSelectionRef.current = {
        editor: activeEditor,
        frame: null,
        isSelecting: false,
        latestClientPoint: originClientPoint,
        originClientPoint,
        originSurfacePoint,
        targets: selectableBlockTargets(activeEditor)
      }

      function handlePointerMove(pointerEvent: PointerEvent) {
        const dragState = dragSelectionRef.current
        if (!dragState) return

        const clientPoint = { x: pointerEvent.clientX, y: pointerEvent.clientY }
        if (!dragState.isSelecting) {
          if (!isPastDragStartDistance(dragState.originClientPoint, clientPoint)) return
          dragState.isSelecting = true
          updateMarqueeElement(dragState.originSurfacePoint, pointFromClientPoint(clientPoint, surfaceElement))
          setMarqueeVisible(true)
        }

        pointerEvent.preventDefault()
        window.getSelection()?.removeAllRanges()
        scheduleDragSelection(clientPoint)
      }

      function handlePointerUp(pointerEvent: PointerEvent) {
        const dragState = dragSelectionRef.current
        if (dragState && dragState.frame !== null) {
          window.cancelAnimationFrame(dragState.frame)
        }
        if (dragState?.isSelecting) {
          pointerEvent.preventDefault()
          suppressNextClickRef.current = true
          applyDragSelection({ x: pointerEvent.clientX, y: pointerEvent.clientY })
          dragEditor.commands.focus()
        }
        dragSelectionRef.current = null
        setMarqueeVisible(false)
        try {
          surfaceElement.releasePointerCapture(pointerEvent.pointerId)
        } catch {
          // The pointer may already be released by the browser.
        }
        window.removeEventListener('pointermove', handlePointerMove)
        window.removeEventListener('pointerup', handlePointerUp)
      }

      window.addEventListener('pointermove', handlePointerMove)
      window.addEventListener('pointerup', handlePointerUp)
    }

    function handleSurfaceClickCapture(event: ReactMouseEvent<HTMLDivElement>) {
      if (!suppressNextClickRef.current) return
      suppressNextClickRef.current = false
      event.preventDefault()
      event.stopPropagation()
    }

    return (
      <div
        ref={surfaceRef}
        className="block-editor-surface"
        onClickCapture={handleSurfaceClickCapture}
        onPointerDownCapture={handleSurfacePointerDown}
        onPointerLeave={handleSurfacePointerLeave}
        onPointerMove={handleSurfacePointerMove}
        onWheelCapture={handleTableWheel}
      >
        <EditorContent editor={editor} className="editor-content" />
        {tableHoverIndicator ? (
          <TableHoverIndicators
            geometry={tableHoverIndicator}
            onDividerHandleEnter={revealTableDividerAfterDelay}
            onDividerHandleLeave={requestTableDividerHide}
            onKeepVisible={keepTableHoverIndicatorVisible}
            onRequestHide={requestTableHoverIndicatorHide}
          />
        ) : null}
        {editor && tableDivider ? (
          <TableDividerControls
            editor={editor}
            divider={tableDivider}
            onChanged={handleTableDividerChanged}
            onKeepVisible={keepTableDividerControlsVisible}
            onRequestHide={requestTableDividerHide}
          />
        ) : null}
        <div ref={marqueeRef} className="block-selection-marquee" hidden />
      </div>
    )
  }
)
