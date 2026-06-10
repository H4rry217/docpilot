import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { useDeveloperSettings } from './useDeveloperSettings'

function DeveloperSettingsProbe() {
  const { developerMode, setDeveloperMode } = useDeveloperSettings()

  return (
    <button type="button" onClick={() => setDeveloperMode(!developerMode)}>
      {developerMode ? 'enabled' : 'disabled'}
    </button>
  )
}

afterEach(() => {
  cleanup()
  window.localStorage.removeItem('docpilot.developerMode')
})

describe('useDeveloperSettings', () => {
  it('keeps developer mode off by default', () => {
    render(<DeveloperSettingsProbe />)

    expect(screen.getByRole('button', { name: 'disabled' })).toBeInTheDocument()
  })

  it('persists developer mode across remounts', () => {
    render(<DeveloperSettingsProbe />)
    fireEvent.click(screen.getByRole('button', { name: 'disabled' }))

    expect(window.localStorage.getItem('docpilot.developerMode')).toBe('true')
    expect(screen.getByRole('button', { name: 'enabled' })).toBeInTheDocument()

    cleanup()
    render(<DeveloperSettingsProbe />)

    expect(screen.getByRole('button', { name: 'enabled' })).toBeInTheDocument()
  })
})
