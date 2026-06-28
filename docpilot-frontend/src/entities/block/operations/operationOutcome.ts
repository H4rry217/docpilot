import type { BlockDocument } from '../types'
import type {
  DocumentOperation,
  DocumentOperationDiagnostic,
  DocumentOperationOptions,
  DocumentOperationPatch
} from './types'

export type OperationRunOptions = DocumentOperationOptions & {
  dryRun: boolean
}

export type OperationOutcome = {
  document?: BlockDocument
  patches: DocumentOperationPatch[]
  diagnostics: DocumentOperationDiagnostic[]
}

export function blockNotFound(operation: DocumentOperation, operationIndex: number, blockId: string): OperationOutcome {
  return failed([
    diagnostic(operation, operationIndex, 'block_not_found', `Block "${blockId}" was not found.`, blockId)
  ])
}

export function succeeded(document: BlockDocument, patch: DocumentOperationPatch): OperationOutcome {
  return {
    document,
    patches: [patch],
    diagnostics: []
  }
}

export function failed(diagnostics: DocumentOperationDiagnostic[]): OperationOutcome {
  return {
    patches: [],
    diagnostics
  }
}

export function diagnostic(
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
