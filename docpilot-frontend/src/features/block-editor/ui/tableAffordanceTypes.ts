export type Point = {
  x: number
  y: number
}

export type Rect = {
  left: number
  top: number
  right: number
  bottom: number
}

export type TableDividerAxis = 'column' | 'row'

export type TableDividerGeometry = {
  axis: TableDividerAxis
  cell: HTMLTableCellElement
  handleLeft: number
  handleTop: number
  insert: 'after' | 'before'
  lineLeft: number
  lineTop: number
  lineHeight?: number
  lineWidth?: number
}

export type TableHoverIndicatorSegment = {
  afterDivider?: TableDividerGeometry
  beforeDivider?: TableDividerGeometry
  cell: HTMLTableCellElement
  index: number
  selected?: boolean
  offset: number
  size: number
  span: number
}

export type TableHoverIndicatorGeometry = {
  columns: TableHoverIndicatorSegment[]
  rows: TableHoverIndicatorSegment[]
  tableHeight: number
  tableLeft: number
  tableTop: number
  tableWidth: number
}

export type TableIndicatorSelectionRange = {
  columns?: {
    from: number
    to: number
  }
  rows?: {
    from: number
    to: number
  }
}

export type TableSelectionToolbarGeometry = {
  left: number
  top: number
}
