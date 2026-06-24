import type { Editor } from '@tiptap/core'
import { Placeholder } from '@tiptap/extensions/placeholder'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'

const EMPTY_TEXTBLOCK_CLASS = 'docpilot-empty-textblock'
const EMPTY_EDITOR_CLASS = 'docpilot-empty-editor'

const PARAGRAPH_ANCESTOR_PLACEHOLDERS: Record<string, string> = {
  blockquote: '引用',
  bulletList: '列表项',
  orderedList: '列表项',
  listItem: '列表项',
  taskItem: '待办',
  taskList: '待办',
  docpilotCallout: '提示',
  docpilotFootnoteDefinition: '脚注',
  docpilotDefinitionItem: '定义',
  docpilotExtensionBlock: '扩展块'
}

export const DocpilotPlaceholder = Placeholder.configure({
  emptyEditorClass: EMPTY_EDITOR_CLASS,
  emptyNodeClass: EMPTY_TEXTBLOCK_CLASS,
  includeChildren: true,
  placeholder: ({ editor, node, pos }) => placeholderForEmptyTextblock(editor, node, pos),
  showOnlyCurrent: true,
  showOnlyWhenEditable: true
})

function placeholderForEmptyTextblock(
  editor: Editor,
  node: ProseMirrorNode,
  pos: number
): string {
  if (node.type.name === 'heading') {
    return `H${headingLevel(node)}`
  }

  if (node.type.name === 'codeBlock') {
    return '代码'
  }

  if (node.type.name === 'docpilotDefinitionTerm') {
    return '术语'
  }

  if (node.type.name === 'paragraph') {
    return paragraphPlaceholderForPosition(editor, pos)
  }

  return '正文'
}

function headingLevel(node: ProseMirrorNode): number {
  const level = Number(node.attrs.level)
  if (!Number.isFinite(level)) return 1
  return Math.max(1, Math.min(6, Math.trunc(level)))
}

function paragraphPlaceholderForPosition(editor: Editor, pos: number): string {
  const doc = editor.state.doc
  const resolvedPos = doc.resolve(Math.max(0, Math.min(pos, doc.content.size)))

  for (let depth = resolvedPos.depth; depth > 0; depth -= 1) {
    const ancestorName = resolvedPos.node(depth).type.name
    const placeholder = PARAGRAPH_ANCESTOR_PLACEHOLDERS[ancestorName]
    if (placeholder) return placeholder
  }

  return '正文'
}
