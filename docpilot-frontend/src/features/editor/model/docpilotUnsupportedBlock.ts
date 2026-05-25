import { mergeAttributes, Node } from '@tiptap/core'

export const DocpilotUnsupportedBlock = Node.create({
  name: 'docpilotUnsupportedBlock',
  group: 'block',
  atom: true,

  addAttributes() {
    return {
      blockId: { default: '' },
      sourceRange: { default: null },
      text: { default: '' }
    }
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'div',
      mergeAttributes(HTMLAttributes, { 'data-docpilot-unsupported-block': '' }),
      'Unsupported block'
    ]
  }
})
