import type { BlockDocument } from '../types'
import {
  cloneBlockDocument,
  findBlockLocation
} from './documentTree'
import { replaceInlineTextRange } from './inlineText'
import {
  blockNotFound,
  diagnostic,
  failed,
  succeeded,
  type OperationOutcome,
  type OperationRunOptions
} from './operationOutcome'
import { checkPreconditions } from './preconditions'
import {
  resolveReplaceTextRange,
  resolveTextRange
} from './resolvers'
import type {
  DeleteTextRangeOperation,
  InsertTextOperation,
  ReplaceTextOperation
} from './types'

export function replaceText(
  document: BlockDocument,
  operation: ReplaceTextOperation,
  operationIndex: number,
  options: OperationRunOptions
): OperationOutcome {
  const location = findBlockLocation(document, operation.blockId)
  if (!location) return blockNotFound(operation, operationIndex, operation.blockId)

  const range = resolveReplaceTextRange(location.block, operation, operationIndex)
  if (range.diagnostics.length || !range.value) return failed(range.diagnostics)

  const preconditionDiagnostics = checkPreconditions(
    document,
    operation,
    operationIndex,
    options,
    location,
    range.value.beforeText
  )
  if (preconditionDiagnostics.length) return failed(preconditionDiagnostics)

  const candidate = cloneBlockDocument(document)
  const candidateLocation = findBlockLocation(candidate, operation.blockId)
  if (!candidateLocation) return blockNotFound(operation, operationIndex, operation.blockId)

  const edit = replaceInlineTextRange(
    candidateLocation.block,
    range.value.startOffset,
    range.value.endOffset,
    operation.replacement
  )
  if (edit) return failed([diagnostic(operation, operationIndex, edit.code, edit.message, operation.blockId)])

  return succeeded(candidate, {
    type: 'text.replaced',
    operationIndex,
    operationType: operation.type,
    message: `Replaced text in block "${operation.blockId}".`,
    blockId: operation.blockId,
    range: {
      blockId: operation.blockId,
      startOffset: range.value.startOffset,
      endOffset: range.value.endOffset
    },
    beforeText: range.value.beforeText,
    afterText: operation.replacement
  })
}

export function insertText(
  document: BlockDocument,
  operation: InsertTextOperation,
  operationIndex: number,
  options: OperationRunOptions
): OperationOutcome {
  const location = findBlockLocation(document, operation.blockId)
  if (!location) return blockNotFound(operation, operationIndex, operation.blockId)

  const range = resolveTextRange(location.block, operation.offset, operation.offset, operation, operationIndex)
  if (range.diagnostics.length || !range.value) return failed(range.diagnostics)

  const preconditionDiagnostics = checkPreconditions(document, operation, operationIndex, options, location)
  if (preconditionDiagnostics.length) return failed(preconditionDiagnostics)

  const candidate = cloneBlockDocument(document)
  const candidateLocation = findBlockLocation(candidate, operation.blockId)
  if (!candidateLocation) return blockNotFound(operation, operationIndex, operation.blockId)

  const edit = replaceInlineTextRange(candidateLocation.block, operation.offset, operation.offset, operation.text)
  if (edit) return failed([diagnostic(operation, operationIndex, edit.code, edit.message, operation.blockId)])

  return succeeded(candidate, {
    type: 'text.inserted',
    operationIndex,
    operationType: operation.type,
    message: `Inserted text in block "${operation.blockId}".`,
    blockId: operation.blockId,
    range: {
      blockId: operation.blockId,
      startOffset: operation.offset,
      endOffset: operation.offset
    },
    afterText: operation.text
  })
}

export function deleteTextRange(
  document: BlockDocument,
  operation: DeleteTextRangeOperation,
  operationIndex: number,
  options: OperationRunOptions
): OperationOutcome {
  const location = findBlockLocation(document, operation.blockId)
  if (!location) return blockNotFound(operation, operationIndex, operation.blockId)

  const range = resolveTextRange(location.block, operation.startOffset, operation.endOffset, operation, operationIndex)
  if (range.diagnostics.length || !range.value) return failed(range.diagnostics)

  const preconditionDiagnostics = checkPreconditions(
    document,
    operation,
    operationIndex,
    options,
    location,
    range.value.beforeText
  )
  if (preconditionDiagnostics.length) return failed(preconditionDiagnostics)

  const candidate = cloneBlockDocument(document)
  const candidateLocation = findBlockLocation(candidate, operation.blockId)
  if (!candidateLocation) return blockNotFound(operation, operationIndex, operation.blockId)

  const edit = replaceInlineTextRange(candidateLocation.block, operation.startOffset, operation.endOffset, '')
  if (edit) return failed([diagnostic(operation, operationIndex, edit.code, edit.message, operation.blockId)])

  return succeeded(candidate, {
    type: 'text.deleted',
    operationIndex,
    operationType: operation.type,
    message: `Deleted text in block "${operation.blockId}".`,
    blockId: operation.blockId,
    range: {
      blockId: operation.blockId,
      startOffset: operation.startOffset,
      endOffset: operation.endOffset
    },
    beforeText: range.value.beforeText
  })
}
