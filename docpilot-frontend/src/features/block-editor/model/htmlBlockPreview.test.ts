import { describe, expect, it } from 'vitest'
import {
  createHtmlPreviewDocument,
  normalizeDisplayMode,
  numberAttr,
  sandboxForHtmlBlock
} from './htmlBlockPreview'

describe('htmlBlockPreview', () => {
  it('wraps fragments in a sandboxable preview document', () => {
    const document = createHtmlPreviewDocument('<div>hello</div>', 'html1', true)

    expect(document).toContain('<base target="_blank" />')
    expect(document).toContain('<div>hello</div>')
    expect(document).toContain("type: 'docpilot-html-block-height'")
    expect(document).toContain('"html1"')
  })

  it('keeps full html documents and injects the height reporter before body close', () => {
    const document = createHtmlPreviewDocument('<!doctype html><html><body><main>x</main></body></html>', 'html2', true)

    expect(document).toContain('<main>x</main><script>')
    expect(document).toContain('"html2"')
  })

  it('normalizes display, height, and sandbox policy', () => {
    expect(normalizeDisplayMode('auto')).toBe('auto')
    expect(normalizeDisplayMode('fit')).toBe('fixed')
    expect(numberAttr(80, 320)).toBe(120)
    expect(numberAttr(2000, 320)).toBe(1600)
    expect(sandboxForHtmlBlock(false)).toBe('allow-same-origin')
    expect(sandboxForHtmlBlock(true)).toBe('allow-scripts')
  })
})
