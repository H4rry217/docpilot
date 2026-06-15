import { afterEach, describe, expect, it, vi } from 'vitest'
import { completeInlineCompletion } from './inlineCompletionApi'

describe('inline completion API', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('posts a non-streaming candidate request through the api prefix', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit): Promise<Response> => new Response(JSON.stringify({
      code: 0,
      msg: 'OK',
      data: {
        completionId: 'c1',
        modelId: 'inline-test',
        shape: 'SENTENCE',
        candidates: [
          { index: 0, markdown: ' first', previewText: ' first' },
          { index: 1, markdown: ' second', previewText: ' second' }
        ],
        diagnostics: []
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await completeInlineCompletion({
      workspaceId: '11',
      documentId: '22',
      cursor: { from: 5, to: 5 },
      currentBlock: {
        id: 'b1',
        type: 'PARAGRAPH',
        text: 'Hello',
        textBeforeCursor: 'Hello',
        textAfterCursor: ''
      },
      headingPath: [],
      nearbyBlocks: [],
      trigger: 'IDLE',
      clientVersion: 'test',
      candidateCount: 3
    }, new AbortController().signal)

    expect(fetchMock).toHaveBeenCalledWith('/api/inline-completion/complete', expect.objectContaining({
      method: 'POST'
    }))
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(JSON.parse(String(init.body))).toEqual(expect.objectContaining({
      workspaceId: '11',
      documentId: '22',
      candidateCount: 3
    }))
    expect(response.candidates).toHaveLength(2)
  })

  it('accepts candidate responses without preview text', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit): Promise<Response> => new Response(JSON.stringify({
      code: 0,
      msg: 'OK',
      data: {
        completionId: 'c1',
        modelId: 'inline-test',
        shape: 'SHORT',
        candidates: [
          { index: 0, markdown: '## 简介\n\n银杏索引是一个高效的文档检索工具。' }
        ],
        diagnostics: []
      }
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    }))
    vi.stubGlobal('fetch', fetchMock)

    const response = await completeInlineCompletion({
      workspaceId: '11',
      documentId: '22',
      cursor: { from: 5, to: 5 },
      currentBlock: {
        id: 'b1',
        type: 'HEADING',
        text: '银杏索引',
        textBeforeCursor: '银杏索引',
        textAfterCursor: ''
      },
      headingPath: [],
      nearbyBlocks: [],
      trigger: 'IDLE',
      clientVersion: 'test',
      candidateCount: 3
    }, new AbortController().signal)

    expect(response.candidates[0]?.markdown).toContain('简介')
  })
})
