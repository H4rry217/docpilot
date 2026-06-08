import { Node } from '@tiptap/core'
import type { DOMOutputSpec } from '@tiptap/pm/model'
import { calloutNodeView } from './docpilotCalloutNodeView'
import {
  blockAttributes,
  booleanAttr,
  footnoteDefinitionId,
  footnoteReferenceId,
  inlineAttributes,
  renderedAttrs,
  renderedCalloutAttrs,
  stringAttr
} from './docpilotMarkdownExtensionUtils'

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
