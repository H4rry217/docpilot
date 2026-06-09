import { NodeViewWrapper, type NodeViewProps } from '@tiptap/react'
import { AlignCenter, AlignLeft, AlignRight, Captions } from 'lucide-react'
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type KeyboardEvent,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent
} from 'react'
import './ImageNodeView.css'

type ImageAlignment = 'left' | 'center' | 'right'
type ResizeCorner = {
  x: -1 | 1
  y: -1 | 1
}

const MIN_IMAGE_WIDTH = 96
const MAX_IMAGE_WIDTH = 720

function textAttr(attrs: Record<string, unknown>, name: string, fallback = ''): string {
  const value = attrs[name]
  return typeof value === 'string' ? value : fallback
}

function numberAttr(value: unknown): number | null {
  const number = typeof value === 'number' ? value : typeof value === 'string' ? Number.parseInt(value, 10) : Number.NaN
  if (!Number.isFinite(number)) return null
  return Math.max(MIN_IMAGE_WIDTH, Math.min(MAX_IMAGE_WIDTH, Math.trunc(number)))
}

function alignmentAttr(value: unknown): ImageAlignment {
  return value === 'center' || value === 'right' ? value : 'left'
}

function clampWidth(width: number): number {
  return Math.max(MIN_IMAGE_WIDTH, Math.min(MAX_IMAGE_WIDTH, Math.round(width)))
}

export function ImageNodeView(props: NodeViewProps) {
  const attrs = props.node.attrs as Record<string, unknown>
  const rootRef = useRef<HTMLElement | null>(null)
  const imageRef = useRef<HTMLImageElement | null>(null)
  const captionInputRef = useRef<HTMLTextAreaElement | null>(null)
  const resizeRef = useRef<{
    corner: ResizeCorner
    startX: number
    startY: number
    startWidth: number
    startHeight: number
  } | null>(null)
  const draftWidthRef = useRef<number | null>(null)
  const [draftWidth, setDraftWidth] = useState<number | null>(null)
  const [isActive, setIsActive] = useState(false)
  const [isCaptionEditing, setIsCaptionEditing] = useState(false)
  const [captionDraft, setCaptionDraft] = useState('')
  const src = textAttr(attrs, 'src')
  const alt = textAttr(attrs, 'alt')
  const title = textAttr(attrs, 'title')
  const caption = textAttr(attrs, 'caption')
  const width = numberAttr(attrs.width)
  const alignment = alignmentAttr(attrs.alignment)
  const activeWidth = draftWidth ?? width
  const shouldShowCaption = isCaptionEditing || Boolean(caption)
  const rootClassName = [
    'image-node',
    `align-${alignment}`,
    isActive || props.selected ? 'is-active' : '',
    props.selected ? 'is-selected' : ''
  ].filter(Boolean).join(' ')
  const imageStyle = useMemo(
    () => (activeWidth ? { width: `${activeWidth}px` } : undefined),
    [activeWidth]
  )

  useEffect(() => {
    if (!isCaptionEditing) {
      setCaptionDraft(caption)
    }
  }, [caption, isCaptionEditing])

  useEffect(() => {
    if (isCaptionEditing) {
      window.setTimeout(() => {
        captionInputRef.current?.focus()
        resizeCaptionInput()
      }, 0)
    }
  }, [isCaptionEditing])

  useEffect(() => {
    if (isCaptionEditing) {
      resizeCaptionInput()
    }
  }, [captionDraft, isCaptionEditing])

  useEffect(() => {
    function handleDocumentPointerDown(event: PointerEvent) {
      const target = event.target
      if (!(target instanceof Node)) return
      if (!rootRef.current?.contains(target)) {
        setIsActive(false)
      }
    }

    function handlePointerMove(event: PointerEvent) {
      const resizeState = resizeRef.current
      if (!resizeState) return
      const deltaX = event.clientX - resizeState.startX
      const deltaY = event.clientY - resizeState.startY
      const aspectRatio = resizeState.startHeight > 0 ? resizeState.startWidth / resizeState.startHeight : 1
      const widthFromX = resizeState.startWidth + deltaX * resizeState.corner.x
      const widthFromY = (resizeState.startHeight + deltaY * resizeState.corner.y) * aspectRatio
      const nextWidth = Math.abs(deltaX) > Math.abs(deltaY * aspectRatio) ? widthFromX : widthFromY
      const clampedWidth = clampWidth(nextWidth)
      draftWidthRef.current = clampedWidth
      setDraftWidth(clampedWidth)
    }

    function handlePointerUp() {
      if (resizeRef.current && draftWidthRef.current) {
        props.updateAttributes({ width: draftWidthRef.current })
      }
      resizeRef.current = null
      draftWidthRef.current = null
      setDraftWidth(null)
      setIsActive(false)
    }

    window.addEventListener('pointerdown', handleDocumentPointerDown, true)
    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
    return () => {
      window.removeEventListener('pointerdown', handleDocumentPointerDown, true)
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
    }
  }, [props])

  useEffect(() => {
    if (props.selected) {
      setIsActive(true)
    }
  }, [props.selected])

  function selectImage() {
    setIsActive(true)
    const position = props.getPos()
    if (typeof position !== 'number') return
    props.editor.chain().focus().setNodeSelection(position).run()
  }

  function updateAlignment(nextAlignment: ImageAlignment) {
    selectImage()
    props.updateAttributes({ alignment: nextAlignment })
  }

  function openCaptionEditor() {
    selectImage()
    setCaptionDraft(caption)
    setIsCaptionEditing(true)
  }

  function commitCaption(value = captionDraft) {
    const nextCaption = value.trim()
    props.updateAttributes({ caption: nextCaption })
    setCaptionDraft(nextCaption)
    setIsCaptionEditing(false)
  }

  function resizeCaptionInput() {
    const element = captionInputRef.current
    if (!element) return
    element.style.height = '0px'
    element.style.height = `${element.scrollHeight}px`
  }

  function handleCaptionChange(event: ChangeEvent<HTMLTextAreaElement>) {
    setCaptionDraft(event.target.value)
    window.requestAnimationFrame(resizeCaptionInput)
  }

  function handleCaptionKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      commitCaption()
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      setCaptionDraft(caption)
      setIsCaptionEditing(false)
    }
  }

  function startResize(event: ReactPointerEvent, corner: ResizeCorner) {
    event.preventDefault()
    event.stopPropagation()
    selectImage()
    const rect = imageRef.current?.getBoundingClientRect()
    resizeRef.current = {
      corner,
      startX: event.clientX,
      startY: event.clientY,
      startWidth: rect?.width ?? width ?? MIN_IMAGE_WIDTH,
      startHeight: rect?.height ?? MIN_IMAGE_WIDTH
    }
  }

  function handleWrapperMouseDown(event: ReactMouseEvent) {
    const target = event.target
    if (target instanceof HTMLElement && target.closest('.image-node-toolbar, .image-node-caption, .image-resize-handle')) {
      return
    }
    event.preventDefault()
    selectImage()
  }

  function handleWrapperMouseLeave() {
    if (resizeRef.current || isCaptionEditing) return
    setIsActive(false)
  }

  return (
    <NodeViewWrapper
      ref={rootRef}
      as="span"
      className={rootClassName}
      contentEditable={false}
      data-alignment={alignment}
      data-width={width ?? undefined}
      onMouseDown={handleWrapperMouseDown}
      onMouseLeave={handleWrapperMouseLeave}
    >
      <span className="image-node-toolbar" contentEditable={false}>
        <button
          className={shouldShowCaption ? 'active' : ''}
          type="button"
          aria-label="Edit image caption"
          title="Edit image caption"
          onMouseDown={(event) => event.preventDefault()}
          onClick={openCaptionEditor}
        >
          <Captions size={16} />
        </button>
        <button
          className={alignment === 'left' ? 'active' : ''}
          type="button"
          aria-label="Align image left"
          title="Align image left"
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => updateAlignment('left')}
        >
          <AlignLeft size={16} />
        </button>
        <button
          className={alignment === 'center' ? 'active' : ''}
          type="button"
          aria-label="Align image center"
          title="Align image center"
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => updateAlignment('center')}
        >
          <AlignCenter size={16} />
        </button>
        <button
          className={alignment === 'right' ? 'active' : ''}
          type="button"
          aria-label="Align image right"
          title="Align image right"
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => updateAlignment('right')}
        >
          <AlignRight size={16} />
        </button>
      </span>
      <span className="image-node-frame">
        <img ref={imageRef} src={src} alt={alt} title={title || undefined} style={imageStyle} draggable />
        <span className="image-resize-handle handle-top-left" onPointerDown={(event) => startResize(event, { x: -1, y: -1 })} />
        <span className="image-resize-handle handle-top-right" onPointerDown={(event) => startResize(event, { x: 1, y: -1 })} />
        <span className="image-resize-handle handle-bottom-left" onPointerDown={(event) => startResize(event, { x: -1, y: 1 })} />
        <span className="image-resize-handle handle-bottom-right" onPointerDown={(event) => startResize(event, { x: 1, y: 1 })} />
      </span>
      {shouldShowCaption ? (
        <span className="image-node-caption" contentEditable={false}>
          {isCaptionEditing ? (
            <textarea
              ref={captionInputRef}
              value={captionDraft}
              placeholder="Add caption"
              aria-label="Image caption"
              rows={1}
              onChange={handleCaptionChange}
              onBlur={() => commitCaption()}
              onKeyDown={handleCaptionKeyDown}
              onMouseDown={(event) => event.stopPropagation()}
            />
          ) : (
            <button
              type="button"
              className="image-node-caption-text"
              aria-label="Edit image caption"
              onMouseDown={(event) => event.stopPropagation()}
              onClick={openCaptionEditor}
            >
              {caption}
            </button>
          )}
        </span>
      ) : null}
    </NodeViewWrapper>
  )
}
