import { describe, expect, it } from 'vitest'
import { formatCodeBlockText } from './codeBlockFormatter'

describe('formatCodeBlockText', () => {
  it('formats valid json with two-space indentation', () => {
    expect(formatCodeBlockText('json', '{"name":"Harry","role":"Developer"}')).toBe(`{
  "name": "Harry",
  "role": "Developer"
}`)
  })

  it('keeps invalid json unchanged', () => {
    const source = '{"name":'
    expect(formatCodeBlockText('json', source)).toBe(source)
  })

  it('formats brace languages into readable blocks', () => {
    expect(formatCodeBlockText('java', 'public class Main { public static void main(String[] args) { System.out.println("Hello"); } }')).toBe(`public class Main {
  public static void main(String[] args) {
    System.out.println("Hello");
  }
}`)
  })
})
