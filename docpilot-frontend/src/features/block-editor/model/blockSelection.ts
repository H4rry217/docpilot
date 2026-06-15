import { Extension, type Editor } from '@tiptap/core'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import { blockIdentityId } from './docpilotBlockIdentity'

type BlockSelectionPluginState = {
  decorations: DecorationSet
  selectedBlocks: ReadonlyMap<string, BlockSelectionDecoration>
}

const blockSelectionPluginKey = new PluginKey<BlockSelectionPluginState>('docpilotBlockSelection')

export type BlockSelectionDecoration = {
  blockId: string
  selectionBlockEndOutset?: number
  selectionBlockStartOutset?: number
  selectionLeft?: number
  selectionTop?: number
  selectionWidth?: number
  selectionHeight?: number
}

export type BlockSelectionDecorationInput = string | BlockSelectionDecoration

function normalizedBlockSelectionMap(blockSelections: Iterable<BlockSelectionDecorationInput>): Map<string, BlockSelectionDecoration> {
  const nextBlocks = new Map<string, BlockSelectionDecoration>()
  for (const blockSelection of blockSelections) {
    const decoration = typeof blockSelection === 'string'
      ? { blockId: blockSelection }
      : blockSelection
    if (decoration.blockId) {
      nextBlocks.set(decoration.blockId, decoration)
    }
  }
  return nextBlocks
}

function blockSelectionStyle(blockSelection: BlockSelectionDecoration): string | undefined {
  const styles: string[] = []
  if (Number.isFinite(blockSelection.selectionLeft)) {
    styles.push(`--docpilot-selection-left: ${Math.round(blockSelection.selectionLeft ?? 0)}px`)
  }
  if (Number.isFinite(blockSelection.selectionBlockStartOutset)) {
    styles.push(`--docpilot-selection-block-start-outset: ${Math.round(blockSelection.selectionBlockStartOutset ?? 0)}px`)
  }
  if (Number.isFinite(blockSelection.selectionBlockEndOutset)) {
    styles.push(`--docpilot-selection-block-end-outset: ${Math.round(blockSelection.selectionBlockEndOutset ?? 0)}px`)
  }
  if (Number.isFinite(blockSelection.selectionTop)) {
    styles.push(`--docpilot-selection-top: ${Math.round(blockSelection.selectionTop ?? 0)}px`)
  }
  if (Number.isFinite(blockSelection.selectionWidth)) {
    styles.push(`--docpilot-selection-width: ${Math.round(blockSelection.selectionWidth ?? 0)}px`)
  }
  if (Number.isFinite(blockSelection.selectionHeight)) {
    styles.push(`--docpilot-selection-height: ${Math.round(blockSelection.selectionHeight ?? 0)}px`)
  }
  return styles.length ? styles.join('; ') : undefined
}

function blockSelectionDecorations(doc: ProseMirrorNode, selectedBlocks: ReadonlyMap<string, BlockSelectionDecoration>): DecorationSet {
  if (!selectedBlocks.size) return DecorationSet.empty

  const decorations: Decoration[] = []
  doc.descendants((node, position) => {
    const blockId = blockIdentityId(node.attrs)
    if (!blockId) {
      return true
    }

    const blockSelection = selectedBlocks.get(blockId)
    if (!blockSelection) {
      return true
    }

    const style = blockSelectionStyle(blockSelection)
    const className = Number.isFinite(blockSelection.selectionHeight)
      ? 'docpilot-block-selected docpilot-block-selected-fixed-height'
      : 'docpilot-block-selected'
    decorations.push(Decoration.node(position, position + node.nodeSize, {
      ...(style ? { style } : {}),
      class: className
    }))
    return false
  })

  return DecorationSet.create(doc, decorations)
}

export const DocpilotBlockSelection = Extension.create({
  name: 'docpilotBlockSelection',

  addProseMirrorPlugins() {
    return [
      new Plugin<BlockSelectionPluginState>({
        key: blockSelectionPluginKey,
        state: {
          init: () => ({
            decorations: DecorationSet.empty,
            selectedBlocks: new Map<string, BlockSelectionDecoration>()
          }),
          apply: (transaction, previousState, _, nextState) => {
            const meta = transaction.getMeta(blockSelectionPluginKey)
            if (meta && Array.isArray(meta.blockSelections)) {
              const selectedBlocks = normalizedBlockSelectionMap(meta.blockSelections)
              return {
                decorations: blockSelectionDecorations(nextState.doc, selectedBlocks),
                selectedBlocks
              }
            }

            if (transaction.docChanged && previousState.selectedBlocks.size) {
              return {
                decorations: blockSelectionDecorations(nextState.doc, previousState.selectedBlocks),
                selectedBlocks: previousState.selectedBlocks
              }
            }

            return previousState
          }
        },
        props: {
          decorations: (state) => blockSelectionPluginKey.getState(state)?.decorations ?? DecorationSet.empty
        }
      })
    ]
  }
})

export function setBlockSelectionDecorations(editor: Editor, blockSelections: Iterable<BlockSelectionDecorationInput>): void {
  editor.view.dispatch(
    editor.state.tr.setMeta(blockSelectionPluginKey, {
      blockSelections: Array.from(normalizedBlockSelectionMap(blockSelections).values())
    })
  )
}

export function deleteBlocksByIds(editor: Editor, blockIds: Iterable<string>): boolean {
  const selectedIds = new Set(blockIds)
  if (!selectedIds.size) return false

  const ranges: Array<{ from: number; to: number }> = []
  editor.state.doc.descendants((node, position) => {
    const blockId = blockIdentityId(node.attrs)
    if (!selectedIds.has(blockId)) {
      return true
    }

    ranges.push({ from: position, to: position + node.nodeSize })
    return false
  })

  if (!ranges.length) return false

  const transaction = editor.state.tr
  for (const range of ranges.sort((left, right) => right.from - left.from)) {
    transaction.delete(range.from, range.to)
  }
  editor.view.dispatch(transaction.scrollIntoView())
  return true
}
