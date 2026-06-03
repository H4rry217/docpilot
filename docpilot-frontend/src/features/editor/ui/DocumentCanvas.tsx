import type { ReactNode } from 'react'

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

  return (
    <section className={`document-canvas ${isEmpty ? 'is-empty' : ''}`}>
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
