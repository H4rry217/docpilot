import type { JSONContent } from '@tiptap/core'
import type { BlockDocument, BlockNode, BlockType, InlineMark, InlineNode, MarkType } from '@/entities/block/types'
import { normalizeBlockAttrsForCanonical, stripInternalAttrs, stringAttr, type JsonAttrs } from './blockAttrs'
import { blockIdentityId, canonicalBlockId, isTransientBlockId } from './docpilotBlockIdentity'

export function proseMirrorJsonToBlockDocument(json: JSONContent): BlockDocument {
  return {
    schemaVersion: stringAttr(json.attrs, 'schemaVersion', 'docpilot-block/2'),
    blocks: (json.content ?? []).map((node, index) => blockFromNode(node, `${index}`)).filter(Boolean),
    metadata: {
      source: 'frontend-live-preview'
    }
  }
}

export function blockDocumentForSave(document: BlockDocument): BlockDocument {
  return {
    ...document,
    blocks: document.blocks.map(blockForSave)
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

  if (node.type === 'docpilotMathBlock') {
    return block(path, 'MATH_BLOCK', attrs)
  }

  if (node.type === 'docpilotDiagramBlock') {
    return block(path, 'CODE_BLOCK', {
      ...attrs,
      language: stringAttr(attrs, 'engine', 'mermaid') || 'mermaid',
      text: stringAttr(attrs, 'text', '')
    })
  }

  if (node.type === 'docpilotFrontMatter') {
    return block(path, 'FRONT_MATTER', attrs)
  }

  if (node.type === 'horizontalRule') {
    return block(path, 'THEMATIC_BREAK', attrs)
  }

  if (node.type === 'tableCell' || node.type === 'tableHeader') {
    return block(path, type, { ...attrs, header: node.type === 'tableHeader' }, inlineContent(node))
  }

  if (type === 'PARAGRAPH' || type === 'HEADING' || type === 'DEFINITION_TERM') {
    return block(path, type, attrs, inlineContent(node), childBlocks(node, path))
  }

  return block(path, type, attrs, [], childBlocks(node, path))
}

function childBlocks(node: JSONContent, path: string): BlockNode[] {
  return (node.content ?? [])
    .filter((child) => !isInlineNode(child))
    .map((child, index) => blockFromNode(child, `${path}.${index}`))
}

function inlineContent(node: JSONContent): InlineNode[] {
  const directInline = (node.content ?? [])
    .filter(isInlineNode)
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

  if (node.type === 'image') {
    const attrs = stripInternalAttrs(node.attrs ?? {})
    return {
      type: 'IMAGE',
      text: stringAttr(attrs, 'alt', ''),
      attrs,
      marks: markTypes(node)
    }
  }

  if (node.type === 'docpilotMathInline') {
    return { type: 'MATH_INLINE', text: stringAttr(node.attrs, 'text', ''), attrs: node.attrs ?? {}, marks: [] }
  }

  if (node.type === 'docpilotFootnoteRef') {
    return { type: 'FOOTNOTE_REF', attrs: node.attrs ?? {}, marks: [] }
  }

  if (node.type === 'docpilotHtmlInline') {
    return { type: 'HTML_INLINE', text: stringAttr(node.attrs, 'source', ''), attrs: node.attrs ?? {}, marks: [] }
  }

  if (node.type === 'docpilotEmoji') {
    return { type: 'EMOJI', text: stringAttr(node.attrs, 'shortcut', ''), attrs: node.attrs ?? {}, marks: [] }
  }

  if (node.type === 'docpilotExtensionInline') {
    return { type: 'EXTENSION_INLINE', text: stringAttr(node.attrs, 'source', ''), attrs: node.attrs ?? {}, marks: [] }
  }

  const text = node.text ?? ''

  return {
    type: 'TEXT',
    text,
    attrs: {},
    marks: markTypes(node)
  }
}

function markTypes(node: JSONContent): InlineMark[] {
  return (node.marks ?? [])
    .map((mark) => {
      const type = markType(mark.type)
      if (!type) return null
      return {
        type,
        attrs: mark.attrs ?? {}
      }
    })
    .filter((mark): mark is InlineMark => mark !== null)
}

function markType(type: string): MarkType | null {
  switch (type) {
    case 'bold':
      return 'BOLD'
    case 'italic':
      return 'ITALIC'
    case 'strike':
      return 'STRIKE'
    case 'code':
      return 'CODE'
    case 'link':
      return 'LINK'
    case 'underline':
      return 'UNDERLINE'
    case 'insert':
      return 'INSERT'
    case 'subscript':
      return 'SUBSCRIPT'
    case 'superscript':
      return 'SUPERSCRIPT'
    case 'highlight':
      return 'HIGHLIGHT'
    default:
      return null
  }
}

function block(_path: string, type: BlockType, attrs: JsonAttrs, inlines: InlineNode[] = [], children: BlockNode[] = []): BlockNode {
  const id = canonicalBlockId(attrs) || blockIdentityId(attrs)
  return {
    id,
    type,
    attrs: normalizeBlockAttrsForCanonical(type, stripInternalAttrs(attrs), id),
    inlines,
    children
  }
}

function blockForSave(block: BlockNode): BlockNode {
  return {
    ...block,
    id: isTransientBlockId(block.id) ? '' : block.id,
    children: block.children.map(blockForSave)
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
    case 'docpilotFrontMatter':
      return 'FRONT_MATTER'
    case 'docpilotMathBlock':
      return 'MATH_BLOCK'
    case 'docpilotDiagramBlock':
      return 'CODE_BLOCK'
    case 'docpilotCallout':
      return 'CALLOUT'
    case 'docpilotFootnoteDefinition':
      return 'FOOTNOTE_DEFINITION'
    case 'docpilotDefinitionList':
      return 'DEFINITION_LIST'
    case 'docpilotDefinitionTerm':
      return 'DEFINITION_TERM'
    case 'docpilotDefinitionItem':
      return 'DEFINITION_ITEM'
    case 'docpilotToc':
      return 'TOC'
    case 'docpilotLinkReferenceDefinition':
      return 'LINK_REFERENCE_DEFINITION'
    case 'docpilotExtensionBlock':
      return 'EXTENSION_BLOCK'
    default:
      return 'UNSUPPORTED_BLOCK'
  }
}

function isInlineNode(node: JSONContent): boolean {
  return [
    'text',
    'hardBreak',
    'image',
    'docpilotMathInline',
    'docpilotFootnoteRef',
    'docpilotHtmlInline',
    'docpilotEmoji',
    'docpilotExtensionInline'
  ].includes(node.type ?? '')
}

function textFromNode(node: JSONContent): string {
  return (node.content ?? []).map((child) => child.text ?? '').join('')
}
