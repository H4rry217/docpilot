import {
  queryBlockDocument,
  runDocumentOperationsDebugInput,
  type DocumentOperation,
  type DocumentOperationDiagnostic,
  type DocumentOperationPatch,
  type DocumentOperationsDebugMode,
  type DocumentQuery,
  type DocumentQueryResult
} from '@/entities/block/operations'
import type { BlockDocument, BlockNode } from '@/entities/block/types'
import type { TranslationKey, useI18n } from '@/shared/i18n'

export type CommandEntry = {
  id: number
  command: string
  feedback: string
  results?: CommandResultItem[]
  status: CommandFeedbackStatus
}

export type CommandFeedbackStatus = 'success' | 'error' | 'info'

export type CommandResultItem = {
  id: string
  label: string
  blockId?: string
  detail?: string
  meta?: string
  text: string
}

export type CommandCategory = 'query' | 'block' | 'text'

export type CommandSuggestion = {
  command: string
  category: CommandCategory
  descriptionKey: TranslationKey
  usage: string
}

export type CommandEvaluationContext = {
  t: ReturnType<typeof useI18n>['t']
  getBlockDocument?: () => BlockDocument | null | undefined
  getDocumentVersion?: () => string | null | undefined
  onApplyBlockDocument?: (blockDocument: BlockDocument) => void
}

export type CommandEvaluation = {
  status: CommandFeedbackStatus
  message: string
  results?: CommandResultItem[]
}

type PlainCommandParseResult =
  | { status: 'none' }
  | { status: 'incomplete'; usage: string }
  | { status: 'query'; query: DocumentQuery }
  | { status: 'operation'; operation: DocumentOperation }

type PlainCommandParseContext = {
  document?: BlockDocument
}

type CommandDefinition = CommandSuggestion & {
  parse: (
    command: string,
    head: string,
    context: PlainCommandParseContext
  ) => PlainCommandParseResult
}

export const COMMAND_CATEGORY_KEYS: Record<CommandCategory, TranslationKey> = {
  query: 'developer.commandCategory.query',
  block: 'developer.commandCategory.block',
  text: 'developer.commandCategory.text'
}

const COMMAND_CONTEXT_RELATION_KEYS = {
  before: 'developer.commandContext.before',
  target: 'developer.commandContext.target',
  after: 'developer.commandContext.after'
} as const satisfies Record<'before' | 'target' | 'after', TranslationKey>

const QUERY_TYPES = new Set(['getBlock', 'searchText', 'getBlockContext'])
const OPERATION_TYPES = new Set([
  'insertBlock',
  'replaceBlock',
  'deleteBlock',
  'moveBlock',
  'updateBlockAttrs',
  'replaceBlockInlines',
  'replaceBlockChildren',
  'replaceText',
  'insertText',
  'deleteTextRange'
])
const RESULT_ITEM_LIMIT = 6
const RESULT_TEXT_LIMIT = 120

const COMMAND_REGISTRY: CommandDefinition[] = [
  {
    command: 'searchText',
    category: 'query',
    descriptionKey: 'developer.command.searchText.description',
    usage: 'searchText <text>',
    parse: (command, head) => {
      const text = command.slice(head.length).trim()
      if (!text) return incomplete('searchText')
      return {
        status: 'query',
        query: {
          type: 'searchText',
          text: stripWrappingQuotes(text),
          limit: 10
        }
      }
    }
  },
  {
    command: 'getBlock',
    category: 'query',
    descriptionKey: 'developer.command.getBlock.description',
    usage: 'getBlock <blockId>',
    parse: (command) => {
      const tokens = tokenizeCommand(command)
      if (tokens.length < 2) return incomplete('getBlock')
      return {
        status: 'query',
        query: {
          type: 'getBlock',
          blockId: tokens[1]
        }
      }
    }
  },
  {
    command: 'getBlockContext',
    category: 'query',
    descriptionKey: 'developer.command.getBlockContext.description',
    usage: 'getBlockContext <blockId>',
    parse: (command) => {
      const tokens = tokenizeCommand(command)
      if (tokens.length < 2) return incomplete('getBlockContext')
      return {
        status: 'query',
        query: {
          type: 'getBlockContext',
          blockId: tokens[1],
          before: numberOrDefault(tokens[2], 1),
          after: numberOrDefault(tokens[3], 1)
        }
      }
    }
  },
  {
    command: 'replaceText',
    category: 'text',
    descriptionKey: 'developer.command.replaceText.description',
    usage: 'replaceText <blockId> <old> => <new>',
    parse: (command, head) => {
      const expression = command.slice(head.length).trim()
      const [left, right] = splitReplaceExpression(expression)
      const leftTokens = tokenizeCommand(left)
      if (leftTokens.length < 2 || right == null || !right.trim()) return incomplete('replaceText')
      return {
        status: 'operation',
        operation: {
          type: 'replaceText',
          blockId: leftTokens[0],
          text: leftTokens.slice(1).join(' '),
          replacement: stripWrappingQuotes(right.trim()),
          occurrence: 1
        }
      }
    }
  },
  {
    command: 'insertText',
    category: 'text',
    descriptionKey: 'developer.command.insertText.description',
    usage: 'insertText <blockId> <offset> <text>',
    parse: (command) => {
      const tokens = tokenizeCommand(command)
      if (tokens.length < 4 || !isFiniteNumberToken(tokens[2])) return incomplete('insertText')
      return {
        status: 'operation',
        operation: {
          type: 'insertText',
          blockId: tokens[1],
          offset: Number(tokens[2]),
          text: tokens.slice(3).join(' ')
        }
      }
    }
  },
  {
    command: 'deleteTextRange',
    category: 'text',
    descriptionKey: 'developer.command.deleteTextRange.description',
    usage: 'deleteTextRange <blockId> <start> <end>',
    parse: (command) => {
      const tokens = tokenizeCommand(command)
      if (tokens.length < 4 || !isFiniteNumberToken(tokens[2]) || !isFiniteNumberToken(tokens[3])) {
        return incomplete('deleteTextRange')
      }
      return {
        status: 'operation',
        operation: {
          type: 'deleteTextRange',
          blockId: tokens[1],
          startOffset: Number(tokens[2]),
          endOffset: Number(tokens[3])
        }
      }
    }
  },
  {
    command: 'insertBlock',
    category: 'block',
    descriptionKey: 'developer.command.insertBlock.description',
    usage: 'insertBlock after <blockId> <text>',
    parse: (command, _head, context) => {
      const tokens = tokenizeCommand(command)
      if (tokens.length < 4 || tokens[1] !== 'after') return incomplete('insertBlock')
      return {
        status: 'operation',
        operation: {
          type: 'insertBlock',
          block: paragraphBlock(uniqueBlockId(context.document, 'new-block'), tokens.slice(3).join(' ')),
          position: {
            afterBlockId: tokens[2]
          }
        }
      }
    }
  },
  {
    command: 'replaceBlock',
    category: 'block',
    descriptionKey: 'developer.command.replaceBlock.description',
    usage: 'replaceBlock <blockId> <text>',
    parse: (command) => {
      const tokens = tokenizeCommand(command)
      if (tokens.length < 3) return incomplete('replaceBlock')
      return {
        status: 'operation',
        operation: {
          type: 'replaceBlock',
          blockId: tokens[1],
          block: paragraphBlock(tokens[1], tokens.slice(2).join(' '))
        }
      }
    }
  },
  {
    command: 'deleteBlock',
    category: 'block',
    descriptionKey: 'developer.command.deleteBlock.description',
    usage: 'deleteBlock <blockId>',
    parse: (command) => {
      const tokens = tokenizeCommand(command)
      if (tokens.length < 2) return incomplete('deleteBlock')
      return {
        status: 'operation',
        operation: {
          type: 'deleteBlock',
          blockId: tokens[1]
        }
      }
    }
  },
  {
    command: 'moveBlock',
    category: 'block',
    descriptionKey: 'developer.command.moveBlock.description',
    usage: 'moveBlock <blockId> after <targetBlockId>',
    parse: (command) => {
      const tokens = tokenizeCommand(command)
      if (tokens.length < 4 || (tokens[2] !== 'after' && tokens[2] !== 'before')) return incomplete('moveBlock')
      return {
        status: 'operation',
        operation: {
          type: 'moveBlock',
          blockId: tokens[1],
          position: tokens[2] === 'after'
            ? { afterBlockId: tokens[3] }
            : { beforeBlockId: tokens[3] }
        }
      }
    }
  }
]

export const COMMAND_SUGGESTIONS: CommandSuggestion[] = COMMAND_REGISTRY.map((definition) => ({
  command: definition.command,
  category: definition.category,
  descriptionKey: definition.descriptionKey,
  usage: definition.usage
}))

export function evaluateCommand(command: string, context: CommandEvaluationContext): CommandEvaluation {
  const parsedJson = parseJsonCommand(command)

  if (parsedJson.status === 'invalid') {
    return {
      status: 'error',
      message: context.t('developer.commandFeedbackInvalidJson', { message: parsedJson.message })
    }
  }

  if (parsedJson.status === 'valid') {
    const document = context.getBlockDocument?.()
    if (!document) return noDocumentEvaluation(context)

    if (isQueryLike(parsedJson.value)) return evaluateQuery(parsedJson.value, document, context)
    if (isOperationInputLike(parsedJson.value)) return evaluateOperationInput(parsedJson.value, document, context)

    return {
      status: 'error',
      message: context.t('developer.commandFeedbackUnsupportedJson')
    }
  }

  const preliminaryPlainCommand = parsePlainCommand(command)
  if (preliminaryPlainCommand.status === 'incomplete') {
    return {
      status: 'info',
      message: context.t('developer.commandFeedbackMissingArgs', { usage: preliminaryPlainCommand.usage })
    }
  }

  if (preliminaryPlainCommand.status === 'none') {
    return {
      status: 'error',
      message: context.t('developer.commandFeedbackUnknown', { command: commandHead(command) })
    }
  }

  const document = context.getBlockDocument?.()
  if (!document) return noDocumentEvaluation(context)

  const parsedPlainCommand = parsePlainCommand(command, { document })
  if (parsedPlainCommand.status === 'query') return evaluateQuery(parsedPlainCommand.query, document, context)
  if (parsedPlainCommand.status === 'operation') return evaluateOperationInput(parsedPlainCommand.operation, document, context)

  return {
    status: 'error',
    message: context.t('developer.commandFeedbackUnknown', { command: commandHead(command) })
  }
}

export function shouldOpenCommandSuggestions(value: string, caretIndex: number): boolean {
  return !isJsonLikeCommand(value) && isCommandPrefixPosition(value, caretIndex)
}

export function executableCommandFromInput(value: string): string {
  const leadingWhitespaceLength = value.length - value.trimStart().length
  return value[leadingWhitespaceLength] === '/'
    ? value.slice(leadingWhitespaceLength + 1).trim()
    : value.trim()
}

export function commandPrefixAtCaret(value: string, caretIndex: number): string {
  if (!isCommandPrefixPosition(value, caretIndex)) return ''

  const leadingWhitespaceLength = value.length - value.trimStart().length
  const commandStart = leadingWhitespaceLength + 1
  const commandEnd = commandTokenEnd(value)
  const safeCaretIndex = Math.min(Math.max(caretIndex, commandStart), commandEnd)
  return value.slice(commandStart, safeCaretIndex).trim()
}

export function nextPlaceholderRange(value: string, fromIndex: number): { start: number; end: number } | null {
  const ranges = placeholderRanges(value)
  if (!ranges.length) return null

  return ranges.find((range) => range.start >= fromIndex) ?? ranges[0]
}

export function previousPlaceholderRange(value: string, fromIndex: number): { start: number; end: number } | null {
  const ranges = placeholderRanges(value)
  if (!ranges.length) return null

  const reversedRanges = [...ranges].reverse()
  return reversedRanges.find((range) => range.end <= fromIndex) ?? reversedRanges[0]
}

export function isJsonLikeCommand(command: string): boolean {
  const trimmed = command.trim()
  return trimmed.startsWith('{') || trimmed.startsWith('[')
}

export function formatResultAnchor(result: CommandResultItem): string {
  const anchor = result.blockId ?? result.label
  return result.detail ? `${anchor} · ${result.detail}` : anchor
}

function incomplete(command: string): PlainCommandParseResult {
  const definition = commandDefinitionByName(command)
  return { status: 'incomplete', usage: definition?.usage ?? command }
}

function noDocumentEvaluation(context: CommandEvaluationContext): CommandEvaluation {
  return {
    status: 'info',
    message: context.t('developer.commandFeedbackNoDocument')
  }
}

function parsePlainCommand(
  command: string,
  context: PlainCommandParseContext = {}
): PlainCommandParseResult {
  const head = commandHead(command)
  const definition = commandDefinitionByName(head)
  return definition ? definition.parse(command, head, context) : { status: 'none' }
}

function commandDefinitionByName(command: string): CommandDefinition | null {
  const normalizedCommand = command.toLowerCase()
  return COMMAND_REGISTRY.find((definition) => definition.command.toLowerCase() === normalizedCommand) ?? null
}

function evaluateOperationInput(
  input: unknown,
  document: BlockDocument,
  context: CommandEvaluationContext
): CommandEvaluation {
  const debugResult = runDocumentOperationsDebugInput(document, input, {
    mode: operationModeForInput(input, context),
    documentVersion: context.getDocumentVersion?.() ?? undefined
  })
  const counts = diagnosticCounts(debugResult.result.diagnostics)
  const isApplyMode = debugResult.mode === 'apply'

  if (debugResult.result.ok) {
    if (isApplyMode) context.onApplyBlockDocument?.(debugResult.result.document)

    return {
      status: 'success',
      results: operationResultItems(debugResult.result.patches, debugResult.result.diagnostics, context),
      message: context.t(isApplyMode ? 'developer.commandFeedbackApplyOk' : 'developer.commandFeedbackPreviewOk', {
        patches: String(debugResult.result.patches.length),
        diagnostics: String(debugResult.result.diagnostics.length)
      })
    }
  }

  return {
    status: 'error',
    results: operationResultItems(debugResult.result.patches, debugResult.result.diagnostics, context),
    message: context.t(isApplyMode ? 'developer.commandFeedbackApplyFailed' : 'developer.commandFeedbackPreviewFailed', {
      errors: String(counts.errors),
      warnings: String(counts.warnings)
    })
  }
}

function operationModeForInput(
  input: unknown,
  context: CommandEvaluationContext
): DocumentOperationsDebugMode | undefined {
  if (!context.onApplyBlockDocument) return 'preview'
  return hasExplicitOperationMode(input) ? undefined : 'apply'
}

function hasExplicitOperationMode(value: unknown): boolean {
  return isRecord(value) && (value.mode === 'preview' || value.mode === 'apply')
}

function evaluateQuery(
  query: DocumentQuery,
  document: BlockDocument,
  context: CommandEvaluationContext
): CommandEvaluation {
  const result = queryBlockDocument(document, query)
  const counts = diagnosticCounts(result.diagnostics)

  if (counts.errors > 0) {
    return {
      status: 'error',
      results: diagnosticResultItems(result.diagnostics, context),
      message: context.t('developer.commandFeedbackQueryFailed', {
        errors: String(counts.errors),
        warnings: String(counts.warnings)
      })
    }
  }

  if (result.matches) {
    return {
      status: 'success',
      results: queryResultItems(result, document, context),
      message: context.t('developer.commandFeedbackQueryMatches', { matches: String(result.matches.length) })
    }
  }

  if (result.block) {
    return {
      status: 'success',
      results: queryResultItems(result, document, context),
      message: context.t('developer.commandFeedbackQueryBlock', { blockId: result.block.id })
    }
  }

  return {
    status: 'success',
    results: queryResultItems(result, document, context),
    message: context.t('developer.commandFeedbackQueryContext', {
      count: String(result.context?.length ?? 0)
    })
  }
}

function queryResultItems(
  result: DocumentQueryResult,
  document: BlockDocument,
  context: CommandEvaluationContext
): CommandResultItem[] {
  if (result.matches) {
    return limitResultItems(
      result.matches.map((match, index) => {
        const block = findBlockById(document.blocks, match.blockId)
        const text = block ? blockText(block) : match.text
        return {
          id: `match-${index}-${match.blockId}-${match.range.startOffset}`,
          label: match.blockId,
          blockId: match.blockId,
          detail: `${match.range.startOffset}-${match.range.endOffset}`,
          meta: match.blockType,
          text: snippetForRange(text, match.range.startOffset, match.range.endOffset)
        }
      }),
      context
    )
  }

  if (result.context) {
    return limitResultItems(
      result.context.map((item, index) => ({
        id: `context-${index}-${item.blockId}`,
        label: item.blockId,
        blockId: item.blockId,
        detail: context.t(COMMAND_CONTEXT_RELATION_KEYS[item.relation]),
        meta: item.blockType,
        text: truncateResultText(item.text)
      })),
      context
    )
  }

  if (result.block) {
    return [
      {
        id: `block-${result.block.id}`,
        label: result.block.id,
        blockId: result.block.id,
        detail: result.block.type,
        meta: result.blockHash,
        text: truncateResultText(blockText(result.block))
      }
    ]
  }

  return diagnosticResultItems(result.diagnostics, context)
}

function operationResultItems(
  patches: DocumentOperationPatch[],
  diagnostics: DocumentOperationDiagnostic[],
  context: CommandEvaluationContext
): CommandResultItem[] {
  const patchItems = patches.map((patch, index) => ({
    id: `patch-${index}-${patch.operationIndex}`,
    label: patch.blockId ?? context.t('developer.commandResultPatchLabel', { type: patch.type }),
    blockId: patch.blockId,
    detail: patch.type,
    text: patch.message
  }))

  return limitResultItems([...patchItems, ...diagnosticResultItems(diagnostics, context)], context)
}

function diagnosticResultItems(
  diagnostics: DocumentOperationDiagnostic[],
  context: CommandEvaluationContext
): CommandResultItem[] {
  return limitResultItems(
    diagnostics.map((diagnostic, index) => ({
      id: `diagnostic-${index}-${diagnostic.code}`,
      label: diagnostic.blockId ?? context.t('developer.commandResultDiagnosticLabel', {
        severity: diagnostic.severity,
        code: diagnostic.code
      }),
      blockId: diagnostic.blockId,
      detail: context.t('developer.commandResultDiagnosticLabel', {
        severity: diagnostic.severity,
        code: diagnostic.code
      }),
      text: diagnostic.message
    })),
    context
  )
}

function limitResultItems(items: CommandResultItem[], context: CommandEvaluationContext): CommandResultItem[] {
  if (items.length <= RESULT_ITEM_LIMIT) return items

  return [
    ...items.slice(0, RESULT_ITEM_LIMIT),
    {
      id: 'more-results',
      label: context.t('developer.commandResultMore', {
        count: String(items.length - RESULT_ITEM_LIMIT)
      }),
      text: ''
    }
  ]
}

function parseJsonCommand(command: string):
  | { status: 'plain' }
  | { status: 'valid'; value: unknown }
  | { status: 'invalid'; message: string } {
  const trimmed = command.trim()
  if (!isJsonLikeCommand(trimmed)) return { status: 'plain' }

  try {
    return { status: 'valid', value: JSON.parse(trimmed) as unknown }
  } catch (error) {
    return {
      status: 'invalid',
      message: error instanceof Error ? error.message : 'Invalid JSON'
    }
  }
}

function isQueryLike(value: unknown): value is DocumentQuery {
  return isRecord(value) && typeof value.type === 'string' && QUERY_TYPES.has(value.type)
}

function isOperationInputLike(value: unknown): boolean {
  if (Array.isArray(value)) return value.every(isOperationLike)
  if (isRecord(value) && Array.isArray(value.operations)) return value.operations.every(isOperationLike)
  return isOperationLike(value)
}

function isOperationLike(value: unknown): boolean {
  return isRecord(value) && typeof value.type === 'string' && OPERATION_TYPES.has(value.type)
}

function tokenizeCommand(command: string): string[] {
  const tokens: string[] = []
  let current = ''
  let quote: '"' | "'" | null = null

  for (const character of command.trim()) {
    if (quote) {
      if (character === quote) {
        quote = null
      } else {
        current += character
      }
      continue
    }

    if (character === '"' || character === "'") {
      quote = character
      continue
    }

    if (/\s/.test(character)) {
      if (current) {
        tokens.push(current)
        current = ''
      }
      continue
    }

    current += character
  }

  if (current) tokens.push(current)
  return tokens
}

function splitReplaceExpression(expression: string): [string, string | null] {
  const separatorIndex = expression.indexOf('=>')
  if (separatorIndex < 0) return [expression, null]
  return [
    expression.slice(0, separatorIndex).trim(),
    expression.slice(separatorIndex + 2).trim()
  ]
}

function paragraphBlock(id: string, text: string): BlockNode {
  return {
    id,
    type: 'PARAGRAPH',
    attrs: {},
    inlines: text
      ? [
          {
            type: 'TEXT',
            text,
            attrs: {},
            marks: []
          }
        ]
      : [],
    children: []
  }
}

function numberOrDefault(value: string | undefined, fallback: number): number {
  return value !== undefined && isFiniteNumberToken(value) ? Number(value) : fallback
}

function isFiniteNumberToken(value: string | undefined): boolean {
  return value !== undefined && value.trim() !== '' && Number.isFinite(Number(value))
}

function stripWrappingQuotes(value: string): string {
  const trimmed = value.trim()
  if (
    (trimmed.startsWith('"') && trimmed.endsWith('"'))
    || (trimmed.startsWith("'") && trimmed.endsWith("'"))
  ) {
    return trimmed.slice(1, -1)
  }
  return trimmed
}

function commandHead(command: string): string {
  return command.trim().match(/^[A-Za-z][\w-]*/)?.[0] ?? command.trim()
}

function isCommandPrefixPosition(value: string, caretIndex: number): boolean {
  if (!value.trim()) return false
  if (isJsonLikeCommand(value)) return false

  const leadingWhitespaceLength = value.length - value.trimStart().length
  if (value[leadingWhitespaceLength] !== '/') return false

  const safeCaretIndex = Math.max(0, Math.min(caretIndex, value.length))
  if (safeCaretIndex < leadingWhitespaceLength) return false

  return safeCaretIndex <= commandTokenEnd(value)
}

function commandTokenEnd(value: string): number {
  const leadingWhitespaceLength = value.length - value.trimStart().length
  const afterLeading = value.slice(leadingWhitespaceLength)
  const firstWhitespaceIndex = afterLeading.search(/\s/)
  return firstWhitespaceIndex < 0
    ? value.length
    : leadingWhitespaceLength + firstWhitespaceIndex
}

function placeholderRanges(value: string): Array<{ start: number; end: number }> {
  return Array.from(value.matchAll(/<[^<>]+>/g), (match) => ({
    start: match.index ?? 0,
    end: (match.index ?? 0) + match[0].length
  }))
}

function findBlockById(blocks: BlockNode[], blockId: string): BlockNode | null {
  for (const block of blocks) {
    if (block.id === blockId) return block

    const childMatch = findBlockById(block.children, blockId)
    if (childMatch) return childMatch
  }

  return null
}

function blockText(block: BlockNode): string {
  const inlineText = block.inlines.map((inline) => inline.text ?? '').join('')
  const childText = block.children.map(blockText).filter(Boolean).join(' ')
  return [inlineText, childText].filter(Boolean).join(' ')
}

function snippetForRange(text: string, startOffset: number, endOffset: number): string {
  if (!text) return ''

  const padding = 36
  const start = Math.max(0, startOffset - padding)
  const end = Math.min(text.length, endOffset + padding)
  const prefix = start > 0 ? '...' : ''
  const suffix = end < text.length ? '...' : ''

  return truncateResultText(`${prefix}${text.slice(start, end)}${suffix}`)
}

function truncateResultText(text: string): string {
  const normalized = text.replace(/\s+/g, ' ').trim()
  if (normalized.length <= RESULT_TEXT_LIMIT) return normalized
  return `${normalized.slice(0, RESULT_TEXT_LIMIT - 1)}...`
}

function uniqueBlockId(document: BlockDocument | undefined, baseId: string): string {
  if (!document || !findBlockById(document.blocks, baseId)) return baseId

  for (let index = 2; index < 1000; index += 1) {
    const candidate = `${baseId}-${index}`
    if (!findBlockById(document.blocks, candidate)) return candidate
  }

  return `${baseId}-${Date.now()}`
}

function diagnosticCounts(diagnostics: Array<{ severity: string }>): { errors: number; warnings: number } {
  return diagnostics.reduce(
    (counts, diagnostic) => {
      if (diagnostic.severity === 'error') counts.errors += 1
      if (diagnostic.severity === 'warning') counts.warnings += 1
      return counts
    },
    { errors: 0, warnings: 0 }
  )
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}
