import { Editor } from '@tiptap/core'
import { describe, expect, it } from 'vitest'
import type { BlockDocument } from '../../../entities/block/types'
import { deleteBlocksByIds } from './blockSelection'
import { blockDocumentToProseMirrorJson, isBlockDocument, isProseMirrorDoc } from './blockDocumentToProseMirror'
import { deleteAdjacentCodeBlock } from './docpilotCodeBlock'
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

  it('unescapes markdown punctuation stored in legacy text inlines', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'p1',
          type: 'PARAGRAPH',
          attrs: {},
          inlines: [
            { type: 'TEXT', text: '\\#不是标题', attrs: {}, marks: [] },
            { type: 'TEXT', text: ' \\*不是斜体\\* ', attrs: {}, marks: [] },
            { type: 'TEXT', text: '\\==不是高亮\\==', attrs: {}, marks: [] }
          ],
          children: []
        }
      ]
    }

    expect(blockDocumentToProseMirrorJson(blockDocument).content?.[0].content).toEqual([
      { type: 'text', text: '#不是标题' },
      { type: 'text', text: ' *不是斜体* ' },
      { type: 'text', text: '==不是高亮==' }
    ])
  })

  it('keeps editable image layout attrs in editor json', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'p1',
          type: 'PARAGRAPH',
          attrs: {},
          inlines: [
            {
              type: 'IMAGE',
              text: 'photo',
              attrs: {
                src: 'https://example.com/photo.png',
                alt: 'photo',
                caption: 'A quiet field',
                width: 280,
                alignment: 'right'
              },
              marks: []
            }
          ],
          children: []
        }
      ]
    }

    expect(blockDocumentToProseMirrorJson(blockDocument).content?.[0].content?.[0]).toMatchObject({
      type: 'image',
      attrs: {
        src: 'https://example.com/photo.png',
        alt: 'photo',
        caption: 'A quiet field',
        width: 280,
        alignment: 'right'
      }
    })
  })

  it('keeps editable mermaid layout attrs in the editor schema', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'diagram1',
          type: 'CODE_BLOCK',
          attrs: {
            language: 'mermaid',
            text: 'graph TD\n  A-->B',
            caption: 'Request flow',
            width: 420
          },
          inlines: [],
          children: []
        }
      ]
    }

    const editor = new Editor({
      extensions: editorExtensions,
      content: blockDocumentToProseMirrorJson(blockDocument)
    })

    expect(editor.getJSON().content?.[0]).toMatchObject({
      type: 'codeBlock',
      attrs: {
        language: 'mermaid',
        caption: 'Request flow',
        width: 420
      }
    })
    editor.destroy()
  })

  it('renders collapsible callouts as native details blocks', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'details1',
          type: 'CALLOUT',
          attrs: {
            kind: 'details',
            title: '点击展开',
            collapsible: true,
            open: false
          },
          inlines: [],
          children: [
            {
              id: 'p1',
              type: 'PARAGRAPH',
              attrs: {},
              inlines: [{ type: 'TEXT', text: '隐藏内容', attrs: {}, marks: [] }],
              children: []
            }
          ]
        }
      ]
    }

    const editor = new Editor({
      extensions: editorExtensions,
      content: blockDocumentToProseMirrorJson(blockDocument)
    })

    expect(editor.getHTML()).toContain('<details')
    expect(editor.getHTML()).toContain('<summary')
    expect(editor.getHTML()).toContain('点击展开')
    expect(editor.getHTML()).toContain('隐藏内容')
    expect(editor.getHTML()).not.toContain('docpilot-html-block')
    editor.destroy()
  })

  it('toggles collapsible callouts locally from summary clicks', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'details1',
          type: 'CALLOUT',
          attrs: {
            kind: 'details',
            title: 'Click to expand',
            collapsible: true,
            open: false
          },
          inlines: [],
          children: [
            {
              id: 'p1',
              type: 'PARAGRAPH',
              attrs: {},
              inlines: [{ type: 'TEXT', text: 'Hidden content', attrs: {}, marks: [] }],
              children: []
            }
          ]
        }
      ]
    }

    const editor = new Editor({
      extensions: editorExtensions,
      content: blockDocumentToProseMirrorJson(blockDocument)
    })
    const summary = editor.view.dom.querySelector('summary.docpilot-callout-summary')
    expect(summary).toBeInstanceOf(HTMLElement)
    expect(summary?.firstChild).toBeInstanceOf(Text)

    summary?.firstChild?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))
    ;(editor.view as unknown as { domObserver?: { flush: () => void } }).domObserver?.flush()

    expect(editor.view.dom.querySelector('details')?.hasAttribute('open')).toBe(true)
    expect(editor.getJSON().content?.[0].attrs).toMatchObject({ open: false })
    editor.destroy()
  })

  it('normalizes legacy split details html blocks before rendering', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'details-open',
          type: 'HTML_BLOCK',
          attrs: { source: '<details><summary>Click to expand</summary>' },
          inlines: [],
          children: []
        },
        {
          id: 'details-body',
          type: 'PARAGRAPH',
          attrs: {},
          inlines: [{ type: 'TEXT', text: 'Hidden content', attrs: {}, marks: [] }],
          children: []
        },
        {
          id: 'details-close',
          type: 'HTML_BLOCK',
          attrs: { source: '</details>' },
          inlines: [],
          children: []
        }
      ]
    }

    const editor = new Editor({
      extensions: editorExtensions,
      content: blockDocumentToProseMirrorJson(blockDocument)
    })

    expect(editor.getJSON().content).toHaveLength(1)
    expect(editor.getHTML()).toContain('<details')
    expect(editor.getHTML()).toContain('<summary')
    expect(editor.getHTML()).toContain('Click to expand')
    expect(editor.getHTML()).toContain('Hidden content')
    expect(editor.getHTML()).not.toContain('docpilot-html-block')
    editor.destroy()
  })

  it('renders math formulas instead of raw tokens and source cards', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'p1',
          type: 'PARAGRAPH',
          attrs: {},
          inlines: [
            { type: 'TEXT', text: 'inline: ', attrs: {}, marks: [] },
            { type: 'MATH_INLINE', text: 'E=mc^2', attrs: { notation: 'latex', delimiter: '$' }, marks: [] }
          ],
          children: []
        },
        {
          id: 'math1',
          type: 'MATH_BLOCK',
          attrs: { notation: 'latex', text: '\\int_a^b f(x)dx', delimiter: '$$' },
          inlines: [],
          children: []
        }
      ]
    }

    const editor = new Editor({
      extensions: editorExtensions,
      content: blockDocumentToProseMirrorJson(blockDocument)
    })
    const html = editor.getHTML()

    expect(html).toContain('docpilot-math-inline')
    expect(html).toContain('docpilot-math-block')
    expect(html).toContain('<sup>2</sup>')
    expect(html).toContain('∫')
    expect(html).toContain('docpilot-math-limit-top')
    expect(html).not.toContain('docpilot-inline-token')
    expect(html).not.toContain('docpilot-leaf-block')
    editor.destroy()
  })

  it('renders double-escaped latex commands in math blocks', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'math1',
          type: 'MATH_BLOCK',
          attrs: { notation: 'latex', text: '\\\\int_a^b f(x)dx', delimiter: '$$' },
          inlines: [],
          children: []
        }
      ]
    }

    const editor = new Editor({
      extensions: editorExtensions,
      content: blockDocumentToProseMirrorJson(blockDocument)
    })
    const html = editor.getHTML()

    expect(html).toContain('∫')
    expect(html).toContain('docpilot-math-limit-top')
    expect(html).not.toContain('\\\\int')
    editor.destroy()
  })

  it('maps legacy mermaid diagram blocks to editable code blocks', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'diagram1',
          type: 'DIAGRAM_BLOCK',
          attrs: {
            engine: 'mermaid',
            text: 'graph TD\n  A[Start] --> B{Process}\n  B --> C[End]'
          },
          inlines: [],
          children: []
        }
      ]
    }

    const editor = new Editor({
      extensions: editorExtensions,
      content: blockDocumentToProseMirrorJson(blockDocument)
    })

    expect(editor.getJSON().content?.[0]).toMatchObject({
      type: 'codeBlock',
      attrs: { language: 'mermaid' },
      content: [{ type: 'text', text: 'graph TD\n  A[Start] --> B{Process}\n  B --> C[End]' }]
    })
    editor.destroy()
  })

  it('renders front matter blocks as metadata', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'frontmatter1',
          type: 'FRONT_MATTER',
          attrs: {
            format: 'yaml',
            raw: '---\ntitle: Markdown Demo\nauthor: Harry\ntags:\n  - markdown\n  - demo\n---',
            data: {
              title: 'Markdown Demo',
              author: 'Harry',
              tags: ['markdown', 'demo']
            }
          },
          inlines: [],
          children: []
        }
      ]
    }

    const editor = new Editor({
      extensions: editorExtensions,
      content: blockDocumentToProseMirrorJson(blockDocument)
    })
    const html = editor.getHTML()

    expect(html).toContain('docpilot-front-matter')
    expect(html).toContain('Markdown Demo')
    expect(html).toContain('Harry')
    expect(html).toContain('markdown')
    expect(html).toContain('demo')
    expect(html).not.toContain('docpilot-leaf-block')
    editor.destroy()
  })

  it('renders inline code marks as TipTap code marks', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'p1',
          type: 'PARAGRAPH',
          attrs: {},
          inlines: [
            { type: 'TEXT', text: '使用 ', attrs: {}, marks: [] },
            {
              type: 'TEXT',
              text: 'System.out.println("Hello")',
              attrs: {},
              marks: [{ type: 'CODE', attrs: {} }]
            },
            { type: 'TEXT', text: ' 输出内容。', attrs: {}, marks: [] }
          ],
          children: []
        }
      ]
    }

    const proseMirrorJson = blockDocumentToProseMirrorJson(blockDocument)
    expect(proseMirrorJson.content?.[0].content?.[1]).toEqual({
      type: 'text',
      text: 'System.out.println("Hello")',
      marks: [{ type: 'code', attrs: {} }]
    })

    const editor = new Editor({
      extensions: editorExtensions,
      content: proseMirrorJson
    })

    expect(editor.getHTML()).toContain('<code>System.out.println("Hello")</code>')
    expect(editor.getHTML()).not.toContain('`System.out.println')
    editor.destroy()
  })

  it('keeps code blocks selectable and deletable as a block node', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'code1',
          type: 'CODE_BLOCK',
          attrs: { language: 'java', text: 'System.out.println("Hello");' },
          inlines: [],
          children: []
        },
        {
          id: 'p1',
          type: 'PARAGRAPH',
          attrs: {},
          inlines: [{ type: 'TEXT', text: 'after', attrs: {}, marks: [] }],
          children: []
        }
      ]
    }

    const editor = new Editor({
      extensions: editorExtensions,
      content: blockDocumentToProseMirrorJson(blockDocument)
    })

    expect(editor.getJSON().content?.[0].attrs).toMatchObject({
      language: 'java'
    })
    editor.commands.setNodeSelection(0)
    editor.commands.deleteSelection()

    expect(editor.getJSON().content?.map((node) => node.type)).toEqual(['paragraph'])
    editor.destroy()
  })

  it('deletes adjacent code blocks from text cursor block boundaries', () => {
    const editorAfterCode = new Editor({
      extensions: editorExtensions,
      content: {
        type: 'doc',
        content: [
          {
            type: 'codeBlock',
            attrs: { language: 'sql' },
            content: [{ type: 'text', text: 'SELECT * FROM user WHERE id = 1;' }]
          },
          { type: 'paragraph' }
        ]
      }
    })
    const paragraphStart = editorAfterCode.state.doc.child(0).nodeSize
    editorAfterCode.commands.setTextSelection(paragraphStart + 1)

    expect(deleteAdjacentCodeBlock(editorAfterCode, 'backward')).toBe(true)
    expect(editorAfterCode.getJSON().content?.map((node) => node.type)).toEqual(['paragraph'])
    editorAfterCode.destroy()

    const editorBeforeCode = new Editor({
      extensions: editorExtensions,
      content: {
        type: 'doc',
        content: [
          { type: 'paragraph' },
          {
            type: 'codeBlock',
            attrs: { language: 'json' },
            content: [{ type: 'text', text: '{"ok": true}' }]
          }
        ]
      }
    })
    editorBeforeCode.commands.setTextSelection(1)

    expect(deleteAdjacentCodeBlock(editorBeforeCode, 'forward')).toBe(true)
    expect(editorBeforeCode.getJSON().content?.map((node) => node.type)).toEqual(['paragraph'])
    editorBeforeCode.destroy()
  })

  it('deletes multiple selected block ids as a single block selection', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: {
        type: 'doc',
        content: [
          {
            type: 'heading',
            attrs: { level: 1, blockId: 'heading1' },
            content: [{ type: 'text', text: 'Title' }]
          },
          {
            type: 'paragraph',
            attrs: { blockId: 'paragraph1' },
            content: [{ type: 'text', text: 'Keep me' }]
          },
          {
            type: 'codeBlock',
            attrs: { blockId: 'code1', language: 'sql' },
            content: [{ type: 'text', text: 'SELECT 1;' }]
          }
        ]
      }
    })

    expect(deleteBlocksByIds(editor, ['heading1', 'code1'])).toBe(true)
    expect(editor.getJSON().content).toMatchObject([
      {
        type: 'paragraph',
        attrs: { blockId: 'paragraph1' },
        content: [{ type: 'text', text: 'Keep me' }]
      }
    ])
    editor.destroy()
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

  it('keeps link reference definitions hidden from rendered editor content', () => {
    const blockDocument: BlockDocument = {
      schemaVersion: 'docpilot-block/2',
      metadata: {},
      blocks: [
        {
          id: 'ref1',
          type: 'LINK_REFERENCE_DEFINITION',
          attrs: { label: 'github', href: 'https://github.com', raw: '[github]: https://github.com' },
          inlines: [],
          children: []
        }
      ]
    }

    const editor = new Editor({
      extensions: editorExtensions,
      content: blockDocumentToProseMirrorJson(blockDocument)
    })

    expect(editor.getJSON().content).toHaveLength(1)
    expect(editor.getHTML()).toContain('docpilot-link-reference-definition')
    expect(editor.getHTML()).not.toContain('Link reference')
    expect(editor.getHTML()).not.toContain('[github]: https://github.com')
    editor.destroy()
  })
})
