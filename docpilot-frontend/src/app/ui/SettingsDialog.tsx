import { KeyRound, SlidersHorizontal, Sparkles } from 'lucide-react'
import { useEffect, useState, type KeyboardEvent, type ReactNode } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import type { UserInformation } from '../../entities/user/types'
import { ProfileControls } from '../../features/auth/ui/ProfileControls'
import type {
  InlineCompletionCloudSettings,
  InlineCompletionTokenKey
} from '../model/useCloudUserSettings'
import { useI18n, type Locale } from '../../shared/i18n'
import { SelectField } from '../../shared/ui/SelectField'

type SettingsSection = 'general' | 'completion' | 'login'

type SettingsDialogProps = {
  user: UserInformation
  locale: Locale
  developerMode: boolean
  inlineCompletion: InlineCompletionCloudSettings
  settingsSaving: boolean
  settingsError: string | null
  onClose: () => void
  onLogout: () => void
  onLocaleChange: (locale: Locale) => void
  onDeveloperModeChange: (enabled: boolean) => void
  onInlineCompletionEnabledChange: (enabled: boolean) => void
  onInlineCompletionIdleDelayChange: (delayMs: number) => void
  onInlineCompletionCandidateCountChange: (candidateCount: number) => void
  onInlineCompletionMaxOutputTokensChange: (key: InlineCompletionTokenKey, value: number) => void
  onUserChange: (user: UserInformation) => void
}

type SettingsNavItem = {
  id: SettingsSection
  icon: ReactNode
  label: string
}

function SettingRow({
  label,
  children
}: {
  label: string
  children: ReactNode
}) {
  return (
    <div className="grid min-h-14 grid-cols-[minmax(0,1fr)_minmax(12rem,auto)] items-center gap-5 border-t py-3">
      <span className="truncate text-sm font-medium text-foreground">{label}</span>
      <div className="justify-self-end">{children}</div>
    </div>
  )
}

function SettingsSectionShell({
  title,
  children
}: {
  title: string
  children: ReactNode
}) {
  return (
    <section className="mx-auto w-full max-w-2xl">
      <header className="pb-3">
        <h3 className="text-sm font-medium tracking-normal text-foreground">{title}</h3>
      </header>
      <div>{children}</div>
    </section>
  )
}

function GeneralSettings({
  developerMode,
  locale,
  onLocaleChange,
  onDeveloperModeChange
}: {
  developerMode: boolean
  locale: Locale
  onLocaleChange: (locale: Locale) => void
  onDeveloperModeChange: (enabled: boolean) => void
}) {
  const { t } = useI18n()

  return (
    <SettingsSectionShell title={t('settings.general')}>
      <SettingRow label={t('sidebar.languageTitle')}>
        <SelectField
          label={t('sidebar.languageTitle')}
          value={locale}
          onChange={(value) => onLocaleChange(value as Locale)}
          options={[
            { label: '中文', value: 'zh-CN' },
            { label: 'English', value: 'en-US' }
          ]}
        />
      </SettingRow>
      <SettingRow label={t('settings.developerMode')}>
        <Switch
          aria-label={t('settings.developerMode')}
          checked={developerMode}
          onCheckedChange={onDeveloperModeChange}
        />
      </SettingRow>
    </SettingsSectionShell>
  )
}

function NumberSettingInput({
  label,
  max,
  min,
  value,
  onCommit
}: {
  label: string
  max?: number
  min: number
  value: number
  onCommit: (value: number) => void
}) {
  const [draft, setDraft] = useState(String(value))

  useEffect(() => {
    setDraft(String(value))
  }, [value])

  function commit() {
    const next = Number(draft)
    if (!Number.isFinite(next)) {
      setDraft(String(value))
      return
    }
    const integer = Math.trunc(next)
    const bounded = Math.max(min, max == null ? integer : Math.min(max, integer))
    setDraft(String(bounded))
    if (bounded !== value) {
      onCommit(bounded)
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') {
      event.currentTarget.blur()
    }
  }

  return (
    <Input
      aria-label={label}
      className="h-8 w-28 text-right"
      inputMode="numeric"
      max={max}
      min={min}
      type="number"
      value={draft}
      onBlur={commit}
      onChange={(event) => setDraft(event.target.value)}
      onKeyDown={handleKeyDown}
    />
  )
}

function CompletionSettings({
  inlineCompletion,
  settingsError,
  settingsSaving,
  onInlineCompletionEnabledChange,
  onInlineCompletionIdleDelayChange,
  onInlineCompletionCandidateCountChange,
  onInlineCompletionMaxOutputTokensChange
}: Pick<SettingsDialogProps,
  | 'inlineCompletion'
  | 'settingsError'
  | 'settingsSaving'
  | 'onInlineCompletionEnabledChange'
  | 'onInlineCompletionIdleDelayChange'
  | 'onInlineCompletionCandidateCountChange'
  | 'onInlineCompletionMaxOutputTokensChange'
>) {
  const { t } = useI18n()
  const tokenRows: Array<{ key: InlineCompletionTokenKey; label: string; max: number }> = [
    { key: 'short', label: t('settings.completionTokenShort'), max: 128 },
    { key: 'sentence', label: t('settings.completionTokenSentence'), max: 256 },
    { key: 'paragraph', label: t('settings.completionTokenParagraph'), max: 512 },
    { key: 'listItem', label: t('settings.completionTokenListItem'), max: 256 },
    { key: 'tableCell', label: t('settings.completionTokenTableCell'), max: 128 },
    { key: 'codeLine', label: t('settings.completionTokenCodeLine'), max: 256 }
  ]

  return (
    <SettingsSectionShell title={t('settings.completion')}>
      <SettingRow label={t('settings.completionEnabled')}>
        <Switch
          aria-label={t('settings.completionEnabled')}
          checked={inlineCompletion.enabled}
          onCheckedChange={onInlineCompletionEnabledChange}
        />
      </SettingRow>
      <SettingRow label={t('settings.completionIdleDelay')}>
        <NumberSettingInput
          label={t('settings.completionIdleDelay')}
          max={2000}
          min={200}
          value={inlineCompletion.idleDelayMs}
          onCommit={onInlineCompletionIdleDelayChange}
        />
      </SettingRow>
      <SettingRow label={t('settings.completionCandidateCount')}>
        <NumberSettingInput
          label={t('settings.completionCandidateCount')}
          max={5}
          min={1}
          value={inlineCompletion.candidateCount}
          onCommit={onInlineCompletionCandidateCountChange}
        />
      </SettingRow>
      {tokenRows.map((row) => (
        <SettingRow key={row.key} label={row.label}>
          <NumberSettingInput
            label={row.label}
            max={row.max}
            min={1}
            value={inlineCompletion.maxOutputTokens[row.key]}
            onCommit={(value) => onInlineCompletionMaxOutputTokensChange(row.key, value)}
          />
        </SettingRow>
      ))}
      {settingsError || settingsSaving ? (
        <div className="border-t pt-3 text-xs text-muted-foreground">
          {settingsError ?? t('settings.saving')}
        </div>
      ) : null}
    </SettingsSectionShell>
  )
}

function LoginSettings({
  user,
  onLogout,
  onUserChange
}: Pick<SettingsDialogProps, 'user' | 'onLogout' | 'onUserChange'>) {
  return <ProfileControls user={user} onUserChange={onUserChange} onLogout={onLogout} />
}

function renderSettingsPage(section: SettingsSection, input: SettingsDialogProps) {
  if (section === 'login') {
    return <LoginSettings user={input.user} onUserChange={input.onUserChange} onLogout={input.onLogout} />
  }
  if (section === 'completion') {
    return (
      <CompletionSettings
        inlineCompletion={input.inlineCompletion}
        settingsError={input.settingsError}
        settingsSaving={input.settingsSaving}
        onInlineCompletionEnabledChange={input.onInlineCompletionEnabledChange}
        onInlineCompletionIdleDelayChange={input.onInlineCompletionIdleDelayChange}
        onInlineCompletionCandidateCountChange={input.onInlineCompletionCandidateCountChange}
        onInlineCompletionMaxOutputTokensChange={input.onInlineCompletionMaxOutputTokensChange}
      />
    )
  }

  return (
    <GeneralSettings
      developerMode={input.developerMode}
      locale={input.locale}
      onLocaleChange={input.onLocaleChange}
      onDeveloperModeChange={input.onDeveloperModeChange}
    />
  )
}

export function SettingsDialog({
  user,
  locale,
  developerMode,
  inlineCompletion,
  settingsSaving,
  settingsError,
  onClose,
  onLogout,
  onLocaleChange,
  onDeveloperModeChange,
  onInlineCompletionEnabledChange,
  onInlineCompletionIdleDelayChange,
  onInlineCompletionCandidateCountChange,
  onInlineCompletionMaxOutputTokensChange,
  onUserChange
}: SettingsDialogProps) {
  const { t } = useI18n()
  const settingsNavItems: SettingsNavItem[] = [
    { id: 'general', icon: <SlidersHorizontal />, label: t('settings.general') },
    { id: 'completion', icon: <Sparkles />, label: t('settings.completion') },
    { id: 'login', icon: <KeyRound />, label: t('settings.login') }
  ]

  return (
    <Dialog open onOpenChange={(open) => {
      if (!open) onClose()
    }}>
      <DialogContent
        aria-describedby={undefined}
        className="grid h-[min(560px,calc(100vh-40px))] max-w-[min(760px,calc(100vw-40px))] grid-rows-[auto_minmax(0,1fr)] gap-0 overflow-hidden rounded-lg bg-background p-0 shadow-none sm:max-w-[min(760px,calc(100vw-40px))]"
      >
        <DialogHeader className="border-b px-5 py-3">
          <DialogTitle className="text-sm">{t('sidebar.settingsTitle')}</DialogTitle>
        </DialogHeader>
        <Tabs defaultValue="general" orientation="vertical" className="min-h-0 gap-0 md:grid md:grid-cols-[190px_minmax(0,1fr)]">
          <TabsList variant="line" className="h-full w-full items-stretch justify-start rounded-none border-r bg-muted/20 p-2 md:flex-col">
            {settingsNavItems.map((item) => (
              <TabsTrigger key={item.id} value={item.id} className="h-8 justify-start rounded-md px-2.5 text-sm">
                <span data-icon="inline-start">{item.icon}</span>
                <span className="truncate">{item.label}</span>
              </TabsTrigger>
            ))}
          </TabsList>
          {settingsNavItems.map((item) => (
            <TabsContent key={item.id} value={item.id} className="min-h-0 overflow-auto px-8 py-6">
              {renderSettingsPage(item.id, {
                user,
                developerMode,
                inlineCompletion,
                settingsSaving,
                settingsError,
                onClose,
                onLogout,
                onLocaleChange,
                onDeveloperModeChange,
                onInlineCompletionEnabledChange,
                onInlineCompletionIdleDelayChange,
                onInlineCompletionCandidateCountChange,
                onInlineCompletionMaxOutputTokensChange,
                onUserChange,
                locale
              })}
            </TabsContent>
          ))}
        </Tabs>
      </DialogContent>
    </Dialog>
  )
}
