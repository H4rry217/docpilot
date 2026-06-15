import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
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
          locale="en-US"
          developerMode={false}
          inlineCompletion={inlineCompletionSettings()}
          settingsSaving={false}
          settingsError={null}
          onClose={vi.fn()}
          onLocaleChange={vi.fn()}
          onDeveloperModeChange={onDeveloperModeChange}
          onInlineCompletionEnabledChange={vi.fn()}
          onInlineCompletionIdleDelayChange={vi.fn()}
          onInlineCompletionCandidateCountChange={vi.fn()}
          onInlineCompletionMaxOutputTokensChange={vi.fn()}
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

  it('exposes inline completion cloud settings', async () => {
    window.localStorage.setItem('docpilot.locale', 'en-US')
    const onEnabledChange = vi.fn()
    const onCandidateCountChange = vi.fn()
    const onTokenChange = vi.fn()

    render(
      <I18nProvider>
        <SettingsDialog
          user={{ userId: 1, email: 'user@example.com', displayName: 'User' }}
          locale="en-US"
          developerMode={false}
          inlineCompletion={inlineCompletionSettings()}
          settingsSaving={false}
          settingsError={null}
          onClose={vi.fn()}
          onLocaleChange={vi.fn()}
          onDeveloperModeChange={vi.fn()}
          onInlineCompletionEnabledChange={onEnabledChange}
          onInlineCompletionIdleDelayChange={vi.fn()}
          onInlineCompletionCandidateCountChange={onCandidateCountChange}
          onInlineCompletionMaxOutputTokensChange={onTokenChange}
          onLogout={vi.fn()}
          onUserChange={vi.fn()}
        />
      </I18nProvider>
    )

    const completionTab = screen.getByRole('tab', { name: 'Completion' })
    fireEvent.mouseDown(completionTab, { button: 0, ctrlKey: false })
    fireEvent.click(completionTab)
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Inline completion' })).toBeInTheDocument())

    fireEvent.click(screen.getByRole('switch', { name: 'Inline completion' }))
    expect(onEnabledChange).toHaveBeenCalledWith(false)

    const candidateCount = screen.getByRole('spinbutton', { name: 'Candidate count' })
    fireEvent.change(candidateCount, { target: { value: '5' } })
    fireEvent.blur(candidateCount)
    expect(onCandidateCountChange).toHaveBeenCalledWith(5)

    const shortTokens = screen.getByRole('spinbutton', { name: 'SHORT tokens' })
    fireEvent.change(shortTokens, { target: { value: '40' } })
    fireEvent.blur(shortTokens)
    expect(onTokenChange).toHaveBeenCalledWith('short', 40)
  })

  it('localizes inline completion settings in Chinese', async () => {
    window.localStorage.setItem('docpilot.locale', 'zh-CN')

    render(
      <I18nProvider>
        <SettingsDialog
          user={{ userId: 1, email: 'user@example.com', displayName: 'User' }}
          locale="zh-CN"
          developerMode={false}
          inlineCompletion={inlineCompletionSettings()}
          settingsSaving={false}
          settingsError={null}
          onClose={vi.fn()}
          onLocaleChange={vi.fn()}
          onDeveloperModeChange={vi.fn()}
          onInlineCompletionEnabledChange={vi.fn()}
          onInlineCompletionIdleDelayChange={vi.fn()}
          onInlineCompletionCandidateCountChange={vi.fn()}
          onInlineCompletionMaxOutputTokensChange={vi.fn()}
          onLogout={vi.fn()}
          onUserChange={vi.fn()}
        />
      </I18nProvider>
    )

    const completionTab = screen.getByRole('tab', { name: '自动补全' })
    fireEvent.mouseDown(completionTab, { button: 0, ctrlKey: false })
    fireEvent.click(completionTab)

    await waitFor(() => expect(screen.getByRole('switch', { name: '启用自动补全' })).toBeInTheDocument())
    expect(screen.getByRole('spinbutton', { name: '触发延迟（毫秒）' })).toBeInTheDocument()
    expect(screen.getByRole('spinbutton', { name: '候选数量' })).toBeInTheDocument()
    expect(screen.getByRole('spinbutton', { name: '短文本 Token 上限' })).toBeInTheDocument()
  })
})

function inlineCompletionSettings() {
  return {
    enabled: true,
    idleDelayMs: 500,
    candidateCount: 3,
    maxOutputTokens: {
      short: 32,
      sentence: 64,
      paragraph: 160,
      listItem: 80,
      tableCell: 32,
      codeLine: 96
    }
  }
}
