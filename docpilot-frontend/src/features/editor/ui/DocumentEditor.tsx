import type { JSONContent } from '@tiptap/core'
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties
} from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  documentOutlineFromBlockDocument,
  type DocumentOutlineItem,
  type DocumentOutlineJumpRequest
} from '../../../entities/block/outline'
import type { BlockDocument } from '../../../entities/block/types'
import type { Workspace, WorkspaceTreeNode } from '../../../entities/workspace/types'
import { useI18n, type Locale } from '../../../shared/i18n'
import { getDocument, saveDocumentContent } from '../api/documentApi'
import { useAiWorkspaceLayout } from '../model/aiWorkspaceLayout'
import {
  TOOL_PANEL_BOTTOM_COLLAPSED_HEIGHT,
  useToolPanelLayout
} from '../model/toolPanelLayout'
import { AiWorkspace } from './AiWorkspace'
import {
  BlockDocumentEditor,
  type BlockDocumentEditorHandle,
  type BlockDocumentEditorSnapshot,
  type BlockDocumentEditorSnapshotSource
} from '../../block-editor'
import { blockDocumentForSave } from '../../block-editor/model/proseMirrorToBlockDocument'
import { DocumentCanvas } from './DocumentCanvas'
import { DocumentEditorToolbar } from './DocumentEditorToolbar'
import { DocumentOutlineNav } from './DocumentOutlineNav'
import { useDocumentOperationsConsoleState } from './DocumentOperationsConsole'
import { WorkbenchToolPanels } from './WorkbenchToolPanels'
import './DocumentEditor.css'

const AUTOSAVE_DELAY_MS = 5000
const OUTLINE_AUTO_COLLAPSE_CANVAS_WIDTH = 1230
type SaveState = 'idle' | 'dirty' | 'saving' | 'saved' | 'error'
type InlineCompletionRuntimeSettings = {
  enabled: boolean
  idleDelayMs: number
  candidateCount: number
}

export type DocumentEditorProps = {
  developerMode?: boolean
  workspace?: Workspace
  documentNode?: WorkspaceTreeNode
  outline: DocumentOutlineItem[]
  activeOutlineId?: string
  outlineJumpRequest?: DocumentOutlineJumpRequest
  inlineCompletionSettings?: InlineCompletionRuntimeSettings
  onOutlineChange?: (outline: DocumentOutlineItem[]) => void
  onSelectOutlineItem: (item: DocumentOutlineItem) => void
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

export function DocumentEditor({
  developerMode = false,
  workspace,
  documentNode,
  outline,
  activeOutlineId,
  outlineJumpRequest,
  inlineCompletionSettings,
  onOutlineChange,
  onSelectOutlineItem
}: DocumentEditorProps) {
  const { locale, t } = useI18n()
  const aiWorkspaceLayout = useAiWorkspaceLayout()
  const toolPanelLayout = useToolPanelLayout()
  const documentOperationsConsole = useDocumentOperationsConsoleState()
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [saveError, setSaveError] = useState<string | null>(null)
  const [blockDebugMode, setBlockDebugMode] = useState(false)
  const [outlineCollapsed, setOutlineCollapsed] = useState(false)
  const [outlineCompact, setOutlineCompact] = useState(false)
  const queryClient = useQueryClient()
  const editorLayoutRef = useRef<HTMLElement | null>(null)
  const blockEditorRef = useRef<BlockDocumentEditorHandle | null>(null)
  const latestSnapshotRef = useRef<BlockDocumentEditorSnapshot | null>(null)
  const documentIdRef = useRef<string | undefined>(undefined)
  const versionRef = useRef<string | null>(null)
  const autosaveTimerRef = useRef<number | undefined>(undefined)

  const documentId = documentNode?.documentId
  const hasDocument = Boolean(documentId)
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
    (blockDocument: BlockDocument, expectedDocumentId = documentIdRef.current) => {
      const activeDocumentId = documentIdRef.current
      const baseVersion = versionRef.current
      if (!activeDocumentId || activeDocumentId !== expectedDocumentId || baseVersion == null) return

      saveMutation.mutate({
        documentId: activeDocumentId,
        blockDocument: blockDocumentForSave(blockDocument),
        baseVersion,
        clientMutationId: crypto.randomUUID()
      })
    },
    [saveMutation]
  )

  const queueAutosave = useCallback(
    (blockDocument: BlockDocument) => {
      const queuedDocumentId = documentIdRef.current
      window.clearTimeout(autosaveTimerRef.current)
      autosaveTimerRef.current = window.setTimeout(() => {
        saveBlockDocument(blockDocument, queuedDocumentId)
      }, AUTOSAVE_DELAY_MS)
    },
    [saveBlockDocument]
  )

  const handleSnapshotChange = useCallback(
    (snapshot: BlockDocumentEditorSnapshot, source: BlockDocumentEditorSnapshotSource) => {
      latestSnapshotRef.current = snapshot
      onOutlineChange?.(documentOutlineFromBlockDocument(snapshot.blockDocument))

      if (source === 'load') return

      setSaveError(null)
      setSaveState('dirty')
      queueAutosave(snapshot.blockDocument)
    },
    [onOutlineChange, queueAutosave]
  )

  function saveNow() {
    const snapshot = blockEditorRef.current?.getSnapshot() ?? latestSnapshotRef.current
    if (!snapshot) return
    window.clearTimeout(autosaveTimerRef.current)
    saveBlockDocument(snapshot.blockDocument)
  }

  const getDeveloperBlockDocument = useCallback(() => {
    const snapshot = blockEditorRef.current?.getSnapshot() ?? latestSnapshotRef.current
    return snapshot?.blockDocument ?? documentQuery.data?.document.content.blockDocument ?? null
  }, [documentQuery.data])

  const jumpToDeveloperBlock = useCallback((blockId: string) => {
    blockEditorRef.current?.scrollToOutlineItem({
      id: blockId,
      headingIndex: 0,
      requestId: Date.now()
    })
  }, [])

  const applyDeveloperBlockDocument = useCallback((blockDocument: BlockDocument) => {
    blockEditorRef.current?.setBlockDocument(blockDocument)
  }, [])

  useEffect(() => {
    return () => window.clearTimeout(autosaveTimerRef.current)
  }, [])

  useEffect(() => {
    window.clearTimeout(autosaveTimerRef.current)
    latestSnapshotRef.current = null
    setSaveError(null)
    setSaveState(documentId ? 'idle' : 'idle')

    if (!documentId) {
      onOutlineChange?.([])
    }
  }, [documentId, onOutlineChange])

  useEffect(() => {
    if (!documentQuery.data) return
    versionRef.current = documentQuery.data.document.currentVersion
    setSaveError(null)
    setSaveState('saved')
    onOutlineChange?.(documentOutlineFromBlockDocument(documentQuery.data.document.content.blockDocument))
  }, [documentQuery.data, onOutlineChange])

  useEffect(() => {
    if (!outlineJumpRequest) return
    blockEditorRef.current?.scrollToOutlineItem(outlineJumpRequest)
  }, [outlineJumpRequest])

  useEffect(() => {
    if (!developerMode) {
      setBlockDebugMode(false)
    }
  }, [developerMode])

  useEffect(() => {
    if (!hasDocument) return
    const canvas = editorLayoutRef.current?.querySelector<HTMLElement>('.document-main')
    if (!canvas) return

    function updateOutlineLayout(width: number) {
      const compact = width < OUTLINE_AUTO_COLLAPSE_CANVAS_WIDTH
      setOutlineCompact(compact)
      if (compact) {
        setOutlineCollapsed(true)
      }
    }

    updateOutlineLayout(canvas.getBoundingClientRect().width)

    if (typeof ResizeObserver === 'undefined') {
      const handleResize = () => updateOutlineLayout(canvas.getBoundingClientRect().width)
      window.addEventListener('resize', handleResize)
      return () => window.removeEventListener('resize', handleResize)
    }

    const observer = new ResizeObserver((entries) => {
      updateOutlineLayout(entries[0]?.contentRect.width ?? canvas.getBoundingClientRect().width)
    })
    observer.observe(canvas)
    return () => observer.disconnect()
  }, [hasDocument, documentId])

  const title = documentQuery.data?.document.title ?? documentNode?.name ?? t('editor.noDocument')
  const pathText = useMemo(() => {
    const workspaceName = workspace?.name ?? t('sidebar.workspace')
    return `${workspaceName} / ${title}`
  }, [title, t, workspace?.name])
  const updatedAt = formatUpdatedAt(documentQuery.data?.document.updateTime, locale) ?? t('editor.notSaved')
  const saveMessage = saveState === 'error' && saveError ? saveError : t(saveStateKey(saveState))
  const contentKey = documentQuery.data?.document.documentId
  const aiWorkspaceDockedOpen = hasDocument
    && aiWorkspaceLayout.state.dockMode === 'docked'
    && !aiWorkspaceLayout.state.minimized
  const aiWorkspaceDockedMinimized = hasDocument
    && aiWorkspaceLayout.state.dockMode === 'docked'
    && aiWorkspaceLayout.state.minimized
  const documentOperationsInBottom = developerMode
    && hasDocument
    && toolPanelLayout.state.placements.documentOperations === 'bottom'
  const editorLayoutStyle = {
    '--ai-workspace-dock-width': `${aiWorkspaceLayout.state.dockWidth}px`,
    '--bottom-tool-panel-height': `${
      toolPanelLayout.state.bottomCollapsed
        ? TOOL_PANEL_BOTTOM_COLLAPSED_HEIGHT
        : toolPanelLayout.state.bottomHeight
    }px`
  } as CSSProperties & Record<string, string>

  return (
    <main
      ref={editorLayoutRef}
      className={`editor-layout ${hasDocument ? '' : 'is-empty'} ${documentOperationsInBottom ? 'has-bottom-tool-panel' : ''} ${toolPanelLayout.state.bottomCollapsed ? 'bottom-tool-panel-collapsed' : ''} ${aiWorkspaceDockedOpen ? 'has-ai-dock' : ''} ${aiWorkspaceDockedMinimized ? 'has-ai-rail' : ''} ${outlineCompact ? 'outline-compact' : ''}`}
      style={editorLayoutStyle}
    >
      {hasDocument ? (
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
            blockDebugMode={blockDebugMode}
            developerMode={developerMode}
            canSave={Boolean(documentId && latestSnapshotRef.current)}
            isSaving={saveMutation.isPending}
            onSave={saveNow}
            onToggleBlockDebugMode={() => setBlockDebugMode((enabled) => !enabled)}
          />
        </header>
      ) : null}

      <div className="document-main">
        {hasDocument ? (
          <DocumentOutlineNav
            title={title}
            outline={outline}
            activeOutlineId={activeOutlineId}
            collapsed={outlineCollapsed}
            onToggleCollapsed={() => setOutlineCollapsed((value) => !value)}
            onSelectOutlineItem={onSelectOutlineItem}
          />
        ) : null}

        <DocumentCanvas
          emptyMessage={t('editor.selectOrCreate')}
          loadingMessage={t('editor.opening')}
          errorMessage={t('editor.loadFailed')}
          hasDocument={hasDocument}
          isLoading={documentQuery.isLoading}
          hasError={Boolean(documentQuery.error)}
        >
          <BlockDocumentEditor
            ref={blockEditorRef}
            contentKey={contentKey}
            blockDocument={documentQuery.data?.document.content.blockDocument}
            debugMode={blockDebugMode}
            inlineCompletionContext={{
              workspaceId: workspace?.workspaceId,
              documentId,
              clientVersion: 'web-0.1.0'
            }}
            inlineCompletionSettings={inlineCompletionSettings}
            proseMirrorFallback={documentQuery.data?.prosemirror as JSONContent | undefined}
            onSnapshotChange={handleSnapshotChange}
          />
        </DocumentCanvas>
      </div>

      {hasDocument ? (
        <AiWorkspace layout={aiWorkspaceLayout} />
      ) : null}

      {developerMode && hasDocument ? (
        <WorkbenchToolPanels
          consoleController={documentOperationsConsole}
          getBlockDocument={getDeveloperBlockDocument}
          getDocumentVersion={() => versionRef.current}
          layout={toolPanelLayout}
          onApplyBlockDocument={applyDeveloperBlockDocument}
          onJumpToBlock={jumpToDeveloperBlock}
        />
      ) : null}
    </main>
  )
}
