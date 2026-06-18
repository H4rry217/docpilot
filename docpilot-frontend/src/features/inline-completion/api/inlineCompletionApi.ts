import { postJson } from '@/shared/api/http'
import { recordInlineCompletionDebug } from '../model/inlineCompletionDebug'

export type InlineCompletionShape =
  | 'SHORT'
  | 'SENTENCE'
  | 'PARAGRAPH'
  | 'LIST_ITEM'
  | 'TABLE_CELL'
  | 'CODE_LINE'

export type InlineCompletionTrigger = 'IDLE' | 'MANUAL'

export type InlineCompletionBlockContext = {
  id?: string
  type: string
  text: string
  textBeforeCursor: string
  textAfterCursor: string
}

export type InlineCompletionRequest = {
  workspaceId: string
  documentId: string
  cursor: {
    from: number
    to: number
  }
  currentBlock: InlineCompletionBlockContext
  headingPath: string[]
  nearbyBlocks: InlineCompletionBlockContext[]
  trigger: InlineCompletionTrigger
  clientVersion: string
  candidateCount: number
}

export type InlineCompletionDiagnostic = {
  path: string
  code: string
  message: string
}

export type InlineCompletionCandidate = {
  index: number
  markdown: string
  previewText?: string
}

export type InlineCompletionCompleteResponse = {
  completionId: string
  modelId: string
  shape: InlineCompletionShape
  candidates: InlineCompletionCandidate[]
  diagnostics: InlineCompletionDiagnostic[]
}

const INLINE_COMPLETION_DEBUG_PREVIEW_CHARS = 400

export async function completeInlineCompletion(
  input: InlineCompletionRequest,
  signal: AbortSignal
): Promise<InlineCompletionCompleteResponse> {
  debugInlineCompletionApi('request started', {
    workspaceId: input.workspaceId,
    documentId: input.documentId,
    candidateCount: input.candidateCount,
    currentBlockType: input.currentBlock.type,
    beforeCursorChars: input.currentBlock.textBeforeCursor.length
  })
  let data: InlineCompletionCompleteResponse
  try {
    data = await postJson<InlineCompletionCompleteResponse, InlineCompletionRequest>(
      '/inline-completion/complete',
      input,
      { signal }
    )
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      debugInlineCompletionApi('request failed', {
        message: error instanceof Error ? error.message : String(error)
      })
    }
    throw error
  }
  if (!isCompleteResponse(data)) {
    throw new Error('Inline completion response is empty')
  }
  debugInlineCompletionApi('response received', {
    completionId: data.completionId,
    modelId: data.modelId,
    shape: data.shape,
    candidates: data.candidates.map((candidate) => ({
      index: candidate.index,
      previewText: previewText(candidate.previewText ?? candidate.markdown)
    })),
    diagnostics: data.diagnostics
  })
  return data
}

function isCompleteResponse(value: unknown): value is InlineCompletionCompleteResponse {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return typeof candidate.completionId === 'string'
    && typeof candidate.modelId === 'string'
    && typeof candidate.shape === 'string'
    && Array.isArray(candidate.candidates)
    && Array.isArray(candidate.diagnostics)
}

function debugInlineCompletionApi(message: string, detail?: unknown): void {
  recordInlineCompletionDebug('api', message, detail)
}

function previewText(value: unknown): string {
  const text = typeof value === 'string' ? value : ''
  if (text.length <= INLINE_COMPLETION_DEBUG_PREVIEW_CHARS) return text
  return `${text.slice(0, INLINE_COMPLETION_DEBUG_PREVIEW_CHARS)}...`
}
