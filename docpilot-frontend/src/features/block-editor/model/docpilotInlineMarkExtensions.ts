import { Mark, mergeAttributes } from '@tiptap/core'

export const DocpilotInsert = Mark.create({
  name: 'insert',

  parseHTML() {
    return [{ tag: 'ins' }]
  },

  renderHTML({ HTMLAttributes }) {
    return ['ins', mergeAttributes(HTMLAttributes), 0]
  }
})
