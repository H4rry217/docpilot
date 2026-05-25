import type { JSONContent } from '@tiptap/core'
import type { BlockDocument, BlockNode, InlineNode, MarkType } from '../../../entities/block/types'
import type { ProseMirrorMark, ProseMirrorNode } from '../../../entities/prosemirror/types'

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
  const attrs = withBlockSource(block, block.attrs)

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
    case 'HTML_BLOCK':
      return { type: 'docpilotHtmlBlock', attrs }
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
    case 'CODE':
      return textNode(inline.text ?? '', [...markContent(inline.marks), { type: 'code' }])
    case 'LINK':
      return textNode(inline.text ?? '', [
        ...markContent(inline.marks),
        {
          type: 'link',
          attrs: {
            href: stringInlineAttr(inline, 'href'),
            title: stringInlineAttr(inline, 'title')
          }
        }
      ])
    case 'HTML_INLINE':
      return textNode(stringInlineAttr(inline, 'source'))
    case 'IMAGE':
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

function markContent(marks: MarkType[]): ProseMirrorMark[] {
  return marks.map((mark) => {
    if (mark === 'BOLD') return { type: 'bold' }
    if (mark === 'ITALIC') return { type: 'italic' }
    return { type: 'strike' }
  })
}

function withBlockSource(block: BlockNode, attrs: JsonObject): JsonObject {
  return {
    ...attrs,
    blockId: block.id,
    ...(block.sourceRange ? { sourceRange: block.sourceRange } : {})
  }
}

function textAttr(block: BlockNode, name: string): string {
  const value = block.attrs[name]
  return typeof value === 'string' ? value : ''
}

function stringInlineAttr(inline: InlineNode, name: string): string {
  const value = inline.attrs[name]
  return typeof value === 'string' ? value : ''
}
