import { KeyRound, LogOut, Save, UserRound } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import type { UserInformation } from '../../../entities/user/types'
import { useI18n } from '../../../shared/i18n'
import { Button } from '../../../shared/ui/Button'
import { changeDisplayName, changePassword } from '../api/authApi'
import './ProfileControls.css'

export function ProfileControls({
  user,
  onUserChange,
  onLogout
}: {
  user: UserInformation
  onUserChange: (user: UserInformation) => void
  onLogout: () => void
}) {
  const { t } = useI18n()
  const [displayName, setDisplayName] = useState(user.displayName ?? '')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isSavingName, setSavingName] = useState(false)
  const [isSavingPassword, setSavingPassword] = useState(false)

  async function submitDisplayName(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMessage(null)
    setError(null)
    setSavingName(true)
    try {
      const updated = await changeDisplayName({ displayName })
      onUserChange(updated)
      setMessage(t('profile.nameUpdated'))
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : t('profile.updateFailed'))
    } finally {
      setSavingName(false)
    }
  }

  async function submitPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMessage(null)
    setError(null)
    setSavingPassword(true)
    try {
      await changePassword({ currentPassword, newPassword })
      setCurrentPassword('')
      setNewPassword('')
      setMessage(t('profile.passwordUpdated'))
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : t('profile.updateFailed'))
    } finally {
      setSavingPassword(false)
    }
  }

  return (
    <section className="profile-panel" aria-label={t('profile.currentUser')}>
      <div className="profile-summary">
        <UserRound size={17} />
        <div>
          <strong>{user.displayName || user.email || t('profile.defaultUser')}</strong>
          <span>{user.email}</span>
        </div>
        <button type="button" aria-label={t('profile.logout')} title={t('profile.logout')} onClick={onLogout}>
          <LogOut size={15} />
        </button>
      </div>

      <form className="profile-form" onSubmit={submitDisplayName}>
        <label>
          <span>{t('profile.displayName')}</span>
          <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
        </label>
        <Button
          disabled={isSavingName || !displayName.trim()}
          icon={<Save size={14} />}
          variant="ghost"
          type="submit"
        >
          {t('profile.save')}
        </Button>
      </form>

      <form className="profile-form" onSubmit={submitPassword}>
        <label>
          <span>{t('profile.currentPassword')}</span>
          <input
            autoComplete="current-password"
            type="password"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
          />
        </label>
        <label>
          <span>{t('profile.newPassword')}</span>
          <input
            autoComplete="new-password"
            minLength={8}
            type="password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
          />
        </label>
        <Button
          disabled={isSavingPassword || currentPassword.length < 1 || newPassword.length < 8}
          icon={<KeyRound size={14} />}
          variant="ghost"
          type="submit"
        >
          {t('profile.changePassword')}
        </Button>
      </form>

      {message ? <div className="profile-message">{message}</div> : null}
      {error ? <div className="profile-error">{error}</div> : null}
    </section>
  )
}
