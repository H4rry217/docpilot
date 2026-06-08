import { useEffect, useRef, useState, type ChangeEvent, type KeyboardEvent } from 'react'

export function MermaidCaptionEditor({
  caption,
  isEditing,
  onCancel,
  onCommit,
  onOpen
}: {
  caption: string
  isEditing: boolean
  onCancel: () => void
  onCommit: (caption: string) => void
  onOpen: () => void
}) {
  const captionInputRef = useRef<HTMLTextAreaElement | null>(null)
  const [captionDraft, setCaptionDraft] = useState('')

  useEffect(() => {
    if (!isEditing) {
      setCaptionDraft(caption)
    }
  }, [caption, isEditing])

  useEffect(() => {
    if (isEditing) {
      window.setTimeout(() => {
        captionInputRef.current?.focus()
        resizeCaptionInput()
      }, 0)
    }
  }, [isEditing])

  useEffect(() => {
    if (isEditing) {
      resizeCaptionInput()
    }
  }, [captionDraft, isEditing])

  function commitCaption(value = captionDraft) {
    const nextCaption = value.trim()
    onCommit(nextCaption)
    setCaptionDraft(nextCaption)
  }

  function resizeCaptionInput() {
    const element = captionInputRef.current
    if (!element) return
    element.style.height = '0px'
    element.style.height = `${element.scrollHeight}px`
  }

  function handleCaptionChange(event: ChangeEvent<HTMLTextAreaElement>) {
    setCaptionDraft(event.target.value)
    window.requestAnimationFrame(resizeCaptionInput)
  }

  function handleCaptionKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      commitCaption()
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      setCaptionDraft(caption)
      onCancel()
    }
  }

  return (
    <div className="code-block-mermaid-caption code-block-control" contentEditable={false}>
      {isEditing ? (
        <textarea
          ref={captionInputRef}
          value={captionDraft}
          placeholder="Add caption"
          aria-label="Diagram caption"
          rows={1}
          onChange={handleCaptionChange}
          onBlur={() => commitCaption()}
          onKeyDown={handleCaptionKeyDown}
          onMouseDown={(event) => event.stopPropagation()}
        />
      ) : (
        <button
          type="button"
          className="code-block-mermaid-caption-text"
          aria-label="Edit diagram caption"
          onMouseDown={(event) => event.stopPropagation()}
          onClick={onOpen}
        >
          {caption}
        </button>
      )}
    </div>
  )
}
