import Emoji from '@tiptap/extension-emoji'
import Highlight from '@tiptap/extension-highlight'
import { TaskItem, TaskList } from '@tiptap/extension-list'
import Subscript from '@tiptap/extension-subscript'
import Superscript from '@tiptap/extension-superscript'
import { TableKit } from '@tiptap/extension-table'
import { Markdown } from '@tiptap/markdown'
import StarterKit from '@tiptap/starter-kit'
import 'katex/dist/katex.min.css'
import { DocpilotBlockAffordanceHighlight } from './blockAffordanceHighlight'
import { DocpilotBlockSelection } from './blockSelection'
import { DocpilotBlockIdentity } from './docpilotBlockIdentity'
import { DocpilotCodeBlock } from './docpilotCodeBlock'
import { DocpilotFootnoteNavigation } from './docpilotFootnoteNavigation'
import { DocpilotHtmlBlock } from './docpilotHtmlBlock'
import { DocpilotInlineCompletion } from './inlineCompletion'
import { DocpilotPlaceholder } from './docpilotPlaceholder'
import { DocpilotTableCellSelection } from './docpilotTableCellSelection'
import {
  DocpilotCallout,
  DocpilotDefinitionItem,
  DocpilotDefinitionList,
  DocpilotDefinitionTerm,
  DocpilotDiagramBlock,
  DocpilotEmoji,
  DocpilotExtensionBlock,
  DocpilotExtensionInline,
  DocpilotFootnoteDefinition,
  DocpilotFootnoteRef,
  DocpilotFrontMatter,
  DocpilotHtmlInline,
  DocpilotImage,
  DocpilotInsert,
  DocpilotLinkReferenceDefinition,
  DocpilotMathBlock,
  DocpilotMathInline,
  DocpilotToc,
} from './docpilotMarkdownExtensions'
import { DocpilotUnsupportedBlock } from './docpilotUnsupportedBlock'
import { TABLE_DEFAULT_COLUMN_WIDTH_PX } from './tableConstants'

export const editorExtensions = [
  StarterKit.configure({
    codeBlock: false,
    link: {
      autolink: true,
      openOnClick: true
    },
    heading: {
      levels: [1, 2, 3, 4, 5, 6]
    },
    trailingNode: false
  }),
  DocpilotTableCellSelection,
  DocpilotBlockIdentity,
  DocpilotBlockSelection,
  DocpilotBlockAffordanceHighlight,
  DocpilotPlaceholder,
  DocpilotInlineCompletion,
  DocpilotFootnoteNavigation,
  DocpilotCodeBlock,
  TaskList.configure({
    itemTypeName: 'taskItem',
    HTMLAttributes: {
      class: 'docpilot-task-list'
    }
  }),
  TaskItem.configure({
    nested: true,
    HTMLAttributes: {
      class: 'docpilot-task-item'
    },
    a11y: {
      checkboxLabel: (node, checked) => `${checked ? 'Completed' : 'Incomplete'} task: ${node.textContent || 'empty task'}`
    }
  }),
  TableKit.configure({
    table: {
      cellMinWidth: TABLE_DEFAULT_COLUMN_WIDTH_PX,
      resizable: true
    }
  }),
  DocpilotHtmlBlock,
  DocpilotImage,
  DocpilotFrontMatter,
  DocpilotMathBlock,
  DocpilotDiagramBlock,
  DocpilotCallout,
  DocpilotFootnoteDefinition,
  DocpilotDefinitionList,
  DocpilotDefinitionTerm,
  DocpilotDefinitionItem,
  DocpilotToc,
  DocpilotLinkReferenceDefinition,
  DocpilotExtensionBlock,
  DocpilotMathInline,
  DocpilotFootnoteRef,
  DocpilotHtmlInline,
  DocpilotEmoji,
  Emoji,
  DocpilotExtensionInline,
  DocpilotInsert,
  Subscript,
  Superscript,
  Highlight.configure({
    multicolor: true
  }),
  Markdown,
  DocpilotUnsupportedBlock
]
