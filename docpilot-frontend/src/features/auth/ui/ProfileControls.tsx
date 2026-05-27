import { KeyRound, LogOut, Save, UserRound } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import type { UserInformation } from '../../../entities/user/types'
import { Button } from '../../../shared/ui/Button'
import { changeDisplayName, changePassword } from '../api/authApi'

export function ProfileControls({
  user,
  onUserChange,
  onLogout
}: {
  user: UserInformation
  onUserChange: (user: UserInformation) => void
  onLogout: () => void
}) {
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
      setMessage('显示名已更新')
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '更新失败')
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
      setMessage('密码已更新')
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '更新失败')
    } finally {
      setSavingPassword(false)
    }
  }

  return (
    <section className="profile-panel" aria-label="当前用户">
      <div className="profile-summary">
        <UserRound size={17} />
        <div>
          <strong>{user.displayName || user.email || 'DocPilot 用户'}</strong>
          <span>{user.email}</span>
        </div>
        <button type="button" aria-label="退出登录" onClick={onLogout}>
          <LogOut size={15} />
        </button>
      </div>

      <form className="profile-form" onSubmit={submitDisplayName}>
        <label>
          <span>显示名</span>
          <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
        </label>
        <Button
          disabled={isSavingName || !displayName.trim()}
          icon={<Save size={14} />}
          variant="ghost"
          type="submit"
        >
          保存
        </Button>
      </form>

      <form className="profile-form" onSubmit={submitPassword}>
        <label>
          <span>当前密码</span>
          <input
            autoComplete="current-password"
            type="password"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
          />
        </label>
        <label>
          <span>新密码</span>
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
          改密
        </Button>
      </form>

      {message ? <div className="profile-message">{message}</div> : null}
      {error ? <div className="profile-error">{error}</div> : null}
    </section>
  )
}
