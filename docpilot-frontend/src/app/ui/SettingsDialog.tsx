import { KeyRound, SlidersHorizontal, X } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import type { UserInformation } from '../../entities/user/types'
import { ProfileControls } from '../../features/auth/ui/ProfileControls'
import { useI18n, type Locale } from '../../shared/i18n'
import { SelectField } from '../../shared/ui/SelectField'
import './SettingsDialog.css'

type SettingsSection = 'general' | 'login'

type SettingsDialogProps = {
  user: UserInformation
  onClose: () => void
  onLogout: () => void
  onUserChange: (user: UserInformation) => void
}

type SettingsNavItem = {
  id: SettingsSection
  icon: ReactNode
  label: string
}

const SETTINGS_NAV_ITEMS: SettingsNavItem[] = [
  { id: 'general', icon: <SlidersHorizontal size={16} />, label: '通用' },
  { id: 'login', icon: <KeyRound size={16} />, label: '登录设置' }
]

function SettingRow({
  label,
  children
}: {
  label: string
  children: ReactNode
}) {
  return (
    <div className="settings-row">
      <span>{label}</span>
      {children}
    </div>
  )
}

function GeneralSettings({ locale, setLocale }: { locale: Locale; setLocale: (locale: Locale) => void }) {
  return (
    <section className="settings-section">
      <h3>语言</h3>
      <div className="settings-list">
        <SettingRow label="界面语言">
          <SelectField
            label="界面语言"
            value={locale}
            onChange={(value) => setLocale(value as Locale)}
            options={[
              { label: '中文', value: 'zh-CN' },
              { label: 'English', value: 'en-US' }
            ]}
          />
        </SettingRow>
      </div>
    </section>
  )
}

function LoginSettings({ user, onLogout, onUserChange }: Omit<SettingsDialogProps, 'onClose'>) {
  return (
    <section className="settings-section">
      <h3>账号</h3>
      <ProfileControls user={user} onUserChange={onUserChange} onLogout={onLogout} />
    </section>
  )
}

function renderSettingsPage(section: SettingsSection, input: SettingsDialogProps & { locale: Locale; setLocale: (locale: Locale) => void }) {
  if (section === 'login') {
    return <LoginSettings user={input.user} onUserChange={input.onUserChange} onLogout={input.onLogout} />
  }

  return <GeneralSettings locale={input.locale} setLocale={input.setLocale} />
}

export function SettingsDialog({ user, onClose, onLogout, onUserChange }: SettingsDialogProps) {
  const [activeSection, setActiveSection] = useState<SettingsSection>('general')
  const { locale, setLocale } = useI18n()
  const activeItem = SETTINGS_NAV_ITEMS.find((item) => item.id === activeSection) ?? SETTINGS_NAV_ITEMS[0]

  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }

    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [onClose])

  return (
    <div
      className="settings-dialog-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <section className="settings-dialog" role="dialog" aria-modal="true" aria-label="设置">
        <aside className="settings-nav" aria-label="设置分类">
          {SETTINGS_NAV_ITEMS.map((item) => (
            <button
              className={item.id === activeSection ? 'active' : ''}
              type="button"
              aria-current={item.id === activeSection ? 'page' : undefined}
              key={item.id}
              onClick={() => setActiveSection(item.id)}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          ))}
        </aside>

        <main className="settings-main">
          <header className="settings-header">
            <h2>{activeItem.label}</h2>
            <button type="button" aria-label="关闭设置" title="关闭设置" onClick={onClose}>
              <X size={18} />
            </button>
          </header>
          <div className="settings-content">
            {renderSettingsPage(activeSection, { user, onClose, onLogout, onUserChange, locale, setLocale })}
          </div>
        </main>
      </section>
    </div>
  )
}
