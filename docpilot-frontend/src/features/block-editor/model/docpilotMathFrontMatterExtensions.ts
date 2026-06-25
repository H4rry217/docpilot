import { mergeAttributes, Node } from '@tiptap/core'
import { BlockMath, InlineMath } from '@tiptap/extension-mathematics'
import type { DOMOutputSpec } from '@tiptap/pm/model'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import katex, { type KatexOptions } from 'katex'
import {
  blockAttributes,
  isPlainRecord,
  renderedSourceAttrs,
  renderLeafBlock,
  setDomAttributes,
  stringAttr,
  type HtmlAttrs
} from './docpilotMarkdownExtensionUtils'

const sharedKatexOptions: KatexOptions = {
  throwOnError: false
}

const blockKatexOptions: KatexOptions = {
  ...sharedKatexOptions,
  displayMode: true
}

const inlineKatexOptions: KatexOptions = {
  ...sharedKatexOptions,
  displayMode: false
}

function mathAttributes(defaultDelimiter: '$' | '$$') {
  return {
    notation: {
      default: 'latex',
      parseHTML: (element: HTMLElement) => element.getAttribute('data-notation') ?? 'latex',
      renderHTML: (attributes: HtmlAttrs) => {
        const notation = stringAttr(attributes, 'notation', 'latex')
        return notation ? { 'data-notation': notation } : {}
      }
    },
    delimiter: {
      default: defaultDelimiter,
      parseHTML: (element: HTMLElement) => element.getAttribute('data-delimiter') ?? defaultDelimiter,
      renderHTML: (attributes: HtmlAttrs) => {
        const delimiter = stringAttr(attributes, 'delimiter', defaultDelimiter)
        return delimiter ? { 'data-delimiter': delimiter } : {}
      }
    },
    sourceRange: {
      default: null,
      parseHTML: () => null,
      renderHTML: () => ({})
    },
    text: {
      default: '',
      parseHTML: () => '',
      renderHTML: () => ({})
    },
    source: {
      default: '',
      parseHTML: () => '',
      renderHTML: () => ({})
    },
    raw: {
      default: '',
      parseHTML: () => '',
      renderHTML: () => ({})
    }
  }
}

function latexAttr(attributes: HtmlAttrs): string {
  return stringAttr(attributes, 'latex')
}

function mathDomAttrs(node: ProseMirrorNode, dataType: 'block-math' | 'inline-math', className: string): HtmlAttrs {
  const latex = latexAttr(node.attrs)
  const blockId = stringAttr(node.attrs, 'blockId')
  const notation = stringAttr(node.attrs, 'notation', 'latex')
  const delimiter = stringAttr(node.attrs, 'delimiter', dataType === 'block-math' ? '$$' : '$')

  return mergeAttributes(
    {
      class: className,
      'data-type': dataType,
      'data-latex': latex,
      role: 'math',
      ...(latex ? { 'aria-label': latex } : {})
    },
    blockId ? { 'data-block-id': blockId } : {},
    notation ? { 'data-notation': notation } : {},
    delimiter ? { 'data-delimiter': delimiter } : {}
  )
}

function renderKatex(target: HTMLElement, latex: string, options: KatexOptions, errorClassName: string) {
  try {
    katex.render(latex, target, options)
    target.classList.remove(errorClassName)
  } catch {
    target.textContent = latex
    target.classList.add(errorClassName)
  }
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

export const DocpilotMathBlock = BlockMath.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      ...mathAttributes('$$')
    }
  },

  addNodeView() {
    return ({ node }) => {
      const wrapper = document.createElement('div')
      const innerWrapper = document.createElement('div')
      const editableClass = this.editor.isEditable ? ' tiptap-mathematics-render--editable' : ''

      setDomAttributes(wrapper, mathDomAttrs(node, 'block-math', `tiptap-mathematics-render docpilot-block docpilot-math-block${editableClass}`))
      innerWrapper.className = 'block-math-inner'
      wrapper.appendChild(innerWrapper)
      renderKatex(innerWrapper, latexAttr(node.attrs), blockKatexOptions, 'block-math-error')

      return { dom: wrapper }
    }
  }
}).configure({
  katexOptions: blockKatexOptions
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

export const DocpilotMathInline = InlineMath.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      ...mathAttributes('$')
    }
  },

  addNodeView() {
    return ({ node }) => {
      const wrapper = document.createElement('span')
      const editableClass = this.editor.isEditable ? ' tiptap-mathematics-render--editable' : ''

      setDomAttributes(wrapper, mathDomAttrs(node, 'inline-math', `tiptap-mathematics-render docpilot-math-inline${editableClass}`))
      renderKatex(wrapper, latexAttr(node.attrs), inlineKatexOptions, 'inline-math-error')

      return { dom: wrapper }
    }
  }
}).configure({
  katexOptions: inlineKatexOptions
})
