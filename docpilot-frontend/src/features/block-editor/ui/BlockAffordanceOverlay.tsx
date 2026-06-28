import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import { useI18n } from '@/shared/i18n'
import type { Editor } from '@tiptap/react'
import { GripVertical, List, Plus } from 'lucide-react'
import {
  useCallback,
  useEffect,
  useRef,
  type MouseEvent as ReactMouseEvent,
  type PointerEvent as ReactPointerEvent
} from 'react'
import { createPortal } from 'react-dom'
import {
  BLOCK_EDIT_MENU_ITEMS,
  FORMAT_STRIP_ITEMS,
  INSERT_MENU_ITEMS,
  TRANSFORM_MENU_ITEMS,
  labelForBlockInfo,
  type BlockMenuActionContext,
  type BlockMenuItem
} from './blockMenuItems'
import type { BlockAffordanceState } from './blockAffordanceTypes'

const HOVER_OPEN_DELAY_MS = 180
const HOVER_CLOSE_DELAY_MS = 220

export function BlockAffordanceOverlay({
  affordance,
  editor,
  onHandleHoverEnd,
  onHandleHoverStart,
  menuOpen,
  onMenuOpenChange,
  portalElement
}: {
  affordance: BlockAffordanceState | null
  editor: Editor
  onHandleHoverEnd: () => void
  onHandleHoverStart: () => void
  menuOpen: boolean
  onMenuOpenChange: (open: boolean) => void
  portalElement: HTMLElement | null
}) {
  const { t } = useI18n()
  const openTimerRef = useRef<number | null>(null)
  const closeTimerRef = useRef<number | null>(null)

  const clearHoverOpenTimer = useCallback(() => {
    if (openTimerRef.current === null) return

    window.clearTimeout(openTimerRef.current)
    openTimerRef.current = null
  }, [])

  const clearHoverCloseTimer = useCallback(() => {
    if (closeTimerRef.current === null) return

    window.clearTimeout(closeTimerRef.current)
    closeTimerRef.current = null
  }, [])

  const openMenuFromHover = useCallback(() => {
    clearHoverCloseTimer()
    if (menuOpen || openTimerRef.current !== null) return

    openTimerRef.current = window.setTimeout(() => {
      openTimerRef.current = null
      onMenuOpenChange(true)
    }, HOVER_OPEN_DELAY_MS)
  }, [clearHoverCloseTimer, menuOpen, onMenuOpenChange])

  const keepHoverStateFromHandle = useCallback(() => {
    clearHoverCloseTimer()
    onHandleHoverStart()
  }, [clearHoverCloseTimer, onHandleHoverStart])

  const keepMenuOpenFromGrip = useCallback(() => {
    clearHoverCloseTimer()
  }, [clearHoverCloseTimer])

  const scheduleHoverClose = useCallback((
    event?: ReactMouseEvent<HTMLElement> | ReactPointerEvent<HTMLElement>
  ) => {
    if (isAffordanceHoverTarget(event?.relatedTarget ?? null)) {
      if (!menuOpen) {
        clearHoverOpenTimer()
      }
      return
    }

    clearHoverOpenTimer()
    clearHoverCloseTimer()
    closeTimerRef.current = window.setTimeout(() => {
      closeTimerRef.current = null
      onMenuOpenChange(false)
    }, HOVER_CLOSE_DELAY_MS)
  }, [clearHoverCloseTimer, clearHoverOpenTimer, menuOpen, onMenuOpenChange])

  const handleMenuOpenChange = useCallback((open: boolean) => {
    clearHoverOpenTimer()
    clearHoverCloseTimer()
    onMenuOpenChange(open)
  }, [clearHoverCloseTimer, clearHoverOpenTimer, onMenuOpenChange])

  const closeMenuAfterAction = useCallback(() => {
    onHandleHoverEnd()
    handleMenuOpenChange(false)
  }, [handleMenuOpenChange, onHandleHoverEnd])

  const openMenuFromClick = useCallback((event: ReactMouseEvent<HTMLButtonElement>) => {
    event.stopPropagation()
    handleMenuOpenChange(true)
  }, [handleMenuOpenChange])

  useEffect(() => () => {
    clearHoverOpenTimer()
    clearHoverCloseTimer()
  }, [clearHoverCloseTimer, clearHoverOpenTimer])

  if (!affordance || !portalElement) return null

  const context: BlockMenuActionContext = {
    block: affordance.block,
    editor,
    t
  }
  const isInsertMenu = affordance.kind === 'insert'
  const BlockIcon = isInsertMenu ? Plus : List
  const blockLabel = labelForBlockInfo(affordance.block, t)
  const label = isInsertMenu
    ? t('blockMenu.insertBlockAria')
    : t('blockMenu.blockMenuAria', { block: blockLabel })

  const content = (
      <div
        className="block-affordance-layer"
        contentEditable={false}
        onMouseEnter={keepHoverStateFromHandle}
        onMouseLeave={onHandleHoverEnd}
        onPointerEnter={keepHoverStateFromHandle}
        onPointerLeave={onHandleHoverEnd}
      >
        <DropdownMenu open={menuOpen} onOpenChange={handleMenuOpenChange}>
          {isInsertMenu ? (
            <DropdownMenuTrigger asChild>
              <button
                className={cn(
                  'block-affordance-trigger',
                  'is-insert-trigger',
                  menuOpen ? 'is-open' : ''
                )}
                type="button"
                aria-label={label}
                title={label}
                draggable={false}
                onMouseEnter={openMenuFromHover}
                onMouseLeave={scheduleHoverClose}
                onPointerEnter={openMenuFromHover}
                onPointerLeave={scheduleHoverClose}
                onClick={openMenuFromClick}
                onDragStart={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                }}
                onPointerDown={(event) => {
                  if (event.button > 0) return
                  event.preventDefault()
                  event.stopPropagation()
                }}
              >
                <BlockIcon size={15} strokeWidth={2.2} />
              </button>
            </DropdownMenuTrigger>
          ) : (
            <div className={cn('block-affordance-context-control', menuOpen ? 'is-open' : '')}>
              <DropdownMenuTrigger asChild>
                <button
                  className={cn(
                    'block-affordance-trigger',
                    'is-context-trigger',
                    menuOpen ? 'is-open' : ''
                  )}
                  type="button"
                  aria-label={label}
                  title={label}
                  draggable={false}
                  onMouseEnter={openMenuFromHover}
                  onMouseLeave={scheduleHoverClose}
                  onPointerEnter={openMenuFromHover}
                  onPointerLeave={scheduleHoverClose}
                  onClick={openMenuFromClick}
                  onDragStart={(event) => {
                    event.preventDefault()
                    event.stopPropagation()
                  }}
                  onPointerDown={(event) => {
                    if (event.button > 0) return
                    event.preventDefault()
                    event.stopPropagation()
                  }}
                >
                  <BlockIcon size={15} strokeWidth={2.2} />
                </button>
              </DropdownMenuTrigger>
              <span
                className="block-affordance-grip"
                aria-hidden="true"
                onMouseEnter={keepMenuOpenFromGrip}
                onPointerEnter={keepMenuOpenFromGrip}
                onClick={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                }}
              >
                <GripVertical size={14} strokeWidth={2.1} />
              </span>
            </div>
          )}
          <DropdownMenuContent
            align="start"
            className="block-affordance-menu"
            side="left"
            sideOffset={8}
            onMouseEnter={openMenuFromHover}
            onMouseLeave={scheduleHoverClose}
            onPointerEnter={openMenuFromHover}
            onPointerLeave={scheduleHoverClose}
            onCloseAutoFocus={(event) => {
              event.preventDefault()
              if (!editor.isDestroyed) {
                editor.commands.focus(undefined, { scrollIntoView: false })
              }
            }}
          >
            {isInsertMenu ? (
              <InsertMenu context={context} onClose={closeMenuAfterAction} />
            ) : (
              <ContextMenu
                context={context}
                onClose={closeMenuAfterAction}
              />
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
  )

  return createPortal(content, portalElement)
}

function isAffordanceHoverTarget(target: EventTarget | null): boolean {
  return target instanceof Element
    && Boolean(target.closest('.block-affordance-layer, .block-affordance-menu'))
}

function InsertMenu({
  context,
  onClose
}: {
  context: BlockMenuActionContext
  onClose: () => void
}) {
  return (
    <>
      <DropdownMenuLabel>{context.t('blockMenu.insert')}</DropdownMenuLabel>
      <DropdownMenuGroup>
        {INSERT_MENU_ITEMS.map((item) => (
          <BlockDropdownItem
            key={item.id}
            context={context}
            item={item}
            onClose={onClose}
          />
        ))}
      </DropdownMenuGroup>
    </>
  )
}

function ContextMenu({
  context,
  onClose
}: {
  context: BlockMenuActionContext
  onClose: () => void
}) {
  return (
    <>
      <div className="block-affordance-menu-title">
        {labelForBlockInfo(context.block, context.t)}
      </div>
      <FormatStrip context={context} onClose={onClose} />
      <DropdownMenuSeparator />
      <DropdownMenuLabel>{context.t('blockMenu.transformTo')}</DropdownMenuLabel>
      <DropdownMenuGroup>
        {TRANSFORM_MENU_ITEMS.map((item) => (
          <BlockDropdownItem
            key={item.id}
            context={context}
            item={item}
            onClose={onClose}
          />
        ))}
      </DropdownMenuGroup>
      <DropdownMenuSeparator />
      <DropdownMenuGroup>
        {BLOCK_EDIT_MENU_ITEMS.map((item) => (
          <BlockDropdownItem
            key={item.id}
            context={context}
            item={item}
            danger={item.id === 'delete'}
            onClose={onClose}
          />
        ))}
      </DropdownMenuGroup>
    </>
  )
}

function FormatStrip({
  context,
  onClose
}: {
  context: BlockMenuActionContext
  onClose: () => void
}) {
  return (
    <div className="block-affordance-format-strip" role="group" aria-label={context.t('blockMenu.blockFormat')}>
      {FORMAT_STRIP_ITEMS.map((item) => {
        const Icon = item.icon
        const active = item.active?.(context) ?? false
        const label = context.t(item.labelKey)

        return (
          <button
            key={item.id}
            className={cn('block-affordance-format-button', active ? 'is-active' : '')}
            type="button"
            aria-label={label}
            aria-pressed={active}
            title={label}
            onClick={(event) => {
              event.preventDefault()
              event.stopPropagation()
              item.run(context)
              onClose()
            }}
            onPointerDown={(event) => {
              event.preventDefault()
              event.stopPropagation()
            }}
          >
            <Icon size={15} strokeWidth={2.2} />
          </button>
        )
      })}
    </div>
  )
}

function BlockDropdownItem({
  context,
  danger = false,
  item,
  onClose
}: {
  context: BlockMenuActionContext
  danger?: boolean
  item: BlockMenuItem
  onClose: () => void
}) {
  const Icon = item.icon
  const label = context.t(item.labelKey)

  return (
    <DropdownMenuItem
      className="block-affordance-menu-item"
      variant={danger ? 'destructive' : 'default'}
      onSelect={() => {
        item.run(context)
        onClose()
      }}
    >
      <Icon />
      <span>{label}</span>
    </DropdownMenuItem>
  )
}
