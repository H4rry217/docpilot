import { useState, type DragEvent } from 'react'
import type { WorkspaceTreeNode } from '@/entities/workspace/types'
import { isMarkdownFileName } from '../model/markdownUpload'

export function hasFileDrag(dataTransfer: DataTransfer): boolean {
  return Array.from(dataTransfer.types).includes('Files')
}

export function markdownFiles(dataTransfer: DataTransfer): File[] {
  return Array.from(dataTransfer.files).filter((file) => isMarkdownFileName(file.name))
}

export function dragStayedInside(event: DragEvent): boolean {
  const relatedTarget = event.relatedTarget
  return relatedTarget instanceof Node && event.currentTarget.contains(relatedTarget)
}

export function useWorkspaceTreeDnd({
  onUploadMarkdownFiles
}: {
  onUploadMarkdownFiles?: (files: File[], parentNode?: WorkspaceTreeNode) => void
}) {
  const [dragTargetNodeId, setDragTargetNodeId] = useState<string>()
  const [rootDropTarget, setRootDropTarget] = useState(false)

  function handlePanelDragOver(event: DragEvent) {
    if (!onUploadMarkdownFiles || !hasFileDrag(event.dataTransfer)) return
    event.preventDefault()
    event.dataTransfer.dropEffect = 'none'
  }

  function handlePanelDragLeave(event: DragEvent) {
    if (dragStayedInside(event)) return
    setDragTargetNodeId(undefined)
    setRootDropTarget(false)
  }

  function handlePanelDrop(event: DragEvent) {
    if (!hasFileDrag(event.dataTransfer)) return
    event.preventDefault()
    setDragTargetNodeId(undefined)
    setRootDropTarget(false)
  }

  function handleRootDragOver(event: DragEvent) {
    if (!onUploadMarkdownFiles || !hasFileDrag(event.dataTransfer)) return
    event.preventDefault()
    event.stopPropagation()
    event.dataTransfer.dropEffect = 'copy'
    setRootDropTarget(true)
    setDragTargetNodeId(undefined)
  }

  function handleRootDragLeave(event: DragEvent) {
    if (dragStayedInside(event)) return
    setRootDropTarget(false)
  }

  function handleRootDrop(event: DragEvent) {
    if (!onUploadMarkdownFiles || !hasFileDrag(event.dataTransfer)) return
    event.preventDefault()
    event.stopPropagation()
    setRootDropTarget(false)
    onUploadMarkdownFiles(markdownFiles(event.dataTransfer))
  }

  function handleMarkdownDragOver(event: DragEvent, node: WorkspaceTreeNode) {
    if (!onUploadMarkdownFiles || !hasFileDrag(event.dataTransfer)) return
    event.preventDefault()
    event.stopPropagation()
    event.dataTransfer.dropEffect = 'copy'
    setDragTargetNodeId(node.nodeId)
    setRootDropTarget(false)
  }

  function handleMarkdownDragLeave(event: DragEvent, node: WorkspaceTreeNode) {
    if (dragStayedInside(event)) return
    setDragTargetNodeId((current) => (current === node.nodeId ? undefined : current))
  }

  function handleMarkdownDrop(event: DragEvent, node: WorkspaceTreeNode) {
    if (!onUploadMarkdownFiles || !hasFileDrag(event.dataTransfer)) return
    event.preventDefault()
    event.stopPropagation()
    setDragTargetNodeId(undefined)
    onUploadMarkdownFiles(markdownFiles(event.dataTransfer), node)
  }

  return {
    dragTargetNodeId,
    rootDropTarget,
    handleMarkdownDragLeave,
    handleMarkdownDragOver,
    handleMarkdownDrop,
    handlePanelDragLeave,
    handlePanelDragOver,
    handlePanelDrop,
    handleRootDragLeave,
    handleRootDragOver,
    handleRootDrop
  }
}
