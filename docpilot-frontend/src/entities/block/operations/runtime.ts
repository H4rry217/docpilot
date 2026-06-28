import type { BlockDocument } from '../types'
import { runDocumentOperations } from './operationRunner'
import type {
  DocumentOperationInput,
  DocumentOperationOptions,
  DocumentOperationResult
} from './types'

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
