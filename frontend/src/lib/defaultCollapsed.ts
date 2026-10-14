import type { GraphData } from './types'

export const DEFAULT_COLLAPSE_DEPTH = 4

/** Thu gọn người ở đời thứ `depth` (tính từ gốc = 1) nếu họ có con. */
export function defaultCollapsed(g: GraphData, depth: number): Set<number> {
  const kids = new Map<number, number[]>()
  for (const l of g.links) {
    const list = kids.get(l.parentId)
    if (list) list.push(l.childId)
    else kids.set(l.parentId, [l.childId])
  }
  const level = new Map<number, number>([[g.rootPersonId, 1]])
  const queue = [g.rootPersonId]
  for (let i = 0; i < queue.length; i++) {
    const p = queue[i]
    for (const c of kids.get(p) ?? []) {
      if (!level.has(c)) { level.set(c, level.get(p)! + 1); queue.push(c) }
    }
  }
  const out = new Set<number>()
  for (const [id, lv] of level) if (lv === depth && kids.has(id)) out.add(id)
  return out
}
