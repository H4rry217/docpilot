import { Captions, Check, ChevronDown, Code2, Copy, Eye, WandSparkles } from 'lucide-react'
import { useEffect, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react'
import { LANGUAGE_OPTIONS, type CodeLanguage } from './codeLanguages'

function stopControlMouseDown(event: ReactMouseEvent) {
  event.preventDefault()
  event.stopPropagation()
}

export function CodeBlockToolbar({
  activeLanguage,
  copied,
  isCollapsed,
  isMermaid,
  isMermaidPreviewMode,
  isMermaidSourceVisible,
  shouldShowFormatAction,
  shouldShowMermaidCaption,
  onCopyBlockContent,
  onEditCaption,
  onFormatCode,
  onSelectLanguage,
  onToggleCollapsed,
  onToggleMermaidSource
}: {
  activeLanguage: CodeLanguage
  copied: boolean
  isCollapsed: boolean
  isMermaid: boolean
  isMermaidPreviewMode: boolean
  isMermaidSourceVisible: boolean
  shouldShowFormatAction: boolean
  shouldShowMermaidCaption: boolean
  onCopyBlockContent: () => void
  onEditCaption: () => void
  onFormatCode: () => void
  onSelectLanguage: (language: string) => void
  onToggleCollapsed: () => void
  onToggleMermaidSource: () => void
}) {
  const languageMenuRef = useRef<HTMLDivElement | null>(null)
  const [isLanguageMenuOpen, setIsLanguageMenuOpen] = useState(false)

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

  function toggleLanguageMenu(event: ReactMouseEvent<HTMLButtonElement>) {
    event.preventDefault()
    event.stopPropagation()
    setIsLanguageMenuOpen((open) => !open)
  }

  function selectLanguage(nextLanguage: string) {
    onSelectLanguage(nextLanguage)
    setIsLanguageMenuOpen(false)
  }

  return (
    <div className="code-block-header" contentEditable={false}>
      <button
        className="code-block-collapse code-block-control"
        type="button"
        aria-label={isCollapsed ? 'Expand code block' : 'Collapse code block'}
        title={isCollapsed ? 'Expand code block' : 'Collapse code block'}
        onMouseDown={(event) => event.preventDefault()}
        onClick={onToggleCollapsed}
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
            onClick={onEditCaption}
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
            onClick={onToggleMermaidSource}
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
            onClick={onFormatCode}
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
          onClick={onCopyBlockContent}
        >
          {copied ? <Check size={15} /> : <Copy size={15} />}
        </button>
      </div>
    </div>
  )
}
