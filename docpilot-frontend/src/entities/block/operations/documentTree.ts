import type { BlockDocument, BlockNode, InlineNode } from '../types'

export type BlockLocation = {
  block: BlockNode
  parentBlocks: BlockNode[]
  parentBlockId: string | null
  index: number
  path: number[]
}

export type TextMatchRange = {
  startOffset: number
  endOffset: number
  text: string
}

export function cloneBlockDocument(document: BlockDocument): BlockDocument {
  return JSON.parse(JSON.stringify(document)) as BlockDocument
}

export function cloneBlock(block: BlockNode): BlockNode {
  return JSON.parse(JSON.stringify(block)) as BlockNode
}

export function collectBlockLocations(document: BlockDocument): BlockLocation[] {
  const locations: BlockLocation[] = []
  collectLocations(document.blocks, null, [], locations)
  return locations
}

export function findBlockLocation(document: BlockDocument, blockId: string): BlockLocation | null {
  return collectBlockLocations(document).find((location) => location.block.id === blockId) ?? null
}

export function blockPlainText(block: BlockNode): string {
  return block.inlines.map(inlinePlainText).join('')
}

export function findTextMatches(source: string, text: string, caseSensitive = false): TextMatchRange[] {
  if (!text) return []

  const haystack = caseSensitive ? source : source.toLocaleLowerCase()
  const needle = caseSensitive ? text : text.toLocaleLowerCase()
  const matches: TextMatchRange[] = []
  let offset = 0

  while (offset <= haystack.length) {
    const startOffset = haystack.indexOf(needle, offset)
    if (startOffset === -1) break
    const endOffset = startOffset + text.length
    matches.push({
      startOffset,
      endOffset,
      text: source.slice(startOffset, endOffset)
    })
    offset = endOffset
  }

  return matches
}

export function stableBlockHash(block: BlockNode): string {
  return hashString(stableStringify(block))
}

export function isDescendantBlockId(block: BlockNode, blockId: string): boolean {
  return block.children.some((child) => child.id === blockId || isDescendantBlockId(child, blockId))
}

function collectLocations(
  blocks: BlockNode[],
  parentBlockId: string | null,
  parentPath: number[],
  locations: BlockLocation[]
) {
  blocks.forEach((block, index) => {
    const path = [...parentPath, index]
    locations.push({
      block,
      parentBlocks: blocks,
      parentBlockId,
      index,
      path
    })
    collectLocations(block.children, block.id, path, locations)
  })
}

function inlinePlainText(inline: InlineNode): string {
  if (inline.type === 'HARD_BREAK' || inline.type === 'SOFT_BREAK') return '\n'
  return inline.text ?? ''
}

function stableStringify(value: unknown): string {
  if (value === null || typeof value !== 'object') return JSON.stringify(value)
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`

  const entries = Object.entries(value as Record<string, unknown>)
    .filter(([, entryValue]) => entryValue !== undefined)
    .sort(([leftKey], [rightKey]) => leftKey.localeCompare(rightKey))

  return `{${entries.map(([key, entryValue]) => `${JSON.stringify(key)}:${stableStringify(entryValue)}`).join(',')}}`
}

function hashString(value: string): string {
  let hash = 0x811c9dc5

  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index)
    hash = Math.imul(hash, 0x01000193)
  }

  return `fnv1a32:${(hash >>> 0).toString(16).padStart(8, '0')}`
}
