import type { JSONContent } from '@tiptap/core'
import { PanelRightClose, PanelRightOpen } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { BlockDocument } from '../../../entities/block/types'
import type { Workspace, WorkspaceTreeNode } from '../../../entities/workspace/types'
import { useI18n, type Locale } from '../../../shared/i18n'
import { usePersistentNumberState } from '../../../shared/ui/usePersistentNumberState'
import { getDocument, saveDocumentContent } from '../api/documentApi'
import { isBlockDocument, isProseMirrorDoc } from '../model/blockDocumentToProseMirror'
import {
  AiReviewPanel,
  AI_REVIEW_PANEL_DEFAULT_WIDTH,
  AI_REVIEW_PANEL_MAX_WIDTH,
  AI_REVIEW_PANEL_MIN_WIDTH
} from './AiReviewPanel'
import {
  BlockDocumentEditor,
  type BlockDocumentEditorHandle,
  type BlockDocumentEditorSnapshot,
  type BlockDocumentEditorSnapshotSource
} from './BlockDocumentEditor'
import { DocumentCanvas } from './DocumentCanvas'
import { DocumentEditorToolbar } from './DocumentEditorToolbar'
import { JsonInspector, type InspectorTab } from './JsonInspector'
import './DocumentEditor.css'

const AUTOSAVE_DELAY_MS = 650

type SaveState = 'idle' | 'dirty' | 'saving' | 'saved' | 'error'

type TextDialogRequest = {
  title: string
  label: string
  defaultValue: string
  confirmLabel: string
}

export type DocumentEditorProps = {
  workspace?: Workspace
  documentNode?: WorkspaceTreeNode
  onRequestText?: (input: TextDialogRequest) => Promise<string | undefined>
}

const EMPTY_PROSEMIRROR_DOC: JSONContent = { type: 'doc', content: [] }

const EMPTY_BLOCK_DOCUMENT: BlockDocument = {
  schemaVersion: 'docpilot-block/2',
  blocks: [],
  metadata: {
    source: 'frontend-empty'
  }
}

const EMPTY_EDITOR_SNAPSHOT: BlockDocumentEditorSnapshot = {
  proseMirrorJson: EMPTY_PROSEMIRROR_DOC,
  blockDocument: EMPTY_BLOCK_DOCUMENT
}

function formatUpdatedAt(value: string | undefined, locale: Locale): string | null {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return null
  return new Intl.DateTimeFormat(locale, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date)
}

function makeHtmlBlockId(): string {
  return `html${crypto.randomUUID().replaceAll('-', '')}`
}

function saveStateKey(saveState: SaveState) {
  switch (saveState) {
    case 'dirty':
      return 'editor.dirty'
    case 'saving':
      return 'editor.saving'
    case 'saved':
      return 'editor.saved'
    case 'error':
      return 'editor.saveFailed'
    case 'idle':
    default:
      return 'editor.saveIdle'
  }
}

export function DocumentEditor({ workspace, documentNode, onRequestText }: DocumentEditorProps) {
  const { locale, t } = useI18n()
  const [reviewPanelWidth, setReviewPanelWidth] = usePersistentNumberState({
    storageKey: 'docpilot.layout.reviewPanelWidth',
    defaultValue: AI_REVIEW_PANEL_DEFAULT_WIDTH,
    min: AI_REVIEW_PANEL_MIN_WIDTH,
    max: AI_REVIEW_PANEL_MAX_WIDTH
  })
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [saveError, setSaveError] = useState<string | null>(null)
  const [inspectorOpen, setInspectorOpen] = useState(false)
  const [inspectorTab, setInspectorTab] = useState<InspectorTab>('block')
  const [editorSnapshot, setEditorSnapshot] = useState<BlockDocumentEditorSnapshot>(EMPTY_EDITOR_SNAPSHOT)
  const [pastedJson, setPastedJson] = useState('')
  const [pasteError, setPasteError] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const blockEditorRef = useRef<BlockDocumentEditorHandle | null>(null)
  const latestSnapshotRef = useRef<BlockDocumentEditorSnapshot | null>(null)
  const documentIdRef = useRef<string | undefined>(undefined)
  const versionRef = useRef<string | null>(null)
  const autosaveTimerRef = useRef<number | undefined>(undefined)

  const documentId = documentNode?.documentId
  documentIdRef.current = documentId

  const documentQuery = useQuery({
    queryKey: ['document', documentId],
    enabled: Boolean(documentId),
    queryFn: () => getDocument(documentId ?? '')
  })

  const saveMutation = useMutation({
    mutationFn: saveDocumentContent,
    onMutate: () => {
      setSaveError(null)
      setSaveState('saving')
    },
    onSuccess: (response) => {
      versionRef.current = response.document.currentVersion
      queryClient.setQueryData(['document', response.document.documentId], response)
      setSaveError(null)
      setSaveState('saved')
    },
    onError: (error) => {
      setSaveState('error')
      setSaveError(error instanceof Error ? error.message : t('editor.saveFailed'))
    }
  })

  const saveBlockDocument = useCallback(
    (blockDocument: BlockDocument) => {
      const activeDocumentId = documentIdRef.current
      const baseVersion = versionRef.current
      if (!activeDocumentId || baseVersion == null) return

      saveMutation.mutate({
        documentId: activeDocumentId,
        blockDocument,
        baseVersion,
        clientMutationId: crypto.randomUUID()
      })
    },
    [saveMutation]
  )

  const queueAutosave = useCallback(
    (blockDocument: BlockDocument) => {
      window.clearTimeout(autosaveTimerRef.current)
      autosaveTimerRef.current = window.setTimeout(() => {
        saveBlockDocument(blockDocument)
      }, AUTOSAVE_DELAY_MS)
    },
    [saveBlockDocument]
  )

  const handleSnapshotChange = useCallback(
    (snapshot: BlockDocumentEditorSnapshot, source: BlockDocumentEditorSnapshotSource) => {
      latestSnapshotRef.current = snapshot
      setEditorSnapshot(snapshot)

      if (source === 'load') return

      setSaveError(null)
      setSaveState('dirty')
      queueAutosave(snapshot.blockDocument)
    },
    [queueAutosave]
  )

  function saveNow() {
    const snapshot = blockEditorRef.current?.getSnapshot() ?? latestSnapshotRef.current
    if (!snapshot) return
    window.clearTimeout(autosaveTimerRef.current)
    saveBlockDocument(snapshot.blockDocument)
  }

  async function insertHtmlBlock() {
    if (!onRequestText) return
    const source = await onRequestText({
      title: t('editor.htmlBlock'),
      label: t('editor.htmlPrompt'),
      defaultValue: '<div class="docpilot-html-block">hello</div>',
      confirmLabel: t('dialog.confirm')
    })
    if (!source) return
    const snapshot = blockEditorRef.current?.insertHtmlBlock({
      id: makeHtmlBlockId(),
      title: 'HTML',
      source,
      displayMode: 'fixed',
      fixedHeightPx: 320,
      allowScripts: false
    })
    if (snapshot) handleSnapshotChange(snapshot, 'programmatic')
  }

  function renderPastedJson() {
    try {
      const parsed: unknown = JSON.parse(pastedJson)
      const snapshot = isBlockDocument(parsed)
        ? blockEditorRef.current?.setBlockDocument(parsed)
        : isProseMirrorDoc(parsed)
          ? blockEditorRef.current?.setProseMirrorJson(parsed as JSONContent)
          : null

      if (!snapshot) {
        setPasteError(t('json.invalidShape'))
        return
      }

      setPasteError(null)
      handleSnapshotChange(snapshot, 'programmatic')
    } catch (error) {
      setPasteError(error instanceof Error ? error.message : t('json.parseFailed'))
    }
  }

  useEffect(() => {
    return () => window.clearTimeout(autosaveTimerRef.current)
  }, [])

  useEffect(() => {
    latestSnapshotRef.current = null
    setEditorSnapshot(EMPTY_EDITOR_SNAPSHOT)
    setSaveError(null)
    setSaveState(documentId ? 'idle' : 'idle')
  }, [documentId])

  useEffect(() => {
    if (!documentQuery.data) return
    versionRef.current = documentQuery.data.document.currentVersion
    setSaveError(null)
    setSaveState('saved')
  }, [documentQuery.data])

  const title = documentQuery.data?.document.title ?? documentNode?.name ?? t('editor.noDocument')
  const pathText = useMemo(() => {
    const workspaceName = workspace?.name ?? t('sidebar.workspace')
    return `${workspaceName} / ${title}`
  }, [title, t, workspace?.name])
  const updatedAt = formatUpdatedAt(documentQuery.data?.document.updateTime, locale) ?? t('editor.notSaved')
  const saveMessage = saveState === 'error' && saveError ? saveError : t(saveStateKey(saveState))
  const blockJsonText = useMemo(
    () => JSON.stringify(editorSnapshot.blockDocument, null, 2),
    [editorSnapshot.blockDocument]
  )
  const editorJsonText = useMemo(
    () => JSON.stringify(editorSnapshot.proseMirrorJson, null, 2),
    [editorSnapshot.proseMirrorJson]
  )
  const contentKey = documentQuery.data?.document.documentId

  return (
    <main
      className="editor-layout"
      style={{ gridTemplateColumns: `minmax(0, 1fr) ${reviewPanelWidth}px` }}
    >
      <header className="document-header">
        <div className="document-meta">
          <div className="breadcrumb">{pathText}</div>
          <div className="document-subtitle">
            <span>{t('editor.updated', { time: updatedAt })}</span>
            <span className={`save-indicator save-${saveState}`}>{saveMessage}</span>
            <span className="document-side-note">{t('editor.sideNote')}</span>
          </div>
        </div>
        <DocumentEditorToolbar
          canSave={Boolean(documentId && latestSnapshotRef.current)}
          isSaving={saveMutation.isPending}
          inspectorOpen={inspectorOpen}
          onSave={saveNow}
          onInsertHtmlBlock={insertHtmlBlock}
          onToggleInspector={() => setInspectorOpen((open) => !open)}
        />
      </header>

      <DocumentCanvas
        emptyMessage={t('editor.selectOrCreate')}
        loadingMessage={t('editor.opening')}
        errorMessage={t('editor.loadFailed')}
        hasDocument={Boolean(documentId)}
        isLoading={documentQuery.isLoading}
        hasError={Boolean(documentQuery.error)}
      >
        <BlockDocumentEditor
          ref={blockEditorRef}
          contentKey={contentKey}
          blockDocument={documentQuery.data?.document.content.blockDocument}
          proseMirrorFallback={documentQuery.data?.prosemirror as JSONContent | undefined}
          onSnapshotChange={handleSnapshotChange}
        />
      </DocumentCanvas>

      <AiReviewPanel width={reviewPanelWidth} onWidthChange={setReviewPanelWidth} />

      <button
        className="right-collapse"
        type="button"
        style={{ right: `${reviewPanelWidth + 6}px` }}
        aria-label={inspectorOpen ? t('json.hide') : t('json.show')}
        title={inspectorOpen ? t('json.hide') : t('json.show')}
        onClick={() => setInspectorOpen((open) => !open)}
      >
        {inspectorOpen ? <PanelRightClose size={15} /> : <PanelRightOpen size={15} />}
      </button>
      <JsonInspector
        open={inspectorOpen}
        activeTab={inspectorTab}
        blockJsonText={blockJsonText}
        editorJsonText={editorJsonText}
        pastedJson={pastedJson}
        pasteError={pasteError}
        onClose={() => setInspectorOpen(false)}
        onTabChange={setInspectorTab}
        onPastedJsonChange={(value) => {
          setPastedJson(value)
          setPasteError(null)
        }}
        onRenderPastedJson={renderPastedJson}
      />
    </main>
  )
}
