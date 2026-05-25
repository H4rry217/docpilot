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
  | 'HTML_BLOCK'
  | 'UNSUPPORTED_BLOCK'

export type InlineType =
  | 'TEXT'
  | 'SOFT_BREAK'
  | 'HARD_BREAK'
  | 'CODE'
  | 'LINK'
  | 'IMAGE'
  | 'HTML_INLINE'
  | 'UNSUPPORTED_INLINE'

export type MarkType = 'BOLD' | 'ITALIC' | 'STRIKE'

export type InlineNode = {
  type: InlineType
  text?: string
  attrs: Record<string, unknown>
  marks: MarkType[]
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
  schemaVersion: 'docpilot-block/1' | string
  blocks: BlockNode[]
  metadata: Record<string, unknown>
}
