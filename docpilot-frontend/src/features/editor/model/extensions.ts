import Link from '@tiptap/extension-link'
import Table from '@tiptap/extension-table'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import TableRow from '@tiptap/extension-table-row'
import StarterKit from '@tiptap/starter-kit'
import { DocpilotBlockSelection } from './blockSelection'
import { DocpilotBlockIdentity } from './docpilotBlockIdentity'
import { DocpilotCodeBlock } from './docpilotCodeBlock'
import { DocpilotFootnoteNavigation } from './docpilotFootnoteNavigation'
import { DocpilotHtmlBlock } from './docpilotHtmlBlock'
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
  DocpilotHighlight,
  DocpilotHtmlInline,
  DocpilotImage,
  DocpilotInsert,
  DocpilotLinkReferenceDefinition,
  DocpilotMathBlock,
  DocpilotMathInline,
  DocpilotSubscript,
  DocpilotSuperscript,
  DocpilotToc,
  DocpilotUnderline
} from './docpilotMarkdownExtensions'
import { DocpilotUnsupportedBlock } from './docpilotUnsupportedBlock'
import { TABLE_DEFAULT_COLUMN_WIDTH_PX } from './tableConstants'

export const editorExtensions = [
  StarterKit.configure({
    codeBlock: false,
    heading: {
      levels: [1, 2, 3, 4, 5, 6]
    }
  }),
  DocpilotTableCellSelection,
  DocpilotBlockIdentity,
  DocpilotBlockSelection,
  DocpilotFootnoteNavigation,
  DocpilotCodeBlock,
  Link.configure({
    autolink: true,
    openOnClick: true
  }),
  Table.configure({
    cellMinWidth: TABLE_DEFAULT_COLUMN_WIDTH_PX,
    resizable: true
  }),
  TableRow,
  TableHeader,
  TableCell,
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
  DocpilotExtensionInline,
  DocpilotUnderline,
  DocpilotInsert,
  DocpilotSubscript,
  DocpilotSuperscript,
  DocpilotHighlight,
  DocpilotUnsupportedBlock
]
