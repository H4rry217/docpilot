import { NodeViewWrapper, type NodeViewProps } from '@tiptap/react'
import { Captions, Check, ChevronDown, Code2, Copy, Eye, WandSparkles } from 'lucide-react'
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type CSSProperties,
  type KeyboardEvent,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent
} from 'react'
import { formatCodeBlockText } from '../model/codeBlockFormatter'
import './CodeBlockNodeView.css'
import { codeLanguage, LANGUAGE_OPTIONS } from './codeLanguages'
import { MermaidPreview } from './MermaidPreview'
import { svgToPngBlob } from './svgToPngBlob'
import { useCodeMirrorNodeView } from './useCodeMirrorNodeView'

type ResizeCorner = {
  x: -1 | 1
  y: -1 | 1
}

const MIN_SPECIAL_BLOCK_WIDTH = 240
const MAX_SPECIAL_BLOCK_WIDTH = 720


function stringAttr(attrs: Record<string, unknown>, name: string, fallback = ''): string {
  const value = attrs[name]
  return typeof value === 'string' ? value : fallback
}

function numberAttr(value: unknown): number | null {
  const number = typeof value === 'number' ? value : typeof value === 'string' ? Number.parseInt(value, 10) : Number.NaN
  if (!Number.isFinite(number)) return null
  return Math.max(MIN_SPECIAL_BLOCK_WIDTH, Math.min(MAX_SPECIAL_BLOCK_WIDTH, Math.trunc(number)))
}

function clampSpecialBlockWidth(width: number): number {
  return Math.max(MIN_SPECIAL_BLOCK_WIDTH, Math.min(MAX_SPECIAL_BLOCK_WIDTH, Math.round(width)))
}

function decorationAttrs(decoration: NodeViewProps['decorations'][number]): Record<string, unknown> {
  const typedDecoration = decoration as unknown as { type?: { attrs?: Record<string, unknown> } }
  return typedDecoration.type?.attrs ?? {}
}

function selectionStyleFromDecoration(styleValue: string): CSSProperties {
  const style: CSSProperties & Record<string, string> = {}
  for (const declaration of styleValue.split(';')) {
    const separatorIndex = declaration.indexOf(':')
    if (separatorIndex < 0) continue
    const property = declaration.slice(0, separatorIndex).trim()
    const value = declaration.slice(separatorIndex + 1).trim()
    if (!property.startsWith('--docpilot-selection-') || !value) continue
    style[property] = value
  }
  return style
}

function blockSelectionDecoration(decorations: NodeViewProps['decorations']): {
  isSelected: boolean
  style: CSSProperties
} {
  let isSelected = false
  let style: CSSProperties = {}

  for (const decoration of decorations) {
    const attrs = decorationAttrs(decoration)
    const className = typeof attrs.class === 'string' ? attrs.class : ''
    if (!className.split(/\s+/).includes('docpilot-block-selected')) continue

    isSelected = true
    if (typeof attrs.style === 'string') {
      style = {
        ...style,
        ...selectionStyleFromDecoration(attrs.style)
      }
    }
  }

  return { isSelected, style }
}

export function CodeBlockNodeView(props: NodeViewProps) {
  const propsRef = useRef(props)
  const mermaidFrameRef = useRef<HTMLDivElement | null>(null)
  const captionInputRef = useRef<HTMLTextAreaElement | null>(null)
  const copyTimerRef = useRef<number | null>(null)
  const languageMenuRef = useRef<HTMLDivElement | null>(null)
  const resizeRef = useRef<{
    corner: ResizeCorner
    startX: number
    startY: number
    startWidth: number
    startHeight: number
  } | null>(null)
  const draftWidthRef = useRef<number | null>(null)
  const [draftWidth, setDraftWidth] = useState<number | null>(null)
  const [isCollapsed, setIsCollapsed] = useState(false)
  const [copied, setCopied] = useState(false)
  const [isLanguageMenuOpen, setIsLanguageMenuOpen] = useState(false)
  const [isMermaidSourceVisible, setIsMermaidSourceVisible] = useState(false)
  const [isCaptionEditing, setIsCaptionEditing] = useState(false)
  const [captionDraft, setCaptionDraft] = useState('')
  const attrs = props.node.attrs as Record<string, unknown>
  const blockId = stringAttr(attrs, 'blockId')
  const caption = stringAttr(attrs, 'caption')
  const language = stringAttr(attrs, 'language', 'text') || 'text'
  const activeLanguage = codeLanguage(language)
  const isMermaid = activeLanguage.value === 'mermaid'
  const shouldShowFormatAction = !isMermaid
  const isMermaidPreviewMode = isMermaid && !isMermaidSourceVisible
  const shouldShowMermaidCaption = isMermaid && (isCaptionEditing || Boolean(caption))
  const storedPreviewWidth = numberAttr(attrs.width)
  const activePreviewWidth = draftWidth ?? storedPreviewWidth
  const selectionDecoration = blockSelectionDecoration(props.decorations)
  const { codeMirrorRef, codeText, editorHostRef } = useCodeMirrorNodeView({ props, language })
  const rootClassName = [
    'code-block-node',
    activeLanguage.special ? 'is-special-code-block' : '',
    `code-block-language-${activeLanguage.value}`,
    resizeRef.current ? 'is-resizing' : '',
    props.selected ? 'is-selected' : '',
    selectionDecoration.isSelected ? 'docpilot-block-selected' : '',
    isCollapsed ? 'is-collapsed' : ''
  ].filter(Boolean).join(' ')
  const mermaidFrameStyle = useMemo<CSSProperties | undefined>(
    () => (activePreviewWidth ? { width: `${activePreviewWidth}px` } : undefined),
    [activePreviewWidth]
  )

  propsRef.current = props

  useEffect(() => {
    return () => {
      if (copyTimerRef.current) {
        window.clearTimeout(copyTimerRef.current)
      }
    }
  }, [])

  useEffect(() => {
    if (!isCaptionEditing) {
      setCaptionDraft(caption)
    }
  }, [caption, isCaptionEditing])

  useEffect(() => {
    if (!isMermaid && isCaptionEditing) {
      setIsCaptionEditing(false)
    }
  }, [isCaptionEditing, isMermaid])

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
    if (!isLanguageMenuOpen) return

    function handlePointerDown(event: PointerEvent) {
      const target = event.target
      if (!(target instanceof Node)) return
      if (languageMenuRef.current?.contains(target)) return
      setIsLanguageMenuOpen(false)
    }

    window.addEventListener('pointerdown', handlePointerDown, true)
    return () => window.removeEventListener('pointerdown', handlePointerDown, true)
  }, [isLanguageMenuOpen])

  useEffect(() => {
    function handlePointerMove(event: PointerEvent) {
      const resizeState = resizeRef.current
      if (!resizeState) return
      const deltaX = event.clientX - resizeState.startX
      const deltaY = event.clientY - resizeState.startY
      const aspectRatio = resizeState.startHeight > 0 ? resizeState.startWidth / resizeState.startHeight : 1
      const widthFromX = resizeState.startWidth + deltaX * resizeState.corner.x
      const widthFromY = (resizeState.startHeight + deltaY * resizeState.corner.y) * aspectRatio
      const nextWidth = Math.abs(deltaX) > Math.abs(deltaY * aspectRatio) ? widthFromX : widthFromY
      const clampedWidth = clampSpecialBlockWidth(nextWidth)
      draftWidthRef.current = clampedWidth
      setDraftWidth(clampedWidth)
    }

    function handlePointerUp() {
      if (resizeRef.current && draftWidthRef.current) {
        propsRef.current.updateAttributes({ width: draftWidthRef.current })
      }
      resizeRef.current = null
      draftWidthRef.current = null
      setDraftWidth(null)
    }

    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
    return () => {
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
    }
  }, [])

  function selectCodeBlock() {
    const position = props.getPos()
    if (typeof position !== 'number') return
    props.editor.chain().focus().setNodeSelection(position).run()
  }

  function stopControlMouseDown(event: ReactMouseEvent) {
    event.preventDefault()
    event.stopPropagation()
  }

  function handleFrameMouseDown(event: ReactMouseEvent) {
    const target = event.target
    if (target instanceof HTMLElement && target.closest('.code-block-editor, .code-block-control')) {
      return
    }
    event.preventDefault()
    selectCodeBlock()
  }

  function toggleLanguageMenu(event: ReactMouseEvent<HTMLButtonElement>) {
    event.preventDefault()
    event.stopPropagation()
    setIsLanguageMenuOpen((open) => !open)
  }

  function selectLanguage(nextLanguage: string) {
    props.updateAttributes({ language: nextLanguage })
    setIsLanguageMenuOpen(false)
  }

  function formatCode() {
    const view = codeMirrorRef.current
    if (!view) return
    const currentText = view.state.doc.toString()
    const formattedText = formatCodeBlockText(language, currentText)
    if (formattedText === currentText) return
    view.dispatch({
      changes: {
        from: 0,
        to: currentText.length,
        insert: formattedText
      }
    })
  }

  function openCaptionEditor() {
    selectCodeBlock()
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

  async function copyCode() {
    const text = codeMirrorRef.current?.state.doc.toString() ?? codeText
    await navigator.clipboard?.writeText(text)
  }

  async function copyRenderedMermaidImage() {
    if (!navigator.clipboard?.write || typeof ClipboardItem === 'undefined') {
      throw new Error('Image clipboard is not supported')
    }

    const svg = mermaidFrameRef.current?.querySelector('.code-block-mermaid-svg')
    if (!(svg instanceof SVGSVGElement)) {
      throw new Error('Rendered diagram is not available')
    }

    const pngBlob = await svgToPngBlob(svg)
    await navigator.clipboard.write([
      new ClipboardItem({
        'image/png': pngBlob
      })
    ])
  }

  async function copyBlockContent() {
    if (isMermaidPreviewMode) {
      await copyRenderedMermaidImage()
    } else {
      await copyCode()
    }

    setCopied(true)
    if (copyTimerRef.current) {
      window.clearTimeout(copyTimerRef.current)
    }
    copyTimerRef.current = window.setTimeout(() => setCopied(false), 1200)
  }

  function startMermaidResize(event: ReactPointerEvent, corner: ResizeCorner) {
    event.preventDefault()
    event.stopPropagation()
    selectCodeBlock()
    const rect = mermaidFrameRef.current?.getBoundingClientRect()
    resizeRef.current = {
      corner,
      startX: event.clientX,
      startY: event.clientY,
      startWidth: rect?.width ?? storedPreviewWidth ?? MIN_SPECIAL_BLOCK_WIDTH,
      startHeight: rect?.height ?? MIN_SPECIAL_BLOCK_WIDTH
    }
  }

  const codeBlockHeader = (
    <div className="code-block-header" contentEditable={false}>
      <button
        className="code-block-collapse code-block-control"
        type="button"
        aria-label={isCollapsed ? 'Expand code block' : 'Collapse code block'}
        title={isCollapsed ? 'Expand code block' : 'Collapse code block'}
        onMouseDown={(event) => event.preventDefault()}
        onClick={() => setIsCollapsed((value) => !value)}
      >
        <ChevronDown size={15} />
      </button>
      <div className="code-block-actions">
        {isMermaid ? (
          <span
            className="code-block-language-static code-block-control"
            aria-label="Code language"
            onMouseDown={stopControlMouseDown}
          >
            {activeLanguage.label}
          </span>
        ) : (
          <div className={`code-block-language-menu code-block-control ${isLanguageMenuOpen ? 'open' : ''}`} ref={languageMenuRef}>
            <button
              className="code-block-language-trigger"
              type="button"
              aria-label="Code language"
              aria-haspopup="listbox"
              aria-expanded={isLanguageMenuOpen}
              onMouseDown={stopControlMouseDown}
              onClick={toggleLanguageMenu}
            >
              <span>{activeLanguage.label}</span>
              <ChevronDown size={14} />
            </button>
            {isLanguageMenuOpen ? (
              <div className="code-block-language-list" role="listbox" aria-label="Code language">
                {LANGUAGE_OPTIONS.map((option) => {
                  const selected = option.value === activeLanguage.value
                  return (
                    <button
                      key={option.value}
                      className={selected ? 'selected' : ''}
                      type="button"
                      role="option"
                      aria-selected={selected}
                      onMouseDown={stopControlMouseDown}
                      onClick={() => selectLanguage(option.value)}
                    >
                      <span>{option.label}</span>
                      {selected ? <Check size={13} /> : null}
                    </button>
                  )
                })}
              </div>
            ) : null}
          </div>
        )}
        {isMermaid ? (
          <button
            className={`code-block-action code-block-control ${shouldShowMermaidCaption ? 'is-active' : ''}`}
            type="button"
            aria-label="Edit diagram caption"
            title="Edit diagram caption"
            onMouseDown={(event) => event.preventDefault()}
            onClick={openCaptionEditor}
          >
            <Captions size={15} />
          </button>
        ) : null}
        {isMermaid ? (
          <button
            className="code-block-action code-block-control"
            type="button"
            aria-label={isMermaidSourceVisible ? 'Preview diagram' : 'Edit source'}
            title={isMermaidSourceVisible ? 'Preview diagram' : 'Edit source'}
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => setIsMermaidSourceVisible((visible) => !visible)}
          >
            {isMermaidSourceVisible ? <Eye size={15} /> : <Code2 size={15} />}
          </button>
        ) : null}
        {shouldShowFormatAction ? (
          <button
            className="code-block-action code-block-control"
            type="button"
            aria-label="Format code"
            title="Format code"
            onMouseDown={(event) => event.preventDefault()}
            onClick={formatCode}
          >
            <WandSparkles size={15} />
          </button>
        ) : null}
        <button
          className="code-block-action code-block-control"
          type="button"
          aria-label={isMermaidPreviewMode ? 'Copy diagram image' : 'Copy code'}
          title={isMermaidPreviewMode ? 'Copy diagram image' : 'Copy code'}
          onMouseDown={(event) => event.preventDefault()}
          onClick={() => void copyBlockContent()}
        >
          {copied ? <Check size={15} /> : <Copy size={15} />}
        </button>
      </div>
    </div>
  )

  return (
    <NodeViewWrapper
      as="div"
      className={rootClassName}
      contentEditable={false}
      data-block-id={blockId || undefined}
      data-language={activeLanguage.value || undefined}
      style={selectionDecoration.style}
    >
      <div className="code-block-frame" onMouseDown={handleFrameMouseDown}>
        {isMermaid ? (
          <div className="code-block-mermaid-shell" style={mermaidFrameStyle}>
            <div className="code-block-mermaid-frame" ref={mermaidFrameRef}>
              {codeBlockHeader}
              {!isMermaidSourceVisible ? <MermaidPreview blockId={blockId || 'mermaid'} source={codeText} /> : null}
              <div className={`code-block-editor ${!isMermaidSourceVisible ? 'is-preview-hidden' : ''}`} ref={editorHostRef} />
              <span className="code-block-resize-handle handle-top-left" onPointerDown={(event) => startMermaidResize(event, { x: -1, y: -1 })} />
              <span className="code-block-resize-handle handle-top-right" onPointerDown={(event) => startMermaidResize(event, { x: 1, y: -1 })} />
              <span className="code-block-resize-handle handle-bottom-left" onPointerDown={(event) => startMermaidResize(event, { x: -1, y: 1 })} />
              <span className="code-block-resize-handle handle-bottom-right" onPointerDown={(event) => startMermaidResize(event, { x: 1, y: 1 })} />
            </div>
            {shouldShowMermaidCaption ? (
              <div className="code-block-mermaid-caption code-block-control" contentEditable={false}>
                {isCaptionEditing ? (
                  <textarea
                    ref={captionInputRef}
                    value={captionDraft}
                    placeholder="Add caption"
                    aria-label="Diagram caption"
                    rows={1}
                    onChange={handleCaptionChange}
                    onBlur={() => commitCaption()}
                    onKeyDown={handleCaptionKeyDown}
                    onMouseDown={(event) => event.stopPropagation()}
                  />
                ) : (
                  <button
                    type="button"
                    className="code-block-mermaid-caption-text"
                    aria-label="Edit diagram caption"
                    onMouseDown={(event) => event.stopPropagation()}
                    onClick={openCaptionEditor}
                  >
                    {caption}
                  </button>
                )}
              </div>
            ) : null}
          </div>
        ) : (
          <>
            {codeBlockHeader}
            <div className="code-block-editor" ref={editorHostRef} />
          </>
        )}
      </div>
    </NodeViewWrapper>
  )
}
