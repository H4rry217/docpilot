import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { I18nProvider } from '../../shared/i18n'
import { SettingsDialog } from './SettingsDialog'

afterEach(() => {
  cleanup()
  window.localStorage.removeItem('docpilot.locale')
})

describe('SettingsDialog', () => {
  it('exposes developer mode as an opt-in setting', () => {
    window.localStorage.setItem('docpilot.locale', 'en-US')
    const onDeveloperModeChange = vi.fn()

    render(
      <I18nProvider>
        <SettingsDialog
          user={{ userId: 1, email: 'user@example.com', displayName: 'User' }}
          developerMode={false}
          onClose={vi.fn()}
          onDeveloperModeChange={onDeveloperModeChange}
          onLogout={vi.fn()}
          onUserChange={vi.fn()}
        />
      </I18nProvider>
    )

    const switchControl = screen.getByRole('switch', { name: 'Developer mode' })
    expect(switchControl).toHaveAttribute('aria-checked', 'false')

    fireEvent.click(switchControl)

    expect(onDeveloperModeChange).toHaveBeenCalledWith(true)
  })
})
