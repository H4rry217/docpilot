import { Extension, type Editor } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import type { InlineCompletionShape } from '../../inline-completion/api/inlineCompletionApi'
import { markdownToHtml, markdownToInlineHtml } from './markdown'

export type InlineCompletionSuggestion = {
  requestSeq: number
  from: number
  to: number
  markdown: string
  previewText: string
  shape: InlineCompletionShape
}

type InlineCompletionPluginState = {
  suggestion: InlineCompletionSuggestion | null
  decorations: DecorationSet
}

type InlineCompletionMeta =
  | { type: 'set'; suggestion: InlineCompletionSuggestion }
  | { type: 'append'; requestSeq: number; markdownDelta: string; previewText: string }
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
              return stateFromSuggestion(nextState.doc, meta.suggestion)
            }
            if (meta?.type === 'append') {
              const previousSuggestion = previousState.suggestion
              if (!previousSuggestion || previousSuggestion.requestSeq !== meta.requestSeq) {
                return previousState
              }
              return stateFromSuggestion(nextState.doc, {
                ...previousSuggestion,
                markdown: previousSuggestion.markdown + meta.markdownDelta,
                previewText: meta.previewText
              })
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
            if (event.key === 'Escape' && getInlineCompletionSuggestion(this.editor)) {
              event.preventDefault()
              clearInlineCompletion(this.editor)
              return true
            }
            if (event.key === 'Tab' && getInlineCompletionSuggestion(this.editor)) {
              event.preventDefault()
              return acceptInlineCompletion(this.editor)
            }
            return false
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

export function appendInlineCompletionDelta(
  editor: Editor,
  requestSeq: number,
  markdownDelta: string,
  previewText: string
): void {
  editor.view.dispatch(
    editor.state.tr.setMeta(inlineCompletionPluginKey, {
      type: 'append',
      requestSeq,
      markdownDelta,
      previewText
    } satisfies InlineCompletionMeta)
  )
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
  if (!suggestion || !suggestion.markdown) return false

  if (suggestion.shape === 'CODE_LINE') {
    editor.view.dispatch(
      editor.state.tr
        .insertText(suggestion.markdown, suggestion.from, suggestion.to)
        .scrollIntoView()
    )
    return true
  }

  const markdown = normalizeCompletionMarkdown(suggestion.markdown, suggestion.shape)
  const html = isInlineShape(suggestion.shape)
    ? markdownToInlineHtml(markdown)
    : markdownToHtml(markdown)
  return editor.chain().focus().insertContent(html).run()
}

function stateFromSuggestion(doc: Parameters<typeof DecorationSet.create>[0], suggestion: InlineCompletionSuggestion) {
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
  doc: Parameters<typeof DecorationSet.create>[0],
  suggestion: InlineCompletionSuggestion
): DecorationSet {
  if (!suggestion.previewText) return DecorationSet.empty
  const position = Math.max(0, Math.min(suggestion.to, doc.content.size))
  return DecorationSet.create(doc, [
    Decoration.widget(
      position,
      () => {
        const element = document.createElement('span')
        element.className = 'docpilot-inline-completion-ghost'
        element.textContent = suggestion.previewText
        return element
      },
      { side: 1 }
    )
  ])
}

function isInlineShape(shape: InlineCompletionShape): boolean {
  return shape === 'SHORT' || shape === 'SENTENCE' || shape === 'TABLE_CELL'
}

function normalizeCompletionMarkdown(markdown: string, shape: InlineCompletionShape): string {
  const decoded = decodeHtmlEntities(markdown)
  if (!isInlineShape(shape)) {
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
