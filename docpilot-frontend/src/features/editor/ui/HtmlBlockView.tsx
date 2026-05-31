import { NodeViewWrapper, type NodeViewProps } from '@tiptap/react'
import { Code2 } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useI18n } from '../../../shared/i18n'
import {
  booleanAttr,
  createHtmlPreviewDocument,
  DEFAULT_HTML_BLOCK_HEIGHT,
  normalizeDisplayMode,
  numberAttr,
  sandboxForHtmlBlock
} from '../model/htmlBlockPreview'
import { HtmlSourceEditor } from './HtmlSourceEditor'
import './HtmlBlockView.css'

function textAttr(attrs: Record<string, unknown>, name: string, fallback: string): string {
  const value = attrs[name]
  return typeof value === 'string' ? value : fallback
}

export function HtmlBlockView(props: NodeViewProps) {
  const { t } = useI18n()
  const iframeRef = useRef<HTMLIFrameElement | null>(null)
  const [viewMode, setViewMode] = useState<'preview' | 'source'>('preview')
  const attrs = props.node.attrs as Record<string, unknown>
  const title = textAttr(attrs, 'title', 'HTML')
  const source = textAttr(attrs, 'source', '')
  const blockId = textAttr(attrs, 'id', textAttr(attrs, 'blockId', 'html-preview'))
  const allowScripts = booleanAttr(attrs.allowScripts)
  const displayMode = normalizeDisplayMode(attrs.displayMode)
  const fixedHeight = numberAttr(attrs.fixedHeightPx, DEFAULT_HTML_BLOCK_HEIGHT)
  const [autoHeight, setAutoHeight] = useState(fixedHeight)
  const iframeHeight = displayMode === 'auto' ? autoHeight : fixedHeight
  const srcDoc = useMemo(
    () => createHtmlPreviewDocument(source || '<div></div>', blockId, displayMode === 'auto'),
    [blockId, displayMode, source]
  )

  useEffect(() => {
    if (displayMode !== 'auto') return

    function handleMessage(event: MessageEvent) {
      const data: unknown = event.data
      if (!data || typeof data !== 'object') return
      const message = data as Record<string, unknown>
      if (message.type !== 'docpilot-html-block-height' || message.id !== blockId) return
      setAutoHeight(numberAttr(message.height, fixedHeight))
    }

    window.addEventListener('message', handleMessage)
    return () => window.removeEventListener('message', handleMessage)
  }, [blockId, displayMode, fixedHeight])

  function measureWithoutScripts() {
    if (displayMode !== 'auto' || allowScripts) return
    const frameDocument = iframeRef.current?.contentDocument
    if (!frameDocument) return
    const body = frameDocument.body
    const root = frameDocument.documentElement
    setAutoHeight(
      numberAttr(
        Math.max(body?.scrollHeight ?? 0, body?.offsetHeight ?? 0, root?.scrollHeight ?? 0, root?.offsetHeight ?? 0),
        fixedHeight
      )
    )
  }

  function updateDisplayMode(nextDisplayMode: 'fixed' | 'auto') {
    props.updateAttributes({ displayMode: nextDisplayMode })
  }

  function updateAllowScripts(nextAllowScripts: boolean) {
    props.updateAttributes({ allowScripts: nextAllowScripts })
  }

  function updateFixedHeight(nextHeight: number) {
    props.updateAttributes({ fixedHeightPx: numberAttr(nextHeight, fixedHeight) })
  }

  function updateSource(nextSource: string) {
    props.updateAttributes({ source: nextSource })
  }

  return (
    <NodeViewWrapper
      className="html-block"
      data-docpilot-html-block=""
      data-html-source={source}
      data-html-title={title}
      data-display-mode={displayMode}
      data-allow-scripts={allowScripts ? 'true' : 'false'}
    >
      <div className="html-block-header">
        <div className="html-block-title">
          <Code2 size={15} />
          <span>{title}</span>
        </div>
        <div className="html-block-controls" contentEditable={false}>
          <label>
            <span>JS</span>
            <input
              type="checkbox"
              checked={allowScripts}
              onChange={(event) => updateAllowScripts(event.target.checked)}
            />
          </label>
          <div className="html-block-mode-switch" role="tablist" aria-label="HTML block mode">
            <button
              className={viewMode === 'preview' ? 'active' : ''}
              type="button"
              onClick={() => setViewMode('preview')}
            >
              {t('html.preview')}
            </button>
            <button
              className={viewMode === 'source' ? 'active' : ''}
              type="button"
              onClick={() => setViewMode('source')}
            >
              {t('html.source')}
            </button>
          </div>
          <select value={displayMode} onChange={(event) => updateDisplayMode(event.target.value as 'fixed' | 'auto')}>
            <option value="fixed">{t('html.fixed')}</option>
            <option value="auto">{t('html.auto')}</option>
          </select>
          {displayMode === 'fixed' ? (
            <input
              aria-label="HTML block fixed height"
              type="number"
              min={120}
              max={1600}
              step={20}
              value={fixedHeight}
              onChange={(event) => updateFixedHeight(Number(event.target.value))}
            />
          ) : null}
        </div>
      </div>
      {viewMode === 'preview' ? (
        <iframe
          ref={iframeRef}
          title={title}
          sandbox={sandboxForHtmlBlock(allowScripts)}
          srcDoc={srcDoc}
          style={{ height: iframeHeight }}
          onLoad={measureWithoutScripts}
        />
      ) : (
        <HtmlSourceEditor source={source} onChange={updateSource} />
      )}
    </NodeViewWrapper>
  )
}
