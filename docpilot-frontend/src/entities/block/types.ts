export type SourcePosition = {
  offset: number
  line: number
  column: number
}

export type SourceRange = {
  start: SourcePosition
  end: SourcePosition
}

export type BlockType =
  | 'DOCUMENT'
  | 'PARAGRAPH'
  | 'HEADING'
  | 'BLOCK_QUOTE'
  | 'BULLET_LIST'
  | 'ORDERED_LIST'
  | 'LIST_ITEM'
  | 'TASK_LIST_ITEM'
  | 'CODE_BLOCK'
  | 'THEMATIC_BREAK'
  | 'TABLE'
  | 'TABLE_ROW'
  | 'TABLE_CELL'
  | 'FRONT_MATTER'
  | 'MATH_BLOCK'
  | 'DIAGRAM_BLOCK'
  | 'CALLOUT'
  | 'FOOTNOTE_DEFINITION'
  | 'DEFINITION_LIST'
  | 'DEFINITION_TERM'
  | 'DEFINITION_ITEM'
  | 'TOC'
  | 'LINK_REFERENCE_DEFINITION'
  | 'HTML_BLOCK'
  | 'EXTENSION_BLOCK'
  | 'UNSUPPORTED_BLOCK'

export type InlineType =
  | 'TEXT'
  | 'SOFT_BREAK'
  | 'HARD_BREAK'
  | 'IMAGE'
  | 'MATH_INLINE'
  | 'FOOTNOTE_REF'
  | 'EMOJI'
  | 'HTML_INLINE'
  | 'EXTENSION_INLINE'
  | 'UNSUPPORTED_INLINE'

export type MarkType =
  | 'BOLD'
  | 'ITALIC'
  | 'STRIKE'
  | 'CODE'
  | 'LINK'
  | 'UNDERLINE'
  | 'INSERT'
  | 'SUBSCRIPT'
  | 'SUPERSCRIPT'
  | 'HIGHLIGHT'

export type InlineMark = {
  type: MarkType
  attrs: Record<string, unknown>
  sourceRange?: SourceRange
}

export type InlineNode = {
  type: InlineType
  text?: string
  attrs: Record<string, unknown>
  marks: InlineMark[]
  sourceRange?: SourceRange
}

export type BlockNode = {
  id: string
  type: BlockType
  attrs: Record<string, unknown>
  inlines: InlineNode[]
  children: BlockNode[]
  sourceRange?: SourceRange
}

export type BlockDocument = {
  schemaVersion: 'docpilot-block/2' | string
  blocks: BlockNode[]
  metadata: Record<string, unknown>
}
