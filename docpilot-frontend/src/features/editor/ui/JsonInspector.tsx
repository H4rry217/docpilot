import type { Dispatch, SetStateAction } from 'react'
import { Braces, PanelRightClose } from 'lucide-react'
import { useI18n } from '../../../shared/i18n'
import { Button } from '../../../shared/ui/Button'

export type InspectorTab = 'block' | 'editor' | 'paste'

export function JsonInspector({
  open,
  activeTab,
  blockJsonText,
  editorJsonText,
  pastedJson,
  pasteError,
  onClose,
  onTabChange,
  onPastedJsonChange,
  onRenderPastedJson
}: {
  open: boolean
  activeTab: InspectorTab
  blockJsonText: string
  editorJsonText: string
  pastedJson: string
  pasteError: string | null
  onClose: () => void
  onTabChange: (tab: InspectorTab) => void
  onPastedJsonChange: Dispatch<SetStateAction<string>>
  onRenderPastedJson: () => void
}) {
  const { t } = useI18n()

  return (
    <aside className={`json-inspector ${open ? 'open' : ''}`} aria-label={t('json.title')}>
      <header className="json-inspector-header">
        <div>
          <Braces size={17} />
          <strong>{t('json.title')}</strong>
        </div>
        <button type="button" onClick={onClose} aria-label={t('json.hide')} title={t('json.hide')}>
          <PanelRightClose size={16} />
        </button>
      </header>

      <div className="json-inspector-tabs" role="tablist">
        <button
          className={activeTab === 'block' ? 'active' : ''}
          type="button"
          onClick={() => onTabChange('block')}
        >
          {t('json.block')}
        </button>
        <button
          className={activeTab === 'editor' ? 'active' : ''}
          type="button"
          onClick={() => onTabChange('editor')}
        >
          {t('json.editor')}
        </button>
        <button
          className={activeTab === 'paste' ? 'active' : ''}
          type="button"
          onClick={() => onTabChange('paste')}
        >
          {t('json.paste')}
        </button>
      </div>

      {activeTab === 'block' ? <pre className="json-viewer">{blockJsonText}</pre> : null}
      {activeTab === 'editor' ? <pre className="json-viewer">{editorJsonText}</pre> : null}
      {activeTab === 'paste' ? (
        <div className="json-paste">
          <textarea
            value={pastedJson}
            spellCheck={false}
            placeholder={t('json.placeholder')}
            onChange={(event) => {
              onPastedJsonChange(event.target.value)
            }}
          />
          {pasteError ? <div className="json-paste-error">{pasteError}</div> : null}
          <Button variant="primary" onClick={onRenderPastedJson} disabled={!pastedJson.trim()}>
            {t('json.render')}
          </Button>
        </div>
      ) : null}
    </aside>
  )
}
