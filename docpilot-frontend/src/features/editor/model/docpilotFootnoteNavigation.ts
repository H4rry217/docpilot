import { Extension } from '@tiptap/core'
import { Plugin } from '@tiptap/pm/state'
import type { EditorView } from '@tiptap/pm/view'

const FOOTNOTE_TARGET_CLASS = 'is-footnote-target'
const FOOTNOTE_TARGET_HIGHLIGHT_MS = 1800

function eventTargetElement(target: EventTarget | null): Element | null {
  if (target instanceof Element) return target
  if (target instanceof Node) return target.parentElement
  return null
}

function attributeSelectorValue(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}

function idFromHash(hash: string): string | null {
  if (!hash.startsWith('#')) return null
  try {
    return decodeURIComponent(hash.slice(1)) || null
  } catch {
    return hash.slice(1) || null
  }
}

function footnoteDefinitionElement(editorDom: HTMLElement, hash: string): HTMLElement | null {
  const id = idFromHash(hash)
  if (!id) return null
  return editorDom.querySelector<HTMLElement>(`.docpilot-footnote-definition[id="${attributeSelectorValue(id)}"]`)
}

export function handleFootnoteReferenceClick(view: EditorView, event: MouseEvent): boolean {
  if (event.button !== 0) return false

  const target = eventTargetElement(event.target)
  const anchor = target?.closest<HTMLAnchorElement>('.docpilot-footnote-ref a[href^="#docpilot-footnote-"]')
  if (!anchor || !view.dom.contains(anchor)) return false

  event.preventDefault()
  event.stopPropagation()

  const definition = footnoteDefinitionElement(view.dom, anchor.getAttribute('href') ?? '')
  if (!definition) return true

  definition.scrollIntoView({ block: 'center', behavior: 'smooth' })
  definition.classList.add(FOOTNOTE_TARGET_CLASS)
  window.setTimeout(() => {
    definition.classList.remove(FOOTNOTE_TARGET_CLASS)
  }, FOOTNOTE_TARGET_HIGHLIGHT_MS)
  return true
}

export const DocpilotFootnoteNavigation = Extension.create({
  name: 'docpilotFootnoteNavigation',
  priority: 1001,

  addProseMirrorPlugins() {
    return [
      new Plugin({
        props: {
          handleClick: (view, _position, event) => handleFootnoteReferenceClick(view, event)
        }
      })
    ]
  }
})
