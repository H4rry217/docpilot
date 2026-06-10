import { ClipboardCopy, Play, Wand2 } from 'lucide-react'
import { useEffect, useState, type ChangeEvent } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import {
  runDocumentOperationsDebugInput,
  type DocumentOperation,
  type DocumentOperationResult,
  type DocumentOperationsDebugMode
} from '../../../entities/block/operations'
import type { BlockDocument, BlockNode, BlockType, InlineNode } from '../../../entities/block/types'
import { useI18n } from '../../../shared/i18n'
import { BlockTemplateFields, JsonTextarea } from './DocumentOperationComposerFields'
import {
  DEFAULT_TEMPLATE_FORM,
  FALLBACK_RAW_BLOCK_TYPE,
  OPERATION_OPTIONS,
  RAW_BLOCK_TYPE_OPTION,
  RAW_BLOCK_TYPE_OPTIONS,
  STRUCTURED_BLOCK_TEMPLATE_TYPES,
  blockTypeSelectionFromValue,
  blockTemplate,
  composerOperationTypeFromValue,
  findBlock,
  parseBlockNode,
  parseJsonArray,
  parseJsonObject,
  prettyJson,
  rawBlockTypeFromValue,
  resolveBlockType,
  structuredTypeForBlock,
  templateFormForBlock,
  uniqueBlockId,
  type BlockTemplateForm,
  type BlockTypeSelection,
  type ComposerOperationType
} from './documentOperationComposerModel'
import './DocumentOperationComposer.css'

type ComposerResult = {
  mode: DocumentOperationsDebugMode
  operation: DocumentOperation
  result: DocumentOperationResult
}

type ComposerValidation =
  | { ok: true; operation: DocumentOperation }
  | { ok: false; message: string }

type DocumentOperationComposerProps = {
  getBlockDocument?: () => BlockDocument | null | undefined
  getDocumentVersion?: () => string | null | undefined
  initialBlockId?: string | null
  onApplyBlockDocument?: (blockDocument: BlockDocument) => void
  onOpenChange: (open: boolean) => void
  open: boolean
}

export function DocumentOperationComposer({
  getBlockDocument,
  getDocumentVersion,
  initialBlockId,
  onApplyBlockDocument,
  onOpenChange,
  open
}: DocumentOperationComposerProps) {
  const { t } = useI18n()
  const [operationType, setOperationType] = useState<ComposerOperationType>('updateBlockAttrs')
  const [targetBlockId, setTargetBlockId] = useState('')
  const [anchorBlockId, setAnchorBlockId] = useState('')
  const [newBlockId, setNewBlockId] = useState('new-block')
  const [blockTypeSelection, setBlockTypeSelection] = useState<BlockTypeSelection>('PARAGRAPH')
  const [rawBlockType, setRawBlockType] = useState<BlockType>(FALLBACK_RAW_BLOCK_TYPE)
  const [templateForm, setTemplateForm] = useState<BlockTemplateForm>(DEFAULT_TEMPLATE_FORM)
  const [blockJson, setBlockJson] = useState('')
  const [attrsJson, setAttrsJson] = useState('{}')
  const [inlinesJson, setInlinesJson] = useState('[]')
  const [childrenJson, setChildrenJson] = useState('[]')
  const [error, setError] = useState<string | null>(null)
  const [composerResult, setComposerResult] = useState<ComposerResult | null>(null)

  const selectedBlockType = resolveBlockType(blockTypeSelection, rawBlockType)
  const operationValidation = buildOperation()
  const operationJson = operationValidation.ok ? prettyJson(operationValidation.operation) : ''

  useEffect(() => {
    if (!open) return

    const document = getBlockDocument?.() ?? null
    const targetBlock = document && initialBlockId ? findBlock(document.blocks, initialBlockId) : null
    const nextBlockType = targetBlock?.type ?? 'PARAGRAPH'
    const nextTypeSelection = structuredTypeForBlock(nextBlockType)
    const nextBlockId = uniqueBlockId(document, 'new-block')
    const nextTemplateForm = templateFormForBlock(targetBlock)
    const nextBlock = blockTemplate(nextBlockId, nextBlockType, nextTemplateForm)

    setOperationType(initialBlockId ? 'updateBlockAttrs' : 'insertBlock')
    setTargetBlockId(initialBlockId ?? '')
    setAnchorBlockId(initialBlockId ?? '')
    setNewBlockId(nextBlockId)
    setBlockTypeSelection(nextTypeSelection)
    setRawBlockType(nextBlockType)
    setTemplateForm(nextTemplateForm)
    setBlockJson(prettyJson(nextBlock))
    setAttrsJson(prettyJson(targetBlock?.attrs ?? {}))
    setInlinesJson(prettyJson(targetBlock?.inlines ?? nextBlock.inlines))
    setChildrenJson(prettyJson(targetBlock?.children ?? []))
    setError(null)
    setComposerResult(null)
  }, [getBlockDocument, initialBlockId, open])

  function handleBlockTypeChange(event: ChangeEvent<HTMLSelectElement>) {
    const nextSelection = blockTypeSelectionFromValue(event.target.value)
    if (!nextSelection) return

    const nextRawType = nextSelection === RAW_BLOCK_TYPE_OPTION ? selectedBlockType : rawBlockType
    const nextType = resolveBlockType(nextSelection, nextRawType)

    setBlockTypeSelection(nextSelection)
    setRawBlockType(nextRawType)
    setBlockJson(prettyJson(blockTemplate(newBlockId, nextType, templateForm)))
  }

  function handleRawBlockTypeChange(value: string) {
    const nextRawBlockType = rawBlockTypeFromValue(value)
    if (!nextRawBlockType) return

    setRawBlockType(nextRawBlockType)
    setBlockJson(prettyJson(blockTemplate(newBlockId, resolveBlockType(blockTypeSelection, nextRawBlockType), templateForm)))
  }

  function handleOperationTypeChange(event: ChangeEvent<HTMLSelectElement>) {
    const nextOperationType = composerOperationTypeFromValue(event.target.value)
    if (!nextOperationType) return

    setOperationType(nextOperationType)
  }

  function updateTemplateForm(patch: Partial<BlockTemplateForm>) {
    setTemplateForm((current) => {
      const next = { ...current, ...patch }
      setBlockJson(prettyJson(blockTemplate(newBlockId, selectedBlockType, next)))
      return next
    })
  }

  function applyTemplate() {
    setBlockJson(prettyJson(blockTemplate(newBlockId, selectedBlockType, templateForm)))
  }

  function previewOperation() {
    runOperation('preview')
  }

  function applyOperation() {
    runOperation('apply')
  }

  function runOperation(mode: DocumentOperationsDebugMode) {
    const document = getBlockDocument?.()
    if (!document) {
      setComposerResult(null)
      setError(t('developer.commandFeedbackNoDocument'))
      return
    }

    const validation = buildOperation()
    if (!validation.ok) {
      setComposerResult(null)
      setError(validation.message)
      return
    }

    const debugResult = runDocumentOperationsDebugInput(document, validation.operation, {
      mode,
      documentVersion: getDocumentVersion?.() ?? undefined
    })
    setComposerResult({
      mode: debugResult.mode,
      operation: validation.operation,
      result: debugResult.result
    })
    setError(null)

    if (debugResult.mode === 'apply' && debugResult.result.ok) {
      onApplyBlockDocument?.(debugResult.result.document)
    }
  }

  function copyOperationJson() {
    const validation = buildOperation()
    if (!validation.ok) {
      setError(validation.message)
      return
    }

    void navigator.clipboard?.writeText(prettyJson(validation.operation))
  }

  function buildOperation(): ComposerValidation {
    try {
      switch (operationType) {
        case 'insertBlock':
          return {
            ok: true,
            operation: {
              type: 'insertBlock',
              block: parseBlockNode(blockJson, t),
              position: targetBlockId ? { afterBlockId: targetBlockId } : undefined
            }
          }
        case 'replaceBlock':
          if (!targetBlockId.trim()) return invalid(t('developer.composer.errorTargetRequired'))
          return {
            ok: true,
            operation: {
              type: 'replaceBlock',
              blockId: targetBlockId.trim(),
              block: parseBlockNode(blockJson, t)
            }
          }
        case 'updateBlockAttrs':
          if (!targetBlockId.trim()) return invalid(t('developer.composer.errorTargetRequired'))
          return {
            ok: true,
            operation: {
              type: 'updateBlockAttrs',
              blockId: targetBlockId.trim(),
              attrs: parseJsonObject(attrsJson, t)
            }
          }
        case 'replaceBlockInlines':
          if (!targetBlockId.trim()) return invalid(t('developer.composer.errorTargetRequired'))
          return {
            ok: true,
            operation: {
              type: 'replaceBlockInlines',
              blockId: targetBlockId.trim(),
              inlines: parseJsonArray<InlineNode>(inlinesJson, t)
            }
          }
        case 'replaceBlockChildren':
          if (!targetBlockId.trim()) return invalid(t('developer.composer.errorTargetRequired'))
          return {
            ok: true,
            operation: {
              type: 'replaceBlockChildren',
              blockId: targetBlockId.trim(),
              children: parseJsonArray<BlockNode>(childrenJson, t)
            }
          }
        case 'deleteBlock':
          if (!targetBlockId.trim()) return invalid(t('developer.composer.errorTargetRequired'))
          return {
            ok: true,
            operation: {
              type: 'deleteBlock',
              blockId: targetBlockId.trim()
            }
          }
        case 'moveBlock':
          if (!targetBlockId.trim()) return invalid(t('developer.composer.errorTargetRequired'))
          if (!anchorBlockId.trim()) return invalid(t('developer.composer.errorAnchorRequired'))
          return {
            ok: true,
            operation: {
              type: 'moveBlock',
              blockId: targetBlockId.trim(),
              position: { afterBlockId: anchorBlockId.trim() }
            }
          }
      }
    } catch (caughtError) {
      return invalid(caughtError instanceof Error ? caughtError.message : t('developer.composer.errorInvalidJson'))
    }
  }

  const result = composerResult?.result
  const feedback = result
    ? t(
      result.ok
        ? composerResult.mode === 'apply'
          ? 'developer.commandFeedbackApplyOk'
          : 'developer.commandFeedbackPreviewOk'
        : composerResult.mode === 'apply'
          ? 'developer.commandFeedbackApplyFailed'
          : 'developer.commandFeedbackPreviewFailed',
      result.ok
        ? { patches: String(result.patches.length), diagnostics: String(result.diagnostics.length) }
        : diagnosticCountParams(result)
    )
    : null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="document-operation-composer-dialog">
        <DialogHeader>
          <DialogTitle>{t('developer.composer.title')}</DialogTitle>
          <DialogDescription className="sr-only">
            {t('developer.composer.description')}
          </DialogDescription>
        </DialogHeader>

        <div className="document-operation-composer">
          <section className="document-operation-composer-section">
            <div className="document-operation-composer-grid">
              <label className="document-operation-composer-field">
                <span>{t('developer.composer.targetBlockId')}</span>
                <Input
                  aria-label={t('developer.composer.targetBlockId')}
                  value={targetBlockId}
                  onChange={(event) => setTargetBlockId(event.target.value)}
                />
              </label>

              <label className="document-operation-composer-field">
                <span>{t('developer.composer.operation')}</span>
                <select
                  aria-label={t('developer.composer.operation')}
                  className="document-operation-composer-select"
                  value={operationType}
                  onChange={handleOperationTypeChange}
                >
                  {OPERATION_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {t(option.labelKey)}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            {operationType === 'moveBlock' ? (
              <label className="document-operation-composer-field">
                <span>{t('developer.composer.anchorBlockId')}</span>
                <Input
                  aria-label={t('developer.composer.anchorBlockId')}
                  value={anchorBlockId}
                  onChange={(event) => setAnchorBlockId(event.target.value)}
                />
              </label>
            ) : null}
          </section>

          {operationType === 'insertBlock' || operationType === 'replaceBlock' ? (
            <section className="document-operation-composer-section">
              <div className="document-operation-composer-grid">
                <label className="document-operation-composer-field">
                  <span>{t('developer.composer.newBlockId')}</span>
                <Input
                  aria-label={t('developer.composer.newBlockId')}
                  value={newBlockId}
                  onChange={(event) => {
                    setNewBlockId(event.target.value)
                    setBlockJson(prettyJson(blockTemplate(event.target.value, selectedBlockType, templateForm)))
                  }}
                />
                </label>

                <label className="document-operation-composer-field">
                  <span>{t('developer.composer.blockType')}</span>
                  <select
                    aria-label={t('developer.composer.blockType')}
                    className="document-operation-composer-select"
                    value={blockTypeSelection}
                    onChange={handleBlockTypeChange}
                  >
                    {STRUCTURED_BLOCK_TEMPLATE_TYPES.map((type) => (
                      <option key={type} value={type}>{type}</option>
                    ))}
                    <option value={RAW_BLOCK_TYPE_OPTION}>
                      {t('developer.composer.rawBlockTypeOption')}
                    </option>
                  </select>
                </label>
              </div>

              {blockTypeSelection === RAW_BLOCK_TYPE_OPTION ? (
                <label className="document-operation-composer-field">
                  <span>{t('developer.composer.rawBlockType')}</span>
                  <select
                    aria-label={t('developer.composer.rawBlockType')}
                    className="document-operation-composer-select"
                    value={rawBlockType}
                    onChange={(event) => handleRawBlockTypeChange(event.target.value)}
                  >
                    {RAW_BLOCK_TYPE_OPTIONS.map((type) => (
                      <option key={type} value={type}>{type}</option>
                    ))}
                  </select>
                </label>
              ) : null}

              {blockTypeSelection === RAW_BLOCK_TYPE_OPTION ? null : (
                <BlockTemplateFields
                  blockType={blockTypeSelection}
                  form={templateForm}
                  onChange={updateTemplateForm}
                />
              )}

              <Button type="button" variant="outline" size="sm" onClick={applyTemplate}>
                <Wand2 aria-hidden="true" />
                {t('developer.composer.useTemplate')}
              </Button>
            </section>
          ) : null}

          <section className="document-operation-composer-section">
            {operationType === 'insertBlock' || operationType === 'replaceBlock' ? (
              <JsonTextarea
                label={t('developer.composer.blockJson')}
                value={blockJson}
                onChange={setBlockJson}
              />
            ) : null}
            {operationType === 'updateBlockAttrs' ? (
              <JsonTextarea
                label={t('developer.composer.attrsJson')}
                value={attrsJson}
                onChange={setAttrsJson}
              />
            ) : null}
            {operationType === 'replaceBlockInlines' ? (
              <JsonTextarea
                label={t('developer.composer.inlinesJson')}
                value={inlinesJson}
                onChange={setInlinesJson}
              />
            ) : null}
            {operationType === 'replaceBlockChildren' ? (
              <JsonTextarea
                label={t('developer.composer.childrenJson')}
                value={childrenJson}
                onChange={setChildrenJson}
              />
            ) : null}
          </section>

          <section className="document-operation-composer-section">
            <JsonTextarea
              label={t('developer.composer.operationJson')}
              value={operationJson}
              onChange={() => undefined}
              readOnly
            />
          </section>

          {error ? (
            <div className="document-operation-composer-feedback is-error">{error}</div>
          ) : null}

          {feedback && result ? (
            <div className={`document-operation-composer-feedback ${result.ok ? 'is-success' : 'is-error'}`}>
              <span>{feedback}</span>
              <div className="document-operation-composer-result-list">
                {result.patches.map((patch) => (
                  <code key={`patch-${patch.operationIndex}-${patch.type}`}>{patch.message}</code>
                ))}
                {result.diagnostics.map((diagnostic) => (
                  <code key={`diagnostic-${diagnostic.operationIndex ?? 'x'}-${diagnostic.code}`}>
                    {diagnostic.message}
                  </code>
                ))}
              </div>
            </div>
          ) : null}
        </div>

        <DialogFooter>
          <Button type="button" variant="ghost" onClick={copyOperationJson}>
            <ClipboardCopy aria-hidden="true" />
            {t('developer.composer.copyJson')}
          </Button>
          <Button type="button" variant="outline" onClick={previewOperation}>
            {t('developer.composer.preview')}
          </Button>
          <Button type="button" onClick={applyOperation}>
            <Play aria-hidden="true" />
            {t('developer.composer.apply')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function invalid(message: string): ComposerValidation {
  return { ok: false, message }
}

function diagnosticCountParams(result: DocumentOperationResult): { errors: string; warnings: string } {
  return {
    errors: String(result.diagnostics.filter((diagnostic) => diagnostic.severity === 'error').length),
    warnings: String(result.diagnostics.filter((diagnostic) => diagnostic.severity === 'warning').length)
  }
}
