import { history, historyKeymap, indentWithTab, defaultKeymap } from '@codemirror/commands'
import { Compartment, EditorState, type Extension } from '@codemirror/state'
import {
  Decoration,
  EditorView,
  MatchDecorator,
  ViewPlugin,
  type DecorationSet,
  type ViewUpdate,
  drawSelection,
  highlightActiveLine,
  highlightActiveLineGutter,
  highlightSpecialChars,
  keymap,
  lineNumbers
} from '@codemirror/view'
import { NodeViewWrapper, type NodeViewProps } from '@tiptap/react'
import { Check, ChevronDown, Copy, WandSparkles } from 'lucide-react'
import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type MouseEvent as ReactMouseEvent
} from 'react'
import { formatCodeBlockText } from '../model/codeBlockFormatter'
import './CodeBlockNodeView.css'

type CodeLanguage = {
  value: string
  label: string
  keywords: string[]
}

const LANGUAGE_OPTIONS: CodeLanguage[] = [
  {
    value: 'java',
    label: 'Java',
    keywords: [
      'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char', 'class', 'const', 'continue',
      'default', 'double', 'else', 'enum', 'extends', 'final', 'finally', 'float', 'for', 'if', 'implements',
      'import', 'instanceof', 'int', 'interface', 'long', 'new', 'package', 'private', 'protected', 'public',
      'return', 'short', 'static', 'strictfp', 'super', 'switch', 'synchronized', 'this', 'throw', 'throws',
      'transient', 'try', 'void', 'volatile', 'while', 'true', 'false', 'null', 'String'
    ]
  },
  {
    value: 'javascript',
    label: 'JavaScript',
    keywords: [
      'async', 'await', 'break', 'case', 'catch', 'class', 'const', 'continue', 'debugger', 'default', 'delete',
      'do', 'else', 'export', 'extends', 'finally', 'for', 'from', 'function', 'if', 'import', 'in', 'instanceof',
      'let', 'new', 'of', 'return', 'static', 'super', 'switch', 'this', 'throw', 'try', 'typeof', 'undefined',
      'var', 'void', 'while', 'yield', 'true', 'false', 'null'
    ]
  },
  {
    value: 'typescript',
    label: 'TypeScript',
    keywords: [
      'abstract', 'as', 'async', 'await', 'boolean', 'break', 'case', 'catch', 'class', 'const', 'continue',
      'declare', 'default', 'delete', 'do', 'else', 'enum', 'export', 'extends', 'finally', 'for', 'from',
      'function', 'if', 'implements', 'import', 'in', 'interface', 'let', 'module', 'namespace', 'new', 'number',
      'of', 'private', 'protected', 'public', 'readonly', 'return', 'static', 'string', 'super', 'switch', 'this',
      'throw', 'try', 'type', 'typeof', 'undefined', 'var', 'void', 'while', 'yield', 'true', 'false', 'null'
    ]
  },
  {
    value: 'python',
    label: 'Python',
    keywords: [
      'and', 'as', 'assert', 'async', 'await', 'break', 'class', 'continue', 'def', 'del', 'elif', 'else',
      'except', 'False', 'finally', 'for', 'from', 'global', 'if', 'import', 'in', 'is', 'lambda', 'None',
      'nonlocal', 'not', 'or', 'pass', 'raise', 'return', 'True', 'try', 'while', 'with', 'yield'
    ]
  },
  {
    value: 'html',
    label: 'HTML',
    keywords: ['html', 'head', 'body', 'div', 'section', 'article', 'main', 'span', 'a', 'img', 'button', 'script', 'style']
  },
  {
    value: 'css',
    label: 'CSS',
    keywords: ['align-items', 'background', 'border', 'color', 'display', 'flex', 'font-size', 'grid', 'height', 'margin', 'padding', 'position', 'width']
  },
  {
    value: 'json',
    label: 'JSON',
    keywords: ['true', 'false', 'null']
  },
  {
    value: 'markdown',
    label: 'Markdown',
    keywords: []
  },
  {
    value: 'text',
    label: 'Text',
    keywords: []
  }
]

const CODE_KEYMAP = [...defaultKeymap, ...historyKeymap, indentWithTab]

function textFromNode(props: NodeViewProps): string {
  return props.node.textContent
}

function stringAttr(attrs: Record<string, unknown>, name: string, fallback = ''): string {
  const value = attrs[name]
  return typeof value === 'string' ? value : fallback
}

function codeLanguage(value: string): CodeLanguage {
  return LANGUAGE_OPTIONS.find((option) => option.value === value) ?? LANGUAGE_OPTIONS[LANGUAGE_OPTIONS.length - 1]
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

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function syntaxExtension(languageValue: string): Extension {
  const language = codeLanguage(languageValue)
  const keywords = language.keywords.length ? language.keywords.map(escapeRegExp).join('|') : '$.'
  const matcher = new MatchDecorator({
    regexp: new RegExp(
      [
        '//.*$',
        '#.*$',
        '/\\*[^]*?\\*/',
        '"(?:\\\\.|[^"\\\\])*"',
        "'(?:\\\\.|[^'\\\\])*'",
        '`(?:\\\\.|[^`\\\\])*`',
        '</?[A-Za-z][\\w:-]*',
        '\\b(?:' + keywords + ')\\b',
        '\\b\\d+(?:\\.\\d+)?\\b'
      ].join('|'),
      'g'
    ),
    decorate(add, from, to, match) {
      const token = match[0]
      let className = 'cm-dp-token-keyword'
      if (token.startsWith('//') || token.startsWith('#') || token.startsWith('/*')) {
        className = 'cm-dp-token-comment'
      } else if (token.startsWith('"') || token.startsWith("'") || token.startsWith('`')) {
        className = 'cm-dp-token-string'
      } else if (/^\d/.test(token)) {
        className = 'cm-dp-token-number'
      } else if (token.startsWith('<')) {
        className = 'cm-dp-token-tag'
      }
      add(from, to, Decoration.mark({ class: className }))
    }
  })

  return ViewPlugin.fromClass(
    class {
      decorations: DecorationSet

      constructor(view: EditorView) {
        this.decorations = matcher.createDeco(view)
      }

      update(update: ViewUpdate) {
        this.decorations = matcher.updateDeco(update, this.decorations)
      }
    },
    {
      decorations: (plugin) => plugin.decorations
    }
  )
}

export function CodeBlockNodeView(props: NodeViewProps) {
  const propsRef = useRef(props)
  const editorHostRef = useRef<HTMLDivElement | null>(null)
  const codeMirrorRef = useRef<EditorView | null>(null)
  const languageCompartmentRef = useRef(new Compartment())
  const applyingExternalChangeRef = useRef(false)
  const copyTimerRef = useRef<number | null>(null)
  const languageMenuRef = useRef<HTMLDivElement | null>(null)
  const [isCollapsed, setIsCollapsed] = useState(false)
  const [copied, setCopied] = useState(false)
  const [isLanguageMenuOpen, setIsLanguageMenuOpen] = useState(false)
  const attrs = props.node.attrs as Record<string, unknown>
  const blockId = stringAttr(attrs, 'blockId')
  const language = stringAttr(attrs, 'language', 'text') || 'text'
  const activeLanguage = codeLanguage(language)
  const selectionDecoration = blockSelectionDecoration(props.decorations)
  const rootClassName = [
    'code-block-node',
    props.selected ? 'is-selected' : '',
    selectionDecoration.isSelected ? 'docpilot-block-selected' : '',
    isCollapsed ? 'is-collapsed' : ''
  ].filter(Boolean).join(' ')

  propsRef.current = props

  const baseExtensions = useMemo<Extension[]>(() => [
    lineNumbers(),
    highlightSpecialChars(),
    history(),
    drawSelection(),
    highlightActiveLine(),
    highlightActiveLineGutter(),
    keymap.of(CODE_KEYMAP),
    EditorState.tabSize.of(2),
    EditorView.updateListener.of((update) => {
      if (!update.docChanged || applyingExternalChangeRef.current) return
      syncCodeTextToProseMirror(update.state.doc.toString())
    }),
    EditorView.domEventHandlers({
      click: (event) => {
        event.stopPropagation()
        return false
      },
      keydown: (event) => {
        event.stopPropagation()
        return false
      },
      mousedown: (event) => {
        event.stopPropagation()
        return false
      }
    })
  ], [])

  useEffect(() => {
    const host = editorHostRef.current
    if (!host) return

    const view = new EditorView({
      parent: host,
      state: EditorState.create({
        doc: textFromNode(propsRef.current),
        extensions: [
          ...baseExtensions,
          languageCompartmentRef.current.of(syntaxExtension(language))
        ]
      })
    })
    codeMirrorRef.current = view

    return () => {
      view.destroy()
      codeMirrorRef.current = null
    }
  }, [baseExtensions])

  useEffect(() => {
    const view = codeMirrorRef.current
    if (!view) return
    view.dispatch({
      effects: languageCompartmentRef.current.reconfigure(syntaxExtension(language))
    })
  }, [language])

  useEffect(() => {
    const view = codeMirrorRef.current
    if (!view) return
    const nextText = textFromNode(props)
    const currentText = view.state.doc.toString()
    if (currentText === nextText) return
    applyingExternalChangeRef.current = true
    view.dispatch({
      changes: {
        from: 0,
        to: currentText.length,
        insert: nextText
      }
    })
    applyingExternalChangeRef.current = false
  }, [props])

  useEffect(() => {
    return () => {
      if (copyTimerRef.current) {
        window.clearTimeout(copyTimerRef.current)
      }
    }
  }, [])

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

  function syncCodeTextToProseMirror(text: string) {
    const activeProps = propsRef.current
    const position = activeProps.getPos()
    if (typeof position !== 'number') return
    const editorView = activeProps.editor.view
    const from = position + 1
    const to = position + activeProps.node.nodeSize - 1
    editorView.dispatch(editorView.state.tr.insertText(text, from, to))
  }

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

  async function copyCode() {
    const text = codeMirrorRef.current?.state.doc.toString() ?? textFromNode(props)
    await navigator.clipboard?.writeText(text)
    setCopied(true)
    if (copyTimerRef.current) {
      window.clearTimeout(copyTimerRef.current)
    }
    copyTimerRef.current = window.setTimeout(() => setCopied(false), 1200)
  }

  return (
    <NodeViewWrapper
      as="div"
      className={rootClassName}
      contentEditable={false}
      data-block-id={blockId || undefined}
      data-language={language || undefined}
      style={selectionDecoration.style}
    >
      <div className="code-block-frame" onMouseDown={handleFrameMouseDown}>
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
            <button
              className="code-block-action code-block-control"
              type="button"
              aria-label="Copy code"
              title="Copy code"
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => void copyCode()}
            >
              {copied ? <Check size={15} /> : <Copy size={15} />}
            </button>
          </div>
        </div>
        <div className="code-block-editor" ref={editorHostRef} />
      </div>
    </NodeViewWrapper>
  )
}
