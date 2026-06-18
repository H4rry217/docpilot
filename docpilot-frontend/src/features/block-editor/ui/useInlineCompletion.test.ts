import { act, cleanup, render } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Editor } from '@tiptap/react'
import { createElement } from 'react'
import { completeInlineCompletion } from '@/features/inline-completion/api/inlineCompletionApi'
import { clearInlineCompletion } from '../model/inlineCompletion'
import {
  clampCandidateCount,
  isEditorInteractionFocused,
  markdownPreviewText
} from './inlineCompletionRuntime'
import { useInlineCompletion } from './useInlineCompletion'

vi.mock('../../inline-completion/api/inlineCompletionApi', () => ({
  completeInlineCompletion: vi.fn()
}))

vi.mock('../model/inlineCompletion', () => ({
  clearInlineCompletion: vi.fn(),
  setInlineCompletionSuggestion: vi.fn()
}))

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  vi.useRealTimers()
})

function InlineCompletionHarness({
  editor
}: {
  editor: Editor
}) {
  useInlineCompletion({
    editor,
    contentKey: 'document-1',
    context: {
      documentId: 'document-1',
      workspaceId: 'workspace-1'
    },
    getSnapshot: () => null,
    isApplyingContent: () => false,
    settings: {
      candidateCount: 3,
      enabled: true,
      idleDelayMs: 1000
    }
  })
  return null
}

function editorWithDom(editorDom: HTMLElement): Editor {
  const handlers = new Map<string, Set<(payload: unknown) => void>>()
  return {
    isFocused: true,
    off: (event: string, handler: (payload: unknown) => void) => {
      handlers.get(event)?.delete(handler)
      return undefined
    },
    on: (event: string, handler: (payload: unknown) => void) => {
      const listeners = handlers.get(event) ?? new Set()
      listeners.add(handler)
      handlers.set(event, listeners)
      return undefined
    },
    state: {
      selection: {
        empty: true,
        from: 1,
        to: 1
      }
    },
    view: {
      dom: editorDom,
      root: document
    }
  } as unknown as Editor
}

describe('isEditorInteractionFocused', () => {
  it('accepts focus inside the editor DOM for embedded node views', () => {
    const editorDom = document.createElement('div')
    const embeddedEditor = document.createElement('textarea')
    editorDom.appendChild(embeddedEditor)
    document.body.appendChild(editorDom)
    embeddedEditor.focus()

    const editor = {
      isFocused: false,
      view: {
        dom: editorDom,
        root: document
      }
    } as unknown as Editor

    expect(isEditorInteractionFocused(editor)).toBe(true)
    editorDom.remove()
  })

  it('rejects focus outside the editor DOM', () => {
    const editorDom = document.createElement('div')
    const outsideInput = document.createElement('input')
    document.body.append(editorDom, outsideInput)
    outsideInput.focus()

    const editor = {
      isFocused: false,
      view: {
        dom: editorDom,
        root: document
      }
    } as unknown as Editor

    expect(isEditorInteractionFocused(editor)).toBe(false)
    editorDom.remove()
    outsideInput.remove()
  })

  it('clamps candidate counts to the supported request range', () => {
    expect(clampCandidateCount(undefined)).toBe(3)
    expect(clampCandidateCount(0)).toBe(1)
    expect(clampCandidateCount(7)).toBe(5)
    expect(clampCandidateCount(2.8)).toBe(2)
  })

  it('creates plain preview text from markdown candidates', () => {
    expect(markdownPreviewText('## Title\n\n- **Item**', 'SENTENCE')).toBe('Title\nItem')
    expect(markdownPreviewText('const value = 1', 'CODE_LINE')).toBe('const value = 1')
  })

  it('stops pending idle completion during IME composition without clearing suggestions', () => {
    vi.useFakeTimers()
    const editorDom = document.createElement('div')
    const editor = editorWithDom(editorDom)
    render(createElement(InlineCompletionHarness, { editor }))
    vi.mocked(clearInlineCompletion).mockClear()

    act(() => {
      editorDom.dispatchEvent(new Event('input', { bubbles: true }))
    })
    expect(clearInlineCompletion).toHaveBeenCalled()
    vi.mocked(clearInlineCompletion).mockClear()

    act(() => {
      editorDom.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
      vi.advanceTimersByTime(1000)
    })

    expect(clearInlineCompletion).not.toHaveBeenCalled()
    expect(completeInlineCompletion).not.toHaveBeenCalled()
  })
})
