import type { Node as ProseMirrorNode, ResolvedPos } from '@tiptap/pm/model'
import type { Editor } from '@tiptap/react'
import type { BlockDocument, BlockNode, InlineNode } from '@/entities/block/types'
import {
  type InlineCompletionBlockContext,
  type InlineCompletionShape
} from '@/features/inline-completion/api/inlineCompletionApi'
import { blockIdentityId } from '../model/docpilotBlockIdentity'

const INLINE_COMPLETION_DEFAULT_CANDIDATE_COUNT = 3
const NEARBY_BLOCK_LIMIT = 2

export function currentBlockContext(editor: Editor): InlineCompletionBlockContext | null {
  const { selection } = editor.state
  if (!selection.empty) return null
  const cursor = selection.$from
  const selected = selectedContextNode(cursor)
  if (!selected) return null
  const parent = cursor.parent
  return {
    id: selected.id,
    type: selected.type,
    text: selected.node.textContent,
    textBeforeCursor: parent.textBetween(0, cursor.parentOffset, '\n', '\n'),
    textAfterCursor: parent.textBetween(cursor.parentOffset, parent.content.size, '\n', '\n')
  }
}

function selectedContextNode(cursor: ResolvedPos): {
  id: string
  type: string
  node: ProseMirrorNode
} | null {
  const preferred = ['codeBlock', 'tableCell', 'tableHeader', 'listItem']
  for (const typeName of preferred) {
    for (let depth = cursor.depth; depth >= 0; depth -= 1) {
      const node = cursor.node(depth)
      if (node.type.name === typeName) {
        return {
          id: blockIdentityId(node.attrs),
          type: blockTypeFromProseMirror(node),
          node
        }
      }
    }
  }

  for (let depth = cursor.depth; depth >= 0; depth -= 1) {
    const node = cursor.node(depth)
    const blockId = blockIdentityId(node.attrs)
    if (blockId || node.isTextblock) {
      return {
        id: blockId,
        type: blockTypeFromProseMirror(node),
        node
      }
    }
  }
  return null
}

function blockTypeFromProseMirror(node: ProseMirrorNode): string {
  switch (node.type.name) {
    case 'paragraph':
      return 'PARAGRAPH'
    case 'heading':
      return 'HEADING'
    case 'blockquote':
      return 'BLOCK_QUOTE'
    case 'bulletList':
      return 'BULLET_LIST'
    case 'orderedList':
      return 'ORDERED_LIST'
    case 'listItem':
      return 'LIST_ITEM'
    case 'codeBlock':
      return 'CODE_BLOCK'
    case 'tableCell':
    case 'tableHeader':
      return 'TABLE_CELL'
    default:
      return node.type.name.toUpperCase()
  }
}

export function headingPathForBlock(document: BlockDocument, blockId?: string): string[] {
  if (!blockId) return []
  const headingPath: string[] = []
  let found = false
  walkBlocks(document.blocks, (block) => {
    if (found) return
    if (block.id === blockId) {
      found = true
      return
    }
    if (block.type === 'HEADING') {
      const level = headingLevel(block)
      headingPath.splice(level - 1)
      headingPath[level - 1] = blockText(block)
    }
  })
  return headingPath.filter(Boolean)
}

export function nearbyBlocks(document: BlockDocument, blockId?: string): InlineCompletionBlockContext[] {
  const flattened: BlockNode[] = []
  walkBlocks(document.blocks, (block) => {
    flattened.push(block)
  })
  const index = flattened.findIndex((block) => block.id === blockId)
  if (index === -1) return []
  const from = Math.max(0, index - NEARBY_BLOCK_LIMIT)
  const to = Math.min(flattened.length, index + NEARBY_BLOCK_LIMIT + 1)
  return flattened
    .slice(from, to)
    .filter((block) => block.id !== blockId)
    .map((block) => ({
      id: block.id,
      type: block.type,
      text: blockText(block),
      textBeforeCursor: '',
      textAfterCursor: ''
    }))
}

function walkBlocks(blocks: BlockNode[], visit: (block: BlockNode) => void): void {
  blocks.forEach((block) => {
    visit(block)
    walkBlocks(block.children, visit)
  })
}

function blockText(block: BlockNode): string {
  if (typeof block.attrs.text === 'string') return block.attrs.text
  if (typeof block.attrs.source === 'string') return block.attrs.source
  return inlineText(block.inlines)
}

function inlineText(inlines: InlineNode[]): string {
  return inlines.map((inline) => {
    if (inline.type === 'HARD_BREAK' || inline.type === 'SOFT_BREAK') return '\n'
    return inline.text ?? ''
  }).join('')
}

function headingLevel(block: BlockNode): number {
  const level = block.attrs.level
  if (typeof level !== 'number' || !Number.isFinite(level)) return 1
  return Math.max(1, Math.min(6, Math.trunc(level)))
}

export function blockSignature(block: InlineCompletionBlockContext): string {
  return [
    block.type,
    hashString(block.textBeforeCursor),
    hashString(block.textAfterCursor)
  ].join(':')
}

function hashString(value: string): string {
  let hash = 0
  for (let index = 0; index < value.length; index += 1) {
    hash = Math.imul(31, hash) + value.charCodeAt(index) | 0
  }
  return `${value.length}:${hash}`
}

export function markdownPreviewText(markdown: string, shape: InlineCompletionShape): string {
  if (shape === 'CODE_LINE') return markdown
  return decodeHtmlEntities(markdown)
    .replace(/```[\s\S]*?```/g, '')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/__([^_]+)__/g, '$1')
    .replace(/~~([^~]+)~~/g, '$1')
    .replace(/\[([^\]]+)]\([^)]*\)/g, '$1')
    .replace(/^\s*(?:>\s*)+/gm, '')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\d+[.)]\s+/gm, '')
    .trimStart()
}

function decodeHtmlEntities(text: string): string {
  return text
    .replaceAll('&gt;', '>')
    .replaceAll('&lt;', '<')
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
    .replaceAll('&amp;', '&')
}

export function clampCandidateCount(value: number | undefined): number {
  if (value == null || !Number.isFinite(value)) return INLINE_COMPLETION_DEFAULT_CANDIDATE_COUNT
  return Math.max(1, Math.min(5, Math.trunc(value)))
}

export function isEditorInteractionFocused(editor: Editor): boolean {
  if (editor.isFocused) return true
  const activeElement = editor.view.root.activeElement
  return activeElement instanceof Element && editor.view.dom.contains(activeElement)
}

export function activeElementDebugName(editor: Editor): string | null {
  const activeElement = editor.view.root.activeElement
  if (!(activeElement instanceof Element)) return null
  const tagName = activeElement.tagName.toLowerCase()
  const className = typeof activeElement.className === 'string'
    ? activeElement.className.trim().replace(/\s+/g, '.')
    : ''
  return className ? `${tagName}.${className}` : tagName
}
