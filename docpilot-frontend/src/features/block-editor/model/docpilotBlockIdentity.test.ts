import { Editor } from '@tiptap/core'
import { describe, expect, it } from 'vitest'
import {
  blockDocumentForSave,
  proseMirrorJsonToBlockDocument
} from './proseMirrorToBlockDocument'
import { editorExtensions } from './extensions'
import {
  blockIdentityId,
  TRANSIENT_BLOCK_ID_PREFIX
} from './docpilotBlockIdentity'

function identityIds(editor: Editor): string[] {
  const ids: string[] = []
  editor.state.doc.descendants((node) => {
    if (node.isBlock) {
      ids.push(blockIdentityId(node.attrs))
    }
  })
  return ids
}

describe('DocpilotBlockIdentity', () => {
  it('assigns transient DOM identities to newly inserted document blocks', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: '<p data-block-id="existing">Existing</p>'
    })

    editor.commands.insertContentAt(editor.state.doc.content.size, '<p>Inserted</p><h2>Heading</h2>')

    const ids = identityIds(editor)
    expect(ids).toContain('existing')
    expect(ids.every(Boolean)).toBe(true)
    expect(new Set(ids).size).toBe(ids.length)
    expect(ids.some((id) => id.startsWith(TRANSIENT_BLOCK_ID_PREFIX))).toBe(true)
    expect(editor.view.dom.querySelectorAll('[data-block-id]').length).toBeGreaterThanOrEqual(ids.length)
    editor.destroy()
  })

  it('keeps transient DOM identities out of canonical block documents', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: '<p data-block-id="existing">Existing</p>'
    })

    editor.commands.insertContentAt(editor.state.doc.content.size, '<p>Inserted</p>')

    const json = editor.getJSON()
    expect(JSON.stringify(json)).toContain(TRANSIENT_BLOCK_ID_PREFIX)

    const localDocument = proseMirrorJsonToBlockDocument(json)
    expect(localDocument.blocks[0].id).toBe('existing')
    expect(localDocument.blocks[1].id).toMatch(new RegExp(`^${TRANSIENT_BLOCK_ID_PREFIX}`))

    const saveDocument = blockDocumentForSave(localDocument)
    expect(JSON.stringify(saveDocument)).not.toContain(TRANSIENT_BLOCK_ID_PREFIX)
    expect(saveDocument.blocks[0].id).toBe('existing')
    expect(saveDocument.blocks[1].id).toBe('')
    editor.destroy()
  })

  it('replaces duplicated canonical block ids with transient DOM identities', () => {
    const editor = new Editor({
      extensions: editorExtensions,
      content: '<p data-block-id="existing">Existing</p>'
    })

    editor.commands.insertContentAt(editor.state.doc.content.size, '<p data-block-id="existing">Duplicate</p>')

    const ids = identityIds(editor)
    expect(ids).toHaveLength(2)
    expect(ids[0]).toBe('existing')
    expect(ids[1]).toMatch(new RegExp(`^${TRANSIENT_BLOCK_ID_PREFIX}`))
    expect(new Set(ids).size).toBe(ids.length)

    const domIds = Array.from(editor.view.dom.querySelectorAll<HTMLElement>('[data-block-id]'))
      .map((element) => element.getAttribute('data-block-id') ?? '')
      .filter(Boolean)
    expect(new Set(domIds).size).toBe(domIds.length)
    expect(domIds).toContain('existing')
    expect(domIds.some((id) => id.startsWith(TRANSIENT_BLOCK_ID_PREFIX))).toBe(true)

    const duplicateNode = editor.getJSON().content?.[1]
    expect(duplicateNode?.attrs?.blockId).toBe('')
    expect(duplicateNode?.attrs?.transientBlockId).toMatch(new RegExp(`^${TRANSIENT_BLOCK_ID_PREFIX}`))
    editor.destroy()
  })
})
