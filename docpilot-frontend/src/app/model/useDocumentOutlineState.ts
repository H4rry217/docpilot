import { useEffect, useState } from 'react'
import type { DocumentOutlineItem, DocumentOutlineJumpRequest } from '../../entities/block/outline'

export function useDocumentOutlineState(documentId?: string) {
  const [documentOutline, setDocumentOutline] = useState<DocumentOutlineItem[]>([])
  const [activeOutlineId, setActiveOutlineId] = useState<string | undefined>()
  const [outlineJumpRequest, setOutlineJumpRequest] = useState<DocumentOutlineJumpRequest | undefined>()

  useEffect(() => {
    setActiveOutlineId(undefined)
    setOutlineJumpRequest(undefined)
  }, [documentId])

  function handleSelectOutlineItem(item: DocumentOutlineItem) {
    setActiveOutlineId(item.id)
    setOutlineJumpRequest((current) => ({
      id: item.id,
      headingIndex: item.headingIndex,
      requestId: (current?.requestId ?? 0) + 1
    }))
  }

  return {
    documentOutline,
    activeOutlineId,
    outlineJumpRequest,
    setDocumentOutline,
    handleSelectOutlineItem
  }
}
