import Link from '@tiptap/extension-link'
import Table from '@tiptap/extension-table'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import TableRow from '@tiptap/extension-table-row'
import StarterKit from '@tiptap/starter-kit'
import { DocpilotBlockIdentity } from './docpilotBlockIdentity'
import { DocpilotHtmlBlock } from './docpilotHtmlBlock'
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

export const editorExtensions = [
  StarterKit.configure({
    heading: {
      levels: [1, 2, 3, 4, 5, 6]
    }
  }),
  DocpilotBlockIdentity,
  Link.configure({
    autolink: true,
    openOnClick: false
  }),
  Table.configure({
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
