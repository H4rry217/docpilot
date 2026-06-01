import { Editor } from '@tiptap/core'
import { describe, expect, it } from 'vitest'
import type { BlockDocument } from '../../../entities/block/types'
import { blockDocumentToProseMirrorJson, isBlockDocument, isProseMirrorDoc } from './blockDocumentToProseMirror'
import { editorExtensions } from './extensions'

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

  it('builds TipTap-renderable json for rich imported markdown elements', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'p1',
          type: 'PARAGRAPH',
          attrs: {},
          inlines: [
            { type: 'TEXT', text: 'before ', attrs: {}, marks: [] },
            { type: 'IMAGE', text: 'sample', attrs: { src: 'https://example.com/a.png', alt: 'sample' }, marks: [] },
            { type: 'TEXT', text: ' x', attrs: {}, marks: [{ type: 'HIGHLIGHT', attrs: {} }] },
            { type: 'MATH_INLINE', text: 'E=mc^2', attrs: { notation: 'latex', delimiter: '$' }, marks: [] },
            { type: 'FOOTNOTE_REF', attrs: { label: 'one' }, marks: [] },
            { type: 'TEXT', text: ' end', attrs: {}, marks: [{ type: 'UNDERLINE', attrs: {} }] }
          ],
          children: []
        },
        {
          id: 'table1',
          type: 'TABLE',
          attrs: {},
          inlines: [],
          children: [
            {
              id: 'row1',
              type: 'TABLE_ROW',
              attrs: {},
              inlines: [],
              children: [
                { id: 'cell1', type: 'TABLE_CELL', attrs: { header: true }, inlines: [{ type: 'TEXT', text: 'Name', attrs: {}, marks: [] }], children: [] },
                { id: 'cell2', type: 'TABLE_CELL', attrs: {}, inlines: [{ type: 'TEXT', text: 'Value', attrs: {}, marks: [] }], children: [] }
              ]
            }
          ]
        },
        {
          id: 'math1',
          type: 'MATH_BLOCK',
          attrs: { notation: 'latex', text: '\\int_a^b f(x)dx', delimiter: '$$' },
          inlines: [],
          children: []
        },
        {
          id: 'diagram1',
          type: 'DIAGRAM_BLOCK',
          attrs: { engine: 'mermaid', text: 'graph TD\\n  A-->B' },
          inlines: [],
          children: []
        },
        {
          id: 'footnote1',
          type: 'FOOTNOTE_DEFINITION',
          attrs: { label: 'one' },
          inlines: [],
          children: [{ id: 'fp1', type: 'PARAGRAPH', attrs: {}, inlines: [{ type: 'TEXT', text: 'Footnote', attrs: {}, marks: [] }], children: [] }]
        },
        {
          id: 'definition1',
          type: 'DEFINITION_LIST',
          attrs: {},
          inlines: [],
          children: [
            { id: 'term1', type: 'DEFINITION_TERM', attrs: {}, inlines: [{ type: 'TEXT', text: 'Markdown', attrs: {}, marks: [] }], children: [] },
            {
              id: 'item1',
              type: 'DEFINITION_ITEM',
              attrs: {},
              inlines: [],
              children: [{ id: 'dp1', type: 'PARAGRAPH', attrs: {}, inlines: [{ type: 'TEXT', text: 'Lightweight markup', attrs: {}, marks: [] }], children: [] }]
            }
          ]
        },
        { id: 'toc1', type: 'TOC', attrs: { raw: '[TOC]' }, inlines: [], children: [] },
        { id: 'ref1', type: 'LINK_REFERENCE_DEFINITION', attrs: { label: 'GitHub', href: 'https://github.com' }, inlines: [], children: [] }
      ]
    }

    const proseMirrorJson = blockDocumentToProseMirrorJson(blockDocument)

    for (const item of proseMirrorJson.content ?? []) {
      if (item.type === 'paragraph') {
        for (const child of item.content ?? []) {
          try {
            new Editor({
              extensions: editorExtensions,
              content: { type: 'doc', content: [{ type: 'paragraph', content: [child] }] }
            }).destroy()
          } catch (error) {
            throw new Error(`TipTap failed to render paragraph child ${child.type}: ${String(error)}`)
          }
        }
      }
      try {
        new Editor({ extensions: editorExtensions, content: { type: 'doc', content: [item] } }).destroy()
      } catch (error) {
        throw new Error(`TipTap failed to render ${item.type}: ${String(error)}`)
      }
    }

    const editor = new Editor({
      extensions: editorExtensions,
      content: proseMirrorJson
    })

    expect(editor.getJSON().content).toHaveLength(blockDocument.blocks.length)
    editor.destroy()
  })
})
