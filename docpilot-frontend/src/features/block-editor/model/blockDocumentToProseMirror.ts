import type { JSONContent } from '@tiptap/core'
import type { BlockDocument, BlockNode, InlineMark, InlineNode } from '../../../entities/block/types'
import type { ProseMirrorMark, ProseMirrorNode } from '../../../entities/prosemirror/types'
import { normalizeBlockAttrsForProseMirror } from './blockAttrs'

type JsonObject = Record<string, unknown>

const DETAILS_OPEN_BLOCK = /^\s*<details\b([^>]*)>\s*(?:<summary\b[^>]*>(.*?)<\/summary>)?\s*$/is
const DETAILS_CLOSE_BLOCK = /^\s*<\/details>\s*$/i
const DETAILS_OPEN_ATTR = /(^|\s)open(\s|=|$)/i

function isObject(value: unknown): value is JsonObject {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

export function isProseMirrorDoc(value: unknown): value is ProseMirrorNode {
  return isObject(value) && value.type === 'doc'
}

export function isBlockDocument(value: unknown): value is BlockDocument {
  return isObject(value) && typeof value.schemaVersion === 'string' && Array.isArray(value.blocks)
}

export function blockDocumentToProseMirrorJson(document: BlockDocument): JSONContent {
  return {
    type: 'doc',
    attrs: {
      schemaVersion: document.schemaVersion
    },
    content: normalizeLegacyDetailsBlocks(document.blocks).map(blockToProseMirrorJson)
  }
}

function blockToProseMirrorJson(block: BlockNode): JSONContent {
  const attrs = withBlockSource(block, normalizeBlockAttrsForProseMirror(block.type, block.attrs, block.id))

  switch (block.type) {
    case 'PARAGRAPH':
      return node('paragraph', attrs, inlineContent(block))
    case 'HEADING':
      return node('heading', attrs, inlineContent(block))
    case 'BLOCK_QUOTE':
      return node('blockquote', attrs, childContent(block))
    case 'BULLET_LIST':
      return node('bulletList', attrs, childContent(block))
    case 'ORDERED_LIST':
      return node('orderedList', attrs, childContent(block))
    case 'LIST_ITEM':
      return node('listItem', attrs, childContent(block))
    case 'CODE_BLOCK':
      return node('codeBlock', attrs, textContent(textAttr(block, 'text')))
    case 'THEMATIC_BREAK':
      return { type: 'horizontalRule', attrs }
    case 'TABLE':
      return node('table', attrs, childContent(block))
    case 'TABLE_ROW':
      return node('tableRow', attrs, childContent(block))
    case 'TABLE_CELL':
      return node(attrs.header === true ? 'tableHeader' : 'tableCell', attrs, tableCellContent(block))
    case 'FRONT_MATTER':
      return { type: 'docpilotFrontMatter', attrs }
    case 'MATH_BLOCK':
      return { type: 'docpilotMathBlock', attrs }
    case 'DIAGRAM_BLOCK':
      return diagramBlockToCodeBlock(block, attrs)
    case 'CALLOUT':
      return node('docpilotCallout', attrs, childContent(block))
    case 'FOOTNOTE_DEFINITION':
      return node('docpilotFootnoteDefinition', attrs, childContent(block))
    case 'DEFINITION_LIST':
      return node('docpilotDefinitionList', attrs, childContent(block))
    case 'DEFINITION_TERM':
      return node('docpilotDefinitionTerm', attrs, inlineContent(block))
    case 'DEFINITION_ITEM':
      return node('docpilotDefinitionItem', attrs, childContent(block))
    case 'TOC':
      return { type: 'docpilotToc', attrs }
    case 'LINK_REFERENCE_DEFINITION':
      return { type: 'docpilotLinkReferenceDefinition', attrs }
    case 'HTML_BLOCK':
      return { type: 'docpilotHtmlBlock', attrs }
    case 'EXTENSION_BLOCK':
      return node('docpilotExtensionBlock', attrs, childContent(block))
    case 'UNSUPPORTED_BLOCK':
      return { type: 'docpilotUnsupportedBlock', attrs }
    case 'DOCUMENT':
      return node('doc', attrs, childContent(block))
    case 'TASK_LIST_ITEM':
      return node('listItem', attrs, childContent(block))
    default:
      return { type: 'docpilotUnsupportedBlock', attrs }
  }
}

function inlineToProseMirrorJson(inline: InlineNode): JSONContent {
  switch (inline.type) {
    case 'TEXT':
      return textNode(unescapeMarkdownText(inline.text ?? ''), markContent(inline.marks))
    case 'SOFT_BREAK':
      return textNode('\n', markContent(inline.marks))
    case 'HARD_BREAK':
      return { type: 'hardBreak' }
    case 'IMAGE':
      return { type: 'image', attrs: withInlineSource(inline, inline.attrs) }
    case 'MATH_INLINE':
      return { type: 'docpilotMathInline', attrs: withInlineText(inline, inline.attrs) }
    case 'FOOTNOTE_REF':
      return { type: 'docpilotFootnoteRef', attrs: withInlineSource(inline, inline.attrs) }
    case 'EMOJI':
      return { type: 'docpilotEmoji', attrs: withInlineText(inline, inline.attrs) }
    case 'HTML_INLINE':
      return { type: 'docpilotHtmlInline', attrs: withInlineSourceAttr(inline, inline.attrs) }
    case 'EXTENSION_INLINE':
      return { type: 'docpilotExtensionInline', attrs: withInlineSourceAttr(inline, inline.attrs) }
    case 'UNSUPPORTED_INLINE':
      return textNode(unescapeMarkdownText(inline.text ?? ''))
    default:
      return textNode(unescapeMarkdownText(inline.text ?? ''))
  }
}

function node(type: string, attrs: JsonObject, content: JSONContent[]): JSONContent {
  return { type, attrs, content }
}

function textNode(text: string, marks: ProseMirrorMark[] = []): JSONContent {
  return marks.length ? { type: 'text', text, marks } : { type: 'text', text }
}

function inlineContent(block: BlockNode): JSONContent[] {
  return block.inlines.map(inlineToProseMirrorJson).filter((node) => node.type !== 'text' || Boolean(node.text))
}

function tableCellContent(block: BlockNode): JSONContent[] {
  const children = childContent(block)
  if (children.length) {
    return children
  }
  return [node('paragraph', {}, inlineContent(block))]
}

function childContent(block: BlockNode): JSONContent[] {
  return normalizeLegacyDetailsBlocks(block.children).map(blockToProseMirrorJson)
}

function textContent(text: string): JSONContent[] {
  return text ? [textNode(text)] : []
}

function diagramBlockToCodeBlock(block: BlockNode, attrs: JsonObject): JSONContent {
  const codeAttrs = { ...attrs }
  const rawEngine = codeAttrs.engine
  delete codeAttrs.engine
  delete codeAttrs.text
  delete codeAttrs.source
  delete codeAttrs.raw
  const language = stringAttr(rawEngine, 'mermaid') || 'mermaid'
  return node('codeBlock', { ...codeAttrs, language }, textContent(textAttr(block, 'text')))
}

function markContent(marks: InlineMark[]): ProseMirrorMark[] {
  return marks.map((mark) => {
    if (mark.type === 'BOLD') return { type: 'bold', attrs: mark.attrs }
    if (mark.type === 'ITALIC') return { type: 'italic', attrs: mark.attrs }
    if (mark.type === 'STRIKE') return { type: 'strike', attrs: mark.attrs }
    if (mark.type === 'CODE') return { type: 'code', attrs: mark.attrs }
    if (mark.type === 'LINK') return { type: 'link', attrs: mark.attrs }
    if (mark.type === 'UNDERLINE') return { type: 'underline', attrs: mark.attrs }
    if (mark.type === 'INSERT') return { type: 'insert', attrs: mark.attrs }
    if (mark.type === 'SUBSCRIPT') return { type: 'subscript', attrs: mark.attrs }
    if (mark.type === 'SUPERSCRIPT') return { type: 'superscript', attrs: mark.attrs }
    return { type: 'highlight', attrs: mark.attrs }
  })
}

function withBlockSource(block: BlockNode, attrs: JsonObject): JsonObject {
  return {
    ...attrs,
    blockId: block.id,
    ...(block.sourceRange ? { sourceRange: block.sourceRange } : {})
  }
}

function withInlineSource(inline: InlineNode, attrs: JsonObject): JsonObject {
  return {
    ...attrs,
    ...(inline.sourceRange ? { sourceRange: inline.sourceRange } : {})
  }
}

function withInlineText(inline: InlineNode, attrs: JsonObject): JsonObject {
  return withInlineSource(inline, {
    ...attrs,
    text: inline.text ?? ''
  })
}

function withInlineSourceAttr(inline: InlineNode, attrs: JsonObject): JsonObject {
  return withInlineSource(inline, {
    ...attrs,
    source: stringAttr(attrs.source, inline.text ?? '')
  })
}

function textAttr(block: BlockNode, name: string): string {
  const value = block.attrs[name]
  return typeof value === 'string' ? value : ''
}

function stringAttr(value: unknown, fallback: string): string {
  return typeof value === 'string' ? value : fallback
}

function unescapeMarkdownText(text: string): string {
  let result = ''
  for (let index = 0; index < text.length; index += 1) {
    const current = text[index]
    const next = text[index + 1]
    if (current === '\\' && next && isMarkdownEscapable(next)) {
      result += next
      index += 1
    } else {
      result += current
    }
  }
  return result
}

function isMarkdownEscapable(character: string): boolean {
  if (character.length !== 1) return false
  const code = character.charCodeAt(0)
  return code >= 33 && code <= 126 && !/[A-Za-z0-9]/.test(character)
}

type DetailsOpening = {
  open: boolean
  title: string
}

function normalizeLegacyDetailsBlocks(blocks: BlockNode[]): BlockNode[] {
  const normalized: BlockNode[] = []

  for (let index = 0; index < blocks.length; index += 1) {
    const opening = detailsOpening(blocks[index])
    if (!opening) {
      normalized.push(blocks[index])
      continue
    }

    const closeIndex = findDetailsCloseIndex(blocks, index + 1)
    if (closeIndex === -1) {
      normalized.push(blocks[index])
      continue
    }

    normalized.push({
      ...blocks[index],
      type: 'CALLOUT',
      attrs: {
        kind: 'details',
        title: opening.title,
        collapsible: true,
        open: opening.open
      },
      inlines: [],
      children: normalizeLegacyDetailsBlocks(blocks.slice(index + 1, closeIndex))
    })
    index = closeIndex
  }

  return normalized
}

function findDetailsCloseIndex(blocks: BlockNode[], fromIndex: number): number {
  let depth = 1

  for (let index = fromIndex; index < blocks.length; index += 1) {
    if (detailsOpening(blocks[index])) {
      depth += 1
      continue
    }

    if (!isDetailsClose(blocks[index])) continue
    depth -= 1
    if (depth === 0) return index
  }

  return -1
}

function detailsOpening(block: BlockNode): DetailsOpening | null {
  const source = htmlBlockSource(block)
  if (!source) return null

  const match = DETAILS_OPEN_BLOCK.exec(source)
  if (!match) return null

  const title = htmlText(match[2])
  return {
    title: title || 'Details',
    open: DETAILS_OPEN_ATTR.test(match[1] ?? '')
  }
}

function isDetailsClose(block: BlockNode): boolean {
  const source = htmlBlockSource(block)
  return Boolean(source && DETAILS_CLOSE_BLOCK.test(source))
}

function htmlBlockSource(block: BlockNode): string | null {
  if (block.type !== 'HTML_BLOCK') return null
  return typeof block.attrs.source === 'string' ? block.attrs.source : null
}

function htmlText(html: string | undefined): string {
  if (!html) return ''
  return unescapeHtml(html.replace(/<[^>]+>/gis, '')).trim()
}

function unescapeHtml(text: string): string {
  return text
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&amp;', '&')
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
}
