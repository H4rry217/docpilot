import { Braces, Save, Share2 } from 'lucide-react'
import { useI18n } from '../../../shared/i18n'
import { Button } from '../../../shared/ui/Button'

export function DocumentEditorToolbar({
  blockDebugMode,
  canSave,
  isSaving,
  onSave,
  onToggleBlockDebugMode
}: {
  blockDebugMode: boolean
  canSave: boolean
  isSaving: boolean
  onSave: () => void
  onToggleBlockDebugMode: () => void
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
      <Button
        variant="ghost"
        icon={<Braces size={14} />}
        className={`block-debug-toggle ${blockDebugMode ? 'is-active' : ''}`}
        aria-pressed={blockDebugMode}
        onClick={onToggleBlockDebugMode}
      >
        {t('editor.blockMode')}
      </Button>
    </div>
  )
}
