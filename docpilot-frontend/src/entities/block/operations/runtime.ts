import type { BlockDocument, BlockNode, InlineMark, InlineNode } from '../types'
import {
  blockPlainText,
  cloneBlock,
  cloneBlockDocument,
  findBlockLocation,
  findTextMatches,
  isDescendantBlockId,
  stableBlockHash,
  type BlockLocation
} from './documentTree'
import type {
  DeleteTextRangeOperation,
  DocumentOperationBatch,
  DocumentBlockPosition,
  DocumentOperation,
  DocumentOperationDiagnostic,
  DocumentOperationInput,
  DocumentOperationOptions,
  DocumentOperationPatch,
  DocumentOperationResult,
  InsertTextOperation,
  ReplaceTextOperation
} from './types'

type OperationRunOptions = DocumentOperationOptions & {
  dryRun: boolean
}

type OperationOutcome = {
  document?: BlockDocument
  patches: DocumentOperationPatch[]
  diagnostics: DocumentOperationDiagnostic[]
}

type ResolvedInsertPosition = {
  parentBlocks: BlockNode[]
  parentBlockId: string | null
  index: number
}

type ResolvedTextRange = {
  startOffset: number
  endOffset: number
  beforeText: string
}

type InlineTextSegment = {
  inlineIndex: number
  startOffset: number
  endOffset: number
  text: string
  editable: boolean
}

export function applyDocumentOperations(
  document: BlockDocument,
  operations: DocumentOperationInput,
  options: DocumentOperationOptions = {}
): DocumentOperationResult {
  return runDocumentOperations(document, operations, { ...options, dryRun: false })
}

export function previewDocumentOperations(
  document: BlockDocument,
  operations: DocumentOperationInput,
  options: DocumentOperationOptions = {}
): DocumentOperationResult {
  return runDocumentOperations(document, operations, { ...options, dryRun: true })
}

function runDocumentOperations(
  document: BlockDocument,
  input: DocumentOperationInput,
  options: OperationRunOptions
): DocumentOperationResult {
  let workingDocument = cloneBlockDocument(document)
  const operations = operationsFromInput(input)
  const patches: DocumentOperationPatch[] = []
  const diagnostics: DocumentOperationDiagnostic[] = []

  for (const [operationIndex, operation] of operations.entries()) {
    const outcome = runOperation(workingDocument, operation, operationIndex, options)
    diagnostics.push(...outcome.diagnostics)
    patches.push(...outcome.patches)

    if (outcome.document) {
      workingDocument = outcome.document
    }

    if (options.stopOnError && outcome.diagnostics.some((item) => item.severity === 'error')) {
      break
    }
  }

  const hasErrors = diagnostics.some((diagnosticItem) => diagnosticItem.severity === 'error')

  return {
    document: workingDocument,
    patches,
    diagnostics,
    changed: patches.length > 0,
    ok: !hasErrors,
    dryRun: options.dryRun
  }
}

function operationsFromInput(input: DocumentOperationInput): DocumentOperation[] {
  if (Array.isArray(input)) return input
  if (isOperationBatch(input)) return input.operations
  return [input]
}

function isOperationBatch(input: DocumentOperationInput): input is DocumentOperationBatch {
  return 'operations' in input && Array.isArray(input.operations)
}

function runOperation(
  document: BlockDocument,
  operation: DocumentOperation,
  operationIndex: number,
  options: OperationRunOptions
): OperationOutcome {
  switch (operation.type) {
    case 'insertBlock':
      return insertBlock(document, operation, operationIndex, options)
    case 'replaceBlock':
      return replaceBlock(document, operation, operationIndex, options)
    case 'deleteBlock':
      return deleteBlock(document, operation, operationIndex, options)
    case 'moveBlock':
      return moveBlock(document, operation, operationIndex, options)
    case 'updateBlockAttrs':
      return updateBlockAttrs(document, operation, operationIndex, options)
    case 'replaceBlockInlines':
      return replaceBlockInlines(document, operation, operationIndex, options)
    case 'replaceBlockChildren':
      return replaceBlockChildren(document, operation, operationIndex, options)
    case 'replaceText':
      return replaceText(document, operation, operationIndex, options)
    case 'insertText':
      return insertText(document, operation, operationIndex, options)
    case 'deleteTextRange':
      return deleteTextRange(document, operation, operationIndex, options)
    default:
      return {
        patches: [],
        diagnostics: [
          diagnostic(
            operation,
            operationIndex,
            'invalid_operation',
            `Unsupported document operation: ${(operation as DocumentOperation).type}`
          )
        ]
      }
  }
}

function insertBlock(
  document: BlockDocument,
  operation: Extract<DocumentOperation, { type: 'insertBlock' }>,
  operationIndex: number,
  options: OperationRunOptions
): OperationOutcome {
  const preconditionDiagnostics = checkPreconditions(document, operation, operationIndex, options)
  if (preconditionDiagnostics.length) return failed(preconditionDiagnostics)

  const candidate = cloneBlockDocument(document)
  if (findBlockLocation(candidate, operation.block.id)) {
    return failed([
      diagnostic(
        operation,
        operationIndex,
        'duplicate_block_id',
        `Block "${operation.block.id}" already exists.`,
        operation.block.id
      )
    ])
  }

  const position = resolveInsertPosition(candidate, operation.position ?? {}, operation, operationIndex)
  if (position.diagnostics.length || !position.value) return failed(position.diagnostics)

  const block = cloneBlock(operation.block)
  position.value.parentBlocks.splice(position.value.index, 0, block)

  return succeeded(candidate, {
    type: 'block.inserted',
    operationIndex,
    operationType: operation.type,
    message: `Inserted block "${block.id}".`,
    blockId: block.id,
    parentBlockId: position.value.parentBlockId,
    afterBlock: cloneBlock(block)
  })
}

function replaceBlock(
  document: BlockDocument,
  operation: Extract<DocumentOperation, { type: 'replaceBlock' }>,
  operationIndex: number,
  options: OperationRunOptions
): OperationOutcome {
  const location = findBlockLocation(document, operation.blockId)
  if (!location) return blockNotFound(operation, operationIndex, operation.blockId)

  const preconditionDiagnostics = checkPreconditions(document, operation, operationIndex, options, location)
  if (preconditionDiagnostics.length) return failed(preconditionDiagnostics)

  const duplicate = findBlockLocation(document, operation.block.id)
  if (duplicate && duplicate.block.id !== operation.blockId) {
    return failed([
      diagnostic(
        operation,
        operationIndex,
        'duplicate_block_id',
        `Block "${operation.block.id}" already exists.`,
        operation.block.id
      )
    ])
  }

  const candidate = cloneBlockDocument(document)
  const candidateLocation = findBlockLocation(candidate, operation.blockId)
  if (!candidateLocation) return blockNotFound(operation, operationIndex, operation.blockId)

  const beforeBlock = cloneBlock(candidateLocation.block)
  const afterBlock = cloneBlock(operation.block)
  candidateLocation.parentBlocks.splice(candidateLocation.index, 1, afterBlock)

  return succeeded(candidate, {
    type: 'block.replaced',
    operationIndex,
    operationType: operation.type,
    message: `Replaced block "${operation.blockId}" with "${afterBlock.id}".`,
    blockId: operation.blockId,
    parentBlockId: candidateLocation.parentBlockId,
    beforeBlock,
    afterBlock: cloneBlock(afterBlock)
  })
}

function deleteBlock(
  document: BlockDocument,
  operation: Extract<DocumentOperation, { type: 'deleteBlock' }>,
  operationIndex: number,
  options: OperationRunOptions
): OperationOutcome {
  const location = findBlockLocation(document, operation.blockId)
  if (!location) return blockNotFound(operation, operationIndex, operation.blockId)

  const preconditionDiagnostics = checkPreconditions(document, operation, operationIndex, options, location)
  if (preconditionDiagnostics.length) return failed(preconditionDiagnostics)

  const candidate = cloneBlockDocument(document)
  const candidateLocation = findBlockLocation(candidate, operation.blockId)
  if (!candidateLocation) return blockNotFound(operation, operationIndex, operation.blockId)

  const beforeBlock = cloneBlock(candidateLocation.block)
  candidateLocation.parentBlocks.splice(candidateLocation.index, 1)

  return succeeded(candidate, {
    type: 'block.deleted',
    operationIndex,
    operationType: operation.type,
    message: `Deleted block "${operation.blockId}".`,
    blockId: operation.blockId,
    parentBlockId: candidateLocation.parentBlockId,
    beforeBlock
  })
}

function moveBlock(
  document: BlockDocument,
  operation: Extract<DocumentOperation, { type: 'moveBlock' }>,
  operationIndex: number,
  options: OperationRunOptions
): OperationOutcome {
  const location = findBlockLocation(document, operation.blockId)
  if (!location) return blockNotFound(operation, operationIndex, operation.blockId)

  const preconditionDiagnostics = checkPreconditions(document, operation, operationIndex, options, location)
  if (preconditionDiagnostics.length) return failed(preconditionDiagnostics)

  if (operation.position.parentBlockId === operation.blockId) {
    return failed([
      diagnostic(
        operation,
        operationIndex,
        'invalid_position',
        `Block "${operation.blockId}" cannot be moved into itself.`,
        operation.blockId
      )
    ])
  }

  if (operation.position.parentBlockId && isDescendantBlockId(location.block, operation.position.parentBlockId)) {
    return failed([
      diagnostic(
        operation,
        operationIndex,
        'invalid_position',
        `Block "${operation.blockId}" cannot be moved into its own descendant.`,
        operation.blockId
      )
    ])
  }

  if (operation.position.beforeBlockId === operation.blockId || operation.position.afterBlockId === operation.blockId) {
    return failed([
      diagnostic(operation, operationIndex, 'invalid_position', 'A block cannot be moved relative to itself.', operation.blockId)
    ])
  }

  if (
    (operation.position.beforeBlockId && isDescendantBlockId(location.block, operation.position.beforeBlockId))
    || (operation.position.afterBlockId && isDescendantBlockId(location.block, operation.position.afterBlockId))
  ) {
    return failed([
      diagnostic(
        operation,
        operationIndex,
        'invalid_position',
        `Block "${operation.blockId}" cannot be moved relative to one of its descendants.`,
        operation.blockId
      )
    ])
  }

  const candidate = cloneBlockDocument(document)
  const candidateLocation = findBlockLocation(candidate, operation.blockId)
  if (!candidateLocation) return blockNotFound(operation, operationIndex, operation.blockId)

  const movingBlock = candidateLocation.parentBlocks.splice(candidateLocation.index, 1)[0]
  const position = resolveInsertPosition(candidate, operation.position, operation, operationIndex)
  if (position.diagnostics.length || !position.value) return failed(position.diagnostics)

  position.value.parentBlocks.splice(position.value.index, 0, movingBlock)

  return succeeded(candidate, {
    type: 'block.moved',
    operationIndex,
    operationType: operation.type,
    message: `Moved block "${operation.blockId}".`,
    blockId: operation.blockId,
    parentBlockId: position.value.parentBlockId,
    afterBlock: cloneBlock(movingBlock)
  })
}

function updateBlockAttrs(
  document: BlockDocument,
  operation: Extract<DocumentOperation, { type: 'updateBlockAttrs' }>,
  operationIndex: number,
  options: OperationRunOptions
): OperationOutcome {
  const location = findBlockLocation(document, operation.blockId)
  if (!location) return blockNotFound(operation, operationIndex, operation.blockId)

  const preconditionDiagnostics = checkPreconditions(document, operation, operationIndex, options, location)
  if (preconditionDiagnostics.length) return failed(preconditionDiagnostics)

  const candidate = cloneBlockDocument(document)
  const candidateLocation = findBlockLocation(candidate, operation.blockId)
  if (!candidateLocation) return blockNotFound(operation, operationIndex, operation.blockId)

  const beforeBlock = cloneBlock(candidateLocation.block)
  candidateLocation.block.attrs = cloneJsonObject(operation.attrs)

  return succeeded(candidate, {
    type: 'block.attrs.updated',
    operationIndex,
    operationType: operation.type,
    message: `Updated attrs for block "${operation.blockId}".`,
    blockId: operation.blockId,
    parentBlockId: candidateLocation.parentBlockId,
    beforeBlock,
    afterBlock: cloneBlock(candidateLocation.block)
  })
}

function replaceBlockInlines(
  document: BlockDocument,
  operation: Extract<DocumentOperation, { type: 'replaceBlockInlines' }>,
  operationIndex: number,
  options: OperationRunOptions
): OperationOutcome {
  const location = findBlockLocation(document, operation.blockId)
  if (!location) return blockNotFound(operation, operationIndex, operation.blockId)

  const preconditionDiagnostics = checkPreconditions(document, operation, operationIndex, options, location)
  if (preconditionDiagnostics.length) return failed(preconditionDiagnostics)

  const candidate = cloneBlockDocument(document)
  const candidateLocation = findBlockLocation(candidate, operation.blockId)
  if (!candidateLocation) return blockNotFound(operation, operationIndex, operation.blockId)

  const beforeBlock = cloneBlock(candidateLocation.block)
  candidateLocation.block.inlines = cloneJson(operation.inlines)

  return succeeded(candidate, {
    type: 'block.inlines.replaced',
    operationIndex,
    operationType: operation.type,
    message: `Replaced inlines for block "${operation.blockId}".`,
    blockId: operation.blockId,
    parentBlockId: candidateLocation.parentBlockId,
    beforeBlock,
    afterBlock: cloneBlock(candidateLocation.block)
  })
}

function replaceBlockChildren(
  document: BlockDocument,
  operation: Extract<DocumentOperation, { type: 'replaceBlockChildren' }>,
  operationIndex: number,
  options: OperationRunOptions
): OperationOutcome {
  const location = findBlockLocation(document, operation.blockId)
  if (!location) return blockNotFound(operation, operationIndex, operation.blockId)

  const preconditionDiagnostics = checkPreconditions(document, operation, operationIndex, options, location)
  if (preconditionDiagnostics.length) return failed(preconditionDiagnostics)

  const duplicateId = firstDuplicateReplacementChildId(document, location.block, operation.children)
  if (duplicateId) {
    return failed([
      diagnostic(
        operation,
        operationIndex,
        'duplicate_block_id',
        `Block "${duplicateId}" already exists.`,
        duplicateId
      )
    ])
  }

  const candidate = cloneBlockDocument(document)
  const candidateLocation = findBlockLocation(candidate, operation.blockId)
  if (!candidateLocation) return blockNotFound(operation, operationIndex, operation.blockId)

  const beforeBlock = cloneBlock(candidateLocation.block)
  candidateLocation.block.children = cloneJson(operation.children)

  return succeeded(candidate, {
    type: 'block.children.replaced',
    operationIndex,
    operationType: operation.type,
    message: `Replaced children for block "${operation.blockId}".`,
    blockId: operation.blockId,
    parentBlockId: candidateLocation.parentBlockId,
    beforeBlock,
    afterBlock: cloneBlock(candidateLocation.block)
  })
}

function replaceText(
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

function insertText(
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

function deleteTextRange(
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

function resolveInsertPosition(
  document: BlockDocument,
  position: DocumentBlockPosition,
  operation: DocumentOperation,
  operationIndex: number
): { value?: ResolvedInsertPosition; diagnostics: DocumentOperationDiagnostic[] } {
  if (position.beforeBlockId && position.afterBlockId) {
    return {
      diagnostics: [
        diagnostic(operation, operationIndex, 'invalid_position', 'Only one of beforeBlockId or afterBlockId can be set.')
      ]
    }
  }

  if (position.beforeBlockId || position.afterBlockId) {
    const anchorId = position.beforeBlockId ?? position.afterBlockId ?? ''
    const anchor = findBlockLocation(document, anchorId)
    if (!anchor) {
      return {
        diagnostics: [
          diagnostic(operation, operationIndex, 'block_not_found', `Anchor block "${anchorId}" was not found.`, anchorId)
        ]
      }
    }

    if (position.parentBlockId !== undefined && position.parentBlockId !== anchor.parentBlockId) {
      return {
        diagnostics: [
          diagnostic(
            operation,
            operationIndex,
            'invalid_position',
            `Anchor block "${anchorId}" is not inside the requested parent.`
          )
        ]
      }
    }

    return {
      value: {
        parentBlocks: anchor.parentBlocks,
        parentBlockId: anchor.parentBlockId,
        index: anchor.index + (position.afterBlockId ? 1 : 0)
      },
      diagnostics: []
    }
  }

  const parent = resolveParentBlocks(document, position.parentBlockId)
  if (parent.diagnostics.length || !parent.value) {
    return { diagnostics: parent.diagnostics }
  }

  const index = position.index ?? parent.value.parentBlocks.length
  if (!Number.isInteger(index) || index < 0 || index > parent.value.parentBlocks.length) {
    return {
      diagnostics: [
        diagnostic(operation, operationIndex, 'invalid_position', `Invalid insertion index: ${String(position.index)}.`)
      ]
    }
  }

  return {
    value: {
      ...parent.value,
      index
    },
    diagnostics: []
  }
}

function resolveParentBlocks(
  document: BlockDocument,
  parentBlockId: string | null | undefined
): { value?: Pick<ResolvedInsertPosition, 'parentBlocks' | 'parentBlockId'>; diagnostics: DocumentOperationDiagnostic[] } {
  if (parentBlockId === undefined || parentBlockId === null) {
    return {
      value: {
        parentBlocks: document.blocks,
        parentBlockId: null
      },
      diagnostics: []
    }
  }

  const parent = findBlockLocation(document, parentBlockId)
  if (!parent) {
    return {
      diagnostics: [
        {
          severity: 'error',
          code: 'block_not_found',
          message: `Parent block "${parentBlockId}" was not found.`,
          blockId: parentBlockId
        }
      ]
    }
  }

  return {
    value: {
      parentBlocks: parent.block.children,
      parentBlockId: parent.block.id
    },
    diagnostics: []
  }
}

function resolveReplaceTextRange(
  block: BlockNode,
  operation: ReplaceTextOperation,
  operationIndex: number
): { value?: ResolvedTextRange; diagnostics: DocumentOperationDiagnostic[] } {
  if (operation.startOffset !== undefined || operation.endOffset !== undefined) {
    if (operation.startOffset === undefined || operation.endOffset === undefined) {
      return {
        diagnostics: [
          diagnostic(operation, operationIndex, 'invalid_text_range', 'Both startOffset and endOffset are required.')
        ]
      }
    }

    return resolveTextRange(block, operation.startOffset, operation.endOffset, operation, operationIndex)
  }

  if (operation.text === undefined) {
    return {
      diagnostics: [
        diagnostic(operation, operationIndex, 'invalid_text_range', 'replaceText requires either a text match or offsets.')
      ]
    }
  }

  const blockText = blockPlainText(block)
  const matches = findTextMatches(blockText, operation.text, operation.caseSensitive)
  if (!matches.length) {
    return {
      diagnostics: [
        diagnostic(operation, operationIndex, 'text_not_found', `Text "${operation.text}" was not found.`, operation.blockId)
      ]
    }
  }

  if (operation.occurrence === undefined && matches.length > 1) {
    return {
      diagnostics: [
        diagnostic(
          operation,
          operationIndex,
          'ambiguous_text_match',
          `Text "${operation.text}" matched ${matches.length} times; specify occurrence.`,
          operation.blockId
        )
      ]
    }
  }

  const occurrence = operation.occurrence ?? 1
  const match = matches[occurrence - 1]
  if (!Number.isInteger(occurrence) || occurrence < 1 || !match) {
    return {
      diagnostics: [
        diagnostic(
          operation,
          operationIndex,
          'text_not_found',
          `Text occurrence ${String(operation.occurrence)} was not found.`,
          operation.blockId
        )
      ]
    }
  }

  return {
    value: {
      startOffset: match.startOffset,
      endOffset: match.endOffset,
      beforeText: match.text
    },
    diagnostics: []
  }
}

function resolveTextRange(
  block: BlockNode,
  startOffset: number,
  endOffset: number,
  operation: DocumentOperation,
  operationIndex: number
): { value?: ResolvedTextRange; diagnostics: DocumentOperationDiagnostic[] } {
  const blockText = blockPlainText(block)

  if (
    !Number.isInteger(startOffset)
    || !Number.isInteger(endOffset)
    || startOffset < 0
    || endOffset < startOffset
    || endOffset > blockText.length
  ) {
    return {
      diagnostics: [
        diagnostic(
          operation,
          operationIndex,
          'invalid_text_range',
          `Invalid text range ${String(startOffset)}..${String(endOffset)} for block "${block.id}".`,
          block.id
        )
      ]
    }
  }

  return {
    value: {
      startOffset,
      endOffset,
      beforeText: blockText.slice(startOffset, endOffset)
    },
    diagnostics: []
  }
}

function checkPreconditions(
  document: BlockDocument,
  operation: DocumentOperation,
  operationIndex: number,
  options: OperationRunOptions,
  location?: BlockLocation,
  selectedText?: string
): DocumentOperationDiagnostic[] {
  const preconditions = operation.preconditions ?? {}

  const diagnostics: DocumentOperationDiagnostic[] = []

  if (preconditions.documentVersion !== undefined && preconditions.documentVersion !== options.documentVersion) {
    diagnostics.push(
      diagnostic(
        operation,
        operationIndex,
        'precondition_failed',
        `Document version precondition failed: expected "${preconditions.documentVersion}".`
      )
    )
  }

  if (location && preconditions.parentBlockId !== undefined && preconditions.parentBlockId !== location.parentBlockId) {
    diagnostics.push(
      diagnostic(
        operation,
        operationIndex,
        'precondition_failed',
        `Parent precondition failed for block "${location.block.id}".`,
        location.block.id
      )
    )
  }

  if (location && preconditions.blockHash !== undefined && preconditions.blockHash !== stableBlockHash(location.block)) {
    diagnostics.push(
      diagnostic(
        operation,
        operationIndex,
        'precondition_failed',
        `Block hash precondition failed for block "${location.block.id}".`,
        location.block.id
      )
    )
  }

  const expectedText = textExpectedByOperation(operation) ?? preconditions.expectedText
  if (expectedText !== undefined) {
    const actualText = selectedText ?? (location ? blockPlainText(location.block) : undefined)
    if (actualText !== expectedText) {
      diagnostics.push(
        diagnostic(
          operation,
          operationIndex,
          'precondition_failed',
          `Text precondition failed: expected "${expectedText}".`,
          location?.block.id
        )
      )
    }
  }

  const insertParentId = operation.type === 'insertBlock' ? operation.position?.parentBlockId : undefined
  if (insertParentId && preconditions.blockHash !== undefined) {
    const parent = findBlockLocation(document, insertParentId)
    if (parent && preconditions.blockHash !== stableBlockHash(parent.block)) {
      diagnostics.push(
        diagnostic(
          operation,
          operationIndex,
          'precondition_failed',
          `Parent block hash precondition failed for block "${insertParentId}".`,
          insertParentId
        )
      )
    }
  }

  return diagnostics
}

function textExpectedByOperation(operation: DocumentOperation): string | undefined {
  if (operation.type === 'replaceText' || operation.type === 'deleteTextRange') return operation.expectedText
  return undefined
}

function replaceInlineTextRange(
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

function cloneJson<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function cloneJsonObject(value: Record<string, unknown>): Record<string, unknown> {
  return cloneJson(value)
}

function sameJson(left: unknown, right: unknown): boolean {
  return JSON.stringify(left) === JSON.stringify(right)
}

function firstDuplicateReplacementChildId(
  document: BlockDocument,
  replacedBlock: BlockNode,
  nextChildren: BlockNode[]
): string | null {
  const idsRemovedByReplacement = new Set(blockIdsInTree(replacedBlock.children))
  const existingIds = new Set<string>()
  collectBlockIds(document.blocks).forEach((blockId) => {
    if (blockId !== replacedBlock.id && !idsRemovedByReplacement.has(blockId)) {
      existingIds.add(blockId)
    }
  })

  const nextIds = new Set<string>()
  for (const blockId of blockIdsInTree(nextChildren)) {
    if (existingIds.has(blockId) || nextIds.has(blockId)) return blockId
    nextIds.add(blockId)
  }

  return null
}

function collectBlockIds(blocks: BlockNode[]): string[] {
  return blockIdsInTree(blocks)
}

function blockIdsInTree(blocks: BlockNode[]): string[] {
  return blocks.flatMap((block) => [block.id, ...blockIdsInTree(block.children)])
}

function blockNotFound(operation: DocumentOperation, operationIndex: number, blockId: string): OperationOutcome {
  return failed([
    diagnostic(operation, operationIndex, 'block_not_found', `Block "${blockId}" was not found.`, blockId)
  ])
}

function succeeded(document: BlockDocument, patch: DocumentOperationPatch): OperationOutcome {
  return {
    document,
    patches: [patch],
    diagnostics: []
  }
}

function failed(diagnostics: DocumentOperationDiagnostic[]): OperationOutcome {
  return {
    patches: [],
    diagnostics
  }
}

function diagnostic(
  operation: DocumentOperation,
  operationIndex: number,
  code: DocumentOperationDiagnostic['code'],
  message: string,
  blockId?: string
): DocumentOperationDiagnostic {
  return {
    severity: 'error',
    code,
    message,
    operationIndex,
    operationType: operation.type,
    blockId
  }
}
