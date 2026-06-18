import { KeyRound, LogOut, Save } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import type { UserInformation } from '@/entities/user/types'
import { useI18n } from '@/shared/i18n'
import { changeDisplayName, changePassword } from '../api/authApi'

export function ProfileControls({
  user,
  onUserChange,
  onLogout,
  supportsDisplayNameChange = true,
  supportsPasswordChange = true
}: {
  user: UserInformation
  onUserChange: (user: UserInformation) => void
  onLogout: () => void
  supportsDisplayNameChange?: boolean
  supportsPasswordChange?: boolean
}) {
  const { t } = useI18n()
  const [displayName, setDisplayName] = useState(user.displayName ?? '')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isSavingName, setSavingName] = useState(false)
  const [isSavingPassword, setSavingPassword] = useState(false)
  const profileName = user.displayName || user.email || t('profile.defaultUser')
  const fallback = profileName.slice(0, 2).toUpperCase()

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
    <section className="mx-auto w-full max-w-2xl" aria-label={t('profile.currentUser')}>
      <header className="pb-3">
        <h3 className="text-sm font-medium tracking-normal text-foreground">{t('profile.currentUser')}</h3>
      </header>

      <div className="flex min-h-16 items-center gap-3 border-t py-3">
        <Avatar>
          <AvatarFallback>{fallback}</AvatarFallback>
        </Avatar>
        <div className="min-w-0 flex-1">
          <strong className="block truncate text-sm font-medium">{profileName}</strong>
          <span className="block truncate text-xs text-muted-foreground">{user.email}</span>
        </div>
        <Button type="button" variant="outline" size="sm" aria-label={t('profile.logout')} title={t('profile.logout')} onClick={onLogout}>
          <LogOut data-icon="inline-start" />
          {t('profile.logout')}
        </Button>
      </div>

      {supportsDisplayNameChange ? (
        <form className="grid gap-3 border-t py-4" onSubmit={submitDisplayName}>
          <FieldGroup className="gap-3">
            <Field orientation="responsive">
              <FieldLabel htmlFor="profile-display-name" className="min-w-32">{t('profile.displayName')}</FieldLabel>
              <Input
                id="profile-display-name"
                className="max-w-sm"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
              />
            </Field>
          </FieldGroup>
          <div className="flex justify-end">
            <Button disabled={isSavingName || !displayName.trim()} variant="outline" type="submit">
              <Save data-icon="inline-start" />
              {t('profile.save')}
            </Button>
          </div>
        </form>
      ) : null}

      {supportsPasswordChange ? (
        <form className="grid gap-3 border-t py-4" onSubmit={submitPassword}>
          <FieldGroup className="gap-3">
            <Field orientation="responsive">
              <FieldLabel htmlFor="profile-current-password" className="min-w-32">{t('profile.currentPassword')}</FieldLabel>
              <Input
                id="profile-current-password"
                className="max-w-sm"
                autoComplete="current-password"
                type="password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
              />
            </Field>
            <Field orientation="responsive">
              <FieldLabel htmlFor="profile-new-password" className="min-w-32">{t('profile.newPassword')}</FieldLabel>
              <Input
                id="profile-new-password"
                className="max-w-sm"
                autoComplete="new-password"
                minLength={8}
                type="password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
              />
            </Field>
          </FieldGroup>
          <div className="flex justify-end">
            <Button
              disabled={isSavingPassword || currentPassword.length < 1 || newPassword.length < 8}
              variant="outline"
              type="submit"
            >
              <KeyRound data-icon="inline-start" />
              {t('profile.changePassword')}
            </Button>
          </div>
        </form>
      ) : null}

      <div className="grid gap-2 border-t pt-4">
        {message ? (
          <Alert>
            <AlertDescription>{message}</AlertDescription>
          </Alert>
        ) : null}
        {error ? (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}
      </div>
    </section>
  )
}
