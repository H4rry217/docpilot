import {
  Braces,
  ChevronDown,
  ChevronUp,
  ExternalLink,
  GripHorizontal,
  PanelBottom,
  Wrench
} from 'lucide-react'
import {
  useCallback,
  type CSSProperties,
  type PointerEvent as ReactPointerEvent
} from 'react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { BlockDocument } from '@/entities/block/types'
import {
  type ToolPanelRect,
  type UseToolPanelLayoutResult
} from '../model/toolPanelLayout'
import { useI18n } from '@/shared/i18n'
import { DocumentOperationComposer } from './DocumentOperationComposer'
import {
  DocumentOperationsConsole,
  type DocumentOperationsConsoleController
} from './DocumentOperationsConsole'
import './WorkbenchToolPanels.css'

const TOOL_PANEL_CONTROL_ATTRIBUTE = 'data-tool-panel-control'

export type WorkbenchToolPanelsProps = {
  consoleController: DocumentOperationsConsoleController
  getBlockDocument?: () => BlockDocument | null | undefined
  getDocumentVersion?: () => string | null | undefined
  layout: UseToolPanelLayoutResult
  onApplyBlockDocument?: (blockDocument: BlockDocument) => void
  onJumpToBlock?: (blockId: string) => void
}

function isControlTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && Boolean(target.closest(`[${TOOL_PANEL_CONTROL_ATTRIBUTE}]`))
}

function ToolPanelTitle({ draggable = false }: { draggable?: boolean }) {
  const { t } = useI18n()

  return (
    <div className="tool-panel-title">
      {draggable ? <GripHorizontal className="tool-panel-drag-icon" aria-hidden="true" /> : null}
      <Braces aria-hidden="true" />
      <span>{t('developer.operationsPanel')}</span>
    </div>
  )
}

function ToolPanelHeader({
  collapsed = false,
  floating = false,
  onCollapseToggle,
  onDock,
  onFloat,
  onOpenComposer,
  onPointerDown
}: {
  collapsed?: boolean
  floating?: boolean
  onCollapseToggle?: () => void
  onDock?: () => void
  onFloat?: () => void
  onOpenComposer: () => void
  onPointerDown?: (event: ReactPointerEvent<HTMLElement>) => void
}) {
  const { t } = useI18n()

  return (
    <header
      className={cn('tool-panel-header', floating && 'is-floating')}
      onPointerDown={onPointerDown}
    >
      <ToolPanelTitle draggable={floating} />
      <div className="tool-panel-header-actions" {...{ [TOOL_PANEL_CONTROL_ATTRIBUTE]: true }}>
        {!collapsed ? (
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            className="tool-panel-compose-button"
            aria-label={t('developer.composer.open')}
            title={t('developer.composer.open')}
            onClick={onOpenComposer}
          >
            <Wrench aria-hidden="true" />
          </Button>
        ) : null}
        {floating ? (
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            className="tool-panel-action-button"
            aria-label={t('toolPanel.dockToBottom')}
            title={t('toolPanel.dockToBottom')}
            onClick={onDock}
          >
            <PanelBottom aria-hidden="true" />
          </Button>
        ) : (
          <>
            {!collapsed ? (
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                className="tool-panel-action-button"
                aria-label={t('toolPanel.floatWindow')}
                title={t('toolPanel.floatWindow')}
                onClick={onFloat}
              >
                <ExternalLink aria-hidden="true" />
              </Button>
            ) : null}
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
            className="tool-panel-action-button"
              aria-label={collapsed ? t('toolPanel.expandBottom') : t('toolPanel.collapseBottom')}
              title={collapsed ? t('toolPanel.expandBottom') : t('toolPanel.collapseBottom')}
              onClick={onCollapseToggle}
            >
              {collapsed ? <ChevronUp aria-hidden="true" /> : <ChevronDown aria-hidden="true" />}
            </Button>
          </>
        )}
      </div>
    </header>
  )
}

function BottomToolPanelDock({
  collapsed,
  consoleController,
  getBlockDocument,
  getDocumentVersion,
  onApplyBlockDocument,
  onCollapseToggle,
  onFloat,
  onJumpToBlock,
  onOpenComposer,
  onResizeStart
}: {
  collapsed: boolean
  consoleController: DocumentOperationsConsoleController
  getBlockDocument?: () => BlockDocument | null | undefined
  getDocumentVersion?: () => string | null | undefined
  onApplyBlockDocument?: (blockDocument: BlockDocument) => void
  onCollapseToggle: () => void
  onFloat: () => void
  onJumpToBlock?: (blockId: string) => void
  onOpenComposer: () => void
  onResizeStart: (event: ReactPointerEvent<HTMLElement>) => void
}) {
  const { t } = useI18n()

  return (
    <section
      className={cn('tool-panel-bottom-dock', collapsed && 'is-collapsed')}
      aria-label={t('developer.documentOperationsPanel')}
    >
      {!collapsed ? (
        <button
          type="button"
          className="tool-panel-bottom-resize-handle"
          aria-label={t('toolPanel.resizeBottom')}
          title={t('toolPanel.resizeBottom')}
          onPointerDown={onResizeStart}
        />
      ) : null}
      <ToolPanelHeader
        collapsed={collapsed}
        onCollapseToggle={onCollapseToggle}
        onFloat={onFloat}
        onOpenComposer={onOpenComposer}
      />
      {!collapsed ? (
        <DocumentOperationsConsole
          controller={consoleController}
          getBlockDocument={getBlockDocument}
          getDocumentVersion={getDocumentVersion}
          onApplyBlockDocument={onApplyBlockDocument}
          onJumpToBlock={onJumpToBlock}
        />
      ) : null}
    </section>
  )
}

function FloatingToolPanelWindow({
  consoleController,
  getBlockDocument,
  getDocumentVersion,
  onApplyBlockDocument,
  onDock,
  onDragStart,
  onJumpToBlock,
  onOpenComposer,
  onResizeStart,
  rect
}: {
  consoleController: DocumentOperationsConsoleController
  getBlockDocument?: () => BlockDocument | null | undefined
  getDocumentVersion?: () => string | null | undefined
  onApplyBlockDocument?: (blockDocument: BlockDocument) => void
  onDock: () => void
  onDragStart: (event: ReactPointerEvent<HTMLElement>) => void
  onJumpToBlock?: (blockId: string) => void
  onOpenComposer: () => void
  onResizeStart: (event: ReactPointerEvent<HTMLElement>) => void
  rect: ToolPanelRect
}) {
  const { t } = useI18n()
  const style = {
    left: rect.x,
    top: rect.y,
    width: rect.width,
    height: rect.height
  } as CSSProperties

  return (
    <section
      className="tool-panel-floating-window"
      style={style}
      aria-label={t('developer.documentOperationsPanel')}
    >
      <ToolPanelHeader
        floating
        onDock={onDock}
        onOpenComposer={onOpenComposer}
        onPointerDown={onDragStart}
      />
      <DocumentOperationsConsole
        controller={consoleController}
        getBlockDocument={getBlockDocument}
        getDocumentVersion={getDocumentVersion}
        onApplyBlockDocument={onApplyBlockDocument}
        onJumpToBlock={onJumpToBlock}
      />
      <button
        type="button"
        className="tool-panel-floating-resize-handle"
        aria-label={t('toolPanel.resizeFloating')}
        title={t('toolPanel.resizeFloating')}
        onPointerDown={onResizeStart}
      />
    </section>
  )
}

export function WorkbenchToolPanels({
  consoleController,
  getBlockDocument,
  getDocumentVersion,
  layout,
  onApplyBlockDocument,
  onJumpToBlock
}: WorkbenchToolPanelsProps) {
  const placement = layout.state.placements.documentOperations
  const floatingRect = layout.state.floatingRects.documentOperations

  const startBottomResize = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      event.preventDefault()
      const startY = event.clientY
      const startHeight = layout.state.bottomHeight

      function handlePointerMove(moveEvent: PointerEvent) {
        layout.setBottomHeight(startHeight + startY - moveEvent.clientY)
      }

      function handlePointerUp() {
        document.body.classList.remove('is-resizing-panel')
        window.removeEventListener('pointermove', handlePointerMove)
        window.removeEventListener('pointerup', handlePointerUp)
        window.removeEventListener('pointercancel', handlePointerUp)
      }

      document.body.classList.add('is-resizing-panel')
      window.addEventListener('pointermove', handlePointerMove)
      window.addEventListener('pointerup', handlePointerUp)
      window.addEventListener('pointercancel', handlePointerUp)
    },
    [layout]
  )

  const startFloatingDrag = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      if (isControlTarget(event.target)) return
      event.preventDefault()
      const startX = event.clientX
      const startY = event.clientY
      const startRect = floatingRect

      function handlePointerMove(moveEvent: PointerEvent) {
        layout.setFloatingRect('documentOperations', {
          ...startRect,
          x: startRect.x + moveEvent.clientX - startX,
          y: startRect.y + moveEvent.clientY - startY
        })
      }

      function handlePointerUp() {
        document.body.classList.remove('is-dragging-panel')
        window.removeEventListener('pointermove', handlePointerMove)
        window.removeEventListener('pointerup', handlePointerUp)
        window.removeEventListener('pointercancel', handlePointerUp)
      }

      document.body.classList.add('is-dragging-panel')
      window.addEventListener('pointermove', handlePointerMove)
      window.addEventListener('pointerup', handlePointerUp)
      window.addEventListener('pointercancel', handlePointerUp)
    },
    [floatingRect, layout]
  )

  const startFloatingResize = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      event.preventDefault()
      const startX = event.clientX
      const startY = event.clientY
      const startRect = floatingRect

      function handlePointerMove(moveEvent: PointerEvent) {
        layout.setFloatingRect('documentOperations', {
          ...startRect,
          width: startRect.width + moveEvent.clientX - startX,
          height: startRect.height + moveEvent.clientY - startY
        })
      }

      function handlePointerUp() {
        document.body.classList.remove('is-resizing-panel')
        window.removeEventListener('pointermove', handlePointerMove)
        window.removeEventListener('pointerup', handlePointerUp)
        window.removeEventListener('pointercancel', handlePointerUp)
      }

      document.body.classList.add('is-resizing-panel')
      window.addEventListener('pointermove', handlePointerMove)
      window.addEventListener('pointerup', handlePointerUp)
      window.addEventListener('pointercancel', handlePointerUp)
    },
    [floatingRect, layout]
  )

  return (
    <>
      {placement === 'bottom' ? (
        <BottomToolPanelDock
          collapsed={layout.state.bottomCollapsed}
          consoleController={consoleController}
          getBlockDocument={getBlockDocument}
          getDocumentVersion={getDocumentVersion}
          onApplyBlockDocument={onApplyBlockDocument}
          onCollapseToggle={layout.toggleBottomCollapsed}
          onFloat={() => layout.floatPanel('documentOperations')}
          onJumpToBlock={onJumpToBlock}
          onOpenComposer={() => consoleController.openComposer()}
          onResizeStart={startBottomResize}
        />
      ) : null}
      {placement === 'floating' ? (
        <FloatingToolPanelWindow
          consoleController={consoleController}
          getBlockDocument={getBlockDocument}
          getDocumentVersion={getDocumentVersion}
          onApplyBlockDocument={onApplyBlockDocument}
          onDock={() => layout.dockPanel('documentOperations')}
          onDragStart={startFloatingDrag}
          onJumpToBlock={onJumpToBlock}
          onOpenComposer={() => consoleController.openComposer()}
          onResizeStart={startFloatingResize}
          rect={floatingRect}
        />
      ) : null}
      <DocumentOperationComposer
        open={consoleController.composerOpen}
        initialBlockId={consoleController.composerInitialBlockId}
        getBlockDocument={getBlockDocument}
        getDocumentVersion={getDocumentVersion}
        onApplyBlockDocument={onApplyBlockDocument}
        onOpenChange={consoleController.setComposerOpen}
      />
    </>
  )
}
