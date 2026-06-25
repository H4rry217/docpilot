import type { JSONContent } from '@tiptap/core'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import { NodeSelection, TextSelection } from '@tiptap/pm/state'
import type { Editor } from '@tiptap/react'
import type { TranslationKey } from '@/shared/i18n'
import {
  CheckSquare,
  Code2,
  Heading1,
  Heading2,
  Heading3,
  Image,
  List,
  ListOrdered,
  Minus,
  Pilcrow,
  Quote,
  Sigma,
  Table2,
  Trash2,
  Type,
  type LucideIcon
} from 'lucide-react'
import { deleteBlocksByIds } from '../model/blockSelection'
import { blockIdentityId } from '../model/docpilotBlockIdentity'

export type BlockMenuBlockInfo = {
  blockId: string
  attrs: Record<string, unknown>
  isEmptyParagraph: boolean
  text: string
  typeName: string
}

export type BlockMenuTranslate = (key: TranslationKey, values?: Record<string, string>) => string

export type BlockMenuActionContext = {
  block: BlockMenuBlockInfo
  editor: Editor
  t: BlockMenuTranslate
}

export type BlockMenuItem = {
  id: string
  icon: LucideIcon
  labelKey: TranslationKey
  active?: (context: BlockMenuActionContext) => boolean
  run: (context: BlockMenuActionContext) => void
}

type BlockRange = {
  from: number
  node: ProseMirrorNode
  to: number
}

function emptyParagraphContent(): JSONContent {
  return {
    type: 'paragraph'
  }
}

function emptyListItemContent(): JSONContent {
  return {
    type: 'listItem',
    content: [emptyParagraphContent()]
  }
}

function taskListItemContent(): JSONContent {
  return {
    type: 'taskItem',
    attrs: {
      checked: false
    },
    content: [
      {
        type: 'paragraph'
      }
    ]
  }
}

export const FORMAT_STRIP_ITEMS: BlockMenuItem[] = [
  transformItem('paragraph', 'blockMenu.paragraph', Type, (context) => setParagraph(context), isParagraphActive),
  headingItem(1, Heading1),
  headingItem(2, Heading2),
  headingItem(3, Heading3),
  transformItem('ordered-list', 'blockMenu.orderedList', ListOrdered, (context) => toggleOrderedList(context), (context) => context.editor.isActive('orderedList')),
  transformItem('bullet-list', 'blockMenu.bulletList', List, (context) => toggleBulletList(context), (context) => context.editor.isActive('bulletList')),
  transformItem('task', 'blockMenu.task', CheckSquare, (context) => toggleTaskList(context), (context) => context.editor.isActive('taskList')),
  transformItem('quote', 'blockMenu.quote', Quote, (context) => toggleBlockquote(context), (context) => context.editor.isActive('blockquote')),
  transformItem('code', 'blockMenu.codeBlock', Code2, (context) => setCodeBlock(context), (context) => context.editor.isActive('codeBlock'))
]

export const INSERT_MENU_ITEMS: BlockMenuItem[] = [
  transformItem('insert-paragraph', 'blockMenu.paragraph', Pilcrow, (context) => replaceCurrentBlock(context, emptyParagraphContent())),
  transformItem('insert-heading-1', 'blockMenu.heading1', Heading1, (context) => replaceCurrentBlock(context, headingContent(1))),
  transformItem('insert-heading-2', 'blockMenu.heading2', Heading2, (context) => replaceCurrentBlock(context, headingContent(2))),
  transformItem('insert-heading-3', 'blockMenu.heading3', Heading3, (context) => replaceCurrentBlock(context, headingContent(3))),
  transformItem('insert-bullet-list', 'blockMenu.bulletList', List, (context) => replaceCurrentBlock(context, {
    type: 'bulletList',
    content: [emptyListItemContent()]
  })),
  transformItem('insert-ordered-list', 'blockMenu.orderedList', ListOrdered, (context) => replaceCurrentBlock(context, {
    type: 'orderedList',
    content: [emptyListItemContent()]
  })),
  transformItem('insert-task', 'blockMenu.task', CheckSquare, (context) => replaceCurrentBlock(context, {
    type: 'taskList',
    content: [taskListItemContent()]
  })),
  transformItem('insert-quote', 'blockMenu.quote', Quote, (context) => replaceCurrentBlock(context, {
    type: 'blockquote',
    content: [emptyParagraphContent()]
  })),
  transformItem('insert-code', 'blockMenu.codeBlock', Code2, (context) => replaceCurrentBlock(context, {
    type: 'codeBlock',
    attrs: { language: '' }
  })),
  transformItem('insert-divider', 'blockMenu.divider', Minus, (context) => replaceCurrentBlock(context, {
    type: 'horizontalRule'
  })),
  transformItem('insert-table', 'blockMenu.table', Table2, (context) => replaceCurrentBlock(context, tableContent())),
  transformItem('insert-image', 'blockMenu.image', Image, (context) => insertImage(context)),
  transformItem('insert-math', 'blockMenu.mathBlock', Sigma, (context) => replaceCurrentBlock(context, {
    type: 'blockMath',
    attrs: {
      delimiter: '$$',
      latex: 'E = mc^2',
      notation: 'latex',
      text: 'E = mc^2'
    }
  }))
]

export const TRANSFORM_MENU_ITEMS: BlockMenuItem[] = [
  transformItem('to-paragraph', 'blockMenu.transform.paragraph', Pilcrow, (context) => setParagraph(context), isParagraphActive),
  headingItem(1, Heading1, 'blockMenu.transform.heading1'),
  headingItem(2, Heading2, 'blockMenu.transform.heading2'),
  headingItem(3, Heading3, 'blockMenu.transform.heading3'),
  transformItem('to-bullet-list', 'blockMenu.transform.bulletList', List, (context) => toggleBulletList(context), (context) => context.editor.isActive('bulletList')),
  transformItem('to-ordered-list', 'blockMenu.transform.orderedList', ListOrdered, (context) => toggleOrderedList(context), (context) => context.editor.isActive('orderedList')),
  transformItem('to-task', 'blockMenu.task', CheckSquare, (context) => toggleTaskList(context), (context) => context.editor.isActive('taskList')),
  transformItem('to-quote', 'blockMenu.transform.quote', Quote, (context) => toggleBlockquote(context), (context) => context.editor.isActive('blockquote')),
  transformItem('to-code', 'blockMenu.transform.codeBlock', Code2, (context) => setCodeBlock(context), (context) => context.editor.isActive('codeBlock'))
]

export const BLOCK_EDIT_MENU_ITEMS: BlockMenuItem[] = [
  transformItem('delete', 'blockMenu.delete', Trash2, (context) => {
    deleteBlocksByIds(context.editor, [context.block.blockId])
  }),
  transformItem('add-below', 'blockMenu.addBelow', Pilcrow, (context) => {
    insertBlockAfter(context, emptyParagraphContent())
  })
]

export function blockInfoForBlockId(editor: Editor, blockId: string): BlockMenuBlockInfo | null {
  const range = findBlockRangeById(editor, blockId)
  if (!range) return null

  return {
    blockId,
    attrs: { ...range.node.attrs },
    isEmptyParagraph: isEmptyParagraph(range.node),
    text: range.node.textContent,
    typeName: range.node.type.name
  }
}

export function iconForBlockInfo(block: BlockMenuBlockInfo): LucideIcon {
  if (block.typeName === 'heading') {
    if (block.attrs.level === 1) return Heading1
    if (block.attrs.level === 2) return Heading2
    return Heading3
  }
  if (block.typeName === 'taskList' || block.typeName === 'taskItem') return CheckSquare
  if (block.typeName === 'bulletList' || block.typeName === 'listItem') return List
  if (block.typeName === 'orderedList') return ListOrdered
  if (block.typeName === 'blockquote') return Quote
  if (block.typeName === 'codeBlock') return Code2
  if (block.typeName === 'horizontalRule') return Minus
  if (block.typeName === 'table') return Table2
  if (block.typeName === 'blockMath') return Sigma
  return Pilcrow
}

export function labelForBlockInfo(block: BlockMenuBlockInfo, t: BlockMenuTranslate): string {
  if (block.isEmptyParagraph) return t('blockMenu.emptyLine')
  if (block.typeName === 'heading') {
    const level = typeof block.attrs.level === 'number' ? block.attrs.level : 1
    return t('blockMenu.headingLevel', { level: String(level) })
  }
  if (block.typeName === 'taskList' || block.typeName === 'taskItem') return t('blockMenu.task')
  if (block.typeName === 'bulletList' || block.typeName === 'listItem') return t('blockMenu.list')
  if (block.typeName === 'orderedList') return t('blockMenu.orderedList')
  if (block.typeName === 'blockquote') return t('blockMenu.quote')
  if (block.typeName === 'codeBlock') return t('blockMenu.codeBlock')
  if (block.typeName === 'horizontalRule') return t('blockMenu.divider')
  if (block.typeName === 'table') return t('blockMenu.table')
  if (block.typeName === 'blockMath') return t('blockMenu.mathBlock')
  return t('blockMenu.paragraph')
}

export function findBlockRangeById(editor: Editor, blockId: string): BlockRange | null {
  let range: BlockRange | null = null

  editor.state.doc.descendants((node, position) => {
    if (blockIdentityId(node.attrs) !== blockId) return true

    range = {
      from: position,
      node,
      to: position + node.nodeSize
    }
    return false
  })

  return range
}

function transformItem(
  id: string,
  labelKey: TranslationKey,
  icon: LucideIcon,
  run: BlockMenuItem['run'],
  active?: BlockMenuItem['active']
): BlockMenuItem {
  return {
    id,
    labelKey,
    icon,
    run,
    active
  }
}

function headingItem(level: 1 | 2 | 3, icon: LucideIcon, labelKey = headingLabelKey(level)): BlockMenuItem {
  return transformItem(
    `heading-${level}`,
    labelKey,
    icon,
    (context) => setHeading(context, level),
    (context) => context.editor.isActive('heading', { level })
  )
}

function headingLabelKey(level: 1 | 2 | 3): TranslationKey {
  if (level === 1) return 'blockMenu.heading1'
  if (level === 2) return 'blockMenu.heading2'
  return 'blockMenu.heading3'
}

function headingContent(level: 1 | 2 | 3): JSONContent {
  return {
    type: 'heading',
    attrs: { level }
  }
}

function tableContent(): JSONContent {
  return {
    type: 'table',
    content: Array.from({ length: 3 }, (_, rowIndex) => ({
      type: 'tableRow',
      content: Array.from({ length: 3 }, () => ({
        type: rowIndex === 0 ? 'tableHeader' : 'tableCell',
        content: [emptyParagraphContent()]
      }))
    }))
  }
}

function isParagraphActive(context: BlockMenuActionContext): boolean {
  return context.editor.isActive('paragraph')
}

function isEmptyParagraph(node: ProseMirrorNode): boolean {
  return node.type.name === 'paragraph'
    && node.content.size === 0
    && node.textContent.trim().length === 0
}

function selectBlockForCommand(context: BlockMenuActionContext): BlockRange | null {
  const range = findBlockRangeById(context.editor, context.block.blockId)
  if (!range) return null

  const position = textSelectionPosition(range)
  if (position !== null) {
    const resolvedPosition = context.editor.state.doc.resolve(position)
    context.editor.view.dispatch(
      context.editor.state.tr.setSelection(TextSelection.near(resolvedPosition))
    )
    context.editor.view.focus()
    return range
  }

  context.editor.view.dispatch(
    context.editor.state.tr.setSelection(NodeSelection.create(context.editor.state.doc, range.from))
  )
  context.editor.view.focus()
  return range
}

function textSelectionPosition(range: BlockRange): number | null {
  if (range.node.isTextblock) return range.from + 1

  let position: number | null = null
  range.node.descendants((node, offset) => {
    if (!node.isTextblock) return true

    position = range.from + offset + 2
    return false
  })

  if (position === null && range.node.inlineContent) {
    return Math.min(range.to - 1, range.from + 1)
  }

  return position
}

function setParagraph(context: BlockMenuActionContext): void {
  if (!selectBlockForCommand(context)) return
  context.editor.chain().focus().setParagraph().run()
}

function setHeading(context: BlockMenuActionContext, level: 1 | 2 | 3): void {
  if (!selectBlockForCommand(context)) return
  context.editor.chain().focus().setHeading({ level }).run()
}

function toggleBulletList(context: BlockMenuActionContext): void {
  if (!selectBlockForCommand(context)) return
  context.editor.chain().focus().toggleBulletList().run()
}

function toggleOrderedList(context: BlockMenuActionContext): void {
  if (!selectBlockForCommand(context)) return
  context.editor.chain().focus().toggleOrderedList().run()
}

function toggleBlockquote(context: BlockMenuActionContext): void {
  if (!selectBlockForCommand(context)) return
  context.editor.chain().focus().toggleBlockquote().run()
}

function setCodeBlock(context: BlockMenuActionContext): void {
  if (!selectBlockForCommand(context)) return
  context.editor.chain().focus().setCodeBlock().run()
}

function toggleTaskList(context: BlockMenuActionContext): void {
  if (!selectBlockForCommand(context)) return
  context.editor.chain().focus().toggleTaskList().run()
}

function replaceCurrentBlock(context: BlockMenuActionContext, content: JSONContent): void {
  const range = findBlockRangeById(context.editor, context.block.blockId)
  if (!range) return

  context.editor.chain().focus().insertContentAt(
    { from: range.from, to: range.to },
    content
  ).run()
}

function insertBlockAfter(context: BlockMenuActionContext, content: JSONContent): void {
  const range = findBlockRangeById(context.editor, context.block.blockId)
  if (!range) return

  context.editor.chain().focus().insertContentAt(range.to, content).run()
}

function insertImage(context: BlockMenuActionContext): void {
  const src = window.prompt(context.t('blockMenu.imageUrlPrompt'))
  if (!src) return

  replaceCurrentBlock(context, {
    type: 'paragraph',
    content: [
      {
        type: 'image',
        attrs: {
          alt: '',
          src: src.trim()
        }
      }
    ]
  })
}
