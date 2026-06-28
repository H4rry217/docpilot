import type { BlockNode, InlineMark, InlineNode } from '../types'
import { blockPlainText } from './documentTree'
import type { DocumentOperationDiagnostic } from './types'

type InlineTextSegment = {
  inlineIndex: number
  startOffset: number
  endOffset: number
  text: string
  editable: boolean
}

export function replaceInlineTextRange(
  block: BlockNode,
  startOffset: number,
  endOffset: number,
  replacement: string
): { code: DocumentOperationDiagnostic['code']; message: string } | null {
  if (startOffset === endOffset) {
    return insertInlineText(block, startOffset, replacement)
  }

  const segments = inlineTextSegments(block)
  const overlappingSegments = segments.filter((segment) => segment.endOffset > startOffset && segment.startOffset < endOffset)
  if (!overlappingSegments.length) {
    return {
      code: 'unsupported_text_range',
      message: `Text range ${startOffset}..${endOffset} does not overlap editable inline text.`
    }
  }

  const unsupportedSegment = overlappingSegments.find((segment) => !segment.editable)
  if (unsupportedSegment) {
    return {
      code: 'unsupported_text_range',
      message: 'Text range crosses a non-text inline node.'
    }
  }

  const segmentByInlineIndex = new Map(overlappingSegments.map((segment) => [segment.inlineIndex, segment]))
  const nextInlines: InlineNode[] = []
  let insertedReplacement = false

  block.inlines.forEach((inline, inlineIndex) => {
    const segment = segmentByInlineIndex.get(inlineIndex)
    if (!segment) {
      nextInlines.push(inline)
      return
    }

    const beforeLength = Math.max(0, Math.min(segment.text.length, startOffset - segment.startOffset))
    const afterStart = Math.max(0, Math.min(segment.text.length, endOffset - segment.startOffset))
    const beforeText = segment.text.slice(0, beforeLength)
    const afterText = segment.text.slice(afterStart)

    if (beforeText) nextInlines.push(textInlineLike(inline, beforeText))
    if (!insertedReplacement && replacement) nextInlines.push(textInlineLike(inline, replacement))
    insertedReplacement = true
    if (afterText) nextInlines.push(textInlineLike(inline, afterText))
  })

  block.inlines = compactInlineText(nextInlines)
  return null
}

function insertInlineText(
  block: BlockNode,
  offset: number,
  text: string
): { code: DocumentOperationDiagnostic['code']; message: string } | null {
  if (!text) return null

  if (!block.inlines.length) {
    block.inlines = [newTextInline(text, [])]
    return null
  }

  const segments = inlineTextSegments(block)
  const editableSegment = segments.find((segment) => {
    if (!segment.editable) return false
    return offset >= segment.startOffset && offset <= segment.endOffset
  })

  if (!editableSegment) {
    const blockLength = blockPlainText(block).length
    if (offset === blockLength) {
      block.inlines = compactInlineText([...block.inlines, newTextInline(text, [])])
      return null
    }

    return {
      code: 'unsupported_text_range',
      message: `Cannot insert text at offset ${offset}; the position is not inside editable inline text.`
    }
  }

  const inline = block.inlines[editableSegment.inlineIndex]
  const localOffset = offset - editableSegment.startOffset
  const beforeText = editableSegment.text.slice(0, localOffset)
  const afterText = editableSegment.text.slice(localOffset)
  const replacement = [
    beforeText ? textInlineLike(inline, beforeText) : null,
    textInlineLike(inline, text),
    afterText ? textInlineLike(inline, afterText) : null
  ].filter((item): item is InlineNode => item !== null)

  block.inlines.splice(editableSegment.inlineIndex, 1, ...replacement)
  block.inlines = compactInlineText(block.inlines)
  return null
}

function inlineTextSegments(block: BlockNode): InlineTextSegment[] {
  const segments: InlineTextSegment[] = []
  let offset = 0

  block.inlines.forEach((inline, inlineIndex) => {
    const text = inlineOffsetText(inline)
    if (!text) return

    const startOffset = offset
    const endOffset = startOffset + text.length
    segments.push({
      inlineIndex,
      startOffset,
      endOffset,
      text,
      editable: inline.type === 'TEXT'
    })
    offset = endOffset
  })

  return segments
}

function inlineOffsetText(inline: InlineNode): string {
  if (inline.type === 'HARD_BREAK' || inline.type === 'SOFT_BREAK') return '\n'
  return inline.text ?? ''
}

function compactInlineText(inlines: InlineNode[]): InlineNode[] {
  const compacted: InlineNode[] = []

  inlines.forEach((inline) => {
    if (inline.type === 'TEXT' && !inline.text) return

    const previous = compacted[compacted.length - 1]
    if (
      previous
      && previous.type === 'TEXT'
      && inline.type === 'TEXT'
      && sameJson(previous.attrs, inline.attrs)
      && sameJson(previous.marks, inline.marks)
    ) {
      previous.text = `${previous.text ?? ''}${inline.text ?? ''}`
      return
    }

    compacted.push(cloneInline(inline))
  })

  return compacted
}

function textInlineLike(inline: InlineNode, text: string): InlineNode {
  return newTextInline(text, inline.marks, inline.attrs)
}

function newTextInline(text: string, marks: InlineMark[], attrs: Record<string, unknown> = {}): InlineNode {
  return {
    type: 'TEXT',
    text,
    attrs: JSON.parse(JSON.stringify(attrs)) as Record<string, unknown>,
    marks: JSON.parse(JSON.stringify(marks)) as InlineMark[]
  }
}

function cloneInline(inline: InlineNode): InlineNode {
  return JSON.parse(JSON.stringify(inline)) as InlineNode
}

function sameJson(left: unknown, right: unknown): boolean {
  return JSON.stringify(left) === JSON.stringify(right)
}
