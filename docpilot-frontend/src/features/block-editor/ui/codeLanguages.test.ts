import { describe, expect, it } from 'vitest'
import { codeLanguage } from './codeLanguages'

describe('codeLanguage', () => {
  it('normalizes common aliases', () => {
    expect(codeLanguage('c++').value).toBe('cpp')
    expect(codeLanguage('c#').value).toBe('csharp')
    expect(codeLanguage('pgsql').value).toBe('sql')
    expect(codeLanguage('ps1').value).toBe('powershell')
    expect(codeLanguage('py').value).toBe('python')
    expect(codeLanguage('sh').value).toBe('bash')
    expect(codeLanguage('js').value).toBe('javascript')
    expect(codeLanguage('ts').value).toBe('typescript')
    expect(codeLanguage('md').value).toBe('markdown')
    expect(codeLanguage('yml').value).toBe('yaml')
  })

  it('recognizes SQL as a case-insensitive language', () => {
    expect(codeLanguage(' SQL ')).toMatchObject({
      caseInsensitive: true,
      label: 'SQL',
      value: 'sql'
    })
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
