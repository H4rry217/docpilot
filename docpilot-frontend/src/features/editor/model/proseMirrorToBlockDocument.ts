import type { JSONContent } from '@tiptap/core'
import type { BlockDocument, BlockNode, BlockType, InlineNode, MarkType } from '../../../entities/block/types'

type JsonAttrs = Record<string, unknown>

export function proseMirrorJsonToBlockDocument(json: JSONContent): BlockDocument {
  return {
    schemaVersion: stringAttr(json.attrs, 'schemaVersion', 'docpilot-block/1'),
    blocks: (json.content ?? []).map((node, index) => blockFromNode(node, `${index}`)).filter(Boolean),
    metadata: {
      source: 'frontend-live-preview'
    }
  }
}

function blockFromNode(node: JSONContent, path: string): BlockNode {
  const attrs = node.attrs ?? {}
  const type = blockType(node.type)

  if (node.type === 'codeBlock') {
    return block(path, 'CODE_BLOCK', { ...attrs, text: textFromNode(node), language: stringAttr(attrs, 'language', '') })
  }

  if (node.type === 'docpilotHtmlBlock') {
    return block(path, 'HTML_BLOCK', attrs)
  }

  if (node.type === 'horizontalRule') {
    return block(path, 'THEMATIC_BREAK', attrs)
  }

  if (type === 'PARAGRAPH' || type === 'HEADING' || type === 'TABLE_CELL') {
    return block(path, type, attrs, inlineContent(node), childBlocks(node, path))
  }

  return block(path, type, attrs, [], childBlocks(node, path))
}

function childBlocks(node: JSONContent, path: string): BlockNode[] {
  return (node.content ?? [])
    .filter((child) => child.type !== 'text' && child.type !== 'hardBreak')
    .map((child, index) => blockFromNode(child, `${path}.${index}`))
}

function inlineContent(node: JSONContent): InlineNode[] {
  const directInline = (node.content ?? [])
    .filter((child) => child.type === 'text' || child.type === 'hardBreak')
    .map(inlineFromNode)

  if (directInline.length) {
    return directInline
  }

  // Table cells usually wrap text in paragraphs. Flatten that for the current backend table cell contract.
  if (node.type === 'tableCell' || node.type === 'tableHeader') {
    return (node.content ?? []).flatMap((child) => inlineContent(child))
  }

  return []
}

function inlineFromNode(node: JSONContent): InlineNode {
  if (node.type === 'hardBreak') {
    return { type: 'HARD_BREAK', attrs: {}, marks: [] }
  }

  const link = node.marks?.find((mark) => mark.type === 'link')
  const code = node.marks?.some((mark) => mark.type === 'code') ?? false
  const text = node.text ?? ''

  if (code) {
    return { type: 'CODE', text, attrs: {}, marks: markTypes(node) }
  }

  if (link) {
    return {
      type: 'LINK',
      text,
      attrs: {
        href: stringAttr(link.attrs, 'href', ''),
        title: stringAttr(link.attrs, 'title', '')
      },
      marks: markTypes(node)
    }
  }

  return {
    type: 'TEXT',
    text,
    attrs: {},
    marks: markTypes(node)
  }
}

function markTypes(node: JSONContent): MarkType[] {
  return (node.marks ?? [])
    .map((mark) => {
      if (mark.type === 'bold') return 'BOLD'
      if (mark.type === 'italic') return 'ITALIC'
      if (mark.type === 'strike') return 'STRIKE'
      return null
    })
    .filter((mark): mark is MarkType => mark !== null)
}

function block(path: string, type: BlockType, attrs: JsonAttrs, inlines: InlineNode[] = [], children: BlockNode[] = []): BlockNode {
  return {
    id: stringAttr(attrs, 'blockId', `frontend${path.replaceAll('.', '')}`),
    type,
    attrs: stripInternalAttrs(attrs),
    inlines,
    children
  }
}

function blockType(type?: string): BlockType {
  switch (type) {
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
    case 'horizontalRule':
      return 'THEMATIC_BREAK'
    case 'table':
      return 'TABLE'
    case 'tableRow':
      return 'TABLE_ROW'
    case 'tableCell':
    case 'tableHeader':
      return 'TABLE_CELL'
    case 'docpilotHtmlBlock':
      return 'HTML_BLOCK'
    default:
      return 'UNSUPPORTED_BLOCK'
  }
}

function textFromNode(node: JSONContent): string {
  return (node.content ?? []).map((child) => child.text ?? '').join('')
}

function stringAttr(attrs: JsonAttrs | undefined, key: string, fallback: string): string {
  const value = attrs?.[key]
  return typeof value === 'string' ? value : fallback
}

function stripInternalAttrs(attrs: JsonAttrs): JsonAttrs {
  const next = { ...attrs }
  delete next.blockId
  delete next.sourceRange
  return next
}
