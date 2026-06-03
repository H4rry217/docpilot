import type { Editor } from '@tiptap/core'
import CodeBlock from '@tiptap/extension-code-block'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import { NodeSelection } from '@tiptap/pm/state'
import { ReactNodeViewRenderer } from '@tiptap/react'
import { CodeBlockNodeView } from '../ui/CodeBlockNodeView'

type DeleteDirection = 'backward' | 'forward'

function isCodeBlockNode(node: ProseMirrorNode | null | undefined): node is ProseMirrorNode {
  return node?.type.name === 'codeBlock'
}

export function deleteAdjacentCodeBlock(editor: Editor, direction: DeleteDirection): boolean {
  const { state, view } = editor
  const { selection } = state

  if (selection instanceof NodeSelection && isCodeBlockNode(selection.node)) {
    view.dispatch(state.tr.delete(selection.from, selection.to).scrollIntoView())
    return true
  }

  if (!selection.empty || !selection.$from.parent.isTextblock) {
    return false
  }

  const { $from } = selection
  const parentBoundaryOffset = direction === 'backward' ? 0 : $from.parent.content.size
  if ($from.parentOffset !== parentBoundaryOffset || $from.depth < 1) {
    return false
  }

  if (direction === 'backward') {
    const beforeCurrentBlock = $from.before($from.depth)
    const previousNode = state.doc.resolve(beforeCurrentBlock).nodeBefore
    if (!isCodeBlockNode(previousNode)) return false
    view.dispatch(
      state.tr
        .delete(beforeCurrentBlock - previousNode.nodeSize, beforeCurrentBlock)
        .scrollIntoView()
    )
    return true
  }

  const afterCurrentBlock = $from.after($from.depth)
  const nextNode = state.doc.resolve(afterCurrentBlock).nodeAfter
  if (!isCodeBlockNode(nextNode)) return false
  view.dispatch(
    state.tr
      .delete(afterCurrentBlock, afterCurrentBlock + nextNode.nodeSize)
      .scrollIntoView()
  )
  return true
}

export const DocpilotCodeBlock = CodeBlock.extend({
  selectable: true,

  addKeyboardShortcuts() {
    return {
      Backspace: () => deleteAdjacentCodeBlock(this.editor, 'backward'),
      Delete: () => deleteAdjacentCodeBlock(this.editor, 'forward')
    }
  },

  addNodeView() {
    return ReactNodeViewRenderer(CodeBlockNodeView, { as: 'div' })
  }
})
