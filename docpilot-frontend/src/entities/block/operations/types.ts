import type { BlockDocument, BlockNode, BlockType } from '../types'

export type DocumentOperationPreconditions = {
  documentVersion?: string
  blockHash?: string
  parentBlockId?: string | null
  expectedText?: string
}

export type DocumentBlockPosition = {
  parentBlockId?: string | null
  beforeBlockId?: string
  afterBlockId?: string
  index?: number
}

export type InsertBlockOperation = {
  type: 'insertBlock'
  block: BlockNode
  position?: DocumentBlockPosition
  preconditions?: DocumentOperationPreconditions
}

export type ReplaceBlockOperation = {
  type: 'replaceBlock'
  blockId: string
  block: BlockNode
  preconditions?: DocumentOperationPreconditions
}

export type DeleteBlockOperation = {
  type: 'deleteBlock'
  blockId: string
  preconditions?: DocumentOperationPreconditions
}

export type MoveBlockOperation = {
  type: 'moveBlock'
  blockId: string
  position: DocumentBlockPosition
  preconditions?: DocumentOperationPreconditions
}

export type UpdateBlockAttrsOperation = {
  type: 'updateBlockAttrs'
  blockId: string
  attrs: Record<string, unknown>
  preconditions?: DocumentOperationPreconditions
}

export type ReplaceBlockInlinesOperation = {
  type: 'replaceBlockInlines'
  blockId: string
  inlines: BlockNode['inlines']
  preconditions?: DocumentOperationPreconditions
}

export type ReplaceBlockChildrenOperation = {
  type: 'replaceBlockChildren'
  blockId: string
  children: BlockNode[]
  preconditions?: DocumentOperationPreconditions
}

export type ReplaceTextOperation = {
  type: 'replaceText'
  blockId: string
  replacement: string
  startOffset?: number
  endOffset?: number
  text?: string
  occurrence?: number
  caseSensitive?: boolean
  expectedText?: string
  preconditions?: DocumentOperationPreconditions
}

export type InsertTextOperation = {
  type: 'insertText'
  blockId: string
  offset: number
  text: string
  preconditions?: DocumentOperationPreconditions
}

export type DeleteTextRangeOperation = {
  type: 'deleteTextRange'
  blockId: string
  startOffset: number
  endOffset: number
  expectedText?: string
  preconditions?: DocumentOperationPreconditions
}

export type DocumentOperation =
  | InsertBlockOperation
  | ReplaceBlockOperation
  | DeleteBlockOperation
  | MoveBlockOperation
  | UpdateBlockAttrsOperation
  | ReplaceBlockInlinesOperation
  | ReplaceBlockChildrenOperation
  | ReplaceTextOperation
  | InsertTextOperation
  | DeleteTextRangeOperation

export type DocumentOperationBatch = {
  operations: DocumentOperation[]
}

export type DocumentOperationInput = DocumentOperation | DocumentOperation[] | DocumentOperationBatch

export type DocumentOperationOptions = {
  documentVersion?: string
  stopOnError?: boolean
}

export type DocumentOperationDiagnosticSeverity = 'info' | 'warning' | 'error'

export type DocumentOperationDiagnosticCode =
  | 'invalid_operation'
  | 'invalid_position'
  | 'block_not_found'
  | 'duplicate_block_id'
  | 'precondition_failed'
  | 'text_not_found'
  | 'ambiguous_text_match'
  | 'invalid_text_range'
  | 'unsupported_text_range'

export type DocumentOperationDiagnostic = {
  severity: DocumentOperationDiagnosticSeverity
  code: DocumentOperationDiagnosticCode
  message: string
  operationIndex?: number
  operationType?: DocumentOperation['type']
  blockId?: string
}

export type DocumentTextRange = {
  blockId: string
  startOffset: number
  endOffset: number
}

export type DocumentOperationPatchType =
  | 'block.inserted'
  | 'block.replaced'
  | 'block.deleted'
  | 'block.moved'
  | 'block.attrs.updated'
  | 'block.inlines.replaced'
  | 'block.children.replaced'
  | 'text.replaced'
  | 'text.inserted'
  | 'text.deleted'

export type DocumentOperationPatch = {
  type: DocumentOperationPatchType
  operationIndex: number
  operationType: DocumentOperation['type']
  message: string
  blockId?: string
  parentBlockId?: string | null
  range?: DocumentTextRange
  beforeBlock?: BlockNode
  afterBlock?: BlockNode
  beforeText?: string
  afterText?: string
}

export type DocumentOperationResult = {
  document: BlockDocument
  patches: DocumentOperationPatch[]
  diagnostics: DocumentOperationDiagnostic[]
  changed: boolean
  ok: boolean
  dryRun: boolean
}

export type GetBlockQuery = {
  type: 'getBlock'
  blockId: string
}

export type SearchTextQuery = {
  type: 'searchText'
  text: string
  caseSensitive?: boolean
  limit?: number
  blockTypes?: BlockType[]
}

export type GetBlockContextQuery = {
  type: 'getBlockContext'
  blockId: string
  before?: number
  after?: number
}

export type DocumentQuery = GetBlockQuery | SearchTextQuery | GetBlockContextQuery

export type DocumentQueryMatch = {
  blockId: string
  blockType: BlockType
  path: number[]
  range: DocumentTextRange
  text: string
}

export type DocumentBlockContextItem = {
  relation: 'before' | 'target' | 'after'
  blockId: string
  blockType: BlockType
  path: number[]
  text: string
  blockHash: string
}

export type DocumentQueryResult = {
  query: DocumentQuery
  diagnostics: DocumentOperationDiagnostic[]
  block?: BlockNode
  blockHash?: string
  parentBlockId?: string | null
  path?: number[]
  matches?: DocumentQueryMatch[]
  context?: DocumentBlockContextItem[]
}

export type DocumentOperationsDebugMode = 'preview' | 'apply'

export type DocumentOperationsDebugRunnerOptions = DocumentOperationOptions & {
  mode?: DocumentOperationsDebugMode
}

export type DocumentOperationsDebugResult = {
  mode: DocumentOperationsDebugMode
  result: DocumentOperationResult
  output: string
}
