import { ChevronDown, ChevronRight, ChevronsLeft, Menu } from 'lucide-react'
import { Fragment, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { DocumentOutlineItem } from '@/entities/block/outline'
import { useI18n } from '@/shared/i18n'
import { useTransientScrollbarVisibility } from './useTransientScrollbarVisibility'

type OutlineTreeItem = DocumentOutlineItem & {
  children: OutlineTreeItem[]
}

type DocumentOutlineNavProps = {
  title: string
  outline: DocumentOutlineItem[]
  activeOutlineId?: string
  collapsed: boolean
  onToggleCollapsed: () => void
  onSelectOutlineItem: (item: DocumentOutlineItem) => void
}

function buildOutlineTree(outline: DocumentOutlineItem[]): OutlineTreeItem[] {
  const roots: OutlineTreeItem[] = []
  const stack: OutlineTreeItem[] = []

  outline.forEach((item) => {
    const treeItem: OutlineTreeItem = { ...item, children: [] }

    while (stack.length && stack[stack.length - 1].level >= treeItem.level) {
      stack.pop()
    }

    const parent = stack[stack.length - 1]
    if (parent) {
      parent.children.push(treeItem)
    } else {
      roots.push(treeItem)
    }

    stack.push(treeItem)
  })

  return roots
}

function outlineItemFromTreeItem(item: OutlineTreeItem): DocumentOutlineItem {
  return {
    id: item.id,
    level: item.level,
    title: item.title,
    headingIndex: item.headingIndex
  }
}

export function DocumentOutlineNav({
  title,
  outline,
  activeOutlineId,
  collapsed,
  onToggleCollapsed,
  onSelectOutlineItem
}: DocumentOutlineNavProps) {
  const { t } = useI18n()
  const [collapsedOutlineIds, setCollapsedOutlineIds] = useState<Set<string>>(() => new Set())
  const outlineTree = useMemo(() => buildOutlineTree(outline), [outline])
  const { isScrollbarVisible, revealScrollbar } = useTransientScrollbarVisibility({
    disabled: collapsed || !outlineTree.length
  })

  useEffect(() => {
    const outlineIds = new Set(outline.map((item) => item.id))
    setCollapsedOutlineIds((current) => {
      const next = new Set([...current].filter((id) => outlineIds.has(id)))
      return next.size === current.size ? current : next
    })
  }, [outline])

  function toggleOutlineItem(id: string) {
    setCollapsedOutlineIds((current) => {
      const next = new Set(current)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  function renderOutlineItems(items: OutlineTreeItem[], depth = 0): ReactNode {
    return items.map((item) => {
      const itemTitle = item.title || t('sidebar.outlineUntitled')
      const hasChildren = item.children.length > 0
      const itemCollapsed = collapsedOutlineIds.has(item.id)
      const active = activeOutlineId === item.id

      return (
        <Fragment key={`${item.id}-${item.headingIndex}`}>
          <div
            className={`document-outline-row document-outline-level-${item.level} ${active ? 'active' : ''}`}
            style={{ paddingLeft: `${depth * 12}px` }}
            title={itemTitle}
          >
            {hasChildren ? (
              <button
                type="button"
                className="document-outline-toggle"
                aria-label={itemCollapsed ? t('sidebar.outlineExpand', { title: itemTitle }) : t('sidebar.outlineCollapse', { title: itemTitle })}
                aria-expanded={!itemCollapsed}
                onClick={() => toggleOutlineItem(item.id)}
              >
                {itemCollapsed ? <ChevronRight size={13} /> : <ChevronDown size={13} />}
              </button>
            ) : (
              <span className="document-outline-toggle-spacer" />
            )}
            <button
              type="button"
              className="document-outline-title"
              onClick={() => onSelectOutlineItem(outlineItemFromTreeItem(item))}
            >
              {itemTitle}
            </button>
          </div>
          {hasChildren && !itemCollapsed ? renderOutlineItems(item.children, depth + 1) : null}
        </Fragment>
      )
    })
  }

  return (
    <aside className={`document-outline-nav ${collapsed ? 'collapsed' : ''}`} aria-label={t('sidebar.outlineTitle')}>
      <button
        type="button"
        className="document-outline-collapse"
        aria-label={collapsed ? t('sidebar.outlineExpand', { title: t('sidebar.outlineTitle') }) : t('sidebar.outlineCollapse', { title: t('sidebar.outlineTitle') })}
        title={collapsed ? t('sidebar.outlineExpand', { title: t('sidebar.outlineTitle') }) : t('sidebar.outlineCollapse', { title: t('sidebar.outlineTitle') })}
        onClick={onToggleCollapsed}
      >
        {collapsed ? <Menu size={18} /> : <ChevronsLeft size={18} />}
      </button>

      <div className="document-outline-body" aria-hidden={collapsed}>
        <strong className="document-outline-document-title" title={title}>{title}</strong>
        {outlineTree.length ? (
          <div
            className={`document-outline-list editor-transient-scrollbar ${isScrollbarVisible ? 'is-scrollbar-visible' : ''}`}
            onPointerMove={revealScrollbar}
            onScroll={revealScrollbar}
            onWheel={revealScrollbar}
          >
            {renderOutlineItems(outlineTree)}
          </div>
        ) : (
          <p className="document-outline-note">{t('sidebar.outlineEmpty')}</p>
        )}
      </div>
    </aside>
  )
}
