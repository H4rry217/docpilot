const MARKDOWN_EXTENSION_PATTERN = /\.(md|markdown)$/i

export function isMarkdownFileName(name: string): boolean {
  return MARKDOWN_EXTENSION_PATTERN.test(name.trim())
}

export function documentTitleFromMarkdownFileName(name: string): string {
  const title = name.trim().replace(MARKDOWN_EXTENSION_PATTERN, '').trim()
  return title || 'Untitled'
}

export function uniqueMarkdownNodeName(name: string, usedNames: Set<string>): string {
  const trimmed = name.trim()
  const fallback = trimmed || 'Untitled.md'
  const match = fallback.match(MARKDOWN_EXTENSION_PATTERN)
  const extension = match?.[0] ?? '.md'
  const baseName = match ? fallback.slice(0, -extension.length) : fallback
  let candidate = `${baseName || 'Untitled'}${extension}`
  let index = 1

  while (usedNames.has(candidate.toLowerCase())) {
    candidate = `${baseName || 'Untitled'} (${index})${extension}`
    index += 1
  }

  usedNames.add(candidate.toLowerCase())
  return candidate
}
