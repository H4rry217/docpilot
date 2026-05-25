import MarkdownIt from 'markdown-it'
import TurndownService from 'turndown'

const markdownIt = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true
})

const turndown = new TurndownService({
  headingStyle: 'atx',
  bulletListMarker: '-',
  codeBlockStyle: 'fenced'
})

turndown.addRule('docpilotHtmlBlock', {
  filter: (node) => node.nodeType === 1 && (node as HTMLElement).hasAttribute('data-docpilot-html-block'),
  replacement: (_content, node) => {
    const element = node as HTMLElement
    const source = element.getAttribute('data-html-source') ?? ''
    return source ? `\n\n${source}\n\n` : ''
  }
})

turndown.addRule('strikethrough', {
  filter: ['s', 'del'] as Array<keyof HTMLElementTagNameMap>,
  replacement: (content) => `~~${content}~~`
})

export function markdownToHtml(markdown: string): string {
  return markdownIt.render(markdown)
}

export function htmlToMarkdown(html: string): string {
  return turndown.turndown(html).trim()
}
