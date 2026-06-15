import { describe, expect, it } from 'vitest'
import { htmlToMarkdown, markdownToHtml, markdownToInlineHtml } from './markdown'

describe('markdown conversion', () => {
  it('renders markdown headings and lists to html', () => {
    const html = markdownToHtml('# 标题\n\n- 第一项')

    expect(html).toContain('<h1>标题</h1>')
    expect(html).toContain('<li>第一项</li>')
  })

  it('preserves docpilot html block source when serializing editor html', () => {
    const markdown = htmlToMarkdown(`
      <div data-docpilot-html-block data-html-source="<section>hello</section>">
        <pre>&lt;section&gt;hello&lt;/section&gt;</pre>
      </div>
    `)

    expect(markdown).toBe('<section>hello</section>')
  })

  it('renders inline markdown without wrapping it in a paragraph', () => {
    expect(markdownToInlineHtml('**重要**')).toBe('<strong>重要</strong>')
  })
})
