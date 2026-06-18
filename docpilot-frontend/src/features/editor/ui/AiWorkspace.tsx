import {
  Bot,
  ChevronLeft,
  GripHorizontal,
  Minimize2,
  PanelRightClose,
  PanelRightOpen
} from 'lucide-react'
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type PointerEvent as ReactPointerEvent,
  type ReactNode
} from 'react'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { useI18n } from '@/shared/i18n'
import {
  AI_WORKSPACE_ORB_SIZE,
  type AiWorkspacePoint,
  type AiWorkspaceRect,
  type UseAiWorkspaceLayoutResult
} from '../model/aiWorkspaceLayout'
import { AiChatPanelContent } from './AiReviewPanel'

const CONTROL_ATTRIBUTE = 'data-ai-workspace-control'
const AI_WORKSPACE_DRAG_START_THRESHOLD = 8
const AI_WORKSPACE_DOCK_SNAP_EDGE_PX = 28
export const AI_WORKSPACE_FLOATING_MORPH_MS = 270

type AiWorkspaceProps = {
  layout: UseAiWorkspaceLayoutResult
}

type FloatingMorphDirection = 'minimize' | 'restore'
type AiWorkspaceDragCue = 'dock' | 'detach' | null

type FloatingMorphState = {
  id: number
  direction: FloatingMorphDirection
  windowRect: AiWorkspaceRect
  orbPosition: AiWorkspacePoint
}

type ControlButtonProps = {
  label: string
  children: ReactNode
  onClick: () => void
}

function addDragClass() {
  document.body.classList.add('is-dragging-panel')
}

function removeDragClass() {
  document.body.classList.remove('is-dragging-panel')
}

function isControlTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && Boolean(target.closest(`[${CONTROL_ATTRIBUTE}]`))
}

function shouldReduceMotion(): boolean {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function isNearRightDockEdge(rect: AiWorkspaceRect): boolean {
  return rect.x + rect.width >= window.innerWidth - AI_WORKSPACE_DOCK_SNAP_EDGE_PX
}

function ControlButton({ label, children, onClick }: ControlButtonProps) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={label}
          title={label}
          onClick={onClick}
          {...{ [CONTROL_ATTRIBUTE]: true }}
        >
          {children}
        </Button>
      </TooltipTrigger>
      <TooltipContent side="bottom">{label}</TooltipContent>
    </Tooltip>
  )
}

function useAiWorkspacePointerInteractions(
  layout: UseAiWorkspaceLayoutResult,
  onOrbRestore: () => void,
  onDragCueChange: (cue: AiWorkspaceDragCue) => void
) {
  const orbWasDraggedRef = useRef(false)
  const dragCueRef = useRef<AiWorkspaceDragCue>(null)

  const setDragCue = useCallback(
    (cue: AiWorkspaceDragCue) => {
      if (dragCueRef.current === cue) return
      dragCueRef.current = cue
      onDragCueChange(cue)
    },
    [onDragCueChange]
  )

  const startDockedDragOut = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      if (isControlTarget(event.target)) return

      event.preventDefault()
      const startX = event.clientX
      const startY = event.clientY
      const startRect = layout.state.floatingRect
      const startWidth = startRect.width
      const startHeight = startRect.height
      const panelRect = event.currentTarget.closest('aside')?.getBoundingClientRect()
      const panelLeft = panelRect && panelRect.width > 0 ? panelRect.left : window.innerWidth - layout.state.dockWidth
      const panelTop = panelRect && panelRect.height > 0 ? panelRect.top : 0
      const pointerOffsetX = Math.min(Math.max(startX - panelLeft, 24), Math.max(24, startWidth - 24))
      const pointerOffsetY = Math.min(Math.max(startY - panelTop, 16), 44)
      let draggingOut = false
      let hasUndocked = false

      function handlePointerMove(moveEvent: PointerEvent) {
        if (!draggingOut) {
          if (startX - moveEvent.clientX < AI_WORKSPACE_DRAG_START_THRESHOLD) return
          draggingOut = true
          addDragClass()
          setDragCue('detach')
        }

        const nextRect = {
          x: moveEvent.clientX - pointerOffsetX,
          y: moveEvent.clientY - pointerOffsetY,
          width: startWidth,
          height: startHeight
        }
        layout.setFloatingRect(nextRect)
        if (!hasUndocked) {
          layout.undock()
          hasUndocked = true
        }
      }

      function handlePointerUp() {
        if (draggingOut) {
          removeDragClass()
        }
        setDragCue(null)
        window.removeEventListener('pointermove', handlePointerMove)
        window.removeEventListener('pointerup', handlePointerUp)
        window.removeEventListener('pointercancel', handlePointerUp)
      }

      window.addEventListener('pointermove', handlePointerMove)
      window.addEventListener('pointerup', handlePointerUp)
      window.addEventListener('pointercancel', handlePointerUp)
    },
    [layout, setDragCue]
  )

  const startDockResize = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      event.preventDefault()
      const startX = event.clientX
      const startWidth = layout.state.dockWidth

      function handlePointerMove(moveEvent: PointerEvent) {
        layout.setDockWidth(startWidth + startX - moveEvent.clientX)
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
      const startRect = layout.state.floatingRect
      let latestRect = startRect

      function handlePointerMove(moveEvent: PointerEvent) {
        latestRect = {
          ...startRect,
          x: startRect.x + moveEvent.clientX - startX,
          y: startRect.y + moveEvent.clientY - startY
        }
        layout.setFloatingRect(latestRect)
        setDragCue(isNearRightDockEdge(latestRect) ? 'dock' : null)
      }

      function handlePointerUp() {
        removeDragClass()
        setDragCue(null)
        if (isNearRightDockEdge(latestRect)) {
          layout.dock()
        }
        window.removeEventListener('pointermove', handlePointerMove)
        window.removeEventListener('pointerup', handlePointerUp)
        window.removeEventListener('pointercancel', handlePointerUp)
      }

      addDragClass()
      window.addEventListener('pointermove', handlePointerMove)
      window.addEventListener('pointerup', handlePointerUp)
      window.addEventListener('pointercancel', handlePointerUp)
    },
    [layout, setDragCue]
  )

  const startFloatingResize = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      event.preventDefault()
      const startX = event.clientX
      const startY = event.clientY
      const startRect = layout.state.floatingRect

      function handlePointerMove(moveEvent: PointerEvent) {
        layout.setFloatingRect({
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
    [layout]
  )

  const startOrbDrag = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      event.preventDefault()
      const startX = event.clientX
      const startY = event.clientY
      const startPosition = layout.state.orbPosition
      orbWasDraggedRef.current = false

      function handlePointerMove(moveEvent: PointerEvent) {
        const deltaX = moveEvent.clientX - startX
        const deltaY = moveEvent.clientY - startY
        if (Math.abs(deltaX) > 3 || Math.abs(deltaY) > 3) {
          orbWasDraggedRef.current = true
        }
        layout.setOrbPosition({
          x: startPosition.x + deltaX,
          y: startPosition.y + deltaY
        })
      }

      function handlePointerUp() {
        removeDragClass()
        window.removeEventListener('pointermove', handlePointerMove)
        window.removeEventListener('pointerup', handlePointerUp)
        window.removeEventListener('pointercancel', handlePointerUp)
      }

      addDragClass()
      window.addEventListener('pointermove', handlePointerMove)
      window.addEventListener('pointerup', handlePointerUp)
      window.addEventListener('pointercancel', handlePointerUp)
    },
    [layout]
  )

  const handleOrbClick = useCallback(() => {
    if (orbWasDraggedRef.current) {
      orbWasDraggedRef.current = false
      return
    }
    onOrbRestore()
  }, [onOrbRestore])

  return {
    startDockedDragOut,
    startDockResize,
    startFloatingDrag,
    startFloatingResize,
    startOrbDrag,
    handleOrbClick
  }
}

function AiWorkspaceHeader({
  docked,
  onDock,
  onUndock,
  onMinimize,
  onHeaderPointerDown
}: {
  docked: boolean
  onDock: () => void
  onUndock: () => void
  onMinimize: () => void
  onHeaderPointerDown?: (event: ReactPointerEvent<HTMLElement>) => void
}) {
  const { t } = useI18n()

  return (
    <header
      aria-label={t('ai.workspace.controlBar')}
      className={cn(
        'flex min-h-11 shrink-0 items-center justify-between gap-2 border-b bg-background px-2.5',
        onHeaderPointerDown && 'cursor-grab active:cursor-grabbing'
      )}
      onPointerDown={onHeaderPointerDown}
    >
      <div className="flex min-w-0 items-center gap-2">
        {!docked ? <GripHorizontal className="text-muted-foreground" aria-hidden="true" /> : null}
        {docked ? (
          <span
            data-ai-workspace-brand="true"
            className="flex size-7 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground"
            aria-hidden="true"
          >
            <Bot />
          </span>
        ) : null}
      </div>
      <div className="flex shrink-0 items-center gap-0.5">
        {docked ? (
          <ControlButton label={t('ai.workspace.switchToFloating')} onClick={onUndock}>
            <PanelRightOpen />
          </ControlButton>
        ) : (
          <ControlButton label={t('ai.workspace.dockRight')} onClick={onDock}>
            <PanelRightClose />
          </ControlButton>
        )}
        <ControlButton label={t('ai.workspace.minimize')} onClick={onMinimize}>
          <Minimize2 />
        </ControlButton>
      </div>
    </header>
  )
}

function AiWorkspaceBody({ focusRequest }: { focusRequest: number }) {
  return (
    <>
      <AiChatPanelContent focusRequest={focusRequest} />
    </>
  )
}

function DockedMinimizedRail({ onRestore }: { onRestore: () => void }) {
  const { t } = useI18n()

  return (
    <button
      type="button"
      className="ai-workspace-docked-rail group col-start-2 row-start-2 row-end-3 flex h-full min-h-0 w-full flex-col items-center overflow-hidden border-l bg-background/95 px-0 py-2 text-muted-foreground transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      aria-label={t('ai.workspace.restore')}
      title={t('ai.workspace.restore')}
      onClick={onRestore}
    >
      <span className="flex size-7 shrink-0 items-center justify-center rounded-[min(var(--radius-md),12px)] transition-colors group-hover:bg-muted group-hover:text-foreground group-focus-visible:bg-muted group-focus-visible:text-foreground">
        <ChevronLeft className="size-4 shrink-0" />
      </span>
    </button>
  )
}

function FloatingOrb({
  position,
  onPointerDown,
  onClick
}: {
  position: AiWorkspacePoint
  onPointerDown: (event: ReactPointerEvent<HTMLElement>) => void
  onClick: () => void
}) {
  const { t } = useI18n()
  const style = {
    left: position.x,
    top: position.y,
    width: AI_WORKSPACE_ORB_SIZE,
    height: AI_WORKSPACE_ORB_SIZE
  } as CSSProperties

  return (
    <button
      type="button"
      className="fixed z-50 flex items-center justify-center rounded-full bg-foreground text-background shadow-xl ring-1 ring-background/80 transition-transform hover:scale-105 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      style={style}
      aria-label={t('ai.workspace.restore')}
      title={t('ai.workspace.restore')}
      onPointerDown={onPointerDown}
      onClick={onClick}
    >
      <Bot />
    </button>
  )
}

function FloatingMorphTransition({
  state
}: {
  state: FloatingMorphState
}) {
  const orbRect = {
    x: state.orbPosition.x,
    y: state.orbPosition.y,
    width: AI_WORKSPACE_ORB_SIZE,
    height: AI_WORKSPACE_ORB_SIZE
  }
  const scaleX = orbRect.width / state.windowRect.width
  const scaleY = orbRect.height / state.windowRect.height
  const deltaX = orbRect.x - state.windowRect.x
  const deltaY = orbRect.y - state.windowRect.y
  const collapsedTransform = `translate3d(${deltaX}px, ${deltaY}px, 0) scale3d(${scaleX}, ${scaleY}, 1)`
  const style = {
    left: state.windowRect.x,
    top: state.windowRect.y,
    width: state.windowRect.width,
    height: state.windowRect.height,
    '--ai-workspace-morph-duration': `${AI_WORKSPACE_FLOATING_MORPH_MS}ms`,
    '--ai-workspace-morph-end-transform': collapsedTransform,
    backfaceVisibility: 'hidden',
    contain: 'paint style',
    willChange: 'transform, opacity'
  } as CSSProperties & Record<string, string | number>
  const orbStyle = {
    left: orbRect.x,
    top: orbRect.y,
    width: orbRect.width,
    height: orbRect.height,
    '--ai-workspace-morph-duration': `${AI_WORKSPACE_FLOATING_MORPH_MS}ms`
  } as CSSProperties & Record<string, string | number>

  return (
    <>
      <div
        data-testid="ai-workspace-floating-morph"
        className={cn(
          'ai-workspace-morph pointer-events-none fixed z-50 overflow-hidden rounded-xl border border-border/80 shadow-sm',
          state.direction === 'restore' ? 'ai-workspace-morph-restore' : 'ai-workspace-morph-minimize'
        )}
        style={style}
        aria-hidden="true"
      />
      <div
        className={cn(
          'ai-workspace-morph-end-orb pointer-events-none fixed z-50 flex items-center justify-center rounded-full bg-foreground text-background shadow-xl ring-1 ring-background/80',
          state.direction === 'restore' ? 'ai-workspace-morph-end-orb-restore' : 'ai-workspace-morph-end-orb-minimize'
        )}
        style={orbStyle}
        aria-hidden="true"
      >
        <Bot />
      </div>
    </>
  )
}

function DockedAiWorkspace({
  layout,
  focusRequest,
  dragCue,
  onHeaderPointerDown,
  onResizeStart
}: {
  layout: UseAiWorkspaceLayoutResult
  focusRequest: number
  dragCue: AiWorkspaceDragCue
  onHeaderPointerDown: (event: ReactPointerEvent<HTMLElement>) => void
  onResizeStart: (event: ReactPointerEvent<HTMLElement>) => void
}) {
  const { t } = useI18n()

  return (
    <aside
      data-testid="ai-workspace-panel"
      className={cn(
        'ai-workspace-docked-panel relative col-start-2 row-start-2 row-end-3 flex h-full min-h-0 min-w-0 overflow-visible border-l bg-background transition-[border-color,box-shadow]',
        dragCue === 'detach' && 'border-blue-400/80 ring-2 ring-inset ring-blue-300/70'
      )}
      aria-label={t('ai.workspace.label')}
    >
      <button
        className="absolute inset-y-0 -left-1.5 z-50 w-3 cursor-col-resize border-0 bg-transparent p-0 after:absolute after:inset-y-0 after:left-1/2 after:w-0.5 after:-translate-x-1/2 after:rounded-full after:bg-primary after:opacity-0 after:transition-opacity hover:after:opacity-100 focus-visible:outline-none focus-visible:after:opacity-100 active:after:opacity-100"
        type="button"
        aria-label={t('ai.workspace.resizeDock')}
        title={t('ai.workspace.resizeDock')}
        onPointerDown={onResizeStart}
      />
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <AiWorkspaceHeader
          docked
          onDock={layout.dock}
          onUndock={layout.undock}
          onMinimize={layout.minimize}
          onHeaderPointerDown={onHeaderPointerDown}
        />
        <AiWorkspaceBody focusRequest={focusRequest} />
      </div>
    </aside>
  )
}

function FloatingAiWorkspace({
  rect,
  layout,
  focusRequest,
  morphing,
  dragCue,
  onMinimize,
  onHeaderPointerDown,
  onResizeStart
}: {
  rect: AiWorkspaceRect
  layout: UseAiWorkspaceLayoutResult
  focusRequest: number
  morphing: boolean
  dragCue: AiWorkspaceDragCue
  onMinimize: () => void
  onHeaderPointerDown: (event: ReactPointerEvent<HTMLElement>) => void
  onResizeStart: (event: ReactPointerEvent<HTMLElement>) => void
}) {
  const { t } = useI18n()
  const style = {
    left: rect.x,
    top: rect.y,
    width: rect.width,
    height: rect.height
  } as CSSProperties

  return (
    <aside
      data-testid="ai-workspace-panel"
      className={cn(
        'fixed z-50 flex min-h-0 min-w-0 flex-col overflow-hidden rounded-xl border bg-background/95 shadow-2xl backdrop-blur transition-[border-color,box-shadow]',
        dragCue && 'border-blue-400/80 ring-2 ring-blue-300/70',
        morphing && 'pointer-events-none opacity-0'
      )}
      style={style}
      aria-hidden={morphing ? 'true' : undefined}
      aria-label={t('ai.workspace.label')}
    >
      <AiWorkspaceHeader
        docked={false}
        onDock={layout.dock}
        onUndock={layout.undock}
        onMinimize={onMinimize}
        onHeaderPointerDown={onHeaderPointerDown}
      />
      <AiWorkspaceBody focusRequest={focusRequest} />
      <Separator />
      <button
        type="button"
        className="absolute bottom-0 right-0 size-5 cursor-nwse-resize border-0 bg-transparent p-0 text-muted-foreground after:absolute after:bottom-1 after:right-1 after:size-2.5 after:rounded-br-md after:border-b after:border-r after:border-current focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        aria-label={t('ai.workspace.resizeFloating')}
        title={t('ai.workspace.resizeFloating')}
        onPointerDown={onResizeStart}
      />
    </aside>
  )
}

export function AiWorkspace({ layout }: AiWorkspaceProps) {
  const { state } = layout
  const [floatingMorph, setFloatingMorph] = useState<FloatingMorphState | null>(null)
  const [composerFocusRequest, setComposerFocusRequest] = useState(0)
  const [dragCue, setDragCue] = useState<AiWorkspaceDragCue>(null)
  const floatingMorphTimerRef = useRef<number | null>(null)
  const floatingMorphFrameRef = useRef<number | null>(null)
  const floatingMorphIdRef = useRef(0)

  const requestComposerFocus = useCallback(() => {
    setComposerFocusRequest((request) => request + 1)
  }, [])

  const clearFloatingMorphTimer = useCallback(() => {
    if (floatingMorphTimerRef.current !== null) {
      window.clearTimeout(floatingMorphTimerRef.current)
      floatingMorphTimerRef.current = null
    }
    if (floatingMorphFrameRef.current !== null) {
      window.cancelAnimationFrame(floatingMorphFrameRef.current)
      floatingMorphFrameRef.current = null
    }
  }, [])

  const startFloatingMorph = useCallback(
    (
      direction: FloatingMorphDirection,
      finishAction: () => void,
      finishAtStart = false,
      afterMorph?: () => void
    ) => {
      clearFloatingMorphTimer()

      if (shouldReduceMotion()) {
        finishAction()
        afterMorph?.()
        return
      }

      setFloatingMorph({
        id: floatingMorphIdRef.current + 1,
        direction,
        windowRect: state.floatingRect,
        orbPosition: state.orbPosition
      })
      floatingMorphIdRef.current += 1
      if (finishAtStart) {
        finishAction()
      }
      floatingMorphTimerRef.current = window.setTimeout(() => {
        if (!finishAtStart) {
          finishAction()
          floatingMorphFrameRef.current = window.requestAnimationFrame(() => {
            floatingMorphFrameRef.current = window.requestAnimationFrame(() => {
              setFloatingMorph(null)
              floatingMorphFrameRef.current = null
              afterMorph?.()
            })
          })
          floatingMorphTimerRef.current = null
          return
        }
        setFloatingMorph(null)
        afterMorph?.()
        floatingMorphTimerRef.current = null
      }, AI_WORKSPACE_FLOATING_MORPH_MS)
    },
    [clearFloatingMorphTimer, state.floatingRect, state.orbPosition]
  )

  const startFloatingMinimize = useCallback(() => {
    if (state.dockMode !== 'floating') {
      layout.minimize()
      return
    }

    startFloatingMorph('minimize', layout.minimize, true)
  }, [layout, startFloatingMorph, state.dockMode])

  const startFloatingRestore = useCallback(() => {
    if (state.dockMode !== 'floating') {
      layout.restore()
      return
    }

    startFloatingMorph('restore', layout.restore)
  }, [layout, startFloatingMorph, state.dockMode])

  const startFloatingRestoreWithComposerFocus = useCallback(() => {
    if (state.dockMode !== 'floating') {
      layout.restore()
      requestComposerFocus()
      return
    }

    startFloatingMorph('restore', layout.restore, false, requestComposerFocus)
  }, [layout, requestComposerFocus, startFloatingMorph, state.dockMode])

  useEffect(() => {
    return () => clearFloatingMorphTimer()
  }, [clearFloatingMorphTimer])

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (!event.ctrlKey || event.key !== 'Enter' || event.repeat || floatingMorph) return

      event.preventDefault()
      if (state.minimized) {
        if (state.dockMode === 'floating') {
          startFloatingRestoreWithComposerFocus()
        } else {
          layout.restore()
          requestComposerFocus()
        }
        return
      }

      if (state.dockMode === 'floating') {
        startFloatingMinimize()
      } else {
        layout.minimize()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [
    floatingMorph,
    layout,
    requestComposerFocus,
    startFloatingMinimize,
    startFloatingRestoreWithComposerFocus,
    state.dockMode,
    state.minimized
  ])

  const {
    startDockedDragOut,
    startDockResize,
    startFloatingDrag,
    startFloatingResize,
    startOrbDrag,
    handleOrbClick
  } = useAiWorkspacePointerInteractions(layout, startFloatingRestore, setDragCue)

  if (state.dockMode === 'docked' && state.minimized) {
    return <DockedMinimizedRail onRestore={layout.restore} />
  }

  if (state.dockMode === 'floating' && state.minimized) {
    return (
      <>
        {!floatingMorph ? (
          <FloatingOrb
            position={state.orbPosition}
            onPointerDown={startOrbDrag}
            onClick={handleOrbClick}
          />
        ) : null}
        {floatingMorph ? <FloatingMorphTransition key={floatingMorph.id} state={floatingMorph} /> : null}
      </>
    )
  }

  if (state.dockMode === 'floating') {
    return (
      <>
        <FloatingAiWorkspace
          rect={state.floatingRect}
          layout={layout}
          focusRequest={composerFocusRequest}
          morphing={Boolean(floatingMorph)}
          dragCue={dragCue}
          onMinimize={startFloatingMinimize}
          onHeaderPointerDown={startFloatingDrag}
          onResizeStart={startFloatingResize}
        />
        {floatingMorph ? <FloatingMorphTransition key={floatingMorph.id} state={floatingMorph} /> : null}
      </>
    )
  }

  return (
    <DockedAiWorkspace
      layout={layout}
      focusRequest={composerFocusRequest}
      dragCue={dragCue}
      onHeaderPointerDown={startDockedDragOut}
      onResizeStart={startDockResize}
    />
  )
}
