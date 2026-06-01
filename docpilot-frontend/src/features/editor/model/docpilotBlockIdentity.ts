import { Extension } from '@tiptap/core'

const BLOCK_IDENTITY_TYPES = [
  'paragraph',
  'heading',
  'blockquote',
  'bulletList',
  'orderedList',
  'listItem',
  'codeBlock',
  'horizontalRule',
  'table',
  'tableRow',
  'tableCell',
  'tableHeader'
]

export const DocpilotBlockIdentity = Extension.create({
  name: 'docpilotBlockIdentity',

  addGlobalAttributes() {
    return [
      {
        types: BLOCK_IDENTITY_TYPES,
        attributes: {
          blockId: {
            default: '',
            parseHTML: (element) => element.getAttribute('data-block-id') ?? '',
            renderHTML: (attributes) => {
              const blockId = attributes.blockId
              return typeof blockId === 'string' && blockId ? { 'data-block-id': blockId } : {}
            }
          },
          sourceRange: {
            default: null,
            parseHTML: () => null,
            renderHTML: () => ({})
          }
        }
      }
    ]
  }
})
