import type { JSONContent } from '@tiptap/core'
import { type Editor, EditorContent, useEditor } from '@tiptap/react'
import { Braces, PanelRightClose, PanelRightOpen, Save, Share2 } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Workspace, WorkspaceTreeNode } from '../../../entities/workspace/types'
import { Button } from '../../../shared/ui/Button'
import { getDocument, saveDocumentContent } from '../api/documentApi'
import {
  blockDocumentToProseMirrorJson,
  isBlockDocument,
  isProseMirrorDoc
} from '../model/blockDocumentToProseMirror'
import { editorExtensions } from '../model/extensions'
import { htmlToMarkdown } from '../model/markdown'
import { proseMirrorJsonToBlockDocument } from '../model/proseMirrorToBlockDocument'

const AUTOSAVE_DELAY_MS = 650

type SaveState = 'idle' | 'dirty' | 'saving' | 'saved' | 'error'
type InspectorTab = 'block' | 'editor' | 'paste'

export type DocumentEditorProps = {
  workspace?: Workspace
  documentNode?: WorkspaceTreeNode
}

function formatUpdatedAt(value?: string): string {
  if (!value) return '尚未保存'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '尚未保存'
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hours = `${date.getHours()}`.padStart(2, '0')
  const minutes = `${date.getMinutes()}`.padStart(2, '0')
  return `${month}月${day}日 ${hours}:${minutes}`
}

function makeHtmlBlockId(): string {
  return `html${crypto.randomUUID().replaceAll('-', '')}`
}

export function DocumentEditor({ workspace, documentNode }: DocumentEditorProps) {
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [saveMessage, setSaveMessage] = useState('已保存')
  const [inspectorOpen, setInspectorOpen] = useState(false)
  const [inspectorTab, setInspectorTab] = useState<InspectorTab>('block')
  const [editorJson, setEditorJson] = useState<JSONContent>({ type: 'doc', content: [] })
  const [pastedJson, setPastedJson] = useState('')
  const [pasteError, setPasteError] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const documentIdRef = useRef<string | undefined>(undefined)
  const versionRef = useRef<number | null>(null)
  const autosaveTimerRef = useRef<number | undefined>(undefined)
  const settingContentRef = useRef(false)

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
      setSaveState('saving')
      setSaveMessage('保存中')
    },
    onSuccess: (response) => {
      versionRef.current = response.document.version
      queryClient.setQueryData(['document', response.document.documentId], response)
      setSaveState('saved')
      setSaveMessage('已保存')
    },
    onError: (error) => {
      setSaveState('error')
      setSaveMessage(error instanceof Error ? error.message : '保存失败')
    }
  })

  function saveEditorContent(activeEditor: Editor) {
    const activeDocumentId = documentIdRef.current
    const expectedVersion = versionRef.current
    if (!activeDocumentId || expectedVersion == null) return

    saveMutation.mutate({
      documentId: activeDocumentId,
      markdown: htmlToMarkdown(activeEditor.getHTML()),
      expectedVersion
    })
  }

  function queueAutosave(activeEditor: Editor) {
    window.clearTimeout(autosaveTimerRef.current)
    autosaveTimerRef.current = window.setTimeout(() => {
      saveEditorContent(activeEditor)
    }, AUTOSAVE_DELAY_MS)
  }

  function saveNow() {
    if (!editor) return
    window.clearTimeout(autosaveTimerRef.current)
    saveEditorContent(editor)
  }

  function refreshEditorJson(activeEditor: Editor) {
    setEditorJson(activeEditor.getJSON())
  }

  const editor = useEditor({
    extensions: editorExtensions,
    content: '',
    editorProps: {
      attributes: {
        class: 'prose-editor',
        spellcheck: 'false'
      }
    },
    onUpdate: ({ editor: activeEditor }) => {
      const activeDocumentId = documentIdRef.current
      if (settingContentRef.current || !activeDocumentId) return
      refreshEditorJson(activeEditor)
      setSaveState('dirty')
      setSaveMessage('有未保存修改')
      queueAutosave(activeEditor)
    }
  })

  useEffect(() => {
    return () => window.clearTimeout(autosaveTimerRef.current)
  }, [])

  useEffect(() => {
    if (!editor) return
    const sync = () => refreshEditorJson(editor)
    sync()
    editor.on('transaction', sync)
    return () => {
      editor.off('transaction', sync)
    }
  }, [editor])

  useEffect(() => {
    if (!editor || !inspectorOpen) return
    const timer = window.setInterval(() => refreshEditorJson(editor), 250)
    return () => window.clearInterval(timer)
  }, [editor, inspectorOpen])

  useEffect(() => {
    if (!editor || !documentQuery.data) return
    settingContentRef.current = true
    versionRef.current = documentQuery.data.document.version
    editor.commands.setContent(documentQuery.data.prosemirror as JSONContent, false)
    refreshEditorJson(editor)
    settingContentRef.current = false
    setSaveState('saved')
    setSaveMessage('已保存')
  }, [documentQuery.data, editor])

  const title = documentQuery.data?.document.title ?? documentNode?.name ?? '未选择文档'
  const pathText = useMemo(() => {
    const workspaceName = workspace?.name ?? '工作区'
    return `HarryZ › ${workspaceName} › ${title}`
  }, [title, workspace?.name])
  const updatedAt = formatUpdatedAt(documentQuery.data?.document.updateTime)
  const blockJsonText = useMemo(
    () => JSON.stringify(proseMirrorJsonToBlockDocument(editorJson), null, 2),
    [editorJson]
  )
  const editorJsonText = useMemo(() => JSON.stringify(editorJson, null, 2), [editorJson])

  function insertHtmlBlock() {
    if (!editor) return
    const source = window.prompt('输入 HTML Block 源码', '<div class="docpilot-html-block">hello</div>')
    if (!source) return
    editor
      .chain()
      .focus()
      .insertContent({
        type: 'docpilotHtmlBlock',
        attrs: {
          id: makeHtmlBlockId(),
          title: 'HTML',
          source,
          displayMode: 'fixed',
          fixedHeightPx: 320,
          allowScripts: false
        }
      })
      .run()
  }

  function renderPastedJson() {
    if (!editor) return
    try {
      const parsed: unknown = JSON.parse(pastedJson)
      const content = isBlockDocument(parsed)
        ? blockDocumentToProseMirrorJson(parsed)
        : isProseMirrorDoc(parsed)
          ? (parsed as JSONContent)
          : null

      if (!content) {
        setPasteError('请粘贴 BlockDocument 或 ProseMirror doc JSON')
        return
      }

      settingContentRef.current = true
      editor.commands.setContent(content, false)
      setEditorJson(editor.getJSON())
      settingContentRef.current = false
      setPasteError(null)
      setSaveState('dirty')
      setSaveMessage('粘贴内容尚未保存')
    } catch (error) {
      setPasteError(error instanceof Error ? error.message : 'JSON 解析失败')
    }
  }

  return (
    <main className="editor-layout">
      <header className="document-header">
        <div className="document-meta">
          <div className="breadcrumb">{pathText}</div>
          <div className="document-subtitle">
            最近修改: {updatedAt} · HarryZ
            <span className={`save-indicator save-${saveState}`}>{saveMessage}</span>
          </div>
        </div>
        <div className="document-actions">
          <Button
            variant="ghost"
            icon={<Save size={14} />}
            disabled={!editor || !documentId || saveMutation.isPending}
            onClick={saveNow}
          >
            保存
          </Button>
          <Button variant="ghost" icon={<Share2 size={14} />}>
            分享
          </Button>
          <Button
            variant="ghost"
            icon={inspectorOpen ? <PanelRightClose size={14} /> : <PanelRightOpen size={14} />}
            onClick={() => setInspectorOpen((open) => !open)}
          >
            JSON
          </Button>
          <Button variant="ghost" onClick={insertHtmlBlock}>
            插入 HTML Block
          </Button>
        </div>
      </header>

      <section className="document-canvas">
        {documentQuery.isLoading ? (
          <div className="document-empty">正在打开文档</div>
        ) : documentQuery.error ? (
          <div className="document-empty">文档加载失败</div>
        ) : (
          <EditorContent editor={editor} className="editor-content" />
        )}
      </section>

      <button
        className="right-collapse"
        type="button"
        aria-label={inspectorOpen ? '隐藏 JSON 面板' : '展开 JSON 面板'}
        onClick={() => setInspectorOpen((open) => !open)}
      >
        {inspectorOpen ? '›' : '‹'}
      </button>
      <aside className={`json-inspector ${inspectorOpen ? 'open' : ''}`} aria-label="JSON 调试面板">
        <header className="json-inspector-header">
          <div>
            <Braces size={17} />
            <strong>JSON 结构</strong>
          </div>
          <button type="button" onClick={() => setInspectorOpen(false)} aria-label="隐藏 JSON 面板">
            <PanelRightClose size={16} />
          </button>
        </header>

        <div className="json-inspector-tabs" role="tablist">
          <button
            className={inspectorTab === 'block' ? 'active' : ''}
            type="button"
            onClick={() => setInspectorTab('block')}
          >
            Block JSON
          </button>
          <button
            className={inspectorTab === 'editor' ? 'active' : ''}
            type="button"
            onClick={() => setInspectorTab('editor')}
          >
            Editor JSON
          </button>
          <button
            className={inspectorTab === 'paste' ? 'active' : ''}
            type="button"
            onClick={() => setInspectorTab('paste')}
          >
            粘贴渲染
          </button>
        </div>

        {inspectorTab === 'block' ? (
          <pre className="json-viewer">{blockJsonText}</pre>
        ) : null}
        {inspectorTab === 'editor' ? (
          <pre className="json-viewer">{editorJsonText}</pre>
        ) : null}
        {inspectorTab === 'paste' ? (
          <div className="json-paste">
            <textarea
              value={pastedJson}
              spellCheck={false}
              placeholder="粘贴 BlockDocument 或 ProseMirror doc JSON"
              onChange={(event) => {
                setPastedJson(event.target.value)
                setPasteError(null)
              }}
            />
            {pasteError ? <div className="json-paste-error">{pasteError}</div> : null}
            <Button variant="primary" onClick={renderPastedJson} disabled={!pastedJson.trim()}>
              渲染到编辑器
            </Button>
          </div>
        ) : null}
      </aside>
    </main>
  )
}
