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
  return (
    <section className="document-canvas">
      {!hasDocument ? (
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
