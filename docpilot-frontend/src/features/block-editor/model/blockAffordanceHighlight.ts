import { Extension, type Editor } from '@tiptap/core'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import { blockIdentityId } from './docpilotBlockIdentity'

type BlockAffordanceHighlightPluginState = {
  decorations: DecorationSet
  highlightedBlocks: ReadonlyMap<string, BlockAffordanceHighlightDecoration>
}

const blockAffordanceHighlightPluginKey = new PluginKey<BlockAffordanceHighlightPluginState>(
  'docpilotBlockAffordanceHighlight'
)

export type BlockAffordanceHighlightDecoration = {
  blockId: string
  selectionLeft?: number
  selectionTop?: number
  selectionWidth?: number
  selectionHeight?: number
}

function normalizedHighlightMap(
  highlights: Iterable<BlockAffordanceHighlightDecoration>
): Map<string, BlockAffordanceHighlightDecoration> {
  const nextBlocks = new Map<string, BlockAffordanceHighlightDecoration>()
  for (const highlight of highlights) {
    if (highlight.blockId) nextBlocks.set(highlight.blockId, highlight)
  }
  return nextBlocks
}

function highlightStyle(highlight: BlockAffordanceHighlightDecoration): string | undefined {
  const styles: string[] = []
  if (Number.isFinite(highlight.selectionLeft)) {
    styles.push(`--docpilot-affordance-highlight-left: ${Math.round(highlight.selectionLeft ?? 0)}px`)
  }
  if (Number.isFinite(highlight.selectionTop)) {
    styles.push(`--docpilot-affordance-highlight-top: ${Math.round(highlight.selectionTop ?? 0)}px`)
  }
  if (Number.isFinite(highlight.selectionWidth)) {
    styles.push(`--docpilot-affordance-highlight-width: ${Math.round(highlight.selectionWidth ?? 0)}px`)
  }
  if (Number.isFinite(highlight.selectionHeight)) {
    styles.push(`--docpilot-affordance-highlight-height: ${Math.round(highlight.selectionHeight ?? 0)}px`)
  }
  return styles.length ? styles.join('; ') : undefined
}

function highlightDecorations(
  doc: ProseMirrorNode,
  highlightedBlocks: ReadonlyMap<string, BlockAffordanceHighlightDecoration>
): DecorationSet {
  if (!highlightedBlocks.size) return DecorationSet.empty

  const decorations: Decoration[] = []
  doc.descendants((node, position) => {
    const blockId = blockIdentityId(node.attrs)
    if (!blockId) return true

    const highlight = highlightedBlocks.get(blockId)
    if (!highlight) return true

    const style = highlightStyle(highlight)
    const className = Number.isFinite(highlight.selectionHeight)
      ? 'docpilot-block-affordance-highlighted docpilot-block-affordance-highlighted-fixed-height'
      : 'docpilot-block-affordance-highlighted'
    decorations.push(Decoration.node(position, position + node.nodeSize, {
      ...(style ? { style } : {}),
      class: className
    }))
    return false
  })

  return DecorationSet.create(doc, decorations)
}

export const DocpilotBlockAffordanceHighlight = Extension.create({
  name: 'docpilotBlockAffordanceHighlight',

  addProseMirrorPlugins() {
    return [
      new Plugin<BlockAffordanceHighlightPluginState>({
        key: blockAffordanceHighlightPluginKey,
        state: {
          init: () => ({
            decorations: DecorationSet.empty,
            highlightedBlocks: new Map<string, BlockAffordanceHighlightDecoration>()
          }),
          apply: (transaction, previousState, _, nextState) => {
            const meta = transaction.getMeta(blockAffordanceHighlightPluginKey)
            if (meta && Array.isArray(meta.blockHighlights)) {
              const highlightedBlocks = normalizedHighlightMap(meta.blockHighlights)
              return {
                decorations: highlightDecorations(nextState.doc, highlightedBlocks),
                highlightedBlocks
              }
            }

            if (transaction.docChanged && previousState.highlightedBlocks.size) {
              return {
                decorations: highlightDecorations(nextState.doc, previousState.highlightedBlocks),
                highlightedBlocks: previousState.highlightedBlocks
              }
            }

            return previousState
          }
        },
        props: {
          decorations: (state) => blockAffordanceHighlightPluginKey.getState(state)?.decorations ?? DecorationSet.empty
        }
      })
    ]
  }
})

export function setBlockAffordanceHighlight(
  editor: Editor,
  blockHighlights: Iterable<BlockAffordanceHighlightDecoration>
): void {
  editor.view.dispatch(
    editor.state.tr.setMeta(blockAffordanceHighlightPluginKey, {
      blockHighlights: Array.from(normalizedHighlightMap(blockHighlights).values())
    })
  )
}
