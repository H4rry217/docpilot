import { describe, expect, it } from 'vitest'
import { hasFileDrag, markdownFiles } from './useWorkspaceTreeDnd'

describe('useWorkspaceTreeDnd helpers', () => {
  it('detects file drags from data transfer types', () => {
    expect(hasFileDrag({ types: ['text/plain'] } as unknown as DataTransfer)).toBe(false)
    expect(hasFileDrag({ types: ['Files'] } as unknown as DataTransfer)).toBe(true)
  })

  it('filters dropped files to Markdown names', () => {
    const markdown = new File(['# Doc'], 'doc.md')
    const markdownLong = new File(['# Doc'], 'doc.markdown')
    const image = new File(['png'], 'image.png')

    expect(markdownFiles({
      files: [markdown, markdownLong, image]
    } as unknown as DataTransfer)).toEqual([markdown, markdownLong])
  })
})
