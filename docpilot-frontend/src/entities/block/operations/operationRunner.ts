import type { BlockDocument } from '../types'
import {
  cloneBlockDocument
} from './documentTree'
import {
  deleteBlock,
  insertBlock,
  moveBlock,
  replaceBlock,
  replaceBlockChildren,
  replaceBlockInlines,
  updateBlockAttrs
} from './blockOps'
import {
  diagnostic,
  type OperationOutcome,
  type OperationRunOptions
} from './operationOutcome'
import {
  deleteTextRange,
  insertText,
  replaceText
} from './textOps'
import type {
  DocumentOperation,
  DocumentOperationBatch,
  DocumentOperationDiagnostic,
  DocumentOperationInput,
  DocumentOperationPatch,
  DocumentOperationResult
} from './types'

export function runDocumentOperations(
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
