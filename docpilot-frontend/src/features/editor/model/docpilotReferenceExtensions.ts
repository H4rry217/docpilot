import { Node } from '@tiptap/core'
import {
  blockAttributes,
  inlineAttributes,
  renderedAttrs,
  renderedHiddenBlockAttrs,
  renderInlineToken,
  renderLeafBlock
} from './docpilotMarkdownExtensionUtils'

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
