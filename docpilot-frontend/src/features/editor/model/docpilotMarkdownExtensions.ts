import { Mark, mergeAttributes, Node } from '@tiptap/core'
import type { DOMOutputSpec } from '@tiptap/pm/model'

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

function stringAttr(attributes: HtmlAttrs, key: string, fallback = ''): string {
  const value = attributes[key]
  return typeof value === 'string' ? value : fallback
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

export const DocpilotImage = Node.create({
  name: 'image',
  group: 'inline',
  inline: true,
  atom: true,
  draggable: true,

  addAttributes() {
    return {
      src: { default: '' },
      alt: { default: '' },
      title: { default: '' },
      sourceRange: { default: null }
    }
  },

  renderHTML({ HTMLAttributes }) {
    return ['img', renderedAttrs(HTMLAttributes, { src: stringAttr(HTMLAttributes, 'src'), alt: stringAttr(HTMLAttributes, 'alt') })]
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
    return renderLeafBlock('Front matter', HTMLAttributes)
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
    return renderLeafBlock('Math', HTMLAttributes)
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
    return ['aside', renderedAttrs(HTMLAttributes, { class: 'docpilot-block docpilot-callout' }), 0]
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
    return ['section', renderedAttrs(HTMLAttributes, { class: 'docpilot-block docpilot-footnote-definition' }), 0]
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
    return renderLeafBlock('Link reference', HTMLAttributes)
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
    return renderInlineToken('math', HTMLAttributes)
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
    return ['sup', renderedAttrs(HTMLAttributes, { class: 'docpilot-footnote-ref' }), stringAttr(HTMLAttributes, 'label', 'fn')] as DOMOutputSpec
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
