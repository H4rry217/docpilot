import { describe, expect, it } from 'vitest'
import { documentTitleFromMarkdownFileName, isMarkdownFileName, uniqueMarkdownNodeName } from './markdownUpload'

describe('markdownUpload', () => {
  it('recognizes markdown file names', () => {
    expect(isMarkdownFileName('README.md')).toBe(true)
    expect(isMarkdownFileName('spec.markdown')).toBe(true)
    expect(isMarkdownFileName('image.png')).toBe(false)
  })

  it('uses the markdown file name as the document title', () => {
    expect(documentTitleFromMarkdownFileName('README.md')).toBe('README')
    expect(documentTitleFromMarkdownFileName('需求文档.markdown')).toBe('需求文档')
  })

  it('deduplicates node names case-insensitively', () => {
    const usedNames = new Set(['readme.md'])

    expect(uniqueMarkdownNodeName('README.md', usedNames)).toBe('README (1).md')
    expect(uniqueMarkdownNodeName('README.md', usedNames)).toBe('README (2).md')
  })
})
