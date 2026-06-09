import { cleanup, render, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { BlockDocument } from '../../../entities/block/types'
import {
  BlockDocumentEditor,
  type BlockDocumentEditorSnapshot,
  type BlockDocumentEditorSnapshotSource
} from '..'

const paragraphDocument: BlockDocument = {
  schemaVersion: 'docpilot-block/2',
  blocks: [
    {
      id: 'paragraph1',
      type: 'PARAGRAPH',
      attrs: {},
      inlines: [
        {
          type: 'TEXT',
          text: 'Hello editor',
          attrs: {},
          marks: []
        }
      ],
      children: []
    }
  ],
  metadata: {}
}

afterEach(() => {
  cleanup()
})

describe('BlockDocumentEditor', () => {
  it('loads a block document and emits an equivalent snapshot', async () => {
    const onSnapshotChange = vi.fn<
      (snapshot: BlockDocumentEditorSnapshot, source: BlockDocumentEditorSnapshotSource) => void
    >()

    render(
      <BlockDocumentEditor
        contentKey="document-1"
        blockDocument={paragraphDocument}
        onSnapshotChange={onSnapshotChange}
      />
    )

    await waitFor(() => {
      expect(onSnapshotChange).toHaveBeenCalledWith(
        expect.objectContaining({
          blockDocument: expect.objectContaining({
            schemaVersion: 'docpilot-block/2',
            blocks: expect.arrayContaining([
              expect.objectContaining({
                id: 'paragraph1',
                type: 'PARAGRAPH',
                inlines: expect.arrayContaining([
                  expect.objectContaining({ text: 'Hello editor' })
                ])
              })
            ])
          })
        }),
        'load'
      )
    })
  })

  it('keeps debug mode scoped to the block editor surface', () => {
    const { container } = render(
      <BlockDocumentEditor
        contentKey="document-1"
        debugMode
        blockDocument={paragraphDocument}
        onSnapshotChange={vi.fn()}
      />
    )

    expect(container.querySelector('.block-editor-surface')).toHaveClass('is-debug-mode')
  })
})
