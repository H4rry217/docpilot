import type { BlockType } from '../../../entities/block/types'

export type JsonAttrs = Record<string, unknown>

const HTML_DISPLAY_MODES = new Set(['fixed', 'fit', 'auto'])

// Normalizes canonical block attrs before they enter the editor document.
export function normalizeBlockAttrsForProseMirror(type: BlockType, attrs: JsonAttrs, blockId: string): JsonAttrs {
  switch (type) {
    case 'HEADING':
      return { ...extraAttrs(attrs, ['level']), level: intAttr(attrs.level, 1, 1, 6) }
    case 'ORDERED_LIST':
      return { ...extraAttrs(attrs, ['start']), start: intAttr(attrs.start, 1, 1) }
    case 'TASK_LIST_ITEM':
      return { ...extraAttrs(attrs, ['checked']), checked: booleanAttr(attrs.checked, false) }
    case 'CODE_BLOCK':
      return {
        ...extraAttrs(attrs, ['language', 'text', 'wrapLines']),
        language: stringAttr(attrs.language, '')
      }
    case 'TABLE_CELL':
      return {
        ...extraAttrs(attrs, ['header', 'alignment']),
        header: booleanAttr(attrs.header, false),
        alignment: alignmentAttr(attrs.alignment)
      }
    case 'HTML_BLOCK':
      return htmlBlockAttrs(attrs, blockId)
    default:
      return { ...attrs }
  }
}

// Normalizes editor attrs before they are written back to the canonical block model.
export function normalizeBlockAttrsForCanonical(type: BlockType, attrs: JsonAttrs, blockId: string): JsonAttrs {
  switch (type) {
    case 'HEADING':
      return { ...extraAttrs(attrs, ['level']), level: intAttr(attrs.level, 1, 1, 6) }
    case 'ORDERED_LIST':
      return { ...extraAttrs(attrs, ['start']), start: intAttr(attrs.start, 1, 1) }
    case 'TASK_LIST_ITEM':
      return { ...extraAttrs(attrs, ['checked']), checked: booleanAttr(attrs.checked, false) }
    case 'CODE_BLOCK':
      return {
        ...extraAttrs(attrs, ['language', 'text', 'wrapLines']),
        language: stringAttr(attrs.language, ''),
        text: stringAttr(attrs.text, '')
      }
    case 'TABLE_CELL':
      return {
        ...extraAttrs(attrs, ['header', 'alignment']),
        header: booleanAttr(attrs.header, false),
        alignment: alignmentAttr(attrs.alignment)
      }
    case 'HTML_BLOCK':
      return htmlBlockAttrs(attrs, blockId)
    default:
      return { ...attrs }
  }
}

// Removes editor-only attrs that should not be persisted in canonical block attrs.
export function stripInternalAttrs(attrs: JsonAttrs): JsonAttrs {
  const next = { ...attrs }
  delete next.blockId
  delete next.sourceRange
  return next
}

// Reads a string value from either a raw attr value or an attrs object.
export function stringAttr(value: unknown, fallback: string): string
export function stringAttr(attrs: JsonAttrs | undefined, key: string, fallback: string): string
export function stringAttr(valueOrAttrs: unknown, keyOrFallback: string, fallback?: string): string {
  if (fallback !== undefined) {
    const value = isAttrs(valueOrAttrs) ? valueOrAttrs[keyOrFallback] : undefined
    return typeof value === 'string' ? value : fallback
  }
  return typeof valueOrAttrs === 'string' ? valueOrAttrs : keyOrFallback
}

// Reads an integer attr, rejecting non-integer strings such as "2px".
export function intAttr(value: unknown, fallback: number, min = Number.MIN_SAFE_INTEGER, max = Number.MAX_SAFE_INTEGER): number {
  const number =
    typeof value === 'number' ? value : typeof value === 'string' && /^-?\d+$/.test(value.trim()) ? Number.parseInt(value, 10) : Number.NaN
  if (!Number.isFinite(number)) return fallback
  const rounded = Math.trunc(number)
  return rounded < min || rounded > max ? fallback : rounded
}

// Reads a boolean attr while accepting legacy string values from editor DOM attrs.
export function booleanAttr(value: unknown, fallback = false): boolean {
  if (typeof value === 'boolean') return value
  if (value === 'true') return true
  if (value === 'false') return false
  return fallback
}

function htmlBlockAttrs(attrs: JsonAttrs, blockId: string): JsonAttrs {
  return {
    ...extraAttrs(attrs, ['id', 'title', 'source', 'displayMode', 'fixedHeightPx', 'allowScripts']),
    id: stringAttr(attrs.id, blockId),
    title: stringAttr(attrs.title, 'HTML'),
    source: stringAttr(attrs.source, ''),
    displayMode: htmlDisplayModeAttr(attrs.displayMode),
    fixedHeightPx: intAttr(attrs.fixedHeightPx, 320, 120, 1600),
    allowScripts: booleanAttr(attrs.allowScripts, false)
  }
}

function htmlDisplayModeAttr(value: unknown): 'fixed' | 'auto' {
  if (typeof value !== 'string') return 'fixed'
  const normalized = value.toLowerCase()
  return normalized === 'auto' ? 'auto' : HTML_DISPLAY_MODES.has(normalized) ? 'fixed' : 'fixed'
}

function alignmentAttr(value: unknown): 'none' | 'left' | 'center' | 'right' {
  if (value === 'left' || value === 'center' || value === 'right') return value
  return 'none'
}

function extraAttrs(attrs: JsonAttrs, knownKeys: string[]): JsonAttrs {
  const next = { ...attrs }
  knownKeys.forEach((key) => {
    delete next[key]
  })
  return next
}

function isAttrs(value: unknown): value is JsonAttrs {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}
