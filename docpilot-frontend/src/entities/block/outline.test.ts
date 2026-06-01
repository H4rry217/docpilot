import { describe, expect, it } from 'vitest'
import type { BlockDocument } from './types'
import { documentOutlineFromBlockDocument } from './outline'

describe('documentOutlineFromBlockDocument', () => {
  it('extracts only markdown headings in document order', () => {
    const document: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'title',
          type: 'HEADING',
          attrs: { level: 1 },
          inlines: [{ type: 'TEXT', text: 'Title', attrs: {}, marks: [] }],
          children: []
        },
        {
          id: 'paragraph',
          type: 'PARAGRAPH',
          attrs: {},
          inlines: [{ type: 'TEXT', text: 'Body', attrs: {}, marks: [] }],
          children: []
        },
        {
          id: 'quote',
          type: 'BLOCK_QUOTE',
          attrs: {},
          inlines: [],
          children: [
            {
              id: 'nested',
              type: 'HEADING',
              attrs: { level: '3' },
              inlines: [
                { type: 'TEXT', text: 'Nested', attrs: {}, marks: [] },
                { type: 'HARD_BREAK', attrs: {}, marks: [] },
                { type: 'TEXT', text: 'Heading', attrs: {}, marks: [] }
              ],
              children: []
            },
            {
              id: 'html',
              type: 'HTML_BLOCK',
              attrs: { source: '<div />' },
              inlines: [],
              children: []
            }
          ]
        }
      ]
    }

    expect(documentOutlineFromBlockDocument(document)).toEqual([
      { id: 'title', level: 1, title: 'Title', headingIndex: 0 },
      { id: 'nested', level: 3, title: 'Nested Heading', headingIndex: 1 }
    ])
  })
})
