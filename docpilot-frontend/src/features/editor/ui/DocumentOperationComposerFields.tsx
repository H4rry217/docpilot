import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useI18n } from '../../../shared/i18n'
import type { BlockTemplateForm, StructuredBlockTemplateType } from './documentOperationComposerModel'

export function JsonTextarea({
  label,
  onChange,
  readOnly = false,
  value
}: {
  label: string
  onChange: (value: string) => void
  readOnly?: boolean
  value: string
}) {
  return (
    <label className="document-operation-composer-field">
      <span>{label}</span>
      <Textarea
        aria-label={label}
        className="document-operation-composer-json"
        readOnly={readOnly}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  )
}

export function BlockTemplateFields({
  blockType,
  form,
  onChange
}: {
  blockType: StructuredBlockTemplateType
  form: BlockTemplateForm
  onChange: (patch: Partial<BlockTemplateForm>) => void
}) {
  const { t } = useI18n()

  switch (blockType) {
    case 'PARAGRAPH':
      return (
        <label className="document-operation-composer-field">
          <span>{t('developer.composer.blockText')}</span>
          <Input
            aria-label={t('developer.composer.blockText')}
            value={form.text}
            onChange={(event) => onChange({ text: event.target.value })}
          />
        </label>
      )
    case 'HEADING':
      return (
        <div className="document-operation-composer-grid">
          <label className="document-operation-composer-field">
            <span>{t('developer.composer.blockText')}</span>
            <Input
              aria-label={t('developer.composer.blockText')}
              value={form.text}
              onChange={(event) => onChange({ text: event.target.value })}
            />
          </label>
          <label className="document-operation-composer-field">
            <span>{t('developer.composer.headingLevel')}</span>
            <Input
              aria-label={t('developer.composer.headingLevel')}
              min={1}
              max={6}
              type="number"
              value={form.headingLevel}
              onChange={(event) => onChange({ headingLevel: event.target.value })}
            />
          </label>
        </div>
      )
    case 'CODE_BLOCK':
      return (
        <>
          <label className="document-operation-composer-field">
            <span>{t('developer.composer.codeLanguage')}</span>
            <Input
              aria-label={t('developer.composer.codeLanguage')}
              value={form.codeLanguage}
              onChange={(event) => onChange({ codeLanguage: event.target.value })}
            />
          </label>
          <JsonTextarea
            label={t('developer.composer.codeText')}
            value={form.codeText}
            onChange={(value) => onChange({ codeText: value })}
          />
        </>
      )
    case 'CALLOUT':
      return (
        <>
          <div className="document-operation-composer-grid">
            <label className="document-operation-composer-field">
              <span>{t('developer.composer.calloutKind')}</span>
              <select
                aria-label={t('developer.composer.calloutKind')}
                className="document-operation-composer-select"
                value={form.calloutKind}
                onChange={(event) => onChange({ calloutKind: event.target.value })}
              >
                <option value="info">info</option>
                <option value="warning">warning</option>
                <option value="success">success</option>
                <option value="danger">danger</option>
                <option value="details">details</option>
              </select>
            </label>
            <label className="document-operation-composer-field">
              <span>{t('developer.composer.calloutTitle')}</span>
              <Input
                aria-label={t('developer.composer.calloutTitle')}
                value={form.calloutTitle}
                onChange={(event) => onChange({ calloutTitle: event.target.value })}
              />
            </label>
          </div>
          <label className="document-operation-composer-field">
            <span>{t('developer.composer.calloutBody')}</span>
            <Input
              aria-label={t('developer.composer.calloutBody')}
              value={form.calloutBody}
              onChange={(event) => onChange({ calloutBody: event.target.value })}
            />
          </label>
        </>
      )
    case 'TABLE':
      return (
        <div className="document-operation-composer-grid">
          <label className="document-operation-composer-field">
            <span>{t('developer.composer.tableRows')}</span>
            <Input
              aria-label={t('developer.composer.tableRows')}
              min={1}
              max={20}
              type="number"
              value={form.tableRows}
              onChange={(event) => onChange({ tableRows: event.target.value })}
            />
          </label>
          <label className="document-operation-composer-field">
            <span>{t('developer.composer.tableColumns')}</span>
            <Input
              aria-label={t('developer.composer.tableColumns')}
              min={1}
              max={12}
              type="number"
              value={form.tableColumns}
              onChange={(event) => onChange({ tableColumns: event.target.value })}
            />
          </label>
          <label className="document-operation-composer-checkbox-field">
            <input
              aria-label={t('developer.composer.tableHeaderRow')}
              checked={form.tableHeaderRow}
              type="checkbox"
              onChange={(event) => onChange({ tableHeaderRow: event.target.checked })}
            />
            <span>{t('developer.composer.tableHeaderRow')}</span>
          </label>
        </div>
      )
    case 'HTML_BLOCK':
      return (
        <>
          <div className="document-operation-composer-grid">
            <label className="document-operation-composer-field">
              <span>{t('developer.composer.htmlTitle')}</span>
              <Input
                aria-label={t('developer.composer.htmlTitle')}
                value={form.htmlTitle}
                onChange={(event) => onChange({ htmlTitle: event.target.value })}
              />
            </label>
            <label className="document-operation-composer-field">
              <span>{t('developer.composer.htmlDisplayMode')}</span>
              <select
                aria-label={t('developer.composer.htmlDisplayMode')}
                className="document-operation-composer-select"
                value={form.htmlDisplayMode}
                onChange={(event) => onChange({ htmlDisplayMode: event.target.value })}
              >
                <option value="fixed">fixed</option>
                <option value="auto">auto</option>
              </select>
            </label>
          </div>
          <JsonTextarea
            label={t('developer.composer.htmlSource')}
            value={form.htmlSource}
            onChange={(value) => onChange({ htmlSource: value })}
          />
        </>
      )
    case 'MATH_BLOCK':
      return (
        <>
          <label className="document-operation-composer-field">
            <span>{t('developer.composer.mathNotation')}</span>
            <Input
              aria-label={t('developer.composer.mathNotation')}
              value={form.mathNotation}
              onChange={(event) => onChange({ mathNotation: event.target.value })}
            />
          </label>
          <JsonTextarea
            label={t('developer.composer.mathFormula')}
            value={form.mathText}
            onChange={(value) => onChange({ mathText: value })}
          />
        </>
      )
    case 'DIAGRAM_BLOCK':
      return (
        <>
          <label className="document-operation-composer-field">
            <span>{t('developer.composer.diagramEngine')}</span>
            <Input
              aria-label={t('developer.composer.diagramEngine')}
              value={form.diagramEngine}
              onChange={(event) => onChange({ diagramEngine: event.target.value })}
            />
          </label>
          <JsonTextarea
            label={t('developer.composer.diagramSource')}
            value={form.diagramText}
            onChange={(value) => onChange({ diagramText: value })}
          />
        </>
      )
    case 'FRONT_MATTER':
      return (
        <>
          <label className="document-operation-composer-field">
            <span>{t('developer.composer.frontMatterFormat')}</span>
            <Input
              aria-label={t('developer.composer.frontMatterFormat')}
              value={form.frontMatterFormat}
              onChange={(event) => onChange({ frontMatterFormat: event.target.value })}
            />
          </label>
          <JsonTextarea
            label={t('developer.composer.frontMatterRaw')}
            value={form.frontMatterRaw}
            onChange={(value) => onChange({ frontMatterRaw: value })}
          />
        </>
      )
  }
}
