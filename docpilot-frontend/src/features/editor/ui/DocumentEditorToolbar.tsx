import { Braces, Save, Share2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useI18n } from '@/shared/i18n'

export function DocumentEditorToolbar({
  blockDebugMode,
  developerMode,
  canSave,
  isSaving,
  onSave,
  onToggleBlockDebugMode
}: {
  blockDebugMode: boolean
  developerMode: boolean
  canSave: boolean
  isSaving: boolean
  onSave: () => void
  onToggleBlockDebugMode: () => void
}) {
  const { t } = useI18n()

  return (
    <div className="flex flex-wrap items-center justify-end gap-1.5">
      <Button variant="outline" disabled={!canSave || isSaving} onClick={onSave}>
        <Save data-icon="inline-start" />
        {t('editor.save')}
      </Button>
      <Button variant="outline">
        <Share2 data-icon="inline-start" />
        {t('editor.share')}
      </Button>
      {developerMode ? (
        <Button
          variant="outline"
          className={cn(blockDebugMode && 'border-primary/30 bg-primary/10 text-primary')}
          aria-pressed={blockDebugMode}
          onClick={onToggleBlockDebugMode}
        >
          <Braces data-icon="inline-start" />
          {t('editor.blockMode')}
        </Button>
      ) : null}
    </div>
  )
}
