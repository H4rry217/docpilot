import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MermaidPreview } from './MermaidPreview'

describe('MermaidPreview', () => {
  it('renders a flowchart preview svg', () => {
    const { container } = render(
      <MermaidPreview
        blockId="mermaid1"
        source={'graph TD\n  A[Start] --> B{Process}\n  B --> C[End]'}
      />
    )

    expect(container.querySelector('.code-block-mermaid-preview')).toBeInstanceOf(HTMLElement)
    expect(container.querySelector('.code-block-mermaid-svg')).toBeInstanceOf(SVGSVGElement)
    expect(container.textContent).toContain('Start')
    expect(container.textContent).toContain('Process')
    expect(container.textContent).toContain('End')
  })
})
