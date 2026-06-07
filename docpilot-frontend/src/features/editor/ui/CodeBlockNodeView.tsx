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
import { MermaidPreview } from './MermaidPreview'

type CodeLanguage = {
  value: string
  label: string
  keywords: string[]
  special?: boolean
}

type ResizeCorner = {
  x: -1 | 1
  y: -1 | 1
}

const MIN_SPECIAL_BLOCK_WIDTH = 240
const MAX_SPECIAL_BLOCK_WIDTH = 720

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
    value: 'yaml',
    label: 'YAML',
    keywords: ['true', 'false', 'null', 'yes', 'no', 'on', 'off']
  },
  {
    value: 'markdown',
    label: 'Markdown',
    keywords: []
  },
  {
    value: 'mermaid',
    label: 'Mermaid',
    keywords: ['graph', 'flowchart', 'TD', 'TB', 'BT', 'LR', 'RL', 'subgraph', 'end', 'classDef', 'class', 'style', 'linkStyle', 'click'],
    special: true
  },
  {
    value: 'text',
    label: 'Text',
    keywords: []
  }
]

const LANGUAGE_ALIASES: Record<string, string> = {
  js: 'javascript',
  ts: 'typescript',
  md: 'markdown',
  yml: 'yaml'
}

const CODE_KEYMAP = [...defaultKeymap, ...historyKeymap, indentWithTab]

function textFromNode(props: NodeViewProps): string {
  return props.node.textContent
}

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

const SVG_STYLE_PROPERTIES = [
  'color',
  'dominant-baseline',
  'fill',
  'font-family',
  'font-size',
  'font-style',
  'font-weight',
  'letter-spacing',
  'stroke',
  'stroke-linecap',
  'stroke-width',
  'text-anchor'
]

function codeLanguage(value: string): CodeLanguage {
  const normalized = LANGUAGE_ALIASES[value.toLowerCase()] ?? value.toLowerCase()
  return LANGUAGE_OPTIONS.find((option) => option.value === normalized) ?? LANGUAGE_OPTIONS[LANGUAGE_OPTIONS.length - 1]
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

function syntaxPattern(language: CodeLanguage): RegExp {
  const keywords = language.keywords.length ? language.keywords.map(escapeRegExp).join('|') : '$.'

  if (language.value === 'yaml') {
    return new RegExp(
      [
        '(^|\\n)(\\s*-\\s*)?([A-Za-z_][\\w.-]*)(?=\\s*:)',
        '(^|\\n)(\\s*-)(?=\\s+)',
        '#[^\\n]*',
        '"(?:\\\\.|[^"\\\\])*"',
        "'(?:\\\\.|[^'\\\\])*'",
        '\\b(?:' + keywords + ')\\b',
        '\\b\\d+(?:\\.\\d+)?\\b'
      ].join('|'),
      'g'
    )
  }

  return new RegExp(
    [
      '//[^\\n]*',
      '#[^\\n]*',
      '/\\*[^]*?\\*/',
      '"(?:\\\\.|[^"\\\\])*"',
      "'(?:\\\\.|[^'\\\\])*'",
      '`(?:\\\\.|[^`\\\\])*`',
      '</?[A-Za-z][\\w:-]*',
      '\\b(?:' + keywords + ')\\b',
      '\\b\\d+(?:\\.\\d+)?\\b'
    ].join('|'),
    'g'
  )
}

function syntaxExtension(languageValue: string): Extension {
  const language = codeLanguage(languageValue)
  const matcher = new MatchDecorator({
    regexp: syntaxPattern(language),
    decorate(add, from, to, match) {
      const token = match[0]
      let className = 'cm-dp-token-keyword'

      if (language.value === 'yaml' && match[3]) {
        const offset = token.lastIndexOf(match[3])
        add(from + offset, from + offset + match[3].length, Decoration.mark({ class: 'cm-dp-token-key' }))
        return
      }

      if (language.value === 'yaml' && match[5]) {
        const offset = token.lastIndexOf(match[5])
        add(from + offset, from + offset + match[5].length, Decoration.mark({ class: 'cm-dp-token-punctuation' }))
        return
      }

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

function copyComputedSvgStyles(sourceSvg: SVGSVGElement, targetSvg: SVGSVGElement) {
  const sourceElements = [sourceSvg, ...sourceSvg.querySelectorAll('*')]
  const targetElements = [targetSvg, ...targetSvg.querySelectorAll('*')]

  for (let index = 0; index < sourceElements.length; index += 1) {
    const sourceElement = sourceElements[index]
    const targetElement = targetElements[index]
    if (!targetElement) continue

    const computedStyle = window.getComputedStyle(sourceElement)
    const declarations = SVG_STYLE_PROPERTIES
      .map((property) => {
        const value = computedStyle.getPropertyValue(property)
        return value ? `${property}: ${value}` : ''
      })
      .filter(Boolean)

    if (declarations.length) {
      targetElement.setAttribute('style', declarations.join('; '))
    }
  }
}

function canvasToPngBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) {
        resolve(blob)
        return
      }
      reject(new Error('Failed to create diagram image'))
    }, 'image/png')
  })
}

function loadImage(source: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('Failed to load diagram image'))
    image.src = source
  })
}

async function svgToPngBlob(svg: SVGSVGElement): Promise<Blob> {
  const clonedSvg = svg.cloneNode(true) as SVGSVGElement
  copyComputedSvgStyles(svg, clonedSvg)

  const rect = svg.getBoundingClientRect()
  const viewBox = svg.viewBox.baseVal
  const width = Math.max(1, Math.round(rect.width || viewBox.width || 640))
  const height = Math.max(1, Math.round(rect.height || viewBox.height || 360))
  clonedSvg.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
  clonedSvg.setAttribute('width', String(width))
  clonedSvg.setAttribute('height', String(height))

  if (!clonedSvg.getAttribute('viewBox') && viewBox.width > 0 && viewBox.height > 0) {
    clonedSvg.setAttribute('viewBox', `${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`)
  }

  const serializedSvg = new XMLSerializer().serializeToString(clonedSvg)
  const svgBlob = new Blob([serializedSvg], { type: 'image/svg+xml;charset=utf-8' })
  const imageUrl = URL.createObjectURL(svgBlob)

  try {
    const image = await loadImage(imageUrl)
    const scale = Math.max(1, Math.min(3, window.devicePixelRatio || 1))
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(width * scale)
    canvas.height = Math.round(height * scale)

    const context = canvas.getContext('2d')
    if (!context) {
      throw new Error('Canvas is not available')
    }

    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.drawImage(image, 0, 0, canvas.width, canvas.height)
    return await canvasToPngBlob(canvas)
  } finally {
    URL.revokeObjectURL(imageUrl)
  }
}

export function CodeBlockNodeView(props: NodeViewProps) {
  const propsRef = useRef(props)
  const editorHostRef = useRef<HTMLDivElement | null>(null)
  const mermaidFrameRef = useRef<HTMLDivElement | null>(null)
  const captionInputRef = useRef<HTMLTextAreaElement | null>(null)
  const codeMirrorRef = useRef<EditorView | null>(null)
  const languageCompartmentRef = useRef(new Compartment())
  const applyingExternalChangeRef = useRef(false)
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
  const [codeText, setCodeText] = useState(() => textFromNode(props))
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
      const nextText = update.state.doc.toString()
      setCodeText(nextText)
      syncCodeTextToProseMirror(nextText)
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
    const host = editorHostRef.current
    const view = codeMirrorRef.current
    if (!host || !view || view.dom.parentElement === host) return
    host.appendChild(view.dom)
  })

  useEffect(() => {
    const view = codeMirrorRef.current
    if (!view) return
    const nextText = textFromNode(props)
    const currentText = view.state.doc.toString()
    setCodeText(nextText)
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
