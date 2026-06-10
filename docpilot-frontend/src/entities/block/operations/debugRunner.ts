import type { BlockDocument } from '../types'
import { cloneBlockDocument } from './documentTree'
import { applyDocumentOperations, previewDocumentOperations } from './runtime'
import type {
  DocumentOperationDiagnostic,
  DocumentOperation,
  DocumentOperationInput,
  DocumentOperationResult,
  DocumentOperationsDebugMode,
  DocumentOperationsDebugRunnerOptions,
  DocumentOperationsDebugResult
} from './types'

export function runDocumentOperationsDebugInput(
  document: BlockDocument,
  input: unknown,
  options: DocumentOperationsDebugRunnerOptions = {}
): DocumentOperationsDebugResult {
  const parsed = parseDebugInput(input)
  const mode = options.mode ?? modeFromInput(parsed.value) ?? 'preview'

  if (parsed.diagnostic) {
    const result = invalidResult(document, mode, parsed.diagnostic)
    return {
      mode,
      result,
      output: formatDocumentOperationResult(result)
    }
  }

  if (!isOperationInputLike(parsed.value)) {
    const result = invalidResult(document, mode, {
      severity: 'error',
      code: 'invalid_operation',
      message: 'Debug input must be a JSON operation, operation array, or { operations } batch.'
    })
    return {
      mode,
      result,
      output: formatDocumentOperationResult(result)
    }
  }

  const operationInput = operationInputFromDebugValue(parsed.value)
  const result = mode === 'apply'
    ? applyDocumentOperations(document, operationInput, options)
    : previewDocumentOperations(document, operationInput, options)

  return {
    mode,
    result,
    output: formatDocumentOperationResult(result)
  }
}

export function formatDocumentOperationResult(result: DocumentOperationResult): string {
  const errorCount = result.diagnostics.filter((diagnostic) => diagnostic.severity === 'error').length
  const warningCount = result.diagnostics.filter((diagnostic) => diagnostic.severity === 'warning').length
  const lines = [
    `${result.ok ? 'OK' : 'FAILED'} ${result.dryRun ? 'preview' : 'apply'}: ${result.patches.length} patch(es), ${errorCount} error(s), ${warningCount} warning(s).`
  ]

  result.patches.forEach((patch) => {
    lines.push(`patch ${patch.operationIndex}: ${patch.message}`)
  })

  result.diagnostics.forEach((diagnostic) => {
    const operation = diagnostic.operationIndex === undefined ? '' : ` ${diagnostic.operationIndex}`
    lines.push(`${diagnostic.severity}${operation} ${diagnostic.code}: ${diagnostic.message}`)
  })

  return lines.join('\n')
}

function parseDebugInput(input: unknown): { value: unknown; diagnostic?: DocumentOperationDiagnostic } {
  if (typeof input !== 'string') return { value: input }

  try {
    return { value: JSON.parse(input) as unknown }
  } catch (error) {
    return {
      value: null,
      diagnostic: {
        severity: 'error',
        code: 'invalid_operation',
        message: error instanceof Error ? error.message : 'Debug input is not valid JSON.'
      }
    }
  }
}

function modeFromInput(value: unknown): DocumentOperationsDebugMode | null {
  if (!isRecord(value)) return null
  return value.mode === 'apply' || value.mode === 'preview' ? value.mode : null
}

function operationInputFromDebugValue(value: unknown): DocumentOperationInput {
  if (isRecord(value) && Array.isArray(value.operations)) {
    return {
      operations: value.operations as DocumentOperation[]
    }
  }

  return value as DocumentOperationInput
}

function isOperationInputLike(value: unknown): boolean {
  if (Array.isArray(value)) return value.every(isOperationLike)
  if (isRecord(value) && Array.isArray(value.operations)) return value.operations.every(isOperationLike)
  return isOperationLike(value)
}

function isOperationLike(value: unknown): boolean {
  return isRecord(value) && typeof value.type === 'string'
}

function invalidResult(
  document: BlockDocument,
  mode: DocumentOperationsDebugMode,
  diagnostic: DocumentOperationDiagnostic
): DocumentOperationResult {
  return {
    document: cloneBlockDocument(document),
    patches: [],
    diagnostics: [diagnostic],
    changed: false,
    ok: false,
    dryRun: mode === 'preview'
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}
