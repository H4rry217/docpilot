import { describe, expect, it } from 'vitest'
import {
  booleanAttr,
  footnoteDefinitionId,
  renderedAttrs,
  renderedSourceAttrs,
  stringAttr
} from './docpilotMarkdownExtensionUtils'
import { frontMatterEntries } from './docpilotMathFrontMatterExtensions'

describe('docpilotMarkdownExtensionUtils', () => {
  it('renders block attrs without editor-only metadata', () => {
    expect(renderedAttrs(
      {
        blockId: 'block-1',
        nodeType: 'paragraph',
        sourceRange: { start: 1 },
        title: 'Title'
      },
      { class: 'docpilot-block' }
    )).toEqual({
      'data-block-id': 'block-1',
      class: 'docpilot-block',
      title: 'Title'
    })
  })

  it('hides selected source attrs before rendering', () => {
    expect(renderedSourceAttrs(
      {
        blockId: 'block-2',
        data: { title: 'Doc' },
        format: 'yaml',
        raw: 'title: Doc'
      },
      ['raw', 'data', 'format']
    )).toEqual({
      'data-block-id': 'block-2'
    })
  })

  it('normalizes simple attr values', () => {
    expect(stringAttr({ label: 'note' }, 'label', 'fallback')).toBe('note')
    expect(stringAttr({ label: 1 }, 'label', 'fallback')).toBe('fallback')
    expect(booleanAttr('true')).toBe(true)
    expect(booleanAttr('false', true)).toBe(false)
  })

  it('builds stable footnote ids for non-id labels', () => {
    expect(footnoteDefinitionId('A B!')).toBe('docpilot-footnote-A-20-B-21')
  })

  it('extracts front matter entries from parsed data or raw yaml', () => {
    expect(frontMatterEntries({ data: { title: 'Doc', tags: ['a'] } })).toEqual([
      ['title', 'Doc'],
      ['tags', ['a']]
    ])
    expect(frontMatterEntries({ raw: '---\ntitle: Doc\ninvalid\n...\n' })).toEqual([
      ['title', 'Doc']
    ])
  })
})
