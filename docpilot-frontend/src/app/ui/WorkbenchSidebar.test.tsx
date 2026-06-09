import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TooltipProvider } from '@/components/ui/tooltip'
import { I18nProvider } from '../../shared/i18n'
import { WorkbenchSidebar } from './WorkbenchSidebar'

afterEach(() => {
  cleanup()
  window.localStorage.removeItem('docpilot.locale')
})

function renderSidebar({ expanded = true } = {}) {
  window.localStorage.setItem('docpilot.locale', 'en-US')

  render(
    <I18nProvider>
      <TooltipProvider>
        <WorkbenchSidebar
          mode="files"
          expanded={expanded}
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
      </TooltipProvider>
    </I18nProvider>
  )
}

describe('WorkbenchSidebar activity bar', () => {
  it('keeps the workspace sidebar focused on files', () => {
    window.localStorage.setItem('docpilot.locale', 'en-US')
    renderSidebar()

    expect(screen.getByLabelText('DocPilot')).toHaveClass('workbench-activity-bar')
    const filesButton = screen.getByRole('button', { name: 'Files' })
    expect(filesButton).toBeInTheDocument()
    expect(filesButton).toHaveClass('size-[38px]')
    expect(filesButton).toHaveClass('[&_svg]:size-[18px]')
    expect(filesButton).toHaveAttribute('aria-expanded', 'true')
    expect(filesButton).toHaveAttribute('data-variant', 'ghost')
    expect(filesButton).toHaveClass('bg-[#F5F8FF]')
    expect(filesButton).toHaveClass('text-[#3D6EFF]')
    expect(filesButton).toHaveClass('before:w-0.5')
    expect(screen.getByRole('button', { name: 'Settings' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Outline' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Search' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Agent' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('DocPilot').querySelectorAll('button')).toHaveLength(2)
  })

  it('renders the logo as a quiet brand mark instead of a button-like control', () => {
    renderSidebar()

    const activityBar = screen.getByLabelText('DocPilot')
    const logo = activityBar.querySelector('img[src*="docpilot-logo-mark"]')
    const logoSlot = logo?.parentElement

    expect(logo).toBeInTheDocument()
    expect(logo).toHaveAttribute('draggable', 'false')
    expect(logoSlot).not.toHaveClass('bg-background')
    expect(logoSlot).not.toHaveClass('ring-1')
    expect(logoSlot).not.toHaveClass('rounded-lg')
  })

  it('uses a closed activity treatment while the files panel is collapsed', () => {
    renderSidebar({ expanded: false })

    const filesButton = screen.getByRole('button', { name: 'Files' })

    expect(filesButton).toHaveAttribute('aria-expanded', 'false')
    expect(filesButton).toHaveAttribute('data-variant', 'ghost')
    expect(filesButton).toHaveClass('bg-transparent')
    expect(filesButton).toHaveClass('text-muted-foreground')
    expect(filesButton).not.toHaveClass('before:bg-[#4F7CFF]')
  })
})
