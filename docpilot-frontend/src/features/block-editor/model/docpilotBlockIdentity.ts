import { Extension } from '@tiptap/core'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import { Plugin } from '@tiptap/pm/state'

type BlockIdentityAttrs = Record<string, unknown>

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

let generatedBlockIdSequence = 0

export const TRANSIENT_BLOCK_ID_PREFIX = 'docpilot-transient-'

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
              const blockId = blockIdentityId(attributes)
              return blockId ? { 'data-block-id': blockId } : {}
            }
          },
          transientBlockId: {
            default: '',
            parseHTML: () => '',
            renderHTML: () => ({})
          },
          sourceRange: {
            default: null,
            parseHTML: () => null,
            renderHTML: () => ({})
          }
        }
      }
    ]
  },

  addProseMirrorPlugins() {
    return [
      new Plugin({
        appendTransaction: (transactions, _oldState, newState) => {
          if (!transactions.some((transaction) => transaction.docChanged)) {
            return null
          }

          // Block debug and marquee selection need a DOM identity immediately,
          // while the canonical blockId should still be assigned by the backend.
          const seenBlockIds = new Set<string>()
          const transaction = newState.tr
          let changed = false

          newState.doc.descendants((node, position) => {
            if (!isBlockIdentityNode(node)) {
              return true
            }

            const blockId = blockIdentityId(node.attrs)
            if (blockId && !seenBlockIds.has(blockId)) {
              seenBlockIds.add(blockId)
              return true
            }

            const nextBlockId = nextTransientBlockId(seenBlockIds)
            transaction.setNodeMarkup(
              position,
              undefined,
              {
                ...node.attrs,
                transientBlockId: nextBlockId
              },
              node.marks
            )
            seenBlockIds.add(nextBlockId)
            changed = true
            return true
          })

          return changed ? transaction : null
        }
      })
    ]
  }
})

export function blockIdentityId(attrs: BlockIdentityAttrs): string {
  const blockId = stringAttr(attrs.blockId)
  if (blockId) {
    return blockId
  }
  return stringAttr(attrs.transientBlockId)
}

export function canonicalBlockId(attrs: BlockIdentityAttrs): string {
  const blockId = stringAttr(attrs.blockId)
  return isTransientBlockId(blockId) ? '' : blockId
}

export function isTransientBlockId(value: unknown): boolean {
  return typeof value === 'string'
    && (value.startsWith(TRANSIENT_BLOCK_ID_PREFIX) || value.startsWith('frontend-'))
}

function isBlockIdentityNode(node: ProseMirrorNode): node is ProseMirrorNode & {
  attrs: ProseMirrorNode['attrs'] & { blockId: string; transientBlockId: string }
} {
  return node.isBlock
    && typeof node.attrs.blockId === 'string'
    && typeof node.attrs.transientBlockId === 'string'
}

function nextTransientBlockId(usedBlockIds: Set<string>): string {
  let blockId = ''
  do {
    generatedBlockIdSequence += 1
    blockId = `${TRANSIENT_BLOCK_ID_PREFIX}${Date.now().toString(36)}-${generatedBlockIdSequence.toString(36)}`
  } while (usedBlockIds.has(blockId))
  return blockId
}

function stringAttr(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}
