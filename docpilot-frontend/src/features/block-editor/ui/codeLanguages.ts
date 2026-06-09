export type CodeLanguage = {
  value: string
  label: string
  keywords: string[]
  special?: boolean
}

export const LANGUAGE_OPTIONS: CodeLanguage[] = [
  {
    value: 'java',
    label: 'Java',
    keywords: [
      'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char', 'class', 'const', 'continue',
      'default', 'double', 'else', 'enum', 'extends', 'final', 'finally', 'float', 'for', 'if', 'implements',
      'import', 'instanceof', 'int', 'interface', 'long', 'new', 'package', 'private', 'protected', 'public',
      'return', 'short', 'static', 'strictfp', 'super', 'switch', 'synchronized', 'this', 'throw', 'throws',
      'transient', 'try', 'void', 'volatile', 'while', 'true', 'false', 'null', 'String'
    ]
  },
  {
    value: 'javascript',
    label: 'JavaScript',
    keywords: [
      'async', 'await', 'break', 'case', 'catch', 'class', 'const', 'continue', 'debugger', 'default', 'delete',
      'do', 'else', 'export', 'extends', 'finally', 'for', 'from', 'function', 'if', 'import', 'in', 'instanceof',
      'let', 'new', 'of', 'return', 'static', 'super', 'switch', 'this', 'throw', 'try', 'typeof', 'undefined',
      'var', 'void', 'while', 'yield', 'true', 'false', 'null'
    ]
  },
  {
    value: 'typescript',
    label: 'TypeScript',
    keywords: [
      'abstract', 'as', 'async', 'await', 'boolean', 'break', 'case', 'catch', 'class', 'const', 'continue',
      'declare', 'default', 'delete', 'do', 'else', 'enum', 'export', 'extends', 'finally', 'for', 'from',
      'function', 'if', 'implements', 'import', 'in', 'interface', 'let', 'module', 'namespace', 'new', 'number',
      'of', 'private', 'protected', 'public', 'readonly', 'return', 'static', 'string', 'super', 'switch', 'this',
      'throw', 'try', 'type', 'typeof', 'undefined', 'var', 'void', 'while', 'yield', 'true', 'false', 'null'
    ]
  },
  {
    value: 'python',
    label: 'Python',
    keywords: [
      'and', 'as', 'assert', 'async', 'await', 'break', 'class', 'continue', 'def', 'del', 'elif', 'else',
      'except', 'False', 'finally', 'for', 'from', 'global', 'if', 'import', 'in', 'is', 'lambda', 'None',
      'nonlocal', 'not', 'or', 'pass', 'raise', 'return', 'True', 'try', 'while', 'with', 'yield'
    ]
  },
  {
    value: 'html',
    label: 'HTML',
    keywords: ['html', 'head', 'body', 'div', 'section', 'article', 'main', 'span', 'a', 'img', 'button', 'script', 'style']
  },
  {
    value: 'css',
    label: 'CSS',
    keywords: ['align-items', 'background', 'border', 'color', 'display', 'flex', 'font-size', 'grid', 'height', 'margin', 'padding', 'position', 'width']
  },
  {
    value: 'json',
    label: 'JSON',
    keywords: ['true', 'false', 'null']
  },
  {
    value: 'yaml',
    label: 'YAML',
    keywords: ['true', 'false', 'null', 'yes', 'no', 'on', 'off']
  },
  {
    value: 'markdown',
    label: 'Markdown',
    keywords: []
  },
  {
    value: 'mermaid',
    label: 'Mermaid',
    keywords: ['graph', 'flowchart', 'TD', 'TB', 'BT', 'LR', 'RL', 'subgraph', 'end', 'classDef', 'class', 'style', 'linkStyle', 'click'],
    special: true
  },
  {
    value: 'text',
    label: 'Text',
    keywords: []
  }
]

const LANGUAGE_ALIASES: Record<string, string> = {
  js: 'javascript',
  ts: 'typescript',
  md: 'markdown',
  yml: 'yaml'
}

export function codeLanguage(value: string): CodeLanguage {
  const normalized = LANGUAGE_ALIASES[value.toLowerCase()] ?? value.toLowerCase()
  return LANGUAGE_OPTIONS.find((option) => option.value === normalized) ?? LANGUAGE_OPTIONS[LANGUAGE_OPTIONS.length - 1]
}
