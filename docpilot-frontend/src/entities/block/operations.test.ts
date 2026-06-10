import { describe, expect, it } from 'vitest'
import type { BlockDocument, BlockNode, InlineNode } from './types'
import {
  applyDocumentOperations,
  previewDocumentOperations,
  queryBlockDocument,
  runDocumentOperationsDebugInput
} from './operations'

describe('Document Operations', () => {
  it('queries blocks, text matches, and sibling context', () => {
    const document = sampleDocument()

    const blockResult = queryBlockDocument(document, { type: 'getBlock', blockId: 'p1' })
    expect(blockResult.diagnostics).toEqual([])
    expect(blockResult.block?.id).toBe('p1')
    expect(blockResult.blockHash).toMatch(/^fnv1a32:/)
    expect(blockResult.parentBlockId).toBeNull()
    expect(blockResult.path).toEqual([1])

    const searchResult = queryBlockDocument(document, { type: 'searchText', text: 'Hello' })
    expect(searchResult.matches?.map((match) => match.range)).toEqual([
      { blockId: 'p1', startOffset: 0, endOffset: 5 },
      { blockId: 'p1', startOffset: 13, endOffset: 18 }
    ])

    const contextResult = queryBlockDocument(document, { type: 'getBlockContext', blockId: 'p1' })
    expect(contextResult.context?.map((item) => [item.relation, item.blockId])).toEqual([
      ['before', 'title'],
      ['target', 'p1'],
      ['after', 'quote']
    ])
  })

  it('applies block insert, replace, move, and delete operations', () => {
    const result = applyDocumentOperations(sampleDocument(), [
      {
        type: 'insertBlock',
        position: { afterBlockId: 'p1' },
        block: paragraph('inserted', 'Inserted')
      },
      {
        type: 'replaceBlock',
        blockId: 'title',
        block: heading('title2', 'Updated')
      },
      {
        type: 'moveBlock',
        blockId: 'p2',
        position: { beforeBlockId: 'p1' }
      },
      {
        type: 'deleteBlock',
        blockId: 'quote'
      }
    ])

    expect(result.ok).toBe(true)
    expect(result.patches.map((patch) => patch.type)).toEqual([
      'block.inserted',
      'block.replaced',
      'block.moved',
      'block.deleted'
    ])
    expect(result.document.blocks.map((block) => block.id)).toEqual(['title2', 'p2', 'p1', 'inserted'])
  })

  it('replaces inline text while preserving untouched marks', () => {
    const result = applyDocumentOperations(sampleDocument(), {
      type: 'replaceText',
      blockId: 'p1',
      text: 'world',
      occurrence: 1,
      replacement: 'DocPilot'
    })

    const block = requireBlock(result.document, 'p1')
    expect(result.ok).toBe(true)
    expect(inlineText(block.inlines)).toBe('Hello DocPilot. Hello world.')
    expect(block.inlines[1]).toMatchObject({
      type: 'TEXT',
      text: 'DocPilot',
      marks: [{ type: 'BOLD', attrs: {} }]
    })
  })

  it('inserts and deletes inline text by offsets', () => {
    const inserted = applyDocumentOperations(sampleDocument(), {
      type: 'insertText',
      blockId: 'p1',
      offset: 5,
      text: ','
    })
    expect(inlineText(requireBlock(inserted.document, 'p1').inlines)).toBe('Hello, world. Hello world.')

    const deleted = applyDocumentOperations(sampleDocument(), {
      type: 'deleteTextRange',
      blockId: 'p1',
      startOffset: 0,
      endOffset: 6,
      expectedText: 'Hello '
    })
    expect(deleted.ok).toBe(true)
    expect(inlineText(requireBlock(deleted.document, 'p1').inlines)).toBe('world. Hello world.')
    expect(requireBlock(deleted.document, 'p1').inlines[0].marks).toEqual([{ type: 'BOLD', attrs: {} }])
  })

  it('updates block attrs without changing inlines or children', () => {
    const result = applyDocumentOperations(sampleDocument(), {
      type: 'updateBlockAttrs',
      blockId: 'title',
      attrs: { level: 2, tone: 'quiet' }
    })

    const block = requireBlock(result.document, 'title')
    expect(result.ok).toBe(true)
    expect(result.patches[0].type).toBe('block.attrs.updated')
    expect(block.attrs).toEqual({ level: 2, tone: 'quiet' })
    expect(inlineText(block.inlines)).toBe('Title')
    expect(block.children).toEqual([])
  })

  it('replaces block inlines without changing block identity or children', () => {
    const result = applyDocumentOperations(sampleDocument(), {
      type: 'replaceBlockInlines',
      blockId: 'quote',
      inlines: [textInline('Quote title')]
    })

    const block = requireBlock(result.document, 'quote')
    expect(result.ok).toBe(true)
    expect(result.patches[0].type).toBe('block.inlines.replaced')
    expect(block.id).toBe('quote')
    expect(block.type).toBe('BLOCK_QUOTE')
    expect(block.children.map((child) => child.id)).toEqual(['quote-child'])
    expect(inlineText(block.inlines)).toBe('Quote title')
  })

  it('replaces block children and reports duplicate child ids', () => {
    const result = applyDocumentOperations(sampleDocument(), {
      type: 'replaceBlockChildren',
      blockId: 'quote',
      children: [paragraph('nested-next', 'Nested next')]
    })

    expect(result.ok).toBe(true)
    expect(result.patches[0].type).toBe('block.children.replaced')
    expect(requireBlock(result.document, 'quote').children.map((child) => child.id)).toEqual(['nested-next'])

    const duplicate = applyDocumentOperations(sampleDocument(), {
      type: 'replaceBlockChildren',
      blockId: 'quote',
      children: [paragraph('p1', 'Duplicate')]
    })

    expect(duplicate.ok).toBe(false)
    expect(duplicate.patches).toEqual([])
    expect(duplicate.diagnostics[0]).toMatchObject({
      code: 'duplicate_block_id',
      blockId: 'p1'
    })
  })

  it('reports ambiguous text matches instead of guessing', () => {
    const result = applyDocumentOperations(sampleDocument(), {
      type: 'replaceText',
      blockId: 'p1',
      text: 'world',
      replacement: 'DocPilot'
    })

    expect(result.ok).toBe(false)
    expect(result.patches).toEqual([])
    expect(result.diagnostics[0]).toMatchObject({
      code: 'ambiguous_text_match',
      blockId: 'p1'
    })
    expect(inlineText(requireBlock(result.document, 'p1').inlines)).toBe('Hello world. Hello world.')
  })

  it('keeps the document unchanged when preconditions fail', () => {
    const result = applyDocumentOperations(sampleDocument(), {
      type: 'deleteTextRange',
      blockId: 'p1',
      startOffset: 0,
      endOffset: 5,
      expectedText: 'Nope'
    })

    expect(result.ok).toBe(false)
    expect(result.patches).toEqual([])
    expect(result.diagnostics[0].code).toBe('precondition_failed')
    expect(inlineText(requireBlock(result.document, 'p1').inlines)).toBe('Hello world. Hello world.')
  })

  it('previews without mutating the input document', () => {
    const document = sampleDocument()
    const result = previewDocumentOperations(document, {
      type: 'insertBlock',
      position: { afterBlockId: 'p1' },
      block: paragraph('draft', 'Draft')
    })

    expect(result.dryRun).toBe(true)
    expect(result.document).not.toBe(document)
    expect(result.document.blocks.map((block) => block.id)).toEqual(['title', 'p1', 'draft', 'quote', 'p2'])
    expect(document.blocks.map((block) => block.id)).toEqual(['title', 'p1', 'quote', 'p2'])
  })

  it('runs JSON operation batches for future console debugging', () => {
    const debugResult = runDocumentOperationsDebugInput(
      sampleDocument(),
      JSON.stringify({
        mode: 'preview',
        operations: [
          {
            type: 'replaceText',
            blockId: 'p1',
            text: 'world',
            occurrence: 2,
            replacement: 'there'
          }
        ]
      })
    )

    expect(debugResult.mode).toBe('preview')
    expect(debugResult.result.ok).toBe(true)
    expect(debugResult.output).toContain('OK preview')
    expect(debugResult.output).toContain('patch 0: Replaced text in block "p1".')
  })
})

function sampleDocument(): BlockDocument {
  return {
    schemaVersion: 'docpilot-block/2',
    metadata: {},
    blocks: [
      heading('title', 'Title'),
      {
        id: 'p1',
        type: 'PARAGRAPH',
        attrs: {},
        inlines: [
          textInline('Hello '),
          textInline('world', [{ type: 'BOLD', attrs: {} }]),
          textInline('. Hello world.')
        ],
        children: []
      },
      {
        id: 'quote',
        type: 'BLOCK_QUOTE',
        attrs: {},
        inlines: [],
        children: [paragraph('quote-child', 'Nested')]
      },
      paragraph('p2', 'Second')
    ]
  }
}

function heading(id: string, text: string): BlockNode {
  return {
    id,
    type: 'HEADING',
    attrs: { level: 1 },
    inlines: [textInline(text)],
    children: []
  }
}

function paragraph(id: string, text: string): BlockNode {
  return {
    id,
    type: 'PARAGRAPH',
    attrs: {},
    inlines: [textInline(text)],
    children: []
  }
}

function textInline(text: string, marks: InlineNode['marks'] = []): InlineNode {
  return {
    type: 'TEXT',
    text,
    attrs: {},
    marks
  }
}

function requireBlock(document: BlockDocument, blockId: string): BlockNode {
  const block = findBlock(document.blocks, blockId)
  if (!block) throw new Error(`Missing block ${blockId}`)
  return block
}

function findBlock(blocks: BlockNode[], blockId: string): BlockNode | null {
  for (const block of blocks) {
    if (block.id === blockId) return block
    const child = findBlock(block.children, blockId)
    if (child) return child
  }

  return null
}

function inlineText(inlines: InlineNode[]): string {
  return inlines.map((inline) => {
    if (inline.type === 'HARD_BREAK' || inline.type === 'SOFT_BREAK') return '\n'
    return inline.text ?? ''
  }).join('')
}
