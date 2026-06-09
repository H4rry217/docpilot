type Direction = 'TD' | 'TB' | 'BT' | 'LR' | 'RL'
type NodeShape = 'rect' | 'round' | 'diamond' | 'circle'

type MermaidNode = {
  id: string
  label: string
  shape: NodeShape
  order: number
}

type MermaidEdge = {
  from: string
  to: string
  arrow: boolean
}

type PositionedNode = MermaidNode & {
  x: number
  y: number
  width: number
  height: number
}

type MermaidGraph = {
  direction: Direction
  nodes: MermaidNode[]
  edges: MermaidEdge[]
}

type MermaidPreviewProps = {
  blockId: string
  source: string
}

const DIRECTION_PATTERN = /^\s*(?:graph|flowchart)\s+(TD|TB|BT|LR|RL)\s*$/i
const EDGE_PATTERN = /^\s*(.*?)\s*(-->|---)\s*(.*?)\s*$/
const ID_PATTERN = /^[A-Za-z][\w-]*$/

function addNode(nodes: Map<string, MermaidNode>, id: string, label = id, shape: NodeShape = 'rect'): MermaidNode {
  const existing = nodes.get(id)
  if (existing) {
    if (label !== id || existing.label === existing.id) {
      existing.label = label
      existing.shape = shape
    }
    return existing
  }

  const node = { id, label, shape, order: nodes.size }
  nodes.set(id, node)
  return node
}

function cleanLabel(value: string): string {
  return value.trim().replace(/^["']|["']$/g, '')
}

function parseEndpoint(rawToken: string, nodes: Map<string, MermaidNode>): MermaidNode | null {
  const token = rawToken.trim()
  const circle = /^([A-Za-z][\w-]*)\(\((.*?)\)\)$/.exec(token)
  if (circle) return addNode(nodes, circle[1], cleanLabel(circle[2]), 'circle')

  const diamond = /^([A-Za-z][\w-]*)\{(.*?)}$/.exec(token)
  if (diamond) return addNode(nodes, diamond[1], cleanLabel(diamond[2]), 'diamond')

  const rect = /^([A-Za-z][\w-]*)\[(.*?)]$/.exec(token)
  if (rect) return addNode(nodes, rect[1], cleanLabel(rect[2]), 'rect')

  const round = /^([A-Za-z][\w-]*)\((.*?)\)$/.exec(token)
  if (round) return addNode(nodes, round[1], cleanLabel(round[2]), 'round')

  if (ID_PATTERN.test(token)) return addNode(nodes, token)
  return null
}

function parseMermaidGraph(source: string): MermaidGraph | null {
  const nodes = new Map<string, MermaidNode>()
  const edges: MermaidEdge[] = []
  let direction: Direction = 'TD'

  const statements = source
    .split(/\r?\n|;/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('%%'))

  for (const statement of statements) {
    const directionMatch = DIRECTION_PATTERN.exec(statement)
    if (directionMatch) {
      direction = directionMatch[1].toUpperCase() as Direction
      continue
    }

    const edgeMatch = EDGE_PATTERN.exec(statement)
    if (edgeMatch) {
      const from = parseEndpoint(edgeMatch[1], nodes)
      const to = parseEndpoint(edgeMatch[3], nodes)
      if (from && to) {
        edges.push({ from: from.id, to: to.id, arrow: edgeMatch[2] === '-->' })
      }
      continue
    }

    parseEndpoint(statement, nodes)
  }

  if (!nodes.size) return null
  return { direction, nodes: [...nodes.values()], edges }
}

function nodeWidth(node: MermaidNode): number {
  return Math.max(node.shape === 'diamond' ? 96 : 88, Math.min(190, node.label.length * 8 + 34))
}

function nodeHeight(node: MermaidNode): number {
  return node.shape === 'diamond' ? 68 : 46
}

function rankedNodes(graph: MermaidGraph): Map<string, number> {
  const ranks = new Map<string, number>()
  for (const node of graph.nodes) {
    ranks.set(node.id, 0)
  }

  for (let pass = 0; pass < graph.nodes.length; pass += 1) {
    let changed = false
    for (const edge of graph.edges) {
      const nextRank = (ranks.get(edge.from) ?? 0) + 1
      if (nextRank > (ranks.get(edge.to) ?? 0)) {
        ranks.set(edge.to, nextRank)
        changed = true
      }
    }
    if (!changed) break
  }

  return ranks
}

function layoutGraph(graph: MermaidGraph): { nodes: PositionedNode[]; width: number; height: number } {
  const vertical = graph.direction === 'TD' || graph.direction === 'TB' || graph.direction === 'BT'
  const reverse = graph.direction === 'BT' || graph.direction === 'RL'
  const ranks = rankedNodes(graph)
  const maxRank = Math.max(0, ...ranks.values())
  const groups = new Map<number, MermaidNode[]>()

  for (const node of graph.nodes) {
    const rank = reverse ? maxRank - (ranks.get(node.id) ?? 0) : ranks.get(node.id) ?? 0
    groups.set(rank, [...(groups.get(rank) ?? []), node])
  }

  for (const group of groups.values()) {
    group.sort((a, b) => a.order - b.order)
  }

  const margin = 36
  const rankGap = vertical ? 118 : 172
  const itemGap = 26
  const positioned: PositionedNode[] = []
  let width = 360
  let height = 220

  if (vertical) {
    const rankWidths = [...groups.values()].map((group) =>
      group.reduce((sum, node, index) => sum + nodeWidth(node) + (index > 0 ? itemGap : 0), 0)
    )
    width = Math.max(width, Math.max(...rankWidths, 0) + margin * 2)
    height = Math.max(height, margin * 2 + (maxRank + 1) * 68 + maxRank * (rankGap - 68))

    for (const [rank, group] of groups) {
      const groupWidth = group.reduce((sum, node, index) => sum + nodeWidth(node) + (index > 0 ? itemGap : 0), 0)
      let x = (width - groupWidth) / 2
      const y = margin + rank * rankGap
      for (const node of group) {
        const nodeW = nodeWidth(node)
        const nodeH = nodeHeight(node)
        positioned.push({ ...node, x, y, width: nodeW, height: nodeH })
        x += nodeW + itemGap
      }
    }
  } else {
    const rankHeights = [...groups.values()].map((group) =>
      group.reduce((sum, node, index) => sum + nodeHeight(node) + (index > 0 ? itemGap : 0), 0)
    )
    width = Math.max(width, margin * 2 + (maxRank + 1) * 112 + maxRank * (rankGap - 112))
    height = Math.max(height, Math.max(...rankHeights, 0) + margin * 2)

    for (const [rank, group] of groups) {
      const groupHeight = group.reduce((sum, node, index) => sum + nodeHeight(node) + (index > 0 ? itemGap : 0), 0)
      const x = margin + rank * rankGap
      let y = (height - groupHeight) / 2
      for (const node of group) {
        const nodeW = nodeWidth(node)
        const nodeH = nodeHeight(node)
        positioned.push({ ...node, x, y, width: nodeW, height: nodeH })
        y += nodeH + itemGap
      }
    }
  }

  return { nodes: positioned, width, height }
}

function center(node: PositionedNode): { x: number; y: number } {
  return { x: node.x + node.width / 2, y: node.y + node.height / 2 }
}

function edgePoints(from: PositionedNode, to: PositionedNode) {
  const fromCenter = center(from)
  const toCenter = center(to)
  const dx = toCenter.x - fromCenter.x
  const dy = toCenter.y - fromCenter.y

  if (Math.abs(dx) > Math.abs(dy)) {
    return {
      x1: fromCenter.x + Math.sign(dx || 1) * from.width / 2,
      y1: fromCenter.y,
      x2: toCenter.x - Math.sign(dx || 1) * to.width / 2,
      y2: toCenter.y
    }
  }

  return {
    x1: fromCenter.x,
    y1: fromCenter.y + Math.sign(dy || 1) * from.height / 2,
    x2: toCenter.x,
    y2: toCenter.y - Math.sign(dy || 1) * to.height / 2
  }
}

function safeSvgId(value: string): string {
  return value.replace(/[^A-Za-z0-9_-]/g, '-') || 'diagram'
}

export function MermaidPreview({ blockId, source }: MermaidPreviewProps) {
  const graph = parseMermaidGraph(source)

  if (!graph) {
    return <pre className="code-block-mermaid-source">{source}</pre>
  }

  const layout = layoutGraph(graph)
  const nodesById = new Map(layout.nodes.map((node) => [node.id, node]))
  const markerId = `code-block-mermaid-arrow-${safeSvgId(blockId)}`

  return (
    <div className="code-block-mermaid-preview" aria-label={source} role="img">
      <svg className="code-block-mermaid-svg" viewBox={`0 0 ${layout.width} ${layout.height}`}>
        <defs>
          <marker id={markerId} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" className="code-block-mermaid-arrow" />
          </marker>
        </defs>
        <g className="code-block-mermaid-edges">
          {graph.edges.map((edge, index) => {
            const from = nodesById.get(edge.from)
            const to = nodesById.get(edge.to)
            if (!from || !to) return null
            const points = edgePoints(from, to)
            return (
              <line
                key={`${edge.from}-${edge.to}-${index}`}
                className="code-block-mermaid-edge"
                x1={points.x1}
                y1={points.y1}
                x2={points.x2}
                y2={points.y2}
                markerEnd={edge.arrow ? `url(#${markerId})` : undefined}
              />
            )
          })}
        </g>
        <g className="code-block-mermaid-nodes">
          {layout.nodes.map((node) => {
            const text = (
              <text
                className="code-block-mermaid-node-text"
                x={node.x + node.width / 2}
                y={node.y + node.height / 2}
                textAnchor="middle"
                dominantBaseline="middle"
              >
                {node.label}
              </text>
            )

            if (node.shape === 'diamond') {
              const points = [
                `${node.x + node.width / 2},${node.y}`,
                `${node.x + node.width},${node.y + node.height / 2}`,
                `${node.x + node.width / 2},${node.y + node.height}`,
                `${node.x},${node.y + node.height / 2}`
              ].join(' ')
              return (
                <g key={node.id} className="code-block-mermaid-node code-block-mermaid-node-diamond">
                  <polygon points={points} />
                  {text}
                </g>
              )
            }

            if (node.shape === 'circle') {
              return (
                <g key={node.id} className="code-block-mermaid-node code-block-mermaid-node-circle">
                  <ellipse cx={node.x + node.width / 2} cy={node.y + node.height / 2} rx={node.width / 2} ry={node.height / 2} />
                  {text}
                </g>
              )
            }

            return (
              <g key={node.id} className={`code-block-mermaid-node code-block-mermaid-node-${node.shape}`}>
                <rect x={node.x} y={node.y} width={node.width} height={node.height} rx={node.shape === 'round' ? 18 : 7} />
                {text}
              </g>
            )
          })}
        </g>
      </svg>
    </div>
  )
}
