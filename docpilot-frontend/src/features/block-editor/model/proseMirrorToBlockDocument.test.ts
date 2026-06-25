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

  it('keeps editor inline nodes in the block model preview', () => {
    const blockDocument = proseMirrorJsonToBlockDocument({
      type: 'doc',
      attrs: { schemaVersion: 'docpilot-block/2' },
      content: [
        {
          type: 'paragraph',
          content: [
            {
              type: 'image',
              attrs: {
                src: 'https://example.com/photo.png',
                alt: 'photo',
                caption: 'A quiet field',
                width: 280,
                alignment: 'center'
              },
              marks: [{ type: 'link', attrs: { href: 'https://example.com' } }]
            },
            {
              type: 'inlineMath',
              attrs: { latex: 'x^2', text: 'x^2', notation: 'latex', delimiter: '$' }
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
        {
          type: 'IMAGE',
          text: 'photo',
          attrs: {
            src: 'https://example.com/photo.png',
            alt: 'photo',
            caption: 'A quiet field',
            width: 280,
            alignment: 'center'
          },
          marks: [{ type: 'LINK', attrs: { href: 'https://example.com' } }]
        },
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
        id: 'html1',
        title: 'HTML',
        source: '<div>hello</div>',
        displayMode: 'fixed',
        fixedHeightPx: 320,
        allowScripts: false
      }
    })
  })

  it('keeps code block language and drops removed wrapping attrs', () => {
    const blockDocument = proseMirrorJsonToBlockDocument({
      type: 'doc',
      content: [
        {
          type: 'codeBlock',
          attrs: {
            blockId: 'code1',
            language: 'java',
            caption: 'Example code',
            width: 360,
            wrapLines: true
          },
          content: [{ type: 'text', text: 'System.out.println("Hello");' }]
        }
      ]
    })

    expect(blockDocument.blocks[0]).toMatchObject({
      id: 'code1',
      type: 'CODE_BLOCK',
      attrs: {
        language: 'java',
        caption: 'Example code',
        width: 360,
        text: 'System.out.println("Hello");'
      }
    })
    expect(blockDocument.blocks[0].attrs).not.toHaveProperty('wrapLines')
  })

  it('saves legacy diagram nodes as mermaid code blocks', () => {
    const blockDocument = proseMirrorJsonToBlockDocument({
      type: 'doc',
      content: [
        {
          type: 'docpilotDiagramBlock',
          attrs: {
            blockId: 'diagram1',
            engine: 'mermaid',
            text: 'graph TD\n  A-->B'
          }
        }
      ]
    })

    expect(blockDocument.blocks[0]).toMatchObject({
      id: 'diagram1',
      type: 'CODE_BLOCK',
      attrs: {
        language: 'mermaid',
        text: 'graph TD\n  A-->B'
      }
    })
  })

  it('saves official Tiptap task nodes as canonical task list items', () => {
    const blockDocument = proseMirrorJsonToBlockDocument({
      type: 'doc',
      content: [
        {
          type: 'taskList',
          attrs: { blockId: 'tasks' },
          content: [
            {
              type: 'taskItem',
              attrs: { blockId: 'task-1', checked: true },
              content: [
                {
                  type: 'paragraph',
                  attrs: { blockId: 'task-text' },
                  content: [{ type: 'text', text: 'Ship task lists' }]
                }
              ]
            }
          ]
        }
      ]
    })

    expect(blockDocument.blocks).toMatchObject([
      {
        id: 'tasks',
        type: 'BULLET_LIST',
        children: [
          {
            id: 'task-1',
            type: 'TASK_LIST_ITEM',
            attrs: { checked: true },
            children: [
              {
                id: 'task-text',
                type: 'PARAGRAPH',
                inlines: [{ type: 'TEXT', text: 'Ship task lists' }]
              }
            ]
          }
        ]
      }
    ])
  })

  it('normalizes html attrs from editor json', () => {
    const blockDocument = proseMirrorJsonToBlockDocument({
      type: 'doc',
      content: [
        {
          type: 'docpilotHtmlBlock',
          attrs: {
            blockId: 'html1',
            source: '<section />',
            displayMode: 'fit',
            fixedHeightPx: 80,
            allowScripts: 'true'
          }
        }
      ]
    })

    expect(blockDocument.blocks[0].attrs).toMatchObject({
      id: 'html1',
      source: '<section />',
      displayMode: 'fixed',
      fixedHeightPx: 320,
      allowScripts: true
    })
  })
})
