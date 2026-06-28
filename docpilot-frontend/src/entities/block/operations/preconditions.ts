import {
  blockPlainText,
  findBlockLocation,
  stableBlockHash,
  type BlockLocation
} from './documentTree'
import { diagnostic, type OperationRunOptions } from './operationOutcome'
import type { BlockDocument } from '../types'
import type {
  DocumentOperation,
  DocumentOperationDiagnostic
} from './types'

export function checkPreconditions(
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
