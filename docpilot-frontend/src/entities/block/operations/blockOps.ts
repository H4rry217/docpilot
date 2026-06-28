import type { BlockDocument, BlockNode } from '../types'
import {
  cloneBlock,
  cloneBlockDocument,
  findBlockLocation,
  isDescendantBlockId
} from './documentTree'
import {
  blockNotFound,
  diagnostic,
  failed,
  succeeded,
  type OperationOutcome,
  type OperationRunOptions
} from './operationOutcome'
import { checkPreconditions } from './preconditions'
import { resolveInsertPosition } from './resolvers'
import type { DocumentOperation } from './types'

export function insertBlock(
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

export function replaceBlock(
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

export function deleteBlock(
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

export function moveBlock(
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

export function updateBlockAttrs(
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

export function replaceBlockInlines(
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

export function replaceBlockChildren(
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

function cloneJson<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function cloneJsonObject(value: Record<string, unknown>): Record<string, unknown> {
  return cloneJson(value)
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
