import { describe, expect, it } from 'vitest'
import { codeLanguage } from './codeLanguages'

describe('codeLanguage', () => {
  it('normalizes common aliases', () => {
    expect(codeLanguage('js').value).toBe('javascript')
    expect(codeLanguage('ts').value).toBe('typescript')
    expect(codeLanguage('md').value).toBe('markdown')
    expect(codeLanguage('yml').value).toBe('yaml')
  })

  it('falls back to text for unknown languages', () => {
    expect(codeLanguage('unknown-language').value).toBe('text')
  })

  it('marks Mermaid as a special code language', () => {
    expect(codeLanguage('mermaid')).toMatchObject({
      label: 'Mermaid',
      special: true,
      value: 'mermaid'
    })
  })
})
