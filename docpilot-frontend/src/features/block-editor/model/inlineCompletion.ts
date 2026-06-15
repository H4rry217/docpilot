import { Extension, type Editor } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import type { InlineCompletionShape } from '../../inline-completion/api/inlineCompletionApi'
import { markdownToHtml, markdownToInlineHtml } from './markdown'

type ProseMirrorDoc = Parameters<typeof DecorationSet.create>[0]

export type InlineCompletionCandidateSuggestion = {
  index: number
  markdown: string
  previewText: string
}

export type InlineCompletionSuggestion = {
  requestSeq: number
  from: number
  to: number
  shape: InlineCompletionShape
  candidates: InlineCompletionCandidateSuggestion[]
  selectedIndex: number
  menuOpen: boolean
}

type InlineCompletionPluginState = {
  suggestion: InlineCompletionSuggestion | null
  decorations: DecorationSet
}

type InlineCompletionMeta =
  | { type: 'set'; suggestion: InlineCompletionSuggestion }
  | { type: 'select'; requestSeq: number; delta?: number; index?: number }
  | { type: 'clear'; requestSeq?: number }

export const inlineCompletionPluginKey = new PluginKey<InlineCompletionPluginState>(
  'docpilotInlineCompletion'
)

export const DocpilotInlineCompletion = Extension.create({
  name: 'docpilotInlineCompletion',
  priority: 1002,

  addProseMirrorPlugins() {
    return [
      new Plugin<InlineCompletionPluginState>({
        key: inlineCompletionPluginKey,
        state: {
          init: () => emptyInlineCompletionState(),
          apply: (transaction, previousState, _oldState, nextState) => {
            const meta = transaction.getMeta(inlineCompletionPluginKey) as InlineCompletionMeta | undefined
            if (meta?.type === 'set') {
              return stateFromSuggestion(nextState.doc, normalizeSuggestion(meta.suggestion))
            }
            if (meta?.type === 'select') {
              const previousSuggestion = previousState.suggestion
              if (!previousSuggestion || previousSuggestion.requestSeq !== meta.requestSeq) {
                return previousState
              }
              const nextIndex = meta.index ?? previousSuggestion.selectedIndex + (meta.delta ?? 0)
              return stateFromSuggestion(nextState.doc, normalizeSuggestion({
                ...previousSuggestion,
                selectedIndex: wrapCandidateIndex(
                  nextIndex,
                  previousSuggestion.candidates.length
                ),
                menuOpen: true
              }))
            }
            if (meta?.type === 'clear') {
              if (meta.requestSeq == null || previousState.suggestion?.requestSeq === meta.requestSeq) {
                return emptyInlineCompletionState()
              }
              return previousState
            }
            if ((transaction.docChanged || transaction.selectionSet) && previousState.suggestion) {
              return emptyInlineCompletionState()
            }
            return previousState
          }
        },
        props: {
          decorations: (state) => inlineCompletionPluginKey.getState(state)?.decorations ?? DecorationSet.empty,
          handleKeyDown: (_view, event) => {
            const suggestion = getInlineCompletionSuggestion(this.editor)
            if (event.key === 'Escape' && suggestion) {
              event.preventDefault()
              clearInlineCompletion(this.editor)
              return true
            }
            if (shouldSelectNextCandidate(event, suggestion)) {
              event.preventDefault()
              return selectInlineCompletionCandidate(this.editor, 1)
            }
            if (shouldSelectPreviousCandidate(event, suggestion)) {
              event.preventDefault()
              return selectInlineCompletionCandidate(this.editor, -1)
            }
            if (shouldAcceptCompletion(event, suggestion)) {
              event.preventDefault()
              return acceptInlineCompletion(this.editor)
            }
            return false
          },
          handleDOMEvents: {
            mousedown: (view, event) => {
              const item = inlineCompletionMenuItem(event.target)
              if (!item) return false
              const suggestion = inlineCompletionPluginKey.getState(view.state)?.suggestion
              if (!suggestion || Number(item.dataset.docpilotInlineCompletionRequestSeq) !== suggestion.requestSeq) {
                return false
              }
              event.preventDefault()
              view.dispatch(
                view.state.tr.setMeta(inlineCompletionPluginKey, {
                  type: 'select',
                  requestSeq: suggestion.requestSeq,
                  index: Number(item.dataset.docpilotInlineCompletionIndex)
                } satisfies InlineCompletionMeta)
              )
              return true
            }
          }
        }
      })
    ]
  }
})

export function setInlineCompletionSuggestion(editor: Editor, suggestion: InlineCompletionSuggestion): void {
  editor.view.dispatch(
    editor.state.tr.setMeta(inlineCompletionPluginKey, {
      type: 'set',
      suggestion
    } satisfies InlineCompletionMeta)
  )
}

export function selectInlineCompletionCandidate(
  editor: Editor,
  delta: number
): boolean {
  const suggestion = getInlineCompletionSuggestion(editor)
  if (!suggestion || suggestion.candidates.length <= 1) return false
  editor.view.dispatch(
    editor.state.tr.setMeta(inlineCompletionPluginKey, {
      type: 'select',
      requestSeq: suggestion.requestSeq,
      delta
    } satisfies InlineCompletionMeta)
  )
  return true
}

export function clearInlineCompletion(editor: Editor, requestSeq?: number): void {
  editor.view.dispatch(
    editor.state.tr.setMeta(inlineCompletionPluginKey, {
      type: 'clear',
      requestSeq
    } satisfies InlineCompletionMeta)
  )
}

export function getInlineCompletionSuggestion(editor: Editor): InlineCompletionSuggestion | null {
  return inlineCompletionPluginKey.getState(editor.state)?.suggestion ?? null
}

export function acceptInlineCompletion(editor: Editor): boolean {
  const suggestion = getInlineCompletionSuggestion(editor)
  const candidate = selectedCandidate(suggestion)
  if (!suggestion || !candidate?.markdown) return false

  if (suggestion.shape === 'CODE_LINE') {
    editor.view.dispatch(
      editor.state.tr
        .insertText(candidate.markdown, suggestion.from, suggestion.to)
        .scrollIntoView()
    )
    return true
  }

  const blockCandidate = isBlockCompletionCandidate(suggestion.shape, candidate)
  const markdown = normalizeCompletionMarkdown(candidate.markdown, suggestion.shape, blockCandidate)
  const html = blockCandidate
    ? markdownToHtml(markdown)
    : markdownToInlineHtml(markdown)

  if (blockCandidate) {
    return editor.chain()
      .focus()
      .insertContentAt(blockInsertionPosition(editor.state.doc, suggestion.to), html)
      .run()
  }

  return editor.chain().focus().insertContent(html).run()
}

function stateFromSuggestion(doc: ProseMirrorDoc, suggestion: InlineCompletionSuggestion) {
  return {
    suggestion,
    decorations: inlineCompletionDecorations(doc, suggestion)
  }
}

function emptyInlineCompletionState(): InlineCompletionPluginState {
  return {
    suggestion: null,
    decorations: DecorationSet.empty
  }
}

function inlineCompletionDecorations(
  doc: ProseMirrorDoc,
  suggestion: InlineCompletionSuggestion
): DecorationSet {
  const candidate = selectedCandidate(suggestion)
  if (!candidate?.previewText) return DecorationSet.empty
  const blockCandidate = isBlockCompletionCandidate(suggestion.shape, candidate)
  const position = blockCandidate
    ? blockInsertionPosition(doc, suggestion.to)
    : Math.max(0, Math.min(suggestion.to, doc.content.size))

  return DecorationSet.create(doc, [
    Decoration.widget(
      position,
      () => {
        const wrapper = document.createElement(blockCandidate ? 'div' : 'span')
        wrapper.className = blockCandidate
          ? 'docpilot-inline-completion-widget is-block'
          : 'docpilot-inline-completion-widget'
        wrapper.contentEditable = 'false'
        wrapper.dataset.docpilotInlineCompletionWidget = 'true'
        const ghost = document.createElement(blockCandidate ? 'div' : 'span')
        ghost.className = 'docpilot-inline-completion-ghost'
        renderGhostPreview(ghost, candidate, blockCandidate)
        wrapper.appendChild(ghost)
        if (suggestion.menuOpen && suggestion.candidates.length > 1) {
          wrapper.appendChild(candidateMenu(suggestion))
        }
        return wrapper
      },
      { side: 1 }
    )
  ])
}

function candidateMenu(suggestion: InlineCompletionSuggestion): HTMLElement {
  const menu = document.createElement('span')
  menu.className = 'docpilot-inline-completion-menu'
  menu.setAttribute('role', 'listbox')
  menu.setAttribute('aria-label', 'Inline completion candidates')
  suggestion.candidates.forEach((candidate, index) => {
    const item = document.createElement('span')
    item.className = index === suggestion.selectedIndex
      ? 'docpilot-inline-completion-menu-item is-selected'
      : 'docpilot-inline-completion-menu-item'
    item.dataset.docpilotInlineCompletionIndex = String(index)
    item.dataset.docpilotInlineCompletionRequestSeq = String(suggestion.requestSeq)
    item.setAttribute('role', 'option')
    item.setAttribute('aria-selected', index === suggestion.selectedIndex ? 'true' : 'false')
    item.title = candidate.previewText

    const badge = document.createElement('span')
    badge.className = 'docpilot-inline-completion-menu-index'
    badge.textContent = String(index + 1)
    item.appendChild(badge)

    const text = document.createElement('span')
    text.className = 'docpilot-inline-completion-menu-text'
    text.textContent = candidate.previewText
    item.appendChild(text)
    menu.appendChild(item)
  })
  return menu
}

function normalizeSuggestion(suggestion: InlineCompletionSuggestion): InlineCompletionSuggestion {
  const candidates = suggestion.candidates.filter((candidate) => candidate.markdown)
  return {
    ...suggestion,
    candidates,
    selectedIndex: Math.max(0, Math.min(suggestion.selectedIndex, Math.max(0, candidates.length - 1))),
    menuOpen: suggestion.menuOpen && candidates.length > 1
  }
}

function selectedCandidate(suggestion: InlineCompletionSuggestion | null): InlineCompletionCandidateSuggestion | null {
  if (!suggestion) return null
  return suggestion.candidates[suggestion.selectedIndex] ?? null
}

function shouldSelectNextCandidate(
  event: KeyboardEvent,
  suggestion: InlineCompletionSuggestion | null
): boolean {
  return isArrowDown(event)
    && hasSelectableCandidateMenu(suggestion)
    && (event.altKey || suggestion?.menuOpen === true)
}

function shouldSelectPreviousCandidate(
  event: KeyboardEvent,
  suggestion: InlineCompletionSuggestion | null
): boolean {
  return isArrowUp(event)
    && hasSelectableCandidateMenu(suggestion)
    && (event.altKey || suggestion?.menuOpen === true)
}

function hasSelectableCandidateMenu(suggestion: InlineCompletionSuggestion | null): boolean {
  return Boolean(suggestion && suggestion.candidates.length > 1)
}

function shouldAcceptCompletion(
  event: KeyboardEvent,
  suggestion: InlineCompletionSuggestion | null
): boolean {
  if (!suggestion || event.isComposing || event.keyCode === 229) {
    return false
  }
  if (event.key === 'Tab') {
    return true
  }
  return event.key === 'Enter'
    && !event.shiftKey
    && !event.altKey
    && !event.ctrlKey
    && !event.metaKey
}

function isArrowDown(event: KeyboardEvent): boolean {
  return event.key === 'ArrowDown' || event.key === 'Down'
}

function isArrowUp(event: KeyboardEvent): boolean {
  return event.key === 'ArrowUp' || event.key === 'Up'
}

function inlineCompletionMenuItem(target: EventTarget | null): HTMLElement | null {
  if (!(target instanceof Element)) return null
  const item = target.closest('[data-docpilot-inline-completion-index]')
  return item instanceof HTMLElement ? item : null
}

function wrapCandidateIndex(index: number, length: number): number {
  if (length <= 0) return 0
  return (index % length + length) % length
}

function isInlineShape(shape: InlineCompletionShape): boolean {
  return shape === 'SHORT' || shape === 'SENTENCE' || shape === 'TABLE_CELL'
}

function isBlockCompletionCandidate(
  shape: InlineCompletionShape,
  candidate: InlineCompletionCandidateSuggestion
): boolean {
  if (shape === 'CODE_LINE') {
    return false
  }
  if (!isInlineShape(shape)) {
    return true
  }
  return hasBlockMarkdown(candidate.markdown) || hasBlockPreview(candidate.previewText)
}

function hasBlockMarkdown(markdown: string): boolean {
  const decoded = decodeHtmlEntities(markdown).replace(/\r\n?/g, '\n')
  return /\n\s*\n/.test(decoded)
    || /(^|\n)\s{0,3}#{1,6}\s+\S/.test(decoded)
    || /(^|\n)\s{0,3}(?:[-*+]|\d+[.)])\s+\S/.test(decoded)
    || /(^|\n)\s{0,3}>\s+\S/.test(decoded)
    || /(^|\n)\s{0,3}```/.test(decoded)
    || /(^|\n)\s*\|.+\|/.test(decoded)
}

function hasBlockPreview(previewText: string): boolean {
  return previewText.replace(/\r\n?/g, '\n').includes('\n')
}

function renderGhostPreview(
  container: HTMLElement,
  candidate: InlineCompletionCandidateSuggestion,
  blockCandidate: boolean
): void {
  if (!blockCandidate) {
    container.textContent = candidate.previewText
    return
  }
  const blocks = ghostPreviewBlocks(candidate.markdown, candidate.previewText)
  if (!blocks.length) {
    container.textContent = candidate.previewText
    return
  }
  blocks.forEach((block) => {
    const element = document.createElement('div')
    element.className = `docpilot-inline-completion-ghost-${block.kind}`
    element.textContent = block.text
    container.appendChild(element)
  })
}

function ghostPreviewBlocks(markdown: string, fallbackPreviewText: string): Array<{
  kind: 'heading' | 'line' | 'spacer'
  text: string
}> {
  const decoded = decodeHtmlEntities(markdown).replace(/\r\n?/g, '\n')
  const lines = decoded.split('\n')
  const blocks: Array<{ kind: 'heading' | 'line' | 'spacer'; text: string }> = []

  lines.forEach((line) => {
    if (!line.trim()) {
      if (blocks.length && blocks.at(-1)?.kind !== 'spacer') {
        blocks.push({ kind: 'spacer', text: '' })
      }
      return
    }

    const heading = line.match(/^\s{0,3}#{1,6}\s+(.+)$/)
    if (heading) {
      blocks.push({ kind: 'heading', text: heading[1]?.trim() ?? '' })
      return
    }

    blocks.push({
      kind: 'line',
      text: line
        .replace(/^\s{0,3}(?:[-*+]|\d+[.)])\s+/, '')
        .replace(/^\s{0,3}>\s+/, '')
    })
  })

  return blocks.filter((block) => block.kind === 'spacer' || block.text.trim())
    .length
    ? blocks
    : fallbackPreviewText.split(/\r\n?|\n/).map((line) => ({ kind: 'line', text: line }))
}

function blockInsertionPosition(doc: ProseMirrorDoc, position: number): number {
  const resolved = doc.resolve(Math.max(0, Math.min(position, doc.content.size)))
  for (let depth = resolved.depth; depth > 0; depth -= 1) {
    if (resolved.node(depth).isTextblock) {
      return resolved.after(depth)
    }
  }
  return resolved.pos
}

function normalizeCompletionMarkdown(
  markdown: string,
  shape: InlineCompletionShape,
  blockCandidate = false
): string {
  const decoded = decodeHtmlEntities(markdown)
  if (blockCandidate || !isInlineShape(shape)) {
    return decoded
  }
  return decoded
    .replace(/^\s*(?:>\s*)+/gm, '')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\d+[.)]\s+/gm, '')
}

function decodeHtmlEntities(text: string): string {
  return text
    .replaceAll('&gt;', '>')
    .replaceAll('&lt;', '<')
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
    .replaceAll('&amp;', '&')
}
