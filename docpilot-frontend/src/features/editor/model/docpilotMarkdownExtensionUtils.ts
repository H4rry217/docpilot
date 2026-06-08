import { mergeAttributes } from '@tiptap/core'
import type { DOMOutputSpec } from '@tiptap/pm/model'

export type HtmlAttrs = Record<string, unknown>

export const blockAttributes = {
  blockId: { default: '' },
  sourceRange: { default: null },
  format: { default: '' },
  raw: { default: '' },
  data: { default: null },
  notation: { default: '' },
  text: { default: '' },
  delimiter: { default: '' },
  engine: { default: '' },
  kind: { default: '' },
  title: { default: '' },
  collapsible: { default: false },
  open: { default: true },
  label: { default: '' },
  href: { default: '' },
  source: { default: '' },
  nodeType: { default: '' }
}

export const inlineAttributes = {
  sourceRange: { default: null },
  text: { default: '' },
  notation: { default: '' },
  delimiter: { default: '' },
  label: { default: '' },
  source: { default: '' },
  shortcut: { default: '' },
  nodeType: { default: '' }
}

export function renderedAttrs(attributes: HtmlAttrs, extra: HtmlAttrs = {}) {
  const attrs = { ...attributes }
  const blockId = attrs.blockId
  delete attrs.blockId
  delete attrs.sourceRange
  delete attrs.nodeType

  return mergeAttributes(
    attrs,
    typeof blockId === 'string' && blockId ? { 'data-block-id': blockId } : {},
    extra
  )
}

export function renderedHiddenBlockAttrs(attributes: HtmlAttrs, extra: HtmlAttrs = {}) {
  const blockId = attributes.blockId

  return mergeAttributes(
    typeof blockId === 'string' && blockId ? { 'data-block-id': blockId } : {},
    extra
  )
}

export function stringAttr(attributes: HtmlAttrs, key: string, fallback = ''): string {
  const value = attributes[key]
  return typeof value === 'string' ? value : fallback
}

export function booleanAttr(value: unknown, fallback = false): boolean {
  if (typeof value === 'boolean') return value
  if (typeof value === 'string') return value === 'true'
  return fallback
}

export function footnoteAnchorToken(label: string): string {
  const value = label.trim() || 'fn'
  const token = Array.from(value)
    .map((character) => (/^[A-Za-z0-9_-]$/.test(character)
      ? character
      : `-${character.codePointAt(0)?.toString(16) ?? 'x'}-`))
    .join('')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')

  return token || 'fn'
}

export function footnoteDefinitionId(label: string): string {
  return `docpilot-footnote-${footnoteAnchorToken(label)}`
}

export function footnoteReferenceId(label: string): string {
  return `docpilot-footnote-ref-${footnoteAnchorToken(label)}`
}

export function renderedCalloutAttrs(attributes: HtmlAttrs, extra: HtmlAttrs = {}) {
  const attrs = { ...attributes }
  delete attrs.kind
  delete attrs.title
  delete attrs.collapsible
  delete attrs.open
  return renderedAttrs(attrs, extra)
}

export function renderLeafBlock(label: string, attributes: HtmlAttrs): DOMOutputSpec {
  const text = stringAttr(attributes, 'text') || stringAttr(attributes, 'source') || stringAttr(attributes, 'raw')
  return [
    'div',
    renderedAttrs(attributes, { class: 'docpilot-block docpilot-leaf-block' }),
    ['span', { class: 'docpilot-block-label' }, label],
    text ? ['pre', { class: 'docpilot-block-source' }, text] : ['span', { class: 'docpilot-block-empty' }, label]
  ]
}

export function renderInlineToken(label: string, attributes: HtmlAttrs): DOMOutputSpec {
  const text =
    stringAttr(attributes, 'text') ||
    stringAttr(attributes, 'label') ||
    stringAttr(attributes, 'shortcut') ||
    stringAttr(attributes, 'source') ||
    label

  return ['span', renderedAttrs(attributes, { class: 'docpilot-inline-token' }), text]
}

export function renderedSourceAttrs(attributes: HtmlAttrs, hiddenKeys: string[], extra: HtmlAttrs = {}) {
  const attrs = { ...attributes }
  hiddenKeys.forEach((key) => {
    delete attrs[key]
  })
  return renderedAttrs(attrs, extra)
}

export function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

export function setDomAttributes(element: HTMLElement, attributes: HtmlAttrs) {
  const nextNames = new Set(Object.keys(attributes))
  for (const attribute of Array.from(element.attributes)) {
    if (attribute.name === 'open') continue
    if (!nextNames.has(attribute.name)) {
      element.removeAttribute(attribute.name)
    }
  }

  for (const [name, value] of Object.entries(attributes)) {
    if (value === false || value === null || value === undefined) {
      element.removeAttribute(name)
    } else if (value === true) {
      element.setAttribute(name, '')
    } else {
      element.setAttribute(name, String(value))
    }
  }
}
