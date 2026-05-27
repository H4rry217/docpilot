import { describe, expect, it } from 'vitest'
import { proseMirrorJsonToBlockDocument } from './proseMirrorToBlockDocument'

describe('proseMirrorJsonToBlockDocument', () => {
  it('creates live block json from editor paragraphs', () => {
    const blockDocument = proseMirrorJsonToBlockDocument({
      type: 'doc',
      attrs: { schemaVersion: 'docpilot-block/2' },
      content: [
        {
          type: 'paragraph',
          content: [{ type: 'text', text: 'hello' }]
        },
        {
          type: 'paragraph',
          content: [{ type: 'text', text: 'world', marks: [{ type: 'bold' }] }]
        }
      ]
    })

    expect(blockDocument.blocks).toMatchObject([
      {
        type: 'PARAGRAPH',
        inlines: [{ type: 'TEXT', text: 'hello', marks: [] }]
      },
      {
        type: 'PARAGRAPH',
        inlines: [{ type: 'TEXT', text: 'world', marks: [{ type: 'BOLD', attrs: {} }] }]
      }
    ])
  })

  it('keeps v2 custom inline nodes in the block model preview', () => {
    const blockDocument = proseMirrorJsonToBlockDocument({
      type: 'doc',
      attrs: { schemaVersion: 'docpilot-block/2' },
      content: [
        {
          type: 'paragraph',
          content: [
            {
              type: 'docpilotMathInline',
              attrs: { text: 'x^2', notation: 'latex', delimiter: '$' }
            },
            {
              type: 'docpilotFootnoteRef',
              attrs: { label: 'one' }
            }
          ]
        }
      ]
    })

    expect(blockDocument.blocks[0]).toMatchObject({
      type: 'PARAGRAPH',
      inlines: [
        { type: 'MATH_INLINE', text: 'x^2' },
        { type: 'FOOTNOTE_REF', attrs: { label: 'one' } }
      ]
    })
  })

  it('keeps html block source in the block model preview', () => {
    const blockDocument = proseMirrorJsonToBlockDocument({
      type: 'doc',
      content: [
        {
          type: 'docpilotHtmlBlock',
          attrs: {
            blockId: 'html1',
            title: 'HTML',
            source: '<div>hello</div>'
          }
        }
      ]
    })

    expect(blockDocument.blocks[0]).toMatchObject({
      id: 'html1',
      type: 'HTML_BLOCK',
      attrs: {
        title: 'HTML',
        source: '<div>hello</div>'
      }
    })
  })
})
