import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { DocumentOutlineItem } from '../../entities/block/outline'
import { I18nProvider } from '../../shared/i18n'
import { WorkbenchSidebar } from './WorkbenchSidebar'

const outline: DocumentOutlineItem[] = [
  { id: 'intro', level: 1, title: '1. 文档基本信息', headingIndex: 0 },
  { id: 'feature', level: 1, title: '3. 功能需求', headingIndex: 1 },
  { id: 'home', level: 2, title: '首页', headingIndex: 2 },
  { id: 'feed', level: 3, title: '信息流帖子', headingIndex: 3 }
]

afterEach(() => cleanup())

function renderOutlineSidebar(onSelectOutlineItem = vi.fn()) {
  render(
    <I18nProvider>
      <WorkbenchSidebar
        mode="outline"
        expanded
        width={240}
        outline={outline}
        activeOutlineId="feature"
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
        onSelectOutlineItem={onSelectOutlineItem}
        onOpenSettings={vi.fn()}
      />
    </I18nProvider>
  )
}

describe('WorkbenchSidebar outline', () => {
  it('collapses heading descendants', () => {
    renderOutlineSidebar()

    expect(screen.getByText('首页')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '折叠 3. 功能需求' }))

    expect(screen.getByText('3. 功能需求')).toBeInTheDocument()
    expect(screen.queryByText('首页')).not.toBeInTheDocument()
    expect(screen.queryByText('信息流帖子')).not.toBeInTheDocument()
  })

  it('selects outline headings', () => {
    const onSelectOutlineItem = vi.fn()
    renderOutlineSidebar(onSelectOutlineItem)

    fireEvent.click(screen.getByRole('button', { name: '首页' }))

    expect(onSelectOutlineItem).toHaveBeenCalledWith(outline[2])
  })
})
