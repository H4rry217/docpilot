export type CodeLanguage = {
  value: string
  label: string
  keywords: string[]
  caseInsensitive?: boolean
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
    value: 'go',
    label: 'Go',
    keywords: [
      'break', 'case', 'chan', 'const', 'continue', 'default', 'defer', 'else', 'fallthrough', 'for', 'func',
      'go', 'goto', 'if', 'import', 'interface', 'map', 'package', 'range', 'return', 'select', 'struct',
      'switch', 'type', 'var', 'true', 'false', 'nil', 'iota', 'string', 'int', 'error'
    ]
  },
  {
    value: 'rust',
    label: 'Rust',
    keywords: [
      'as', 'async', 'await', 'break', 'const', 'continue', 'crate', 'dyn', 'else', 'enum', 'extern', 'false',
      'fn', 'for', 'if', 'impl', 'in', 'let', 'loop', 'match', 'mod', 'move', 'mut', 'pub', 'ref', 'return',
      'self', 'Self', 'static', 'struct', 'super', 'trait', 'true', 'type', 'unsafe', 'use', 'where', 'while'
    ]
  },
  {
    value: 'c',
    label: 'C',
    keywords: [
      'auto', 'break', 'case', 'char', 'const', 'continue', 'default', 'do', 'double', 'else', 'enum', 'extern',
      'float', 'for', 'goto', 'if', 'inline', 'int', 'long', 'register', 'restrict', 'return', 'short', 'signed',
      'sizeof', 'static', 'struct', 'switch', 'typedef', 'union', 'unsigned', 'void', 'volatile', 'while'
    ]
  },
  {
    value: 'cpp',
    label: 'C++',
    keywords: [
      'alignas', 'alignof', 'auto', 'bool', 'break', 'case', 'catch', 'char', 'class', 'concept', 'const',
      'constexpr', 'continue', 'decltype', 'default', 'delete', 'do', 'double', 'else', 'enum', 'explicit',
      'export', 'extern', 'false', 'float', 'for', 'friend', 'if', 'inline', 'int', 'long', 'namespace', 'new',
      'noexcept', 'nullptr', 'operator', 'private', 'protected', 'public', 'return', 'short', 'signed', 'sizeof',
      'static', 'struct', 'switch', 'template', 'this', 'throw', 'true', 'try', 'typedef', 'typename', 'union',
      'unsigned', 'using', 'virtual', 'void', 'volatile', 'while'
    ]
  },
  {
    value: 'csharp',
    label: 'C#',
    keywords: [
      'abstract', 'as', 'async', 'await', 'base', 'bool', 'break', 'case', 'catch', 'class', 'const', 'continue',
      'decimal', 'default', 'delegate', 'do', 'double', 'else', 'enum', 'event', 'explicit', 'extern', 'false',
      'finally', 'fixed', 'float', 'for', 'foreach', 'if', 'implicit', 'in', 'int', 'interface', 'internal',
      'is', 'lock', 'long', 'namespace', 'new', 'null', 'object', 'operator', 'out', 'override', 'params',
      'private', 'protected', 'public', 'readonly', 'ref', 'return', 'sealed', 'short', 'sizeof', 'stackalloc',
      'static', 'string', 'struct', 'switch', 'this', 'throw', 'true', 'try', 'typeof', 'uint', 'ulong',
      'unchecked', 'unsafe', 'ushort', 'using', 'virtual', 'void', 'volatile', 'while'
    ]
  },
  {
    value: 'kotlin',
    label: 'Kotlin',
    keywords: [
      'abstract', 'actual', 'annotation', 'as', 'break', 'by', 'catch', 'class', 'companion', 'const',
      'constructor', 'continue', 'data', 'do', 'else', 'enum', 'expect', 'false', 'finally', 'for', 'fun',
      'if', 'import', 'in', 'interface', 'internal', 'is', 'lateinit', 'null', 'object', 'open', 'operator',
      'out', 'override', 'package', 'private', 'protected', 'public', 'return', 'sealed', 'super', 'suspend',
      'this', 'throw', 'true', 'try', 'typealias', 'val', 'var', 'when', 'where', 'while'
    ]
  },
  {
    value: 'swift',
    label: 'Swift',
    keywords: [
      'Any', 'as', 'associatedtype', 'break', 'case', 'catch', 'class', 'continue', 'default', 'defer', 'deinit',
      'do', 'else', 'enum', 'extension', 'fallthrough', 'false', 'fileprivate', 'for', 'func', 'guard', 'if',
      'import', 'in', 'init', 'inout', 'internal', 'is', 'let', 'nil', 'open', 'operator', 'private', 'protocol',
      'public', 'repeat', 'return', 'self', 'Self', 'static', 'struct', 'subscript', 'super', 'switch', 'throw',
      'throws', 'true', 'try', 'typealias', 'var', 'where', 'while'
    ]
  },
  {
    value: 'php',
    label: 'PHP',
    keywords: [
      'abstract', 'and', 'array', 'as', 'break', 'callable', 'case', 'catch', 'class', 'clone', 'const',
      'continue', 'declare', 'default', 'die', 'do', 'echo', 'else', 'elseif', 'empty', 'enddeclare', 'endfor',
      'endforeach', 'endif', 'endswitch', 'endwhile', 'eval', 'exit', 'extends', 'final', 'finally', 'fn',
      'for', 'foreach', 'function', 'global', 'goto', 'if', 'implements', 'include', 'include_once',
      'instanceof', 'insteadof', 'interface', 'isset', 'list', 'match', 'namespace', 'new', 'or', 'print',
      'private', 'protected', 'public', 'readonly', 'require', 'require_once', 'return', 'static', 'switch',
      'throw', 'trait', 'try', 'unset', 'use', 'var', 'while', 'xor', 'yield', 'true', 'false', 'null'
    ]
  },
  {
    value: 'ruby',
    label: 'Ruby',
    keywords: [
      'BEGIN', 'END', 'alias', 'and', 'begin', 'break', 'case', 'class', 'def', 'defined?', 'do', 'else',
      'elsif', 'end', 'ensure', 'false', 'for', 'if', 'in', 'module', 'next', 'nil', 'not', 'or', 'redo',
      'rescue', 'retry', 'return', 'self', 'super', 'then', 'true', 'undef', 'unless', 'until', 'when',
      'while', 'yield'
    ]
  },
  {
    value: 'sql',
    label: 'SQL',
    caseInsensitive: true,
    keywords: [
      'add', 'all', 'alter', 'and', 'as', 'asc', 'avg', 'begin', 'between', 'by', 'case', 'check', 'column',
      'commit', 'constraint', 'count', 'create', 'cross', 'database', 'default', 'delete', 'desc', 'distinct',
      'drop', 'else', 'end', 'exists', 'false', 'foreign', 'from', 'full', 'grant', 'group', 'having', 'in',
      'index', 'inner', 'insert', 'into', 'is', 'join', 'key', 'left', 'like', 'limit', 'max', 'min', 'not',
      'null', 'offset', 'on', 'or', 'order', 'outer', 'primary', 'references', 'returning', 'revoke', 'right',
      'rollback', 'schema', 'select', 'set', 'sum', 'table', 'then', 'transaction', 'true', 'union', 'unique',
      'update', 'values', 'view', 'when', 'where', 'with'
    ]
  },
  {
    value: 'html',
    label: 'HTML',
    keywords: ['html', 'head', 'body', 'div', 'section', 'article', 'main', 'span', 'a', 'img', 'button', 'script', 'style']
  },
  {
    value: 'xml',
    label: 'XML',
    keywords: ['xml', 'version', 'encoding', 'standalone', 'xmlns']
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
    value: 'bash',
    label: 'Shell',
    keywords: [
      'alias', 'bg', 'break', 'case', 'cd', 'command', 'continue', 'do', 'done', 'echo', 'elif', 'else', 'esac',
      'eval', 'exec', 'exit', 'export', 'false', 'fg', 'fi', 'for', 'function', 'getopts', 'if', 'in', 'jobs',
      'let', 'local', 'printf', 'pwd', 'read', 'readonly', 'return', 'select', 'set', 'shift', 'source', 'test',
      'then', 'time', 'trap', 'true', 'type', 'ulimit', 'umask', 'unalias', 'unset', 'until', 'while'
    ]
  },
  {
    value: 'powershell',
    label: 'PowerShell',
    caseInsensitive: true,
    keywords: [
      'begin', 'break', 'catch', 'class', 'continue', 'data', 'define', 'do', 'dynamicparam', 'else', 'elseif',
      'end', 'exit', 'filter', 'finally', 'for', 'foreach', 'from', 'function', 'if', 'in', 'param', 'process',
      'return', 'switch', 'throw', 'trap', 'try', 'until', 'using', 'var', 'while', 'Get-ChildItem',
      'Set-Location', 'Where-Object', 'Select-Object', 'ForEach-Object', 'Write-Host', 'Write-Output'
    ]
  },
  {
    value: 'dockerfile',
    label: 'Dockerfile',
    caseInsensitive: true,
    keywords: [
      'add', 'arg', 'cmd', 'copy', 'entrypoint', 'env', 'expose', 'from', 'healthcheck', 'label', 'maintainer',
      'onbuild', 'run', 'shell', 'stopsignal', 'user', 'volume', 'workdir'
    ]
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
  cplusplus: 'cpp',
  'c++': 'cpp',
  cc: 'cpp',
  cs: 'csharp',
  'c#': 'csharp',
  docker: 'dockerfile',
  dockerfile: 'dockerfile',
  golang: 'go',
  htm: 'html',
  jsx: 'javascript',
  js: 'javascript',
  jsonc: 'json',
  kt: 'kotlin',
  kts: 'kotlin',
  ts: 'typescript',
  tsx: 'typescript',
  md: 'markdown',
  mysql: 'sql',
  pgsql: 'sql',
  postgresql: 'sql',
  postgres: 'sql',
  ps1: 'powershell',
  py: 'python',
  rb: 'ruby',
  rs: 'rust',
  sh: 'bash',
  shell: 'bash',
  sqlite: 'sql',
  tsql: 'sql',
  yml: 'yaml',
  zsh: 'bash'
}

export function codeLanguage(value: string): CodeLanguage {
  const normalizedValue = value.trim().toLowerCase()
  const normalized = LANGUAGE_ALIASES[normalizedValue] ?? normalizedValue
  return LANGUAGE_OPTIONS.find((option) => option.value === normalized) ?? LANGUAGE_OPTIONS[LANGUAGE_OPTIONS.length - 1]
}
