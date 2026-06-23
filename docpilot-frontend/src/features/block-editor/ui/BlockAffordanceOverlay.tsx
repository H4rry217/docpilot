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
import { useCallback, useEffect, useRef } from 'react'
import {
  BLOCK_EDIT_MENU_ITEMS,
  FORMAT_STRIP_ITEMS,
  INSERT_MENU_ITEMS,
  TRANSFORM_MENU_ITEMS,
  labelForBlockInfo,
  type BlockMenuActionContext,
  type BlockMenuItem
} from './blockMenuItems'
import type { BlockAffordanceState } from './useBlockAffordances'

const HOVER_CLOSE_DELAY_MS = 180

export function BlockAffordanceOverlay({
  affordance,
  editor,
  menuOpen,
  onMenuOpenChange
}: {
  affordance: BlockAffordanceState | null
  editor: Editor
  menuOpen: boolean
  onMenuOpenChange: (open: boolean) => void
}) {
  const { t } = useI18n()
  const closeTimerRef = useRef<number | null>(null)

  const clearHoverCloseTimer = useCallback(() => {
    if (closeTimerRef.current === null) return

    window.clearTimeout(closeTimerRef.current)
    closeTimerRef.current = null
  }, [])

  const openMenuFromHover = useCallback(() => {
    clearHoverCloseTimer()
    onMenuOpenChange(true)
  }, [clearHoverCloseTimer, onMenuOpenChange])

  const scheduleHoverClose = useCallback(() => {
    clearHoverCloseTimer()
    closeTimerRef.current = window.setTimeout(() => {
      closeTimerRef.current = null
      onMenuOpenChange(false)
    }, HOVER_CLOSE_DELAY_MS)
  }, [clearHoverCloseTimer, onMenuOpenChange])

  const handleMenuOpenChange = useCallback((open: boolean) => {
    clearHoverCloseTimer()
    onMenuOpenChange(open)
  }, [clearHoverCloseTimer, onMenuOpenChange])

  useEffect(() => clearHoverCloseTimer, [clearHoverCloseTimer])

  if (!affordance) return null

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

  return (
    <>
      <div
        className="block-affordance-layer"
        contentEditable={false}
        style={{
          left: `${affordance.left}px`,
          top: `${affordance.top}px`
        }}
      >
        <DropdownMenu open={menuOpen} onOpenChange={handleMenuOpenChange}>
          <DropdownMenuTrigger asChild>
            <button
              className={cn(
                'block-affordance-trigger',
                isInsertMenu ? 'is-insert-trigger' : 'is-context-trigger',
                menuOpen ? 'is-open' : ''
              )}
              type="button"
              aria-label={label}
              title={label}
              onMouseEnter={openMenuFromHover}
              onMouseLeave={scheduleHoverClose}
              onPointerEnter={openMenuFromHover}
              onPointerLeave={scheduleHoverClose}
              onClick={(event) => event.stopPropagation()}
              onPointerDown={(event) => {
                if (event.button > 0) return
                event.preventDefault()
                event.stopPropagation()
              }}
            >
              <BlockIcon size={15} strokeWidth={2.2} />
              {isInsertMenu ? null : (
                <span className="block-affordance-grip" aria-hidden="true">
                  <GripVertical size={14} strokeWidth={2.1} />
                </span>
              )}
            </button>
          </DropdownMenuTrigger>
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
              editor.commands.focus()
            }}
          >
            {isInsertMenu ? (
              <InsertMenu context={context} />
            ) : (
              <ContextMenu
                context={context}
                onClose={() => onMenuOpenChange(false)}
              />
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </>
  )
}

function InsertMenu({ context }: { context: BlockMenuActionContext }) {
  return (
    <>
      <DropdownMenuLabel>{context.t('blockMenu.insert')}</DropdownMenuLabel>
      <DropdownMenuGroup>
        {INSERT_MENU_ITEMS.map((item) => (
          <BlockDropdownItem
            key={item.id}
            context={context}
            item={item}
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
  item
}: {
  context: BlockMenuActionContext
  danger?: boolean
  item: BlockMenuItem
}) {
  const Icon = item.icon
  const active = item.active?.(context) ?? false
  const label = context.t(item.labelKey)

  return (
    <DropdownMenuItem
      className={cn('block-affordance-menu-item', active ? 'is-active' : '')}
      variant={danger ? 'destructive' : 'default'}
      onSelect={() => item.run(context)}
    >
      <Icon />
      <span>{label}</span>
    </DropdownMenuItem>
  )
}
