import type { BlockDocument, BlockNode, InlineNode } from './types'

export type DocumentOutlineItem = {
  id: string
  level: number
  title: string
  headingIndex: number
}

export type DocumentOutlineJumpRequest = {
  id: string
  headingIndex: number
  requestId: number
}

export function documentOutlineFromBlockDocument(document: BlockDocument): DocumentOutlineItem[] {
  const outline: DocumentOutlineItem[] = []
  collectHeadings(document.blocks, outline)
  return outline
}

function collectHeadings(blocks: BlockNode[], outline: DocumentOutlineItem[]) {
  blocks.forEach((block) => {
    if (block.type === 'HEADING') {
      outline.push({
        id: block.id,
        level: headingLevel(block.attrs.level),
        title: inlineText(block.inlines).trim(),
        headingIndex: outline.length
      })
    }

    collectHeadings(block.children, outline)
  })
}

function headingLevel(value: unknown): number {
  const parsed = typeof value === 'number'
    ? value
    : typeof value === 'string' && /^\d+$/.test(value.trim())
      ? Number.parseInt(value, 10)
      : 1

  if (!Number.isFinite(parsed)) return 1
  return Math.min(6, Math.max(1, Math.trunc(parsed)))
}

function inlineText(inlines: InlineNode[]): string {
  return inlines.map((inline) => {
    if (inline.type === 'HARD_BREAK' || inline.type === 'SOFT_BREAK') return ' '
    return inline.text ?? ''
  }).join('')
}
