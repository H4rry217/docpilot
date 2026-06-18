import type { ReactNode } from 'react'
import { useTransientScrollbarVisibility } from './useTransientScrollbarVisibility'

export function DocumentCanvas({
  emptyMessage,
  loadingMessage,
  errorMessage,
  hasDocument,
  isLoading,
  hasError,
  children
}: {
  emptyMessage: string
  loadingMessage: string
  errorMessage: string
  hasDocument: boolean
  isLoading: boolean
  hasError: boolean
  children: ReactNode
}) {
  const isEmpty = !hasDocument
  const { isScrollbarVisible, revealScrollbar } = useTransientScrollbarVisibility({ disabled: isEmpty })

  return (
    <section
      className={`document-canvas editor-transient-scrollbar ${isEmpty ? 'is-empty' : ''} ${isScrollbarVisible ? 'is-scrollbar-visible' : ''}`}
      onPointerMove={revealScrollbar}
      onScroll={revealScrollbar}
      onWheel={revealScrollbar}
    >
      {isEmpty ? (
        <div className="document-empty">{emptyMessage}</div>
      ) : isLoading ? (
        <div className="document-empty">{loadingMessage}</div>
      ) : hasError ? (
        <div className="document-empty">{errorMessage}</div>
      ) : (
        children
      )}
    </section>
  )
}
