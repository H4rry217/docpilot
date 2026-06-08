import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useState, type ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TooltipProvider } from '@/components/ui/tooltip'
import { WORKSPACE_NODE_TYPE, WORKSPACE_RESOURCE_TYPE, WORKSPACE_TYPE, type Workspace, type WorkspaceTreeNode } from '../../../entities/workspace/types'
import { I18nProvider } from '../../../shared/i18n'
import { WorkspaceTree } from './WorkspaceTree'

const now = '2026-05-31T00:00:00Z'

const nodes: WorkspaceTreeNode[] = [
  {
    nodeId: 'folder-1',
    workspaceId: 'workspace-1',
    ancestors: [],
    nodeType: WORKSPACE_NODE_TYPE.FOLDER,
    name: 'New Folder',
    createTime: now,
    updateTime: now,
    metadata: {},
    children: [
      {
        nodeId: 'doc-node-1',
        workspaceId: 'workspace-1',
        parentNodeId: 'folder-1',
        ancestors: ['folder-1'],
        nodeType: WORKSPACE_NODE_TYPE.RESOURCE,
        resourceType: WORKSPACE_RESOURCE_TYPE.DOCUMENT,
        name: 'README.md',
        documentId: 'document-1',
        createTime: now,
        updateTime: now,
        metadata: {},
        children: []
      }
    ]
  }
]

const workspaces: Workspace[] = [
  {
    workspaceId: 'workspace-1',
    ownerUserId: 'user-1',
    name: 'Alice Workspace',
    type: WORKSPACE_TYPE.PERSONAL,
    rootNodeId: 'folder-1',
    settings: {},
    createTime: now,
    updateTime: now
  },
  {
    workspaceId: 'workspace-2',
    ownerUserId: 'user-1',
    name: 'Project Workspace',
    type: WORKSPACE_TYPE.CUSTOM,
    rootNodeId: 'folder-2',
    settings: {},
    createTime: now,
    updateTime: now
  }
]

afterEach(() => cleanup())

function WorkspaceTreeHarness() {
  const [selectedNodeId, setSelectedNodeId] = useState<string>()

  return (
    <TestProviders>
      <WorkspaceTree
        nodes={nodes}
        selectedNodeId={selectedNodeId}
        onSelectNode={(node) => setSelectedNodeId(node.nodeId)}
      />
    </TestProviders>
  )
}

function TestProviders({ children }: { children: ReactNode }) {
  return (
    <I18nProvider>
      <TooltipProvider>{children}</TooltipProvider>
    </I18nProvider>
  )
}

describe('WorkspaceTree', () => {
  it('selects and highlights folders by node id', () => {
    render(<WorkspaceTreeHarness />)

    const folder = screen.getByRole('button', { name: 'New Folder' })
    fireEvent.click(folder)

    expect(folder).toHaveAttribute('aria-current', 'true')
    expect(folder.parentElement).toHaveClass('selected')
    expect(screen.getByRole('button', { name: 'README.md' })).toBeInTheDocument()
  })

  it('toggles folders only from the chevron button', () => {
    render(<WorkspaceTreeHarness />)

    fireEvent.click(screen.getByRole('button', { name: 'New Folder' }))
    expect(screen.getByRole('button', { name: 'README.md' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Collapse New Folder' }))
    expect(screen.queryByRole('button', { name: 'README.md' })).not.toBeInTheDocument()
  })

  it('keeps a created document highlighted when its server node id arrives', () => {
    render(
      <TestProviders>
        <WorkspaceTree
          nodes={nodes}
          selectedNodeId="pending-document-1"
          selectedDocumentId="document-1"
          onSelectNode={() => undefined}
        />
      </TestProviders>
    )

    const document = screen.getByRole('button', { name: 'README.md' })

    expect(document).toHaveAttribute('aria-current', 'true')
    expect(document.parentElement).toHaveClass('selected')
  })

  it('renders long file names in a single ellipsis target and exposes the full file name', () => {
    const longFileName = 'very-long-untitled-document-name.md'
    const longNameNodes: WorkspaceTreeNode[] = [
      {
        ...nodes[0],
        children: [
          {
            ...nodes[0].children[0],
            nodeId: 'doc-node-long',
            name: longFileName
          }
        ]
      }
    ]

    render(
      <TestProviders>
        <WorkspaceTree nodes={longNameNodes} onSelectNode={() => undefined} />
      </TestProviders>
    )

    const document = screen.getByRole('button', { name: longFileName })

    expect(document).toHaveAttribute('title', longFileName)
    expect(document.querySelector('.tree-row-name')).toHaveTextContent(longFileName)
    expect(document.querySelector('.tree-row-name-main')).not.toBeInTheDocument()
    expect(document.querySelector('.tree-row-name-extension')).not.toBeInTheDocument()
  })

  it('switches from the file tree to the workspace list from the workspace card', () => {
    const onSelectWorkspace = vi.fn()
    render(
      <TestProviders>
        <WorkspaceTree
          nodes={nodes}
          workspaces={workspaces}
          workspaceName="Alice Workspace"
          selectedWorkspaceId="workspace-1"
          onSelectWorkspace={onSelectWorkspace}
          onSelectNode={() => undefined}
        />
      </TestProviders>
    )

    fireEvent.click(screen.getByRole('button', { name: 'Alice Workspace' }))
    fireEvent.click(screen.getByRole('button', { name: 'Project Workspace' }))

    expect(onSelectWorkspace).toHaveBeenCalledWith(workspaces[1])
    expect(screen.getByRole('button', { name: 'Alice Workspace' })).toBeInTheDocument()
  })

  it('does not show management actions for the personal workspace', () => {
    const onRenameWorkspace = vi.fn()
    const onDeleteWorkspace = vi.fn()
    const { container } = render(
      <TestProviders>
        <WorkspaceTree
          nodes={nodes}
          workspaces={workspaces}
          workspaceName="Alice Workspace"
          selectedWorkspaceId="workspace-1"
          onRenameWorkspace={onRenameWorkspace}
          onDeleteWorkspace={onDeleteWorkspace}
          onSelectNode={() => undefined}
        />
      </TestProviders>
    )

    fireEvent.click(screen.getByRole('button', { name: 'Alice Workspace' }))

    const actionButtons = container.querySelectorAll<HTMLButtonElement>('.workspace-list-action')
    const dangerButtons = container.querySelectorAll<HTMLButtonElement>('.workspace-list-action.danger')
    expect(actionButtons).toHaveLength(2)
    expect(dangerButtons).toHaveLength(1)

    fireEvent.click(actionButtons[0])
    fireEvent.click(dangerButtons[0])
    expect(onRenameWorkspace).toHaveBeenCalledWith(workspaces[1])
    expect(onDeleteWorkspace).toHaveBeenCalledWith(workspaces[1])
  })

  it('drops markdown files onto folders', () => {
    const onUploadMarkdownFiles = vi.fn()
    const markdownFile = new File(['# Hello'], 'hello.md', { type: 'text/markdown' })
    const ignoredFile = new File(['nope'], 'image.png', { type: 'image/png' })
    render(
      <TestProviders>
        <WorkspaceTree
          nodes={nodes}
          onSelectNode={() => undefined}
          onUploadMarkdownFiles={onUploadMarkdownFiles}
        />
      </TestProviders>
    )

    const folderDropTarget = screen.getByRole('button', { name: 'New Folder' }).parentElement as HTMLElement
    fireEvent.drop(folderDropTarget, {
      dataTransfer: {
        types: ['Files'],
        files: [markdownFile, ignoredFile],
        dropEffect: 'move'
      }
    })

    expect(onUploadMarkdownFiles).toHaveBeenCalledWith([markdownFile], nodes[0])
  })

  it('drops markdown files onto the workspace root card', () => {
    const onUploadMarkdownFiles = vi.fn()
    const markdownFile = new File(['# Root'], 'root.markdown', { type: 'text/markdown' })
    render(
      <TestProviders>
        <WorkspaceTree
          nodes={nodes}
          workspaceName="Alice Workspace"
          onSelectNode={() => undefined}
          onUploadMarkdownFiles={onUploadMarkdownFiles}
        />
      </TestProviders>
    )

    fireEvent.drop(screen.getByRole('button', { name: 'Alice Workspace' }), {
      dataTransfer: {
        types: ['Files'],
        files: [markdownFile],
        dropEffect: 'move'
      }
    })

    expect(onUploadMarkdownFiles).toHaveBeenCalledWith([markdownFile])
  })
})
