import {
  defaultComputePositionConfig,
  DragHandlePlugin,
  normalizeNestedOptions,
  type DragHandleRule
} from '@tiptap/extension-drag-handle'
import type { Node as ProseMirrorNode } from '@tiptap/pm/model'
import type { Editor } from '@tiptap/react'
import { useEffect, useState, type RefObject } from 'react'
import type { BlockAffordanceHighlightDecoration } from '../model/blockAffordanceHighlight'
import {
  BLOCK_AFFORDANCE_CONTEXT_HANDLE_WIDTH_PX,
  BLOCK_AFFORDANCE_TRIGGER_HEIGHT_PX
} from './blockAffordanceTypes'

const DRAG_HANDLE_PLUGIN_KEY = 'docpilotBlockDragHandle'
const LIST_ITEM_NODE_TYPES = new Set(['listItem', 'taskItem'])
const LIST_WRAPPER_NODE_TYPES = new Set(['bulletList', 'orderedList', 'taskList'])
const TABLE_NODE_TYPES = new Set(['table', 'tableRow', 'tableCell', 'tableHeader'])

const docpilotDragHandleRules: DragHandleRule[] = [
  {
    id: 'docpilotExcludeListWrapperTargets',
    evaluate: ({ node }) => {
      if (LIST_WRAPPER_NODE_TYPES.has(node.type.name)) return 1500
      return 0
    }
  },
  {
    id: 'docpilotPreferDeepListItems',
    evaluate: ({ node, depth }) => {
      if (LIST_ITEM_NODE_TYPES.has(node.type.name)) return -(depth * 700)
      return 0
    }
  },
  {
    id: 'docpilotExcludeTableTargets',
    evaluate: ({ node, parent }) => {
      if (TABLE_NODE_TYPES.has(node.type.name)) return 1000
      if (parent && TABLE_NODE_TYPES.has(parent.type.name)) return 1000
      return 0
    }
  }
]

export function useBlockDragHandle({
  activateBlockElement,
  activateBlockPosition,
  clearHighlightRevealTimer,
  dragHandleApproachLockedRef,
  dragHandleElementRef,
  dragHandlePointerBlockElementRef,
  dragHandleReferenceRectRef,
  editor,
  menuOpenRef,
  notifyBlockInteractionStart,
  pendingBlockHighlightRef,
  refreshCurrentGeometry,
  releaseDragHandleApproachLock,
  setMenuOpen
}: {
  activateBlockElement: (blockElement: HTMLElement | null) => void
  activateBlockPosition: (position: number, node: ProseMirrorNode | null) => void
  clearHighlightRevealTimer: () => void
  dragHandleApproachLockedRef: RefObject<boolean>
  dragHandleElementRef: RefObject<HTMLDivElement | null>
  dragHandlePointerBlockElementRef: RefObject<HTMLElement | null>
  dragHandleReferenceRectRef: RefObject<DOMRect | null>
  editor: Editor | null
  menuOpenRef: RefObject<boolean>
  notifyBlockInteractionStart: () => void
  pendingBlockHighlightRef: RefObject<BlockAffordanceHighlightDecoration | null>
  refreshCurrentGeometry: () => void
  releaseDragHandleApproachLock: () => void
  setMenuOpen: (open: boolean) => void
}) {
  const [portalElement, setPortalElement] = useState<HTMLElement | null>(null)

  useEffect(() => {
    if (!editor) return
    const activeEditor: Editor = editor
    if (!activeEditor || activeEditor.isDestroyed) return

    const element = document.createElement('div')
    element.className = 'block-affordance-drag-handle'
    element.style.position = 'absolute'
    element.style.visibility = 'hidden'
    element.style.width = `${BLOCK_AFFORDANCE_CONTEXT_HANDLE_WIDTH_PX}px`
    element.style.height = `${BLOCK_AFFORDANCE_TRIGGER_HEIGHT_PX}px`
    element.dataset.dragging = 'false'
    element.addEventListener('mouseenter', releaseDragHandleApproachLock)
    dragHandleElementRef.current = element
    setPortalElement(element)

    const { plugin, unbind } = DragHandlePlugin({
      editor: activeEditor,
      element,
      pluginKey: DRAG_HANDLE_PLUGIN_KEY,
      computePositionConfig: defaultComputePositionConfig,
      getReferencedVirtualElement: () => {
        const rect = dragHandleReferenceRectRef.current
        return rect ? { getBoundingClientRect: () => rect } : null
      },
      nestedOptions: normalizeNestedOptions({
        defaultRules: true,
        edgeDetection: 'none',
        rules: docpilotDragHandleRules
      }),
      onNodeChange: ({ node, pos }) => {
        if (menuOpenRef.current || dragHandleApproachLockedRef.current) return
        const pointerBlockElement = dragHandlePointerBlockElementRef.current
        if (pointerBlockElement && activeEditor.view.dom.contains(pointerBlockElement)) {
          activateBlockElement(pointerBlockElement)
          return
        }
        activateBlockPosition(pos, node)
      },
      onElementDragStart: () => {
        notifyBlockInteractionStart()
        setMenuOpen(false)
      },
      onElementDragEnd: () => {
        window.requestAnimationFrame(refreshCurrentGeometry)
      }
    })

    activeEditor.registerPlugin(plugin)

    return () => {
      if (!activeEditor.isDestroyed) {
        activeEditor.unregisterPlugin(DRAG_HANDLE_PLUGIN_KEY)
      }
      unbind()
      element.removeEventListener('mouseenter', releaseDragHandleApproachLock)
      dragHandleReferenceRectRef.current = null
      dragHandleElementRef.current = null
      dragHandlePointerBlockElementRef.current = null
      dragHandleApproachLockedRef.current = false
      pendingBlockHighlightRef.current = null
      clearHighlightRevealTimer()
      setPortalElement(null)
    }
  }, [
    activateBlockElement,
    activateBlockPosition,
    clearHighlightRevealTimer,
    dragHandleApproachLockedRef,
    dragHandleElementRef,
    dragHandlePointerBlockElementRef,
    dragHandleReferenceRectRef,
    editor,
    menuOpenRef,
    notifyBlockInteractionStart,
    pendingBlockHighlightRef,
    refreshCurrentGeometry,
    releaseDragHandleApproachLock,
    setMenuOpen
  ])

  return portalElement
}
