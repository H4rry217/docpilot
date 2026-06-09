import { NodeViewWrapper, type NodeViewProps } from '@tiptap/react'
import { useEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react'
import { formatCodeBlockText } from '../model/codeBlockFormatter'
import './CodeBlockNodeView.css'
import { CodeBlockToolbar } from './CodeBlockToolbar'
import {
  blockSelectionDecoration,
  codeBlockWidthAttr,
  stringAttr
} from './codeBlockNodeViewUtils'
import { codeLanguage } from './codeLanguages'
import { MermaidCaptionEditor } from './MermaidCaptionEditor'
import { MermaidPreview } from './MermaidPreview'
import { svgToPngBlob } from './svgToPngBlob'
import { useCodeMirrorNodeView } from './useCodeMirrorNodeView'
import { useMermaidResize } from './useMermaidResize'

export function CodeBlockNodeView(props: NodeViewProps) {
  const mermaidFrameRef = useRef<HTMLDivElement | null>(null)
  const copyTimerRef = useRef<number | null>(null)
  const [isCollapsed, setIsCollapsed] = useState(false)
  const [copied, setCopied] = useState(false)
  const [isMermaidSourceVisible, setIsMermaidSourceVisible] = useState(false)
  const [isCaptionEditing, setIsCaptionEditing] = useState(false)
  const attrs = props.node.attrs as Record<string, unknown>
  const blockId = stringAttr(attrs, 'blockId')
  const caption = stringAttr(attrs, 'caption')
  const language = stringAttr(attrs, 'language', 'text') || 'text'
  const activeLanguage = codeLanguage(language)
  const isMermaid = activeLanguage.value === 'mermaid'
  const shouldShowFormatAction = !isMermaid
  const isMermaidPreviewMode = isMermaid && !isMermaidSourceVisible
  const shouldShowMermaidCaption = isMermaid && (isCaptionEditing || Boolean(caption))
  const storedPreviewWidth = codeBlockWidthAttr(attrs.width)
  const selectionDecoration = blockSelectionDecoration(props.decorations)
  const { codeMirrorRef, codeText, editorHostRef } = useCodeMirrorNodeView({ props, language })
  const { activePreviewWidth, isResizing, startMermaidResize } = useMermaidResize({
    frameRef: mermaidFrameRef,
    storedPreviewWidth,
    onCommitWidth: (width) => props.updateAttributes({ width }),
    onSelect: selectCodeBlock
  })
  const rootClassName = [
    'code-block-node',
    activeLanguage.special ? 'is-special-code-block' : '',
    `code-block-language-${activeLanguage.value}`,
    isResizing ? 'is-resizing' : '',
    props.selected ? 'is-selected' : '',
    selectionDecoration.isSelected ? 'docpilot-block-selected' : '',
    isCollapsed ? 'is-collapsed' : ''
  ].filter(Boolean).join(' ')
  const mermaidFrameStyle = useMemo(
    () => (activePreviewWidth ? { width: `${activePreviewWidth}px` } : undefined),
    [activePreviewWidth]
  )

  useEffect(() => {
    return () => {
      if (copyTimerRef.current) {
        window.clearTimeout(copyTimerRef.current)
      }
    }
  }, [])

  useEffect(() => {
    if (!isMermaid && isCaptionEditing) {
      setIsCaptionEditing(false)
    }
  }, [isCaptionEditing, isMermaid])

  function selectCodeBlock() {
    const position = props.getPos()
    if (typeof position !== 'number') return
    props.editor.chain().focus().setNodeSelection(position).run()
  }

  function handleFrameMouseDown(event: ReactMouseEvent) {
    const target = event.target
    if (target instanceof HTMLElement && target.closest('.code-block-editor, .code-block-control')) {
      return
    }
    event.preventDefault()
    selectCodeBlock()
  }

  function selectLanguage(nextLanguage: string) {
    props.updateAttributes({ language: nextLanguage })
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
    setIsCaptionEditing(true)
  }

  function commitCaption(nextCaption: string) {
    props.updateAttributes({ caption: nextCaption })
    setIsCaptionEditing(false)
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

  const codeBlockHeader = (
    <CodeBlockToolbar
      activeLanguage={activeLanguage}
      copied={copied}
      isCollapsed={isCollapsed}
      isMermaid={isMermaid}
      isMermaidPreviewMode={isMermaidPreviewMode}
      isMermaidSourceVisible={isMermaidSourceVisible}
      shouldShowFormatAction={shouldShowFormatAction}
      shouldShowMermaidCaption={shouldShowMermaidCaption}
      onCopyBlockContent={() => void copyBlockContent()}
      onEditCaption={openCaptionEditor}
      onFormatCode={formatCode}
      onSelectLanguage={selectLanguage}
      onToggleCollapsed={() => setIsCollapsed((value) => !value)}
      onToggleMermaidSource={() => setIsMermaidSourceVisible((visible) => !visible)}
    />
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
              <MermaidCaptionEditor
                caption={caption}
                isEditing={isCaptionEditing}
                onCancel={() => setIsCaptionEditing(false)}
                onCommit={commitCaption}
                onOpen={openCaptionEditor}
              />
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
