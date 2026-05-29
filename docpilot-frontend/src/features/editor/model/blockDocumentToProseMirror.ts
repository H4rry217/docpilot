import type { JSONContent } from '@tiptap/core'
import type { BlockDocument, BlockNode, InlineMark, InlineNode } from '../../../entities/block/types'
import type { ProseMirrorMark, ProseMirrorNode } from '../../../entities/prosemirror/types'
import { normalizeBlockAttrsForProseMirror } from './blockAttrs'

type JsonObject = Record<string, unknown>

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
    content: document.blocks.map(blockToProseMirrorJson)
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
      return node('tableCell', attrs, inlineContent(block))
    case 'FRONT_MATTER':
      return { type: 'docpilotFrontMatter', attrs }
    case 'MATH_BLOCK':
      return { type: 'docpilotMathBlock', attrs }
    case 'DIAGRAM_BLOCK':
      return { type: 'docpilotDiagramBlock', attrs }
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
      return textNode(inline.text ?? '', markContent(inline.marks))
    case 'SOFT_BREAK':
      return textNode('\n', markContent(inline.marks))
    case 'HARD_BREAK':
      return { type: 'hardBreak' }
    case 'IMAGE':
      return { type: 'image', attrs: inline.attrs }
    case 'MATH_INLINE':
      return { type: 'docpilotMathInline', attrs: withInlineSource(inline, inline.attrs) }
    case 'FOOTNOTE_REF':
      return { type: 'docpilotFootnoteRef', attrs: withInlineSource(inline, inline.attrs) }
    case 'EMOJI':
      return { type: 'docpilotEmoji', attrs: withInlineSource(inline, inline.attrs) }
    case 'HTML_INLINE':
      return { type: 'docpilotHtmlInline', attrs: withInlineSource(inline, inline.attrs) }
    case 'EXTENSION_INLINE':
      return { type: 'docpilotExtensionInline', attrs: withInlineSource(inline, inline.attrs) }
    case 'UNSUPPORTED_INLINE':
      return textNode(inline.text ?? '')
    default:
      return textNode(inline.text ?? '')
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

function childContent(block: BlockNode): JSONContent[] {
  return block.children.map(blockToProseMirrorJson)
}

function textContent(text: string): JSONContent[] {
  return text ? [textNode(text)] : []
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

function textAttr(block: BlockNode, name: string): string {
  const value = block.attrs[name]
  return typeof value === 'string' ? value : ''
}
