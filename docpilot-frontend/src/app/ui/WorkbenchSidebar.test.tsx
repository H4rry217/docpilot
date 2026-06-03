import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { I18nProvider } from '../../shared/i18n'
import { WorkbenchSidebar } from './WorkbenchSidebar'

afterEach(() => {
  cleanup()
  window.localStorage.removeItem('docpilot.locale')
})

function renderSidebar() {
  render(
    <I18nProvider>
      <WorkbenchSidebar
        mode="files"
        expanded
        width={240}
        workspaces={[]}
        tree={[]}
        onModeChange={vi.fn()}
        onWidthChange={vi.fn()}
        onSelectWorkspace={vi.fn()}
        onCreateWorkspace={vi.fn()}
        onRenameWorkspace={vi.fn()}
        onDeleteWorkspace={vi.fn()}
        onSelectNode={vi.fn()}
        onCreateFolder={vi.fn()}
        onCreateDocument={vi.fn()}
        onUploadMarkdownFiles={vi.fn()}
        onRenameNode={vi.fn()}
        onDeleteNode={vi.fn()}
        onOpenSettings={vi.fn()}
      />
    </I18nProvider>
  )
}

describe('WorkbenchSidebar activity bar', () => {
  it('keeps the workspace sidebar focused on files', () => {
    window.localStorage.setItem('docpilot.locale', 'en-US')
    renderSidebar()

    expect(screen.getByRole('button', { name: 'Files' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Outline' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Search' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Agent' })).not.toBeInTheDocument()
  })
})
