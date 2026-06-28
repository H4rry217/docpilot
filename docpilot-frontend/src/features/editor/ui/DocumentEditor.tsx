import type { JSONContent } from '@tiptap/core'
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties
} from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  documentOutlineFromBlockDocument,
  type DocumentOutlineItem,
  type DocumentOutlineJumpRequest
} from '@/entities/block/outline'
import type { BlockDocument } from '@/entities/block/types'
import type { Workspace, WorkspaceTreeNode } from '@/entities/workspace/types'
import {
  BlockDocumentEditor,
  type BlockDocumentEditorHandle,
  type BlockDocumentEditorSnapshot,
  type BlockDocumentEditorSnapshotSource
} from '@/features/block-editor'
import { useI18n, type Locale } from '@/shared/i18n'
import { getDocument } from '../api/documentApi'
import { useAiWorkspaceLayout } from '../model/aiWorkspaceLayout'
import {
  saveStateKey,
  useDocumentSave
} from '../model/useDocumentSave'
import {
  TOOL_PANEL_BOTTOM_COLLAPSED_HEIGHT,
  useToolPanelLayout
} from '../model/toolPanelLayout'
import { AiWorkspace } from './AiWorkspace'
import { DocumentCanvas } from './DocumentCanvas'
import { DocumentEditorToolbar } from './DocumentEditorToolbar'
import { DocumentOutlineNav } from './DocumentOutlineNav'
import { useDocumentOperationsConsoleState } from './DocumentOperationsConsole'
import { WorkbenchToolPanels } from './WorkbenchToolPanels'
import './DocumentEditor.css'

const OUTLINE_AUTO_COLLAPSE_CANVAS_WIDTH = 1230
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

export function formatUpdatedAt(value: number | undefined, locale: Locale): string | null {
  if (value === undefined) return null
  const date = new Date(value * 1000)
  if (Number.isNaN(date.getTime())) return null
  return new Intl.DateTimeFormat(locale, {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date)
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
  const [blockDebugMode, setBlockDebugMode] = useState(false)
  const [outlineCollapsed, setOutlineCollapsed] = useState(false)
  const [outlineCompact, setOutlineCompact] = useState(false)
  const queryClient = useQueryClient()
  const editorLayoutRef = useRef<HTMLElement | null>(null)
  const blockEditorRef = useRef<BlockDocumentEditorHandle | null>(null)
  const latestSnapshotRef = useRef<BlockDocumentEditorSnapshot | null>(null)

  const documentId = documentNode?.documentId
  const hasDocument = Boolean(documentId)

  const documentQuery = useQuery({
    queryKey: ['document', documentId],
    enabled: Boolean(documentId),
    queryFn: () => getDocument(documentId ?? '')
  })

  const getSnapshotForSave = useCallback(() => (
    blockEditorRef.current?.getSnapshot() ?? latestSnapshotRef.current
  ), [])

  const onSavedDocumentData = useCallback((response: typeof documentQuery.data) => {
    if (!response) return
    queryClient.setQueryData(['document', response.document.documentId], response)
  }, [queryClient])

  const onOutlineFromDocument = useCallback((blockDocument: BlockDocument) => {
    onOutlineChange?.(documentOutlineFromBlockDocument(blockDocument))
  }, [onOutlineChange])

  const translateSaveFailed = useCallback((error: unknown) => (
    error instanceof Error ? error.message : t('editor.saveFailed')
  ), [t])

  const {
    getDocumentVersion,
    isSaving,
    queueAutosave,
    registerLoadedDocument,
    saveError,
    saveNow,
    saveState
  } = useDocumentSave({
    documentId,
    getSnapshot: getSnapshotForSave,
    onOutlineFromDocument,
    onSavedDocumentData,
    translateSaveFailed
  })

  const handleSnapshotChange = useCallback(
    (snapshot: BlockDocumentEditorSnapshot, source: BlockDocumentEditorSnapshotSource) => {
      latestSnapshotRef.current = snapshot
      onOutlineChange?.(documentOutlineFromBlockDocument(snapshot.blockDocument))

      if (source === 'load') return

      queueAutosave(snapshot.blockDocument)
    },
    [onOutlineChange, queueAutosave]
  )

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
    latestSnapshotRef.current = null

    if (!documentId) {
      onOutlineChange?.([])
    }
  }, [documentId, onOutlineChange])

  useEffect(() => {
    if (!documentQuery.data) return
    registerLoadedDocument(documentQuery.data.document)
  }, [documentQuery.data, registerLoadedDocument])

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
  const outlineOpen = hasDocument && !outlineCollapsed
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
      className={`editor-layout ${hasDocument ? '' : 'is-empty'} ${documentOperationsInBottom ? 'has-bottom-tool-panel' : ''} ${toolPanelLayout.state.bottomCollapsed ? 'bottom-tool-panel-collapsed' : ''} ${aiWorkspaceDockedOpen ? 'has-ai-dock' : ''} ${aiWorkspaceDockedMinimized ? 'has-ai-rail' : ''} ${outlineCompact ? 'outline-compact' : ''} ${outlineOpen ? 'outline-open' : ''}`}
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
            isSaving={isSaving}
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
          getDocumentVersion={getDocumentVersion}
          layout={toolPanelLayout}
          onApplyBlockDocument={applyDeveloperBlockDocument}
          onJumpToBlock={jumpToDeveloperBlock}
        />
      ) : null}
    </main>
  )
}
