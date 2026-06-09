import { LogIn, UserPlus } from 'lucide-react'
import type { FormEvent } from 'react'
import { useState } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { useI18n } from '../../../shared/i18n'
import { login, register, type AuthSession } from '../api/authApi'

type AuthMode = 'login' | 'register'

const docpilotLogoHorizontalUrl = new URL('../../../assets/brand/docpilot-logo-horizontal.svg', import.meta.url).href

export function AuthScreen({ onAuthenticated }: { onAuthenticated: (session: AuthSession) => void }) {
  const { t } = useI18n()
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
        setMessage(t('auth.registerSuccess'))
        return
      }
      const session = await login({ email, password })
      onAuthenticated(session)
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : t('auth.failed'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="grid h-screen w-screen place-items-center bg-muted/40 p-6">
      <Card className="w-full max-w-sm" aria-label={t('auth.panel')}>
        <CardHeader className="gap-1">
          <CardTitle>
            <img className="h-12 w-auto" src={docpilotLogoHorizontalUrl} alt="DocPilot" />
          </CardTitle>
          <p className="text-sm font-medium text-muted-foreground">{t('auth.brandSubtitle')}</p>
        </CardHeader>
        <CardContent>
          <form className="flex flex-col gap-4" onSubmit={submit}>
            <ToggleGroup
              type="single"
              value={mode}
              variant="outline"
              size="sm"
              spacing={0}
              className="grid w-full grid-cols-2"
              aria-label={t('auth.mode')}
              onValueChange={(value) => {
                if (!value) return
                setMode(value as AuthMode)
                setError(null)
                setMessage(null)
              }}
            >
              <ToggleGroupItem value="login">{t('auth.login')}</ToggleGroupItem>
              <ToggleGroupItem value="register">{t('auth.register')}</ToggleGroupItem>
            </ToggleGroup>

            <FieldGroup className="gap-3">
              <Field>
                <FieldLabel htmlFor="auth-email">{t('auth.email')}</FieldLabel>
                <Input
                  id="auth-email"
                  autoComplete="email"
                  inputMode="email"
                  required
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </Field>

              {isRegister ? (
                <Field>
                  <FieldLabel htmlFor="auth-display-name">{t('auth.displayName')}</FieldLabel>
                  <Input
                    id="auth-display-name"
                    autoComplete="name"
                    value={displayName}
                    onChange={(event) => setDisplayName(event.target.value)}
                  />
                </Field>
              ) : null}

              <Field>
                <FieldLabel htmlFor="auth-password">{t('auth.password')}</FieldLabel>
                <Input
                  id="auth-password"
                  autoComplete={isRegister ? 'new-password' : 'current-password'}
                  minLength={8}
                  required
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
              </Field>
            </FieldGroup>

            {error ? (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            ) : null}
            {message ? (
              <Alert>
                <AlertDescription>{message}</AlertDescription>
              </Alert>
            ) : null}

            <Button className="w-full" disabled={isSubmitting} type="submit">
              {isSubmitting ? (
                <Spinner data-icon="inline-start" />
              ) : isRegister ? (
                <UserPlus data-icon="inline-start" />
              ) : (
                <LogIn data-icon="inline-start" />
              )}
              {isSubmitting ? t('auth.processing') : isRegister ? t('auth.createAccount') : t('auth.login')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  )
}
