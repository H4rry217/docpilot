import type { BlockDocument, BlockNode } from '../types'
import {
  blockPlainText,
  findBlockLocation,
  findTextMatches
} from './documentTree'
import { diagnostic } from './operationOutcome'
import type {
  DocumentBlockPosition,
  DocumentOperation,
  DocumentOperationDiagnostic,
  ReplaceTextOperation
} from './types'

export type ResolvedInsertPosition = {
  parentBlocks: BlockNode[]
  parentBlockId: string | null
  index: number
}

export type ResolvedTextRange = {
  startOffset: number
  endOffset: number
  beforeText: string
}

export function resolveInsertPosition(
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

export function resolveParentBlocks(
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

export function resolveReplaceTextRange(
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

export function resolveTextRange(
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
