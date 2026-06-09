import type { DOMOutputSpec } from '@tiptap/pm/model'

type MathNode =
  | { type: 'text'; text: string }
  | { type: 'symbol'; value: string; className?: string; limits?: boolean }
  | { type: 'script'; base: MathNode; sub?: MathNode[]; sup?: MathNode[] }
  | { type: 'fraction'; numerator: MathNode[]; denominator: MathNode[] }
  | { type: 'sqrt'; body: MathNode[]; index?: MathNode[] }
  | { type: 'styled'; className: string; children: MathNode[] }

type MathDomChild = string | DOMOutputSpec

const commandSymbols: Record<string, { value: string; className?: string; limits?: boolean }> = {
  alpha: { value: 'α' },
  beta: { value: 'β' },
  gamma: { value: 'γ' },
  delta: { value: 'δ' },
  epsilon: { value: 'ε' },
  varepsilon: { value: 'ε' },
  zeta: { value: 'ζ' },
  eta: { value: 'η' },
  theta: { value: 'θ' },
  vartheta: { value: 'ϑ' },
  iota: { value: 'ι' },
  kappa: { value: 'κ' },
  lambda: { value: 'λ' },
  mu: { value: 'μ' },
  nu: { value: 'ν' },
  xi: { value: 'ξ' },
  pi: { value: 'π' },
  varpi: { value: 'ϖ' },
  rho: { value: 'ρ' },
  varrho: { value: 'ϱ' },
  sigma: { value: 'σ' },
  varsigma: { value: 'ς' },
  tau: { value: 'τ' },
  upsilon: { value: 'υ' },
  phi: { value: 'φ' },
  varphi: { value: 'ϕ' },
  chi: { value: 'χ' },
  psi: { value: 'ψ' },
  omega: { value: 'ω' },
  Gamma: { value: 'Γ' },
  Delta: { value: 'Δ' },
  Theta: { value: 'Θ' },
  Lambda: { value: 'Λ' },
  Xi: { value: 'Ξ' },
  Pi: { value: 'Π' },
  Sigma: { value: 'Σ' },
  Upsilon: { value: 'Υ' },
  Phi: { value: 'Φ' },
  Psi: { value: 'Ψ' },
  Omega: { value: 'Ω' },
  int: { value: '∫', className: 'docpilot-math-operator', limits: true },
  oint: { value: '∮', className: 'docpilot-math-operator', limits: true },
  sum: { value: '∑', className: 'docpilot-math-operator', limits: true },
  prod: { value: '∏', className: 'docpilot-math-operator', limits: true },
  coprod: { value: '∐', className: 'docpilot-math-operator', limits: true },
  lim: { value: 'lim', className: 'docpilot-math-upright', limits: true },
  infty: { value: '∞', className: 'docpilot-math-symbol' },
  partial: { value: '∂', className: 'docpilot-math-symbol' },
  nabla: { value: '∇', className: 'docpilot-math-symbol' },
  pm: { value: '±', className: 'docpilot-math-symbol' },
  mp: { value: '∓', className: 'docpilot-math-symbol' },
  times: { value: '×', className: 'docpilot-math-symbol' },
  cdot: { value: '·', className: 'docpilot-math-symbol' },
  div: { value: '÷', className: 'docpilot-math-symbol' },
  le: { value: '≤', className: 'docpilot-math-symbol' },
  leq: { value: '≤', className: 'docpilot-math-symbol' },
  ge: { value: '≥', className: 'docpilot-math-symbol' },
  geq: { value: '≥', className: 'docpilot-math-symbol' },
  neq: { value: '≠', className: 'docpilot-math-symbol' },
  approx: { value: '≈', className: 'docpilot-math-symbol' },
  equiv: { value: '≡', className: 'docpilot-math-symbol' },
  propto: { value: '∝', className: 'docpilot-math-symbol' },
  forall: { value: '∀', className: 'docpilot-math-symbol' },
  exists: { value: '∃', className: 'docpilot-math-symbol' },
  neg: { value: '¬', className: 'docpilot-math-symbol' },
  land: { value: '∧', className: 'docpilot-math-symbol' },
  lor: { value: '∨', className: 'docpilot-math-symbol' },
  in: { value: '∈', className: 'docpilot-math-symbol' },
  notin: { value: '∉', className: 'docpilot-math-symbol' },
  subset: { value: '⊂', className: 'docpilot-math-symbol' },
  subseteq: { value: '⊆', className: 'docpilot-math-symbol' },
  superset: { value: '⊃', className: 'docpilot-math-symbol' },
  supseteq: { value: '⊇', className: 'docpilot-math-symbol' },
  emptyset: { value: '∅', className: 'docpilot-math-symbol' },
  cup: { value: '∪', className: 'docpilot-math-symbol' },
  cap: { value: '∩', className: 'docpilot-math-symbol' },
  angle: { value: '∠', className: 'docpilot-math-symbol' },
  degree: { value: '°', className: 'docpilot-math-symbol' },
  to: { value: '→', className: 'docpilot-math-symbol' },
  rightarrow: { value: '→', className: 'docpilot-math-symbol' },
  leftarrow: { value: '←', className: 'docpilot-math-symbol' },
  leftrightarrow: { value: '↔', className: 'docpilot-math-symbol' }
}

const uprightCommands = new Set([
  'arccos',
  'arcsin',
  'arctan',
  'arg',
  'cos',
  'cosh',
  'cot',
  'coth',
  'csc',
  'deg',
  'det',
  'dim',
  'exp',
  'gcd',
  'hom',
  'ker',
  'lg',
  'ln',
  'log',
  'max',
  'min',
  'sec',
  'sin',
  'sinh',
  'sup',
  'tan',
  'tanh'
])

function pushText(nodes: MathNode[], text: string) {
  if (!text) return
  const last = nodes[nodes.length - 1]
  if (last?.type === 'text') {
    last.text += text
    return
  }
  nodes.push({ type: 'text', text })
}

function skipWhitespace(source: string, index: number) {
  let next = index
  while (next < source.length && /\s/.test(source[next])) next += 1
  return next
}

function splitTextBase(nodes: MathNode[]): MathNode | null {
  const last = nodes.pop()
  if (!last) return null
  if (last.type !== 'text' || last.text.length <= 1) return last

  const rest = last.text.slice(0, -1)
  const base = last.text.slice(-1)
  pushText(nodes, rest)
  return { type: 'text', text: base }
}

function attachScript(base: MathNode, kind: 'sub' | 'sup', value: MathNode[]): MathNode {
  if (base.type === 'script') {
    return { ...base, [kind]: value }
  }
  return { type: 'script', base, [kind]: value }
}

function parseSequence(source: string, startIndex = 0, terminator = ''): { nodes: MathNode[]; index: number } {
  const nodes: MathNode[] = []
  let index = startIndex

  while (index < source.length) {
    const char = source[index]

    if (terminator && char === terminator) {
      return { nodes, index: index + 1 }
    }

    if (char === '\\') {
      const parsed = parseCommand(source, index)
      nodes.push(...parsed.nodes)
      index = parsed.index
      continue
    }

    if (char === '{') {
      const parsed = parseSequence(source, index + 1, '}')
      nodes.push({ type: 'styled', className: 'docpilot-math-group', children: parsed.nodes })
      index = parsed.index
      continue
    }

    if (char === '}') {
      if (terminator) return { nodes, index: index + 1 }
      pushText(nodes, char)
      index += 1
      continue
    }

    if (char === '^' || char === '_') {
      const base = splitTextBase(nodes)
      const argument = parseArgument(source, index + 1)
      if (base) {
        nodes.push(attachScript(base, char === '^' ? 'sup' : 'sub', argument.nodes))
      } else {
        pushText(nodes, char)
        nodes.push(...argument.nodes)
      }
      index = argument.index
      continue
    }

    pushText(nodes, char)
    index += 1
  }

  return { nodes, index }
}

function parseArgument(source: string, startIndex: number): { nodes: MathNode[]; index: number } {
  const index = skipWhitespace(source, startIndex)
  if (index >= source.length) return { nodes: [], index }

  if (source[index] === '{') {
    return parseSequence(source, index + 1, '}')
  }

  if (source[index] === '\\') {
    return parseCommand(source, index)
  }

  return { nodes: [{ type: 'text', text: source[index] }], index: index + 1 }
}

function parseOptionalRootIndex(source: string, startIndex: number): { nodes?: MathNode[]; index: number } {
  const index = skipWhitespace(source, startIndex)
  if (source[index] !== '[') return { index: startIndex }

  let cursor = index + 1
  let depth = 1
  while (cursor < source.length && depth > 0) {
    if (source[cursor] === '[') depth += 1
    if (source[cursor] === ']') depth -= 1
    cursor += 1
  }

  const inner = source.slice(index + 1, Math.max(index + 1, cursor - 1))
  return { nodes: parseSequence(inner).nodes, index: cursor }
}

function parseCommand(source: string, startIndex: number): { nodes: MathNode[]; index: number } {
  let index = startIndex + 1
  if (index >= source.length) return { nodes: [{ type: 'text', text: '\\' }], index }

  const first = source[index]
  if (!/[A-Za-z]/.test(first)) {
    if (first === ',' || first === ':' || first === ';' || first === ' ') {
      return { nodes: [{ type: 'text', text: ' ' }], index: index + 1 }
    }
    if (first === '!') {
      return { nodes: [], index: index + 1 }
    }
    return { nodes: [{ type: 'text', text: first }], index: index + 1 }
  }

  while (index < source.length && /[A-Za-z]/.test(source[index])) index += 1
  const command = source.slice(startIndex + 1, index)

  if (command === 'frac' || command === 'dfrac' || command === 'tfrac') {
    const numerator = parseArgument(source, index)
    const denominator = parseArgument(source, numerator.index)
    return {
      nodes: [{ type: 'fraction', numerator: numerator.nodes, denominator: denominator.nodes }],
      index: denominator.index
    }
  }

  if (command === 'sqrt') {
    const rootIndex = parseOptionalRootIndex(source, index)
    const body = parseArgument(source, rootIndex.index)
    return { nodes: [{ type: 'sqrt', body: body.nodes, index: rootIndex.nodes }], index: body.index }
  }

  if (command === 'text' || command === 'mathrm' || command === 'operatorname') {
    const argument = parseArgument(source, index)
    return {
      nodes: [{ type: 'styled', className: 'docpilot-math-upright', children: argument.nodes }],
      index: argument.index
    }
  }

  if (command === 'mathbf') {
    const argument = parseArgument(source, index)
    return {
      nodes: [{ type: 'styled', className: 'docpilot-math-bold', children: argument.nodes }],
      index: argument.index
    }
  }

  if (command === 'mathit') {
    const argument = parseArgument(source, index)
    return {
      nodes: [{ type: 'styled', className: 'docpilot-math-italic', children: argument.nodes }],
      index: argument.index
    }
  }

  if (command === 'left' || command === 'right' || command === 'big' || command === 'Big' || command === 'bigg' || command === 'Bigg') {
    const delimiterIndex = skipWhitespace(source, index)
    if (delimiterIndex < source.length && source[delimiterIndex] !== '\\') {
      return { nodes: [{ type: 'text', text: source[delimiterIndex] }], index: delimiterIndex + 1 }
    }
    return { nodes: [], index }
  }

  if (command === 'limits' || command === 'nolimits') {
    return { nodes: [], index }
  }

  const symbol = commandSymbols[command]
  if (symbol) {
    return {
      nodes: [{
        type: 'symbol',
        value: symbol.value,
        className: symbol.className,
        limits: symbol.limits
      }],
      index
    }
  }

  if (uprightCommands.has(command)) {
    return {
      nodes: [{ type: 'symbol', value: command, className: 'docpilot-math-upright' }],
      index
    }
  }

  return { nodes: [{ type: 'text', text: command }], index }
}

export function normalizeLatexSource(source: string): string {
  return source.replace(/\\\\(?=[A-Za-z])/g, '\\')
}

function renderNodes(nodes: MathNode[]): MathDomChild[] {
  return nodes.map(renderNode)
}

function renderNode(node: MathNode): MathDomChild {
  if (node.type === 'text') return node.text

  if (node.type === 'symbol') {
    return ['span', { class: node.className ?? 'docpilot-math-symbol' }, node.value]
  }

  if (node.type === 'script') {
    const base = renderNode(node.base)
    if (node.base.type === 'symbol' && node.base.limits) {
      return [
        'span',
        { class: 'docpilot-math-limits' },
        node.sup ? ['span', { class: 'docpilot-math-limit-top' }, ...renderNodes(node.sup)] : ['span', { class: 'docpilot-math-limit-spacer' }],
        ['span', { class: node.base.className ?? 'docpilot-math-operator' }, node.base.value],
        node.sub ? ['span', { class: 'docpilot-math-limit-bottom' }, ...renderNodes(node.sub)] : ['span', { class: 'docpilot-math-limit-spacer' }]
      ]
    }

    const children: MathDomChild[] = [base]
    if (node.sub) children.push(['sub', {}, ...renderNodes(node.sub)])
    if (node.sup) children.push(['sup', {}, ...renderNodes(node.sup)])
    return ['span', { class: 'docpilot-math-script' }, ...children]
  }

  if (node.type === 'fraction') {
    return [
      'span',
      { class: 'docpilot-math-fraction' },
      ['span', { class: 'docpilot-math-fraction-numerator' }, ...renderNodes(node.numerator)],
      ['span', { class: 'docpilot-math-fraction-denominator' }, ...renderNodes(node.denominator)]
    ]
  }

  if (node.type === 'sqrt') {
    return [
      'span',
      { class: 'docpilot-math-root' },
      node.index ? ['span', { class: 'docpilot-math-root-index' }, ...renderNodes(node.index)] : '',
      ['span', { class: 'docpilot-math-root-symbol' }, '√'],
      ['span', { class: 'docpilot-math-root-body' }, ...renderNodes(node.body)]
    ]
  }

  return ['span', { class: node.className }, ...renderNodes(node.children)]
}

export function renderLatexMath(source: string): MathDomChild[] {
  const trimmed = normalizeLatexSource(source).trim()
  if (!trimmed) return []
  return renderNodes(parseSequence(trimmed).nodes)
}
