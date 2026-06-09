const BRACE_LANGUAGES = new Set(['css', 'java', 'javascript', 'typescript'])

export function formatCodeBlockText(language: string, text: string): string {
  const normalizedLanguage = language.toLowerCase()
  const normalizedText = text.replace(/\r\n?/g, '\n')

  if (!normalizedText.trim()) {
    return normalizedText
  }

  if (normalizedLanguage === 'json') {
    return formatJson(normalizedText)
  }

  if (normalizedLanguage === 'html') {
    return formatHtml(normalizedText)
  }

  if (BRACE_LANGUAGES.has(normalizedLanguage)) {
    return formatBraceLanguage(normalizedText)
  }

  return trimTrailingWhitespace(normalizedText)
}

function formatJson(text: string): string {
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

function formatHtml(text: string): string {
  const lines = text
    .trim()
    .replace(/>\s*</g, '>\n<')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)

  let indent = 0
  return lines
    .map((line) => {
      if (/^<\//.test(line)) {
        indent = Math.max(0, indent - 1)
      }
      const formatted = `${'  '.repeat(indent)}${line}`
      if (/^<[^!?/][^>]*[^/]>\s*$/.test(line) && !/^<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)\b/i.test(line)) {
        indent += 1
      }
      return formatted
    })
    .join('\n')
}

function formatBraceLanguage(text: string): string {
  return indentBraceLines(insertBraceNewlines(text))
}

function insertBraceNewlines(text: string): string {
  let output = ''
  let quote: '"' | "'" | '`' | null = null
  let escaped = false
  let inLineComment = false
  let inBlockComment = false

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index]
    const next = text[index + 1]

    if (inLineComment) {
      output += char
      if (char === '\n') {
        inLineComment = false
      }
      continue
    }

    if (inBlockComment) {
      output += char
      if (char === '*' && next === '/') {
        output += next
        index += 1
        inBlockComment = false
      }
      continue
    }

    if (quote) {
      output += char
      if (escaped) {
        escaped = false
      } else if (char === '\\') {
        escaped = true
      } else if (char === quote) {
        quote = null
      }
      continue
    }

    if (char === '/' && next === '/') {
      output = trimLineEnd(output) + '//'
      index += 1
      inLineComment = true
      continue
    }

    if (char === '/' && next === '*') {
      output = trimLineEnd(output) + '/*'
      index += 1
      inBlockComment = true
      continue
    }

    if (char === '"' || char === "'" || char === '`') {
      quote = char
      output += char
      continue
    }

    if (char === '{') {
      output = trimLineEnd(output) + ' {\n'
      continue
    }

    if (char === '}') {
      output = `${trimLineEnd(output)}\n}\n`
      continue
    }

    if (char === ';') {
      output = `${trimLineEnd(output)};\n`
      continue
    }

    if (char === '\n') {
      output = `${trimLineEnd(output)}\n`
      continue
    }

    output += char
  }

  return output
}

function indentBraceLines(text: string): string {
  const lines = text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)

  let indent = 0
  return lines
    .map((line) => {
      if (line.startsWith('}')) {
        indent = Math.max(0, indent - 1)
      }
      const formatted = `${'  '.repeat(indent)}${line}`
      if (line.endsWith('{')) {
        indent += 1
      }
      return formatted
    })
    .join('\n')
}

function trimTrailingWhitespace(text: string): string {
  return text
    .split('\n')
    .map((line) => line.trimEnd())
    .join('\n')
}

function trimLineEnd(text: string): string {
  return text.replace(/[ \t]+$/g, '')
}
