import { Activity, Braces, CheckCircle2, Code2, PanelRightClose, Save, Share2 } from 'lucide-react'
import { useI18n } from '../../../shared/i18n'
import { Button } from '../../../shared/ui/Button'

export function DocumentEditorToolbar({
  canSave,
  isSaving,
  inspectorOpen,
  onSave,
  onInsertHtmlBlock,
  onToggleInspector
}: {
  canSave: boolean
  isSaving: boolean
  inspectorOpen: boolean
  onSave: () => void
  onInsertHtmlBlock: () => void
  onToggleInspector: () => void
}) {
  const { t } = useI18n()

  return (
    <div className="document-actions">
      <Button variant="ghost" icon={<Save size={14} />} disabled={!canSave || isSaving} onClick={onSave}>
        {t('editor.save')}
      </Button>
      <Button variant="ghost" icon={<Share2 size={14} />}>
        {t('editor.share')}
      </Button>
      <span className="runtime-pill">
        <CheckCircle2 size={13} />
        {t('editor.node')}
      </span>
      <span className="runtime-pill">
        <CheckCircle2 size={13} />
        {t('editor.java')}
      </span>
      <Button variant="ghost" icon={<Braces size={14} />}>
        {t('editor.blockMode')}
      </Button>
      <Button variant="ghost" icon={<Code2 size={14} />} onClick={onInsertHtmlBlock}>
        {t('editor.htmlBlock')}
      </Button>
      <Button
        variant="ghost"
        icon={inspectorOpen ? <PanelRightClose size={14} /> : <Activity size={14} />}
        onClick={onToggleInspector}
      >
        {t('editor.statusMonitor')}
      </Button>
    </div>
  )
}
