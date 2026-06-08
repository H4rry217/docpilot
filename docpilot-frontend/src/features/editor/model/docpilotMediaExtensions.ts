import { Node, mergeAttributes } from '@tiptap/core'
import { ReactNodeViewRenderer } from '@tiptap/react'
import { ImageNodeView } from '../ui/ImageNodeView'
import { stringAttr, type HtmlAttrs } from './docpilotMarkdownExtensionUtils'

export function imageAlignmentAttr(value: unknown): 'left' | 'center' | 'right' {
  return value === 'center' || value === 'right' ? value : 'left'
}

export function imageDimensionAttr(value: unknown): number | null {
  const number = typeof value === 'number' ? value : typeof value === 'string' ? Number.parseInt(value, 10) : Number.NaN
  if (!Number.isFinite(number)) return null
  const rounded = Math.trunc(number)
  return rounded > 0 ? rounded : null
}

export function renderedImageAttrs(attributes: HtmlAttrs): HtmlAttrs {
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
