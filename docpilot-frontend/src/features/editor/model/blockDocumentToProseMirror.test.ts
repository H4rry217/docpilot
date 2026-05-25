import { describe, expect, it } from 'vitest'
import type { BlockDocument } from '../../../entities/block/types'
import { blockDocumentToProseMirrorJson, isBlockDocument, isProseMirrorDoc } from './blockDocumentToProseMirror'

describe('blockDocumentToProseMirrorJson', () => {
  it('converts DocPilot block documents to renderable ProseMirror JSON', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/1',
      metadata: {},
      blocks: [
        {
          id: 'heading1',
          type: 'HEADING',
          attrs: { level: 1 },
          inlines: [{ type: 'TEXT', text: '标题', attrs: {}, marks: [] }],
          children: []
        },
        {
          id: 'html1',
          type: 'HTML_BLOCK',
          attrs: { title: 'HTML', source: '<section>hello</section>' },
          inlines: [],
          children: []
        }
      ]
    }

    expect(blockDocumentToProseMirrorJson(blockDocument)).toEqual({
      type: 'doc',
      attrs: { schemaVersion: 'docpilot-block/1' },
      content: [
        {
          type: 'heading',
          attrs: { level: 1, blockId: 'heading1' },
          content: [{ type: 'text', text: '标题' }]
        },
        {
          type: 'docpilotHtmlBlock',
          attrs: { title: 'HTML', source: '<section>hello</section>', blockId: 'html1' }
        }
      ]
    })
  })

  it('detects supported pasted json shapes', () => {
    expect(isBlockDocument({ schemaVersion: 'docpilot-block/1', blocks: [] })).toBe(true)
    expect(isProseMirrorDoc({ type: 'doc', content: [] })).toBe(true)
    expect(isBlockDocument({ type: 'doc', content: [] })).toBe(false)
  })
})
