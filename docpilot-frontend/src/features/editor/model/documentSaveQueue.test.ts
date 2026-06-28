import type { BlockDocument } from '@/entities/block/types'
import { describe, expect, it, vi } from 'vitest'
import { createDocumentSaveQueue, type DocumentSaveSubmission } from './documentSaveQueue'

function documentWithText(text: string): BlockDocument {
  return {
    schemaVersion: 'docpilot-block/2',
    blocks: [
      {
        id: 'paragraph-1',
        type: 'PARAGRAPH',
        attrs: {},
        inlines: [
          {
            type: 'TEXT',
            text,
            attrs: {},
            marks: []
          }
        ],
        children: []
      }
    ],
    metadata: {}
  }
}

describe('createDocumentSaveQueue', () => {
  it('submits a save with the current document version', () => {
    const submit = vi.fn<(submission: DocumentSaveSubmission) => void>()
    const queue = createDocumentSaveQueue(submit)

    queue.setVersion('document-1', '7')
    expect(queue.requestSave({
      documentId: 'document-1',
      blockDocument: documentWithText('First save')
    })).toBe(true)

    expect(submit).toHaveBeenCalledWith({
      documentId: 'document-1',
      blockDocument: documentWithText('First save'),
      baseVersion: '7'
    })
  })

  it('queues the latest save while an earlier save is in flight', () => {
    const submit = vi.fn<(submission: DocumentSaveSubmission) => void>()
    const queue = createDocumentSaveQueue(submit)

    queue.setVersion('document-1', '1')
    queue.requestSave({
      documentId: 'document-1',
      blockDocument: documentWithText('Initial edit')
    })
    queue.requestSave({
      documentId: 'document-1',
      blockDocument: documentWithText('Intermediate edit')
    })
    queue.requestSave({
      documentId: 'document-1',
      blockDocument: documentWithText('Latest edit')
    })

    expect(submit).toHaveBeenCalledTimes(1)
    expect(submit).toHaveBeenLastCalledWith({
      documentId: 'document-1',
      blockDocument: documentWithText('Initial edit'),
      baseVersion: '1'
    })

    expect(queue.finishSave('document-1', '2')).toBe(true)

    expect(submit).toHaveBeenCalledTimes(2)
    expect(submit).toHaveBeenLastCalledWith({
      documentId: 'document-1',
      blockDocument: documentWithText('Latest edit'),
      baseVersion: '2'
    })
    expect(queue.hasPendingSave('document-1')).toBe(true)

    expect(queue.finishSave('document-1', '3')).toBe(false)
    expect(queue.hasPendingSave('document-1')).toBe(false)
  })

  it('keeps save queues isolated by document id', () => {
    const submit = vi.fn<(submission: DocumentSaveSubmission) => void>()
    const queue = createDocumentSaveQueue(submit)

    queue.setVersion('document-1', '1')
    queue.setVersion('document-2', '4')
    queue.requestSave({
      documentId: 'document-1',
      blockDocument: documentWithText('Document one')
    })
    queue.requestSave({
      documentId: 'document-2',
      blockDocument: documentWithText('Document two')
    })

    expect(submit).toHaveBeenCalledTimes(2)
    expect(submit).toHaveBeenNthCalledWith(1, {
      documentId: 'document-1',
      blockDocument: documentWithText('Document one'),
      baseVersion: '1'
    })
    expect(submit).toHaveBeenNthCalledWith(2, {
      documentId: 'document-2',
      blockDocument: documentWithText('Document two'),
      baseVersion: '4'
    })
  })
})
