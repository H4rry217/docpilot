import { Mark, mergeAttributes, Node, type NodeViewRendererProps } from '@tiptap/core'
import { ReactNodeViewRenderer } from '@tiptap/react'
import type { DOMOutputSpec } from '@tiptap/pm/model'
import type { NodeView, ViewMutationRecord } from '@tiptap/pm/view'
import { ImageNodeView } from '../ui/ImageNodeView'
import { normalizeLatexSource, renderLatexMath } from './docpilotMathRendering'

type HtmlAttrs = Record<string, unknown>

const blockAttributes = {
  blockId: { default: '' },
  sourceRange: { default: null },
  format: { default: '' },
  raw: { default: '' },
  data: { default: null },
  notation: { default: '' },
  text: { default: '' },
  delimiter: { default: '' },
  engine: { default: '' },
  kind: { default: '' },
  title: { default: '' },
  collapsible: { default: false },
  open: { default: true },
  label: { default: '' },
  href: { default: '' },
  source: { default: '' },
  nodeType: { default: '' }
}

const inlineAttributes = {
  sourceRange: { default: null },
  text: { default: '' },
  notation: { default: '' },
  delimiter: { default: '' },
  label: { default: '' },
  source: { default: '' },
  shortcut: { default: '' },
  nodeType: { default: '' }
}

function renderedAttrs(attributes: HtmlAttrs, extra: HtmlAttrs = {}) {
  const attrs = { ...attributes }
  const blockId = attrs.blockId
  delete attrs.blockId
  delete attrs.sourceRange
  delete attrs.nodeType

  return mergeAttributes(
    attrs,
    typeof blockId === 'string' && blockId ? { 'data-block-id': blockId } : {},
    extra
  )
}

function renderedHiddenBlockAttrs(attributes: HtmlAttrs, extra: HtmlAttrs = {}) {
  const blockId = attributes.blockId

  return mergeAttributes(
    typeof blockId === 'string' && blockId ? { 'data-block-id': blockId } : {},
    extra
  )
}

function stringAttr(attributes: HtmlAttrs, key: string, fallback = ''): string {
  const value = attributes[key]
  return typeof value === 'string' ? value : fallback
}

function booleanAttr(value: unknown, fallback = false): boolean {
  if (typeof value === 'boolean') return value
  if (typeof value === 'string') return value === 'true'
  return fallback
}

function footnoteAnchorToken(label: string): string {
  const value = label.trim() || 'fn'
  const token = Array.from(value)
    .map((character) => (/^[A-Za-z0-9_-]$/.test(character)
      ? character
      : `-${character.codePointAt(0)?.toString(16) ?? 'x'}-`))
    .join('')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')

  return token || 'fn'
}

function footnoteDefinitionId(label: string): string {
  return `docpilot-footnote-${footnoteAnchorToken(label)}`
}

function footnoteReferenceId(label: string): string {
  return `docpilot-footnote-ref-${footnoteAnchorToken(label)}`
}

function renderedCalloutAttrs(attributes: HtmlAttrs, extra: HtmlAttrs = {}) {
  const attrs = { ...attributes }
  delete attrs.kind
  delete attrs.title
  delete attrs.collapsible
  delete attrs.open
  return renderedAttrs(attrs, extra)
}

function imageAlignmentAttr(value: unknown): 'left' | 'center' | 'right' {
  return value === 'center' || value === 'right' ? value : 'left'
}

function imageDimensionAttr(value: unknown): number | null {
  const number = typeof value === 'number' ? value : typeof value === 'string' ? Number.parseInt(value, 10) : Number.NaN
  if (!Number.isFinite(number)) return null
  const rounded = Math.trunc(number)
  return rounded > 0 ? rounded : null
}

function renderedImageAttrs(attributes: HtmlAttrs): HtmlAttrs {
  const src = stringAttr(attributes, 'src')
  const alt = stringAttr(attributes, 'alt')
  const title = stringAttr(attributes, 'title')
  const caption = stringAttr(attributes, 'caption')
  const alignment = imageAlignmentAttr(attributes.alignment)
  const width = imageDimensionAttr(attributes.width)

  return mergeAttributes(
    {
      src,
      alt,
      ...(title ? { title } : {}),
      ...(caption ? { 'data-caption': caption } : {}),
      'data-alignment': alignment,
      ...(width ? { width: String(width), 'data-width': String(width) } : {})
    }
  )
}

function renderLeafBlock(label: string, attributes: HtmlAttrs): DOMOutputSpec {
  const text = stringAttr(attributes, 'text') || stringAttr(attributes, 'source') || stringAttr(attributes, 'raw')
  return [
    'div',
    renderedAttrs(attributes, { class: 'docpilot-block docpilot-leaf-block' }),
    ['span', { class: 'docpilot-block-label' }, label],
    text ? ['pre', { class: 'docpilot-block-source' }, text] : ['span', { class: 'docpilot-block-empty' }, label]
  ]
}

function renderInlineToken(label: string, attributes: HtmlAttrs): DOMOutputSpec {
  const text =
    stringAttr(attributes, 'text') ||
    stringAttr(attributes, 'label') ||
    stringAttr(attributes, 'shortcut') ||
    stringAttr(attributes, 'source') ||
    label

  return ['span', renderedAttrs(attributes, { class: 'docpilot-inline-token' }), text]
}

function renderedSourceAttrs(attributes: HtmlAttrs, hiddenKeys: string[], extra: HtmlAttrs = {}) {
  const attrs = { ...attributes }
  hiddenKeys.forEach((key) => {
    delete attrs[key]
  })
  return renderedAttrs(attrs, extra)
}

function mathText(attributes: HtmlAttrs): string {
  return stringAttr(attributes, 'text') || stringAttr(attributes, 'source') || stringAttr(attributes, 'raw')
}

function renderedMathAttrs(attributes: HtmlAttrs, extra: HtmlAttrs = {}) {
  const attrs = { ...attributes }
  delete attrs.text
  delete attrs.source
  delete attrs.raw
  delete attrs.delimiter
  delete attrs.notation
  return renderedAttrs(attrs, extra)
}

function renderMathBlock(attributes: HtmlAttrs): DOMOutputSpec {
  const text = mathText(attributes)
  const label = normalizeLatexSource(text)
  return [
    'div',
    renderedMathAttrs(attributes, {
      class: 'docpilot-block docpilot-math-block',
      role: 'math',
      ...(label ? { 'aria-label': label } : {})
    }),
    ['span', { class: 'docpilot-math-rendered' }, ...renderLatexMath(text)]
  ]
}

function renderMathInline(attributes: HtmlAttrs): DOMOutputSpec {
  const text = mathText(attributes)
  const label = normalizeLatexSource(text)
  return [
    'span',
    renderedMathAttrs(attributes, {
      class: 'docpilot-math-inline',
      role: 'math',
      ...(label ? { 'aria-label': label } : {})
    }),
    ...renderLatexMath(text)
  ]
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function frontMatterEntries(attributes: HtmlAttrs): Array<[string, unknown]> {
  if (isPlainRecord(attributes.data)) {
    return Object.entries(attributes.data)
  }

  const raw = stringAttr(attributes, 'raw')
  if (!raw) return []

  return raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && line !== '---' && line !== '...')
    .map((line): [string, string] | null => {
      const separator = line.indexOf(':')
      if (separator <= 0) return null
      return [line.slice(0, separator).trim(), line.slice(separator + 1).trim()]
    })
    .filter((entry): entry is [string, string] => entry !== null)
}

function renderFrontMatterValue(value: unknown): DOMOutputSpec {
  if (Array.isArray(value)) {
    return ['span', { class: 'docpilot-front-matter-tags' }, ...value.map((item) => ['span', { class: 'docpilot-front-matter-tag' }, String(item)] as DOMOutputSpec)]
  }

  if (isPlainRecord(value)) {
    return ['code', {}, JSON.stringify(value)]
  }

  return ['span', {}, String(value ?? '')]
}

function renderFrontMatterBlock(attributes: HtmlAttrs): DOMOutputSpec {
  const raw = stringAttr(attributes, 'raw')
  const entries = frontMatterEntries(attributes)

  return [
    'section',
    renderedSourceAttrs(attributes, ['raw', 'data', 'format'], { class: 'docpilot-block docpilot-front-matter' }),
    ['div', { class: 'docpilot-front-matter-title' }, 'YAML Front Matter'],
    entries.length
      ? ['dl', { class: 'docpilot-front-matter-list' }, ...entries.flatMap(([key, value]) => [
          ['dt', {}, key],
          ['dd', {}, renderFrontMatterValue(value)]
        ] as DOMOutputSpec[])]
      : ['pre', { class: 'docpilot-front-matter-source' }, raw]
  ]
}

function setDomAttributes(element: HTMLElement, attributes: HtmlAttrs) {
  const nextNames = new Set(Object.keys(attributes))
  for (const attribute of Array.from(element.attributes)) {
    if (attribute.name === 'open') continue
    if (!nextNames.has(attribute.name)) {
      element.removeAttribute(attribute.name)
    }
  }

  for (const [name, value] of Object.entries(attributes)) {
    if (value === false || value === null || value === undefined) {
      element.removeAttribute(name)
    } else if (value === true) {
      element.setAttribute(name, '')
    } else {
      element.setAttribute(name, String(value))
    }
  }
}

function calloutTitle(attributes: HtmlAttrs): string {
  return stringAttr(attributes, 'title', 'Details') || 'Details'
}

function calloutNodeView(props: NodeViewRendererProps): NodeView {
  const attrs = props.node.attrs as HtmlAttrs
  const collapsible = booleanAttr(attrs.collapsible)
  const dom = document.createElement(collapsible ? 'details' : 'aside')
  const contentDOM = document.createElement('div')

  let summary: HTMLElement | null = null

  if (collapsible) {
    const details = dom as HTMLDetailsElement
    const initialOpen = booleanAttr(attrs.open, true)
    details.open = initialOpen
    details.toggleAttribute('open', initialOpen)
    setDomAttributes(details, renderedCalloutAttrs(props.HTMLAttributes, {
      class: 'docpilot-block docpilot-callout docpilot-callout-collapsible'
    }))

    summary = document.createElement('summary')
    summary.className = 'docpilot-callout-summary'
    summary.contentEditable = 'false'
    summary.textContent = calloutTitle(attrs)
    summary.addEventListener('click', (event) => {
      event.preventDefault()
      details.open = !details.open
      details.toggleAttribute('open', details.open)
    })

    contentDOM.className = 'docpilot-callout-body'
    details.append(summary, contentDOM)
  } else {
    setDomAttributes(dom, renderedCalloutAttrs(props.HTMLAttributes, {
      class: 'docpilot-block docpilot-callout'
    }))
    dom.append(contentDOM)
  }

  return {
    dom,
    contentDOM,
    update(nextNode) {
      if (nextNode.type.name !== props.node.type.name) return false
      const nextAttrs = nextNode.attrs as HtmlAttrs
      if (booleanAttr(nextAttrs.collapsible) !== collapsible) return false

      if (collapsible) {
        setDomAttributes(dom, renderedCalloutAttrs(nextAttrs, {
          class: 'docpilot-block docpilot-callout docpilot-callout-collapsible'
        }))
        if (summary) {
          summary.textContent = calloutTitle(nextAttrs)
        }
      } else {
        setDomAttributes(dom, renderedCalloutAttrs(nextAttrs, {
          class: 'docpilot-block docpilot-callout'
        }))
      }
      return true
    },
    ignoreMutation(mutation: ViewMutationRecord) {
      return mutation.type === 'attributes'
        && mutation.target === dom
        && mutation.attributeName === 'open'
    }
  }
}

export const DocpilotImage = Node.create({
  name: 'image',
  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  draggable: true,

  addAttributes() {
    return {
      src: {
        default: '',
        parseHTML: (element) => element.getAttribute('src') ?? ''
      },
      alt: {
        default: '',
        parseHTML: (element) => element.getAttribute('alt') ?? ''
      },
      title: {
        default: '',
        parseHTML: (element) => element.getAttribute('title') ?? ''
      },
      caption: {
        default: '',
        parseHTML: (element) => element.getAttribute('data-caption') ?? ''
      },
      width: {
        default: null,
        parseHTML: (element) => imageDimensionAttr(element.getAttribute('data-width') ?? element.getAttribute('width'))
      },
      alignment: {
        default: 'left',
        parseHTML: (element) => imageAlignmentAttr(element.getAttribute('data-alignment'))
      },
      sourceRange: { default: null }
    }
  },

  parseHTML() {
    return [{ tag: 'img[src]' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['img', renderedImageAttrs(HTMLAttributes)]
  },

  addNodeView() {
    return ReactNodeViewRenderer(ImageNodeView, { as: 'span' })
  }
})

export const DocpilotFrontMatter = Node.create({
  name: 'docpilotFrontMatter',
  group: 'block',
  atom: true,

  addAttributes() {
    return blockAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return renderFrontMatterBlock(HTMLAttributes)
  }
})

export const DocpilotMathBlock = Node.create({
  name: 'docpilotMathBlock',
  group: 'block',
  atom: true,

  addAttributes() {
    return blockAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return renderMathBlock(HTMLAttributes)
  }
})

export const DocpilotDiagramBlock = Node.create({
  name: 'docpilotDiagramBlock',
  group: 'block',
  atom: true,

  addAttributes() {
    return blockAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return renderLeafBlock(stringAttr(HTMLAttributes, 'engine', 'Diagram'), HTMLAttributes)
  }
})

export const DocpilotCallout = Node.create({
  name: 'docpilotCallout',
  group: 'block',
  content: 'block*',

  addAttributes() {
    return blockAttributes
  },

  renderHTML({ HTMLAttributes }) {
    if (booleanAttr(HTMLAttributes.collapsible)) {
      const title = stringAttr(HTMLAttributes, 'title', 'Details') || 'Details'
      return [
        'details',
        renderedCalloutAttrs(HTMLAttributes, {
          class: 'docpilot-block docpilot-callout docpilot-callout-collapsible',
          ...(booleanAttr(HTMLAttributes.open, true) ? { open: 'open' } : {})
        }),
        ['summary', { class: 'docpilot-callout-summary' }, title],
        ['div', { class: 'docpilot-callout-body' }, 0]
      ]
    }

    return ['aside', renderedCalloutAttrs(HTMLAttributes, { class: 'docpilot-block docpilot-callout' }), 0]
  },

  addNodeView() {
    return calloutNodeView
  }
})

export const DocpilotFootnoteDefinition = Node.create({
  name: 'docpilotFootnoteDefinition',
  group: 'block',
  content: 'block*',

  addAttributes() {
    return blockAttributes
  },

  renderHTML({ HTMLAttributes }) {
    const label = stringAttr(HTMLAttributes, 'label', 'fn') || 'fn'
    return [
      'section',
      renderedAttrs(HTMLAttributes, {
        class: 'docpilot-block docpilot-footnote-definition',
        id: footnoteDefinitionId(label),
        'data-footnote-label': label
      }),
      0
    ]
  }
})

export const DocpilotDefinitionList = Node.create({
  name: 'docpilotDefinitionList',
  group: 'block',
  content: 'block*',

  addAttributes() {
    return blockAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return ['dl', renderedAttrs(HTMLAttributes, { class: 'docpilot-definition-list' }), 0]
  }
})

export const DocpilotDefinitionTerm = Node.create({
  name: 'docpilotDefinitionTerm',
  group: 'block',
  content: 'inline*',

  addAttributes() {
    return blockAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return ['dt', renderedAttrs(HTMLAttributes, { class: 'docpilot-definition-term' }), 0]
  }
})

export const DocpilotDefinitionItem = Node.create({
  name: 'docpilotDefinitionItem',
  group: 'block',
  content: 'block*',

  addAttributes() {
    return blockAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return ['dd', renderedAttrs(HTMLAttributes, { class: 'docpilot-definition-item' }), 0]
  }
})

export const DocpilotToc = Node.create({
  name: 'docpilotToc',
  group: 'block',
  atom: true,

  addAttributes() {
    return blockAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return renderLeafBlock('TOC', HTMLAttributes)
  }
})

export const DocpilotLinkReferenceDefinition = Node.create({
  name: 'docpilotLinkReferenceDefinition',
  group: 'block',
  atom: true,

  addAttributes() {
    return blockAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'div',
      renderedHiddenBlockAttrs(HTMLAttributes, {
        class: 'docpilot-link-reference-definition',
        hidden: 'hidden',
        'aria-hidden': 'true'
      })
    ]
  }
})

export const DocpilotExtensionBlock = Node.create({
  name: 'docpilotExtensionBlock',
  group: 'block',
  content: 'block*',

  addAttributes() {
    return blockAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return ['div', renderedAttrs(HTMLAttributes, { class: 'docpilot-block docpilot-extension-block' }), 0]
  }
})

export const DocpilotMathInline = Node.create({
  name: 'docpilotMathInline',
  group: 'inline',
  inline: true,
  atom: true,

  addAttributes() {
    return inlineAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return renderMathInline(HTMLAttributes)
  }
})

export const DocpilotFootnoteRef = Node.create({
  name: 'docpilotFootnoteRef',
  group: 'inline',
  inline: true,
  atom: true,

  addAttributes() {
    return inlineAttributes
  },

  renderHTML({ HTMLAttributes }) {
    const label = stringAttr(HTMLAttributes, 'label', 'fn') || 'fn'
    return [
      'sup',
      renderedAttrs(HTMLAttributes, {
        class: 'docpilot-footnote-ref',
        id: footnoteReferenceId(label)
      }),
      [
        'a',
        {
          'aria-label': `Footnote ${label}`,
          href: `#${footnoteDefinitionId(label)}`
        },
        label
      ]
    ] as DOMOutputSpec
  }
})

export const DocpilotHtmlInline = Node.create({
  name: 'docpilotHtmlInline',
  group: 'inline',
  inline: true,
  atom: true,

  addAttributes() {
    return inlineAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return renderInlineToken('html', HTMLAttributes)
  }
})

export const DocpilotEmoji = Node.create({
  name: 'docpilotEmoji',
  group: 'inline',
  inline: true,
  atom: true,

  addAttributes() {
    return inlineAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return renderInlineToken('emoji', HTMLAttributes)
  }
})

export const DocpilotExtensionInline = Node.create({
  name: 'docpilotExtensionInline',
  group: 'inline',
  inline: true,
  atom: true,

  addAttributes() {
    return inlineAttributes
  },

  renderHTML({ HTMLAttributes }) {
    return renderInlineToken('extension', HTMLAttributes)
  }
})

export const DocpilotUnderline = Mark.create({
  name: 'underline',

  parseHTML() {
    return [{ tag: 'u' }, { style: 'text-decoration=underline' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['u', mergeAttributes(HTMLAttributes), 0]
  }
})

export const DocpilotInsert = Mark.create({
  name: 'insert',

  parseHTML() {
    return [{ tag: 'ins' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['ins', mergeAttributes(HTMLAttributes), 0]
  }
})

export const DocpilotSubscript = Mark.create({
  name: 'subscript',
  excludes: 'superscript',

  parseHTML() {
    return [{ tag: 'sub' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['sub', mergeAttributes(HTMLAttributes), 0]
  }
})

export const DocpilotSuperscript = Mark.create({
  name: 'superscript',
  excludes: 'subscript',

  parseHTML() {
    return [{ tag: 'sup' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['sup', mergeAttributes(HTMLAttributes), 0]
  }
})

export const DocpilotHighlight = Mark.create({
  name: 'highlight',

  parseHTML() {
    return [{ tag: 'mark' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['mark', mergeAttributes(HTMLAttributes), 0]
  }
})
