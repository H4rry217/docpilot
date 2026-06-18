import type { BlockDocument, BlockNode, BlockType, InlineNode } from '@/entities/block/types'
import type { TranslationKey, useI18n } from '@/shared/i18n'

export type ComposerOperationType =
  | 'insertBlock'
  | 'replaceBlock'
  | 'updateBlockAttrs'
  | 'replaceBlockInlines'
  | 'replaceBlockChildren'
  | 'deleteBlock'
  | 'moveBlock'

export type BlockTemplateForm = {
  text: string
  headingLevel: string
  codeLanguage: string
  codeText: string
  calloutKind: string
  calloutTitle: string
  calloutBody: string
  tableRows: string
  tableColumns: string
  tableHeaderRow: boolean
  htmlTitle: string
  htmlSource: string
  htmlDisplayMode: string
  mathNotation: string
  mathText: string
  diagramEngine: string
  diagramText: string
  frontMatterFormat: string
  frontMatterRaw: string
}

export type StructuredBlockTemplateType =
  | 'PARAGRAPH'
  | 'HEADING'
  | 'CODE_BLOCK'
  | 'CALLOUT'
  | 'TABLE'
  | 'HTML_BLOCK'
  | 'MATH_BLOCK'
  | 'DIAGRAM_BLOCK'
  | 'FRONT_MATTER'

export type BlockTypeSelection = StructuredBlockTemplateType | typeof RAW_BLOCK_TYPE_OPTION

export const RAW_BLOCK_TYPE_OPTION = '__RAW_BLOCK_TYPE__'
export const FALLBACK_RAW_BLOCK_TYPE: BlockType = 'UNSUPPORTED_BLOCK'

export const OPERATION_OPTIONS: Array<{ value: ComposerOperationType; labelKey: TranslationKey }> = [
  { value: 'insertBlock', labelKey: 'developer.composer.operation.insertBlock' },
  { value: 'replaceBlock', labelKey: 'developer.composer.operation.replaceBlock' },
  { value: 'updateBlockAttrs', labelKey: 'developer.composer.operation.updateBlockAttrs' },
  { value: 'replaceBlockInlines', labelKey: 'developer.composer.operation.replaceBlockInlines' },
  { value: 'replaceBlockChildren', labelKey: 'developer.composer.operation.replaceBlockChildren' },
  { value: 'deleteBlock', labelKey: 'developer.composer.operation.deleteBlock' },
  { value: 'moveBlock', labelKey: 'developer.composer.operation.moveBlock' }
]

export const STRUCTURED_BLOCK_TEMPLATE_TYPES: StructuredBlockTemplateType[] = [
  'PARAGRAPH',
  'HEADING',
  'CODE_BLOCK',
  'CALLOUT',
  'TABLE',
  'HTML_BLOCK',
  'MATH_BLOCK',
  'DIAGRAM_BLOCK',
  'FRONT_MATTER'
]

export const RAW_BLOCK_TYPE_OPTIONS: BlockType[] = [
  'DOCUMENT',
  'PARAGRAPH',
  'HEADING',
  'BLOCK_QUOTE',
  'BULLET_LIST',
  'ORDERED_LIST',
  'LIST_ITEM',
  'TASK_LIST_ITEM',
  'CODE_BLOCK',
  'THEMATIC_BREAK',
  'CALLOUT',
  'TABLE',
  'TABLE_ROW',
  'TABLE_CELL',
  'HTML_BLOCK',
  'MATH_BLOCK',
  'DIAGRAM_BLOCK',
  'FRONT_MATTER',
  'FOOTNOTE_DEFINITION',
  'DEFINITION_LIST',
  'DEFINITION_TERM',
  'DEFINITION_ITEM',
  'TOC',
  'LINK_REFERENCE_DEFINITION',
  'EXTENSION_BLOCK',
  'UNSUPPORTED_BLOCK'
]

const OPERATION_TYPE_VALUES = new Set<string>(OPERATION_OPTIONS.map((option) => option.value))
const STRUCTURED_BLOCK_TEMPLATE_TYPE_VALUES = new Set<string>(STRUCTURED_BLOCK_TEMPLATE_TYPES)
const RAW_BLOCK_TYPE_VALUES = new Set<string>(RAW_BLOCK_TYPE_OPTIONS)

export const DEFAULT_TEMPLATE_FORM: BlockTemplateForm = {
  text: '',
  headingLevel: '2',
  codeLanguage: 'text',
  codeText: '',
  calloutKind: 'info',
  calloutTitle: '',
  calloutBody: '',
  tableRows: '2',
  tableColumns: '2',
  tableHeaderRow: true,
  htmlTitle: 'HTML',
  htmlSource: '<div>HTML block</div>',
  htmlDisplayMode: 'fixed',
  mathNotation: 'latex',
  mathText: '\\\\int_a^b f(x)dx',
  diagramEngine: 'mermaid',
  diagramText: 'graph TD\\n  A[Start] --> B[End]',
  frontMatterFormat: 'yaml',
  frontMatterRaw: '---\\ntitle: Untitled\\n---'
}

export function blockTemplate(id: string, type: BlockType, form: BlockTemplateForm): BlockNode {
  const blockId = id.trim() || 'new-block'

  switch (type) {
    case 'PARAGRAPH':
      return leafBlock(blockId, type, {}, textInlines(form.text))
    case 'HEADING':
      return leafBlock(blockId, type, { level: intFromString(form.headingLevel, 2, 1, 6) }, textInlines(form.text))
    case 'CODE_BLOCK':
      return leafBlock(blockId, type, {
        language: form.codeLanguage.trim(),
        text: form.codeText
      })
    case 'CALLOUT': {
      const attrs: Record<string, unknown> = { kind: form.calloutKind || 'info' }
      if (form.calloutTitle.trim()) attrs.title = form.calloutTitle.trim()
      if (form.calloutKind === 'details') {
        attrs.collapsible = true
        attrs.open = true
      }
      return {
        id: blockId,
        type,
        attrs,
        inlines: [],
        children: form.calloutBody.trim()
          ? [leafBlock(`${blockId}-body`, 'PARAGRAPH', {}, textInlines(form.calloutBody))]
          : []
      }
    }
    case 'TABLE':
      return {
        id: blockId,
        type,
        attrs: {},
        inlines: [],
        children: tableChildren(blockId, form)
      }
    case 'HTML_BLOCK':
      return leafBlock(blockId, type, {
        id: blockId,
        title: form.htmlTitle.trim() || 'HTML',
        source: form.htmlSource,
        displayMode: form.htmlDisplayMode === 'auto' ? 'auto' : 'fixed'
      })
    case 'MATH_BLOCK':
      return leafBlock(blockId, type, {
        notation: form.mathNotation.trim() || 'latex',
        text: form.mathText,
        delimiter: '$$'
      })
    case 'DIAGRAM_BLOCK':
      return leafBlock(blockId, type, {
        engine: form.diagramEngine.trim() || 'mermaid',
        text: form.diagramText
      })
    case 'FRONT_MATTER':
      return leafBlock(blockId, type, {
        format: form.frontMatterFormat.trim() || 'yaml',
        raw: form.frontMatterRaw
      })
    default:
      return leafBlock(blockId, type, {}, [])
  }
}

export function parseBlockNode(value: string, t: ReturnType<typeof useI18n>['t']): BlockNode {
  const parsed = parseJsonObject(value, t)
  if (
    typeof parsed.id !== 'string'
    || typeof parsed.type !== 'string'
    || !isRecord(parsed.attrs)
    || !Array.isArray(parsed.inlines)
    || !Array.isArray(parsed.children)
  ) {
    throw new Error(t('developer.composer.errorInvalidBlock'))
  }

  return parsed as unknown as BlockNode
}

export function parseJsonObject(value: string, t: ReturnType<typeof useI18n>['t']): Record<string, unknown> {
  const parsed = parseJson(value, t)
  if (!isRecord(parsed) || Array.isArray(parsed)) throw new Error(t('developer.composer.errorObjectRequired'))
  return parsed
}

export function parseJsonArray<T>(value: string, t: ReturnType<typeof useI18n>['t']): T[] {
  const parsed = parseJson(value, t)
  if (!Array.isArray(parsed)) throw new Error(t('developer.composer.errorArrayRequired'))
  return parsed as T[]
}

export function resolveBlockType(selection: BlockTypeSelection, rawBlockType: BlockType): BlockType {
  return selection === RAW_BLOCK_TYPE_OPTION ? rawBlockType : selection
}

export function composerOperationTypeFromValue(value: string): ComposerOperationType | null {
  return isComposerOperationType(value) ? value : null
}

export function blockTypeSelectionFromValue(value: string): BlockTypeSelection | null {
  if (value === RAW_BLOCK_TYPE_OPTION) return RAW_BLOCK_TYPE_OPTION
  return isStructuredBlockTemplateType(value) ? value : null
}

export function rawBlockTypeFromValue(value: string): BlockType | null {
  return isRawBlockType(value) ? value : null
}

export function structuredTypeForBlock(blockType: BlockType): BlockTypeSelection {
  return isStructuredBlockTemplateType(blockType) ? blockType : RAW_BLOCK_TYPE_OPTION
}

export function templateFormForBlock(block: BlockNode | null): BlockTemplateForm {
  if (!block) return DEFAULT_TEMPLATE_FORM

  const text = blockPlainText(block)
  const firstChildText = block.children[0] ? blockPlainText(block.children[0]) : ''

  return {
    ...DEFAULT_TEMPLATE_FORM,
    text,
    headingLevel: stringFromAttr(block.attrs.level, DEFAULT_TEMPLATE_FORM.headingLevel),
    codeLanguage: stringFromAttr(block.attrs.language, DEFAULT_TEMPLATE_FORM.codeLanguage),
    codeText: stringFromAttr(block.attrs.text, text),
    calloutKind: stringFromAttr(block.attrs.kind, DEFAULT_TEMPLATE_FORM.calloutKind),
    calloutTitle: stringFromAttr(block.attrs.title, ''),
    calloutBody: firstChildText,
    tableRows: block.type === 'TABLE' && block.children.length ? String(block.children.length) : DEFAULT_TEMPLATE_FORM.tableRows,
    tableColumns: block.type === 'TABLE' && block.children[0]?.children.length
      ? String(block.children[0].children.length)
      : DEFAULT_TEMPLATE_FORM.tableColumns,
    tableHeaderRow: block.type === 'TABLE'
      ? block.children[0]?.children.some((cell) => cell.attrs.header === true) ?? true
      : DEFAULT_TEMPLATE_FORM.tableHeaderRow,
    htmlTitle: stringFromAttr(block.attrs.title, DEFAULT_TEMPLATE_FORM.htmlTitle),
    htmlSource: stringFromAttr(block.attrs.source, DEFAULT_TEMPLATE_FORM.htmlSource),
    htmlDisplayMode: stringFromAttr(block.attrs.displayMode, DEFAULT_TEMPLATE_FORM.htmlDisplayMode),
    mathNotation: stringFromAttr(block.attrs.notation, DEFAULT_TEMPLATE_FORM.mathNotation),
    mathText: stringFromAttr(block.attrs.text, DEFAULT_TEMPLATE_FORM.mathText),
    diagramEngine: stringFromAttr(block.attrs.engine, DEFAULT_TEMPLATE_FORM.diagramEngine),
    diagramText: stringFromAttr(block.attrs.text, DEFAULT_TEMPLATE_FORM.diagramText),
    frontMatterFormat: stringFromAttr(block.attrs.format, DEFAULT_TEMPLATE_FORM.frontMatterFormat),
    frontMatterRaw: stringFromAttr(block.attrs.raw, DEFAULT_TEMPLATE_FORM.frontMatterRaw)
  }
}

export function findBlock(blocks: BlockNode[], blockId: string): BlockNode | null {
  for (const block of blocks) {
    if (block.id === blockId) return block
    const childMatch = findBlock(block.children, blockId)
    if (childMatch) return childMatch
  }

  return null
}

export function uniqueBlockId(document: BlockDocument | null, baseId: string): string {
  if (!document || !findBlock(document.blocks, baseId)) return baseId

  for (let index = 2; index < 1000; index += 1) {
    const candidate = `${baseId}-${index}`
    if (!findBlock(document.blocks, candidate)) return candidate
  }

  return `${baseId}-${Date.now()}`
}

export function prettyJson(value: unknown): string {
  return JSON.stringify(value, null, 2)
}

function leafBlock(
  id: string,
  type: BlockType,
  attrs: Record<string, unknown>,
  inlines: InlineNode[] = []
): BlockNode {
  return {
    id,
    type,
    attrs,
    inlines,
    children: []
  }
}

function tableChildren(blockId: string, form: BlockTemplateForm): BlockNode[] {
  const rows = intFromString(form.tableRows, 2, 1, 20)
  const columns = intFromString(form.tableColumns, 2, 1, 12)
  const children: BlockNode[] = []

  for (let rowIndex = 0; rowIndex < rows; rowIndex += 1) {
    children.push({
      id: `${blockId}-row-${rowIndex + 1}`,
      type: 'TABLE_ROW',
      attrs: {},
      inlines: [],
      children: Array.from({ length: columns }, (_, columnIndex) => {
        const header = form.tableHeaderRow && rowIndex === 0
        return {
          id: `${blockId}-cell-${rowIndex + 1}-${columnIndex + 1}`,
          type: 'TABLE_CELL',
          attrs: header ? { header: true } : {},
          inlines: textInlines(header ? `Header ${columnIndex + 1}` : `Cell ${rowIndex + 1}-${columnIndex + 1}`),
          children: []
        }
      })
    })
  }

  return children
}

function textInlines(text: string): InlineNode[] {
  return text ? [textInline(text)] : []
}

function textInline(text: string): InlineNode {
  return {
    type: 'TEXT',
    text,
    attrs: {},
    marks: []
  }
}

function intFromString(value: string, fallback: number, min: number, max: number): number {
  const parsed = Number.parseInt(value, 10)
  if (!Number.isFinite(parsed)) return fallback
  return Math.min(max, Math.max(min, parsed))
}

function parseJson(value: string, t: ReturnType<typeof useI18n>['t']): unknown {
  try {
    return JSON.parse(value) as unknown
  } catch {
    throw new Error(t('developer.composer.errorInvalidJson'))
  }
}

function isStructuredBlockTemplateType(type: string): type is StructuredBlockTemplateType {
  return STRUCTURED_BLOCK_TEMPLATE_TYPE_VALUES.has(type)
}

function isComposerOperationType(value: string): value is ComposerOperationType {
  return OPERATION_TYPE_VALUES.has(value)
}

function isRawBlockType(value: string): value is BlockType {
  return RAW_BLOCK_TYPE_VALUES.has(value)
}

function stringFromAttr(value: unknown, fallback: string): string {
  return typeof value === 'string' ? value : fallback
}

function blockPlainText(block: BlockNode): string {
  return block.inlines.map((inline) => inline.text ?? '').join('')
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
