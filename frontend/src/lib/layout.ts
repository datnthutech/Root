import type { GraphData, GraphUnion } from './types'

export const NODE_W = 120
export const NODE_H = 60
export const GAP_X = 24
export const UNIT_GAP = 40
export const ROW_H = 140

export interface LayoutNode { id: number; x: number; y: number; w: number; h: number; generation: number; lead: boolean; hasKids: boolean; collapsed: boolean }
export interface LayoutEdge { kind: 'union' | 'child'; x1: number; y1: number; x2: number; y2: number }
export interface LayoutResult { nodes: LayoutNode[]; edges: LayoutEdge[]; width: number; height: number }
export interface LayoutOptions { collapsed?: Set<number> }

interface Unit { members: number[]; children: Unit[]; ownWidth: number; width: number; hasKids: boolean; isCollapsed: boolean }

const BIG = 1e9

export function computeLayout(g: GraphData, opts: LayoutOptions = {}): LayoutResult {
  const collapsed = opts.collapsed ?? new Set<number>()
  const person = new Map(g.persons.map((p) => [p.id, p]))
  if (!person.has(g.rootPersonId)) return { nodes: [], edges: [], width: 0, height: 0 }

  const unionsOf = new Map<number, GraphUnion[]>()
  for (const u of g.unions) {
    for (const pid of [u.partnerAId, u.partnerBId]) {
      if (!person.has(pid)) continue
      const list = unionsOf.get(pid)
      if (list) list.push(u)
      else unionsOf.set(pid, [u])
    }
  }
  unionsOf.forEach((list) => list.sort((a, b) => a.order - b.order || a.id - b.id))

  const childrenOf = new Map<number, number[]>()
  for (const l of g.links) {
    if (!person.has(l.parentId) || !person.has(l.childId)) continue
    const list = childrenOf.get(l.parentId)
    if (list) list.push(l.childId)
    else childrenOf.set(l.parentId, [l.childId])
  }

  const visited = new Set<number>()

  const cmp = (a: number, b: number) => {
    const pa = person.get(a)!, pb = person.get(b)!
    return (pa.birthOrder ?? BIG) - (pb.birthOrder ?? BIG) || (pa.birthYear ?? BIG) - (pb.birthYear ?? BIG) || a - b
  }

  function build(leadId: number): Unit {
    visited.add(leadId)
    const members = [leadId]
    for (const u of unionsOf.get(leadId) ?? []) {
      const other = u.partnerAId === leadId ? u.partnerBId : u.partnerAId
      if (!visited.has(other)) { visited.add(other); members.push(other) }
    }
    const ownWidth = members.length * NODE_W + (members.length - 1) * GAP_X

    const kids: number[] = []
    const seen = new Set<number>()
    for (const m of members) {
      for (const c of childrenOf.get(m) ?? []) {
        if (!visited.has(c) && !seen.has(c)) { seen.add(c); kids.push(c) }
      }
    }
    kids.sort(cmp)
    const hasKids = kids.length > 0
    const isCollapsed = hasKids && collapsed.has(leadId)
    const children: Unit[] = []
    if (!isCollapsed) for (const c of kids) if (!visited.has(c)) children.push(build(c))

    const childrenWidth = children.length
      ? children.reduce((s, c) => s + c.width, 0) + (children.length - 1) * UNIT_GAP
      : 0
    return { members, children, ownWidth, width: Math.max(ownWidth, childrenWidth), hasKids, isCollapsed }
  }

  const nodes: LayoutNode[] = []
  const edges: LayoutEdge[] = []
  let maxDepth = 0

  /** Trả về toạ độ x tâm của người dẫn đầu đơn vị. */
  function place(u: Unit, left: number, depth: number): number {
    if (depth > maxDepth) maxDepth = depth
    const y = depth * ROW_H
    const ownLeft = left + (u.width - u.ownWidth) / 2
    u.members.forEach((id, i) => {
      const x = ownLeft + i * (NODE_W + GAP_X)
      nodes.push({ id, x, y, w: NODE_W, h: NODE_H, generation: person.get(id)!.generation,
        lead: i === 0, hasKids: i === 0 && u.hasKids, collapsed: i === 0 && u.isCollapsed })
      if (i > 0) edges.push({ kind: 'union', x1: x - GAP_X, y1: y + NODE_H / 2, x2: x, y2: y + NODE_H / 2 })
    })
    const anchorX = ownLeft + u.ownWidth / 2
    if (u.children.length) {
      const childrenWidth = u.children.reduce((s, c) => s + c.width, 0) + (u.children.length - 1) * UNIT_GAP
      let cx = left + (u.width - childrenWidth) / 2
      for (const c of u.children) {
        const leadCenter = place(c, cx, depth + 1)
        edges.push({ kind: 'child', x1: anchorX, y1: y + NODE_H, x2: leadCenter, y2: (depth + 1) * ROW_H })
        cx += c.width + UNIT_GAP
      }
    }
    return ownLeft + NODE_W / 2
  }

  const root = build(g.rootPersonId)
  place(root, 0, 0)
  return { nodes, edges, width: root.width, height: maxDepth * ROW_H + NODE_H }
}
