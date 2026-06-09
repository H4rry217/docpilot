import { mergeAttributes, Node } from '@tiptap/core'
import { ReactNodeViewRenderer } from '@tiptap/react'
import { HtmlBlockView } from '../ui/HtmlBlockView'

export const DocpilotHtmlBlock = Node.create({
  name: 'docpilotHtmlBlock',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,

  addAttributes() {
    return {
      id: {
        default: ''
      },
      title: {
        default: 'HTML'
      },
      source: {
        default: ''
      },
      displayMode: {
        default: 'fixed'
      },
      fixedHeightPx: {
        default: 320
      },
      allowScripts: {
        default: false
      },
      blockId: {
        default: ''
      },
      sourceRange: {
        default: null
      }
    }
  },

  parseHTML() {
    return [
      {
        tag: 'div[data-docpilot-html-block]',
        getAttrs: (node) => {
          if (!(node instanceof HTMLElement)) {
            return false
          }
          return {
            title: node.getAttribute('data-html-title') ?? 'HTML',
            source: node.getAttribute('data-html-source') ?? '',
            displayMode: node.getAttribute('data-display-mode') ?? 'fixed',
            fixedHeightPx: Number(node.getAttribute('data-fixed-height-px') ?? 320),
            allowScripts: node.getAttribute('data-allow-scripts') === 'true'
          }
        }
      }
    ]
  },

  renderHTML({ HTMLAttributes }) {
    const attrs = HTMLAttributes as Record<string, unknown>
    const source = typeof attrs.source === 'string' ? attrs.source : ''
    const title = typeof attrs.title === 'string' ? attrs.title : 'HTML'
    const displayMode = attrs.displayMode === 'auto' ? 'auto' : 'fixed'
    const fixedHeightPx = typeof attrs.fixedHeightPx === 'number' ? attrs.fixedHeightPx : 320
    const allowScripts = attrs.allowScripts === true
    return [
      'div',
      mergeAttributes(HTMLAttributes, {
        'data-docpilot-html-block': '',
        'data-html-title': title,
        'data-html-source': source,
        'data-display-mode': displayMode,
        'data-fixed-height-px': String(fixedHeightPx),
        'data-allow-scripts': allowScripts ? 'true' : 'false'
      }),
      ['iframe', { sandbox: allowScripts ? 'allow-scripts' : 'allow-same-origin', srcdoc: source }]
    ]
  },

  addNodeView() {
    return ReactNodeViewRenderer(HtmlBlockView)
  }
})
