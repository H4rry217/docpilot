import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { I18nProvider } from '../../../shared/i18n'
import { DocumentEditorToolbar } from './DocumentEditorToolbar'

afterEach(() => {
  cleanup()
  window.localStorage.removeItem('docpilot.locale')
})

function renderToolbar(developerMode: boolean) {
  window.localStorage.setItem('docpilot.locale', 'en-US')

  render(
    <I18nProvider>
      <DocumentEditorToolbar
        blockDebugMode={false}
        developerMode={developerMode}
        canSave
        isSaving={false}
        onSave={vi.fn()}
        onToggleBlockDebugMode={vi.fn()}
      />
    </I18nProvider>
  )
}

describe('DocumentEditorToolbar', () => {
  it('hides block mode outside developer mode', () => {
    renderToolbar(false)

    expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Block mode' })).not.toBeInTheDocument()
  })

  it('shows block mode in developer mode', () => {
    renderToolbar(true)

    expect(screen.getByRole('button', { name: 'Block mode' })).toBeInTheDocument()
  })
})
