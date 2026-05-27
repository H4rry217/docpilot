import { LogIn, UserPlus } from 'lucide-react'
import type { FormEvent } from 'react'
import { useState } from 'react'
import { Button } from '../../../shared/ui/Button'
import { login, register, type AuthSession } from '../api/authApi'

type AuthMode = 'login' | 'register'

export function AuthScreen({ onAuthenticated }: { onAuthenticated: (session: AuthSession) => void }) {
  const [mode, setMode] = useState<AuthMode>('login')
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [isSubmitting, setSubmitting] = useState(false)
  const isRegister = mode === 'register'

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setMessage(null)
    setSubmitting(true)
    try {
      if (isRegister) {
        await register({ email, password, displayName: displayName || undefined })
        setMode('login')
        setPassword('')
        setDisplayName('')
        setMessage('注册成功，请登录')
        return
      }
      const session = await login({ email, password })
      onAuthenticated(session)
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '认证失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-panel" aria-label="DocPilot 用户认证">
        <div className="auth-brand">
          <strong>DocPilot</strong>
          <span>默认用户体系</span>
        </div>

        <div className="auth-mode-switch" role="tablist" aria-label="认证模式">
          <button
            className={mode === 'login' ? 'active' : ''}
            type="button"
            onClick={() => {
              setMode('login')
              setError(null)
              setMessage(null)
            }}
          >
            登录
          </button>
          <button
            className={mode === 'register' ? 'active' : ''}
            type="button"
            onClick={() => {
              setMode('register')
              setError(null)
              setMessage(null)
            }}
          >
            注册
          </button>
        </div>

        <form className="auth-form" onSubmit={submit}>
          <label>
            <span>邮箱</span>
            <input
              autoComplete="email"
              inputMode="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>

          {isRegister ? (
            <label>
              <span>显示名</span>
              <input
                autoComplete="name"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
              />
            </label>
          ) : null}

          <label>
            <span>密码</span>
            <input
              autoComplete={isRegister ? 'new-password' : 'current-password'}
              minLength={8}
              required
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>

          {error ? <div className="auth-error">{error}</div> : null}
          {message ? <div className="auth-message">{message}</div> : null}

          <Button
            className="auth-submit"
            disabled={isSubmitting}
            icon={isRegister ? <UserPlus size={15} /> : <LogIn size={15} />}
            variant="primary"
            type="submit"
          >
            {isSubmitting ? '处理中' : isRegister ? '创建账号' : '登录'}
          </Button>
        </form>
      </section>
    </main>
  )
}
