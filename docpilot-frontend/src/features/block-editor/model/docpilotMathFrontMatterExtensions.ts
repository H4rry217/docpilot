import { Node } from '@tiptap/core'
import type { DOMOutputSpec } from '@tiptap/pm/model'
import { normalizeLatexSource, renderLatexMath } from './docpilotMathRendering'
import {
  blockAttributes,
  inlineAttributes,
  isPlainRecord,
  renderedAttrs,
  renderedSourceAttrs,
  renderLeafBlock,
  stringAttr,
  type HtmlAttrs
} from './docpilotMarkdownExtensionUtils'

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

export function renderMathBlock(attributes: HtmlAttrs): DOMOutputSpec {
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

export function renderMathInline(attributes: HtmlAttrs): DOMOutputSpec {
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

export function frontMatterEntries(attributes: HtmlAttrs): Array<[string, unknown]> {
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

export function renderFrontMatterBlock(attributes: HtmlAttrs): DOMOutputSpec {
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
