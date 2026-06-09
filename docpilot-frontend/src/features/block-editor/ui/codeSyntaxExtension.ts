import {
  Decoration,
  MatchDecorator,
  ViewPlugin,
  type DecorationSet,
  type ViewUpdate
} from '@codemirror/view'
import { codeLanguage, type CodeLanguage } from './codeLanguages'

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function syntaxPattern(language: CodeLanguage): RegExp {
  const keywords = language.keywords.length ? language.keywords.map(escapeRegExp).join('|') : '$.'

  if (language.value === 'yaml') {
    return new RegExp(
      [
        '(^|\\n)(\\s*-\\s*)?([A-Za-z_][\\w.-]*)(?=\\s*:)',
        '(^|\\n)(\\s*-)(?=\\s+)',
        '#[^\\n]*',
        '"(?:\\\\.|[^"\\\\])*"',
        "'(?:\\\\.|[^'\\\\])*'",
        '\\b(?:' + keywords + ')\\b',
        '\\b\\d+(?:\\.\\d+)?\\b'
      ].join('|'),
      'g'
    )
  }

  return new RegExp(
    [
      '//[^\\n]*',
      '#[^\\n]*',
      '/\\*[^]*?\\*/',
      '"(?:\\\\.|[^"\\\\])*"',
      "'(?:\\\\.|[^'\\\\])*'",
      '`(?:\\\\.|[^`\\\\])*`',
      '</?[A-Za-z][\\w:-]*',
      '\\b(?:' + keywords + ')\\b',
      '\\b\\d+(?:\\.\\d+)?\\b'
    ].join('|'),
    'g'
  )
}

export function syntaxExtension(languageValue: string) {
  const language = codeLanguage(languageValue)
  const matcher = new MatchDecorator({
    regexp: syntaxPattern(language),
    decorate(add, from, to, match) {
      const token = match[0]
      let className = 'cm-dp-token-keyword'

      if (language.value === 'yaml' && match[3]) {
        const offset = token.lastIndexOf(match[3])
        add(from + offset, from + offset + match[3].length, Decoration.mark({ class: 'cm-dp-token-key' }))
        return
      }

      if (language.value === 'yaml' && match[5]) {
        const offset = token.lastIndexOf(match[5])
        add(from + offset, from + offset + match[5].length, Decoration.mark({ class: 'cm-dp-token-punctuation' }))
        return
      }

      if (token.startsWith('//') || token.startsWith('#') || token.startsWith('/*')) {
        className = 'cm-dp-token-comment'
      } else if (token.startsWith('"') || token.startsWith("'") || token.startsWith('`')) {
        className = 'cm-dp-token-string'
      } else if (/^\d/.test(token)) {
        className = 'cm-dp-token-number'
      } else if (token.startsWith('<')) {
        className = 'cm-dp-token-tag'
      }
      add(from, to, Decoration.mark({ class: className }))
    }
  })

  return ViewPlugin.fromClass(
    class {
      decorations: DecorationSet

      constructor(view: import('@codemirror/view').EditorView) {
        this.decorations = matcher.createDeco(view)
      }

      update(update: ViewUpdate) {
        this.decorations = matcher.updateDeco(update, this.decorations)
      }
    },
    {
      decorations: (plugin) => plugin.decorations
    }
  )
}
