import { describe, expect, it } from 'vitest'
import type { BlockDocument } from '../../../entities/block/types'
import { blockDocumentToProseMirrorJson, isBlockDocument, isProseMirrorDoc } from './blockDocumentToProseMirror'

describe('blockDocumentToProseMirrorJson', () => {
  it('converts DocPilot block documents to renderable ProseMirror JSON', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
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
        },
        {
          id: 'math1',
          type: 'MATH_BLOCK',
          attrs: { notation: 'latex', text: 'x^2', delimiter: '$$' },
          inlines: [],
          children: []
        }
      ]
    }

    expect(blockDocumentToProseMirrorJson(blockDocument)).toEqual({
      type: 'doc',
      attrs: { schemaVersion: 'docpilot-block/2' },
      content: [
        {
          type: 'heading',
          attrs: { level: 1, blockId: 'heading1' },
          content: [{ type: 'text', text: '标题' }]
        },
        {
          type: 'docpilotHtmlBlock',
          attrs: {
            id: 'html1',
            title: 'HTML',
            source: '<section>hello</section>',
            displayMode: 'fixed',
            fixedHeightPx: 320,
            allowScripts: false,
            blockId: 'html1'
          }
        },
        {
          type: 'docpilotMathBlock',
          attrs: { notation: 'latex', text: 'x^2', delimiter: '$$', blockId: 'math1' }
        }
      ]
    })
  })

  it('detects supported pasted json shapes', () => {
    expect(isBlockDocument({ schemaVersion: 'docpilot-block/2', blocks: [] })).toBe(true)
    expect(isProseMirrorDoc({ type: 'doc', content: [] })).toBe(true)
    expect(isBlockDocument({ type: 'doc', content: [] })).toBe(false)
  })

  it('normalizes known block attrs before building editor json', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'html1',
          type: 'HTML_BLOCK',
          attrs: { source: '<div />', displayMode: 'fit', fixedHeightPx: 2000, allowScripts: 'true' },
          inlines: [],
          children: []
        },
        {
          id: 'heading1',
          type: 'HEADING',
          attrs: { level: 'bad' },
          inlines: [],
          children: []
        }
      ]
    }

    expect(blockDocumentToProseMirrorJson(blockDocument).content).toMatchObject([
      {
        type: 'docpilotHtmlBlock',
        attrs: {
          id: 'html1',
          displayMode: 'fixed',
          fixedHeightPx: 320,
          allowScripts: true
        }
      },
      {
        type: 'heading',
        attrs: {
          level: 1
        }
      }
    ])
  })
})
