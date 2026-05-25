import Link from '@tiptap/extension-link'
import Table from '@tiptap/extension-table'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import TableRow from '@tiptap/extension-table-row'
import StarterKit from '@tiptap/starter-kit'
import { DocpilotHtmlBlock } from './docpilotHtmlBlock'
import { DocpilotUnsupportedBlock } from './docpilotUnsupportedBlock'

export const editorExtensions = [
  StarterKit.configure({
    heading: {
      levels: [1, 2, 3, 4]
    }
  }),
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
  DocpilotUnsupportedBlock
]
