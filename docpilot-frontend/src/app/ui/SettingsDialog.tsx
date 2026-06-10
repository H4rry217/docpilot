import { KeyRound, SlidersHorizontal } from 'lucide-react'
import type { ReactNode } from 'react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Switch } from '@/components/ui/switch'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import type { UserInformation } from '../../entities/user/types'
import { ProfileControls } from '../../features/auth/ui/ProfileControls'
import { useI18n, type Locale } from '../../shared/i18n'
import { SelectField } from '../../shared/ui/SelectField'

type SettingsSection = 'general' | 'login'

type SettingsDialogProps = {
  user: UserInformation
  developerMode: boolean
  onClose: () => void
  onLogout: () => void
  onDeveloperModeChange: (enabled: boolean) => void
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
  setLocale,
  onDeveloperModeChange
}: {
  developerMode: boolean
  locale: Locale
  setLocale: (locale: Locale) => void
  onDeveloperModeChange: (enabled: boolean) => void
}) {
  const { t } = useI18n()

  return (
    <SettingsSectionShell title={t('settings.general')}>
      <SettingRow label={t('sidebar.languageTitle')}>
        <SelectField
          label={t('sidebar.languageTitle')}
          value={locale}
          onChange={(value) => setLocale(value as Locale)}
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

function LoginSettings({
  user,
  onLogout,
  onUserChange
}: Pick<SettingsDialogProps, 'user' | 'onLogout' | 'onUserChange'>) {
  return <ProfileControls user={user} onUserChange={onUserChange} onLogout={onLogout} />
}

function renderSettingsPage(section: SettingsSection, input: SettingsDialogProps & { locale: Locale; setLocale: (locale: Locale) => void }) {
  if (section === 'login') {
    return <LoginSettings user={input.user} onUserChange={input.onUserChange} onLogout={input.onLogout} />
  }

  return (
    <GeneralSettings
      developerMode={input.developerMode}
      locale={input.locale}
      setLocale={input.setLocale}
      onDeveloperModeChange={input.onDeveloperModeChange}
    />
  )
}

export function SettingsDialog({
  user,
  developerMode,
  onClose,
  onLogout,
  onDeveloperModeChange,
  onUserChange
}: SettingsDialogProps) {
  const { locale, setLocale, t } = useI18n()
  const settingsNavItems: SettingsNavItem[] = [
    { id: 'general', icon: <SlidersHorizontal />, label: t('settings.general') },
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
                onClose,
                onLogout,
                onDeveloperModeChange,
                onUserChange,
                locale,
                setLocale
              })}
            </TabsContent>
          ))}
        </Tabs>
      </DialogContent>
    </Dialog>
  )
}
