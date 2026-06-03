import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { DocumentOutlineItem } from '../../../entities/block/outline'
import { I18nProvider } from '../../../shared/i18n'
import { DocumentOutlineNav } from './DocumentOutlineNav'

const outline: DocumentOutlineItem[] = [
  { id: 'parent', level: 1, title: 'Parent', headingIndex: 0 },
  { id: 'child', level: 2, title: 'Child', headingIndex: 1 },
  { id: 'deep-child', level: 3, title: 'Deep child', headingIndex: 2 }
]

afterEach(() => {
  cleanup()
  window.localStorage.removeItem('docpilot.locale')
})

function renderOutline(onSelectOutlineItem = vi.fn()) {
  window.localStorage.setItem('docpilot.locale', 'en-US')
  render(
    <I18nProvider>
      <DocumentOutlineNav
        title="Spec"
        outline={outline}
        activeOutlineId="child"
        collapsed={false}
        onToggleCollapsed={vi.fn()}
        onSelectOutlineItem={onSelectOutlineItem}
      />
    </I18nProvider>
  )
}

describe('DocumentOutlineNav', () => {
  it('collapses heading descendants', () => {
    renderOutline()

    expect(screen.getByRole('button', { name: 'Child' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Collapse Parent' }))

    expect(screen.queryByRole('button', { name: 'Child' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Deep child' })).not.toBeInTheDocument()
  })

  it('selects outline headings', () => {
    const onSelectOutlineItem = vi.fn()
    renderOutline(onSelectOutlineItem)

    fireEvent.click(screen.getByRole('button', { name: 'Child' }))

    expect(onSelectOutlineItem).toHaveBeenCalledWith(outline[1])
  })
})
