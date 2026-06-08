import type { NodeViewProps } from '@tiptap/react'
import type { CSSProperties } from 'react'

export type ResizeCorner = {
  x: -1 | 1
  y: -1 | 1
}

export const MIN_SPECIAL_BLOCK_WIDTH = 240
export const MAX_SPECIAL_BLOCK_WIDTH = 720

export function stringAttr(attrs: Record<string, unknown>, name: string, fallback = ''): string {
  const value = attrs[name]
  return typeof value === 'string' ? value : fallback
}

export function codeBlockWidthAttr(value: unknown): number | null {
  const number = typeof value === 'number' ? value : typeof value === 'string' ? Number.parseInt(value, 10) : Number.NaN
  if (!Number.isFinite(number)) return null
  return clampSpecialBlockWidth(Math.trunc(number))
}

export function clampSpecialBlockWidth(width: number): number {
  return Math.max(MIN_SPECIAL_BLOCK_WIDTH, Math.min(MAX_SPECIAL_BLOCK_WIDTH, Math.round(width)))
}

function decorationAttrs(decoration: NodeViewProps['decorations'][number]): Record<string, unknown> {
  const typedDecoration = decoration as unknown as { type?: { attrs?: Record<string, unknown> } }
  return typedDecoration.type?.attrs ?? {}
}

export function selectionStyleFromDecoration(styleValue: string): CSSProperties {
  const style: CSSProperties & Record<string, string> = {}
  for (const declaration of styleValue.split(';')) {
    const separatorIndex = declaration.indexOf(':')
    if (separatorIndex < 0) continue
    const property = declaration.slice(0, separatorIndex).trim()
    const value = declaration.slice(separatorIndex + 1).trim()
    if (!property.startsWith('--docpilot-selection-') || !value) continue
    style[property] = value
  }
  return style
}

export function blockSelectionDecoration(decorations: NodeViewProps['decorations']): {
  isSelected: boolean
  style: CSSProperties
} {
  let isSelected = false
  let style: CSSProperties = {}

  for (const decoration of decorations) {
    const attrs = decorationAttrs(decoration)
    const className = typeof attrs.class === 'string' ? attrs.class : ''
    if (!className.split(/\s+/).includes('docpilot-block-selected')) continue

    isSelected = true
    if (typeof attrs.style === 'string') {
      style = {
        ...style,
        ...selectionStyleFromDecoration(attrs.style)
      }
    }
  }

  return { isSelected, style }
}
