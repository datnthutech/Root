# E1 Bản thử vẽ (cổng M1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Chứng minh vẽ 10.000 người dạng cây dọc có vợ/chồng kéo-phóng ≥ 55 fps, bố cục < 1 giây, hover trúng nút — chỉ dùng dữ liệu giả, chưa cần BE.

**Architecture:** Thuật toán bố cục thuần (`lib/layout.ts`, test được) chạy trong Web Worker; PixiJS vẽ với culling bằng quadtree, 3 mức chi tiết theo độ phóng; nút ở mức "chấm" vẽ một lần vào một `Graphics` tĩnh. Các module này được E5 dùng lại nguyên.

**Tech Stack:** TypeScript, PixiJS 7 + pixi-viewport 5, Vitest, idb-keyval (+ fake-indexeddb cho test).

**Spec:** `docs/superpowers/specs/2026-10-14-gia-pha-design.md` (mục 4 và 6)

## Global Constraints

- Mọi lệnh chạy ở `src/Root/frontend`.
- Hằng số bố cục (export từ `lib/layout.ts`): `NODE_W=120`, `NODE_H=60`, `GAP_X=24`, `UNIT_GAP=40`, `ROW_H=140`.
- Mức chi tiết theo `viewport.scale.x`: `< 0.3` → `'dot'`; `< 0.8` → `'box'`; còn lại → `'full'`.
- Mốc nghiệm thu M1: 10.000 nút, kéo phóng ≥ 55 fps trên laptop phổ thông; tính bố cục < 1 giây; hover trúng đúng nút.
- Kiểu dữ liệu graph khớp spec mục 4 (không đổi tên trường).

## Review Focus

- Người vừa là con trong họ vừa là vợ/chồng (hai dòng gặp nhau) chỉ được vẽ **một lần**, không lặp vô hạn.
- Cây chỉ có một người (không vợ, không con) và dữ liệu rỗng không làm bố cục lỗi.
- Một người có 3 vợ: cả 4 người nằm cùng hàng, đúng thứ tự `union.order` dù mảng `unions` đầu vào xáo trộn.
- Người không rõ năm sinh được xếp cuối trong anh em, không làm thuật toán sắp xếp lỗi (`null`).
- Hover/click ở vùng trống giữa các nút trả về `null`, không trả nút gần nhất.

---

### Task 1: Kiểu dữ liệu và bộ sinh dữ liệu giả

**Files:**
- Create: `src/lib/types.ts`, `src/lib/fakeData.ts`
- Test: `src/lib/fakeData.test.ts`

**Interfaces:**
- Produces: các kiểu `Gender`, `GraphPerson`, `GraphUnion`, `GraphLink`, `GraphData`; hàm `generateFakeGraph(total: number, seed?: number): GraphData`.

- [ ] **Step 1: Tạo `src/lib/types.ts`**

```ts
export type Gender = 'M' | 'F' | 'U'

export interface GraphPerson {
  id: number
  name: string
  gender: Gender
  birthYear: number | null
  deathYear: number | null
  alive: boolean
  thumb: string | null
  birthOrder: number | null
  orderLabel: string | null
  generation: number
  membership: 'BLOOD' | 'IN_LAW'
}

export interface GraphUnion {
  id: number
  partnerAId: number
  partnerBId: number
  order: number
  status: 'MARRIED' | 'DIVORCED' | 'WIDOWED'
}

export interface GraphLink {
  parentId: number
  childId: number
  unionId: number | null
  kind: 'BIOLOGICAL' | 'ADOPTED' | 'STEP'
}

export interface GraphData {
  clanId: number
  rootPersonId: number
  siblingCountMode: 'PER_UNION' | 'PER_FATHER'
  persons: GraphPerson[]
  unions: GraphUnion[]
  links: GraphLink[]
}
```

- [ ] **Step 2: Viết test (failing)** `src/lib/fakeData.test.ts`

```ts
import { generateFakeGraph } from './fakeData'

test('sinh đúng số người, tham chiếu hợp lệ và có tính lặp lại', () => {
  const a = generateFakeGraph(500, 7)
  const b = generateFakeGraph(500, 7)
  expect(a.persons).toHaveLength(500)
  expect(JSON.stringify(a)).toBe(JSON.stringify(b))
  const ids = new Set(a.persons.map((p) => p.id))
  expect(ids.has(a.rootPersonId)).toBe(true)
  for (const l of a.links) { expect(ids.has(l.parentId)).toBe(true); expect(ids.has(l.childId)).toBe(true) }
  for (const u of a.unions) { expect(ids.has(u.partnerAId)).toBe(true); expect(ids.has(u.partnerBId)).toBe(true) }
})
```

- [ ] **Step 3: Chạy test, thấy FAIL**

Run: `npx vitest run src/lib/fakeData.test.ts`
Expected: FAIL — không tìm thấy `./fakeData`.

- [ ] **Step 4: Viết `src/lib/fakeData.ts`**

```ts
import type { GraphData, GraphLink, GraphPerson, GraphUnion } from './types'

function mulberry32(seed: number) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export function generateFakeGraph(total: number, seed = 1): GraphData {
  const rnd = mulberry32(seed)
  const persons: GraphPerson[] = []
  const unions: GraphUnion[] = []
  const links: GraphLink[] = []
  let nextId = 1
  let nextUnion = 1

  const make = (gender: 'M' | 'F', generation: number, birthYear: number, membership: 'BLOOD' | 'IN_LAW'): GraphPerson => {
    const p: GraphPerson = {
      id: nextId++, name: `Người ${nextId - 1}`, gender, birthYear,
      deathYear: birthYear < 1950 ? birthYear + 60 + Math.floor(rnd() * 25) : null,
      alive: birthYear >= 1950, thumb: null, birthOrder: null, orderLabel: null, generation, membership,
    }
    persons.push(p)
    return p
  }

  const root = make('M', 1, 1800, 'BLOOD')
  const queue: GraphPerson[] = [root]
  while (queue.length && persons.length < total) {
    const p = queue.shift()!
    if (persons.length >= total) break
    const spouse = make(p.gender === 'M' ? 'F' : 'M', p.generation, (p.birthYear ?? 1800) + Math.floor(rnd() * 6), 'IN_LAW')
    const union: GraphUnion = { id: nextUnion++, partnerAId: p.id, partnerBId: spouse.id, order: 1, status: 'MARRIED' }
    unions.push(union)
    const kids = 1 + Math.floor(rnd() * 4)
    for (let i = 0; i < kids && persons.length < total; i++) {
      const child = make(rnd() < 0.5 ? 'M' : 'F', p.generation + 1, (p.birthYear ?? 1800) + 22 + i * 2, 'BLOOD')
      child.birthOrder = i + 2
      const [father, mother] = p.gender === 'M' ? [p, spouse] : [spouse, p]
      links.push({ parentId: father.id, childId: child.id, unionId: union.id, kind: 'BIOLOGICAL' })
      links.push({ parentId: mother.id, childId: child.id, unionId: union.id, kind: 'BIOLOGICAL' })
      queue.push(child)
    }
  }
  return { clanId: 1, rootPersonId: root.id, siblingCountMode: 'PER_UNION', persons, unions, links }
}
```

- [ ] **Step 5: Chạy test, thấy PASS**

Run: `npx vitest run src/lib/fakeData.test.ts`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/lib && git commit -m "feat(E1): graph types and fake data generator"
```

---

### Task 2: Thuật toán bố cục

**Files:**
- Create: `src/lib/layout.ts`
- Test: `src/lib/layout.test.ts`

**Interfaces:**
- Consumes: `GraphData` (Task 1).
- Produces:
```ts
export const NODE_W = 120, NODE_H = 60, GAP_X = 24, UNIT_GAP = 40, ROW_H = 140
export interface LayoutNode { id: number; x: number; y: number; w: number; h: number; generation: number }
export interface LayoutEdge { kind: 'union' | 'child'; x1: number; y1: number; x2: number; y2: number }
export interface LayoutResult { nodes: LayoutNode[]; edges: LayoutEdge[]; width: number; height: number }
export interface LayoutOptions { collapsed?: Set<number> }
export function computeLayout(g: GraphData, opts?: LayoutOptions): LayoutResult
```
`x,y` là góc trên-trái trong toạ độ thế giới. `collapsed` = tập `personId` mà hậu duệ bị ẩn.

- [ ] **Step 1: Viết test (failing)** `src/lib/layout.test.ts`

```ts
import { computeLayout, NODE_W, NODE_H, GAP_X, UNIT_GAP, ROW_H } from './layout'
import { generateFakeGraph } from './fakeData'
import type { GraphData, GraphPerson } from './types'

const P = (id: number, extra: Partial<GraphPerson> = {}): GraphPerson => ({
  id, name: `P${id}`, gender: 'M', birthYear: null, deathYear: null, alive: false, thumb: null,
  birthOrder: null, orderLabel: null, generation: 1, membership: 'BLOOD', ...extra,
})
const G = (persons: GraphPerson[], unions: GraphData['unions'] = [], links: GraphData['links'] = [], root = 1): GraphData => ({
  clanId: 1, rootPersonId: root, siblingCountMode: 'PER_UNION', persons, unions, links,
})
const U = (id: number, a: number, b: number, order = 1) => ({ id, partnerAId: a, partnerBId: b, order, status: 'MARRIED' as const })
const L = (parentId: number, childId: number, unionId: number | null = null) => ({ parentId, childId, unionId, kind: 'BIOLOGICAL' as const })
const at = (r: ReturnType<typeof computeLayout>, id: number) => r.nodes.find((n) => n.id === id)!

test('một người đứng một mình', () => {
  const r = computeLayout(G([P(1)]))
  expect(r.nodes).toHaveLength(1)
  expect(at(r, 1)).toMatchObject({ x: 0, y: 0, w: NODE_W, h: NODE_H })
  expect(r.width).toBe(NODE_W)
  expect(r.height).toBe(NODE_H)
})

test('dữ liệu không có người gốc trả về rỗng, không lỗi', () => {
  const r = computeLayout(G([], [], [], 99))
  expect(r.nodes).toHaveLength(0)
})

test('cặp vợ chồng đứng cạnh nhau cùng hàng và có đường nối hôn phối', () => {
  const r = computeLayout(G([P(1), P(2, { gender: 'F' })], [U(1, 1, 2)]))
  expect(at(r, 1).x).toBe(0)
  expect(at(r, 2).x).toBe(NODE_W + GAP_X)
  expect(at(r, 2).y).toBe(0)
  expect(r.edges.filter((e) => e.kind === 'union')).toHaveLength(1)
})

test('cặp có 2 con: dải con rộng hơn cặp thì cặp căn giữa trên dải con', () => {
  const g = G([P(1), P(2, { gender: 'F' }), P(3, { birthOrder: 2 }), P(4, { birthOrder: 3 })],
    [U(1, 1, 2)], [L(1, 3, 1), L(2, 3, 1), L(1, 4, 1), L(2, 4, 1)])
  const r = computeLayout(g)
  expect(at(r, 3)).toMatchObject({ x: 0, y: ROW_H })
  expect(at(r, 4).x).toBe(NODE_W + UNIT_GAP)
  expect(at(r, 1).x).toBe(8) // (280 - 264) / 2
  expect(at(r, 2).x).toBe(8 + NODE_W + GAP_X)
})

test('cha một mình có 3 con thì nằm giữa dải con', () => {
  const g = G([P(1), P(2), P(3), P(4)], [], [L(1, 2), L(1, 3), L(1, 4)])
  const r = computeLayout(g)
  expect(at(r, 1).x).toBe(160)
  expect([at(r, 2).x, at(r, 3).x, at(r, 4).x]).toEqual([0, 160, 320])
})

test('đường nối cha mẹ - con đi từ đáy đơn vị tới đỉnh con', () => {
  const g = G([P(1), P(2)], [], [L(1, 2)])
  const r = computeLayout(g)
  const e = r.edges.find((x) => x.kind === 'child')!
  expect(e).toMatchObject({ x1: NODE_W / 2, y1: NODE_H, x2: NODE_W / 2, y2: ROW_H })
})

test('3 vợ được xếp theo union.order dù mảng đầu vào xáo trộn', () => {
  const g = G([P(1), P(2, { gender: 'F' }), P(3, { gender: 'F' }), P(4, { gender: 'F' })],
    [U(30, 1, 4, 3), U(10, 1, 2, 1), U(20, 1, 3, 2)])
  const r = computeLayout(g)
  const xs = [1, 2, 3, 4].map((id) => at(r, id).x)
  expect(xs).toEqual([...xs].sort((a, b) => a - b))
  expect(new Set([1, 2, 3, 4].map((id) => at(r, id).y)).size).toBe(1)
})

test('anh em sắp theo birthOrder rồi birthYear, người thiếu dữ liệu xuống cuối', () => {
  const g = G([P(1), P(2, { birthYear: null }), P(3, { birthYear: 1900 }), P(4, { birthOrder: 2 })],
    [], [L(1, 2), L(1, 3), L(1, 4)])
  const r = computeLayout(g)
  const order = [2, 3, 4].sort((a, b) => at(r, a).x - at(r, b).x)
  expect(order).toEqual([4, 3, 2])
})

test('thu gọn: ẩn toàn bộ hậu duệ của người được thu gọn', () => {
  const g = G([P(1), P(2), P(3)], [], [L(1, 2), L(2, 3)])
  const r = computeLayout(g, { collapsed: new Set([2]) })
  expect(r.nodes.map((n) => n.id).sort()).toEqual([1, 2])
})

test('hôn phối trong họ: người vừa là con vừa là vợ chỉ vẽ một lần và không lặp vô hạn', () => {
  // 1 -> 2, 1 -> 3 ; 2 và 3 (anh em họ giả định) cưới nhau; 4 là con chung
  const g = G([P(1), P(2), P(3, { gender: 'F' }), P(4)], [U(1, 2, 3)],
    [L(1, 2), L(1, 3), L(2, 4, 1), L(3, 4, 1)])
  const r = computeLayout(g)
  expect(r.nodes.map((n) => n.id).sort()).toEqual([1, 2, 3, 4])
})

test('10.000 người bố cục dưới 1 giây', () => {
  const g = generateFakeGraph(10000, 3)
  const t0 = performance.now()
  const r = computeLayout(g)
  const ms = performance.now() - t0
  expect(r.nodes).toHaveLength(10000)
  expect(ms).toBeLessThan(1000)
})
```

- [ ] **Step 2: Chạy test, thấy FAIL**

Run: `npx vitest run src/lib/layout.test.ts`
Expected: FAIL — không tìm thấy `./layout`.

- [ ] **Step 3: Viết `src/lib/layout.ts`**

```ts
import type { GraphData, GraphUnion } from './types'

export const NODE_W = 120
export const NODE_H = 60
export const GAP_X = 24
export const UNIT_GAP = 40
export const ROW_H = 140

export interface LayoutNode { id: number; x: number; y: number; w: number; h: number; generation: number }
export interface LayoutEdge { kind: 'union' | 'child'; x1: number; y1: number; x2: number; y2: number }
export interface LayoutResult { nodes: LayoutNode[]; edges: LayoutEdge[]; width: number; height: number }
export interface LayoutOptions { collapsed?: Set<number> }

interface Unit { members: number[]; children: Unit[]; ownWidth: number; width: number }

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
    if (!collapsed.has(leadId)) {
      const seen = new Set<number>()
      for (const m of members) {
        for (const c of childrenOf.get(m) ?? []) {
          if (!visited.has(c) && !seen.has(c)) { seen.add(c); kids.push(c) }
        }
      }
      kids.sort(cmp)
    }
    const children: Unit[] = []
    for (const c of kids) if (!visited.has(c)) children.push(build(c))

    const childrenWidth = children.length
      ? children.reduce((s, c) => s + c.width, 0) + (children.length - 1) * UNIT_GAP
      : 0
    return { members, children, ownWidth, width: Math.max(ownWidth, childrenWidth) }
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
      nodes.push({ id, x, y, w: NODE_W, h: NODE_H, generation: person.get(id)!.generation })
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
```

- [ ] **Step 4: Chạy test, thấy PASS**

Run: `npx vitest run src/lib/layout.test.ts`
Expected: PASS (10 tests). Nếu test "10.000 người" chậm hơn 1 s, báo lại, không nới ngưỡng.

- [ ] **Step 5: Commit**

```bash
git add src/lib && git commit -m "feat(E1): vertical family-unit tree layout"
```

---

### Task 3: Quadtree và mức chi tiết (LOD)

**Files:**
- Create: `src/lib/quadtree.ts`, `src/lib/lod.ts`
- Test: `src/lib/quadtree.test.ts`, `src/lib/lod.test.ts`

**Interfaces:**
- Produces:
```ts
export interface Rect { x: number; y: number; w: number; h: number }
export interface Item extends Rect { id: number }
export class QuadTree { constructor(bounds: Rect, maxItems?: number, maxDepth?: number); insert(it: Item): void; queryRect(x: number, y: number, w: number, h: number): Item[]; queryPoint(x: number, y: number): Item | null }
export type Lod = 'dot' | 'box' | 'full'
export function lodForScale(scale: number): Lod
```

- [ ] **Step 1: Viết test (failing)**

`src/lib/lod.test.ts`:
```ts
import { lodForScale } from './lod'

test('chọn mức chi tiết theo độ phóng', () => {
  expect(lodForScale(0.1)).toBe('dot')
  expect(lodForScale(0.29)).toBe('dot')
  expect(lodForScale(0.3)).toBe('box')
  expect(lodForScale(0.79)).toBe('box')
  expect(lodForScale(0.8)).toBe('full')
  expect(lodForScale(2)).toBe('full')
})
```
`src/lib/quadtree.test.ts`:
```ts
import { QuadTree } from './quadtree'

const mk = () => {
  const qt = new QuadTree({ x: 0, y: 0, w: 1000, h: 1000 }, 2, 6)
  for (let i = 0; i < 100; i++) qt.insert({ id: i, x: (i % 10) * 100, y: Math.floor(i / 10) * 100, w: 50, h: 50 })
  return qt
}

test('queryRect trả đúng các ô giao nhau', () => {
  const ids = mk().queryRect(0, 0, 120, 120).map((i) => i.id).sort((a, b) => a - b)
  expect(ids).toEqual([0, 1, 10, 11])
})

test('queryPoint trúng ô', () => {
  expect(mk().queryPoint(25, 25)?.id).toBe(0)
  expect(mk().queryPoint(525, 325)?.id).toBe(35)
})

test('queryPoint vùng trống trả null', () => {
  expect(mk().queryPoint(75, 75)).toBeNull()
})

test('ô nằm ngoài bounds ban đầu vẫn tìm thấy được', () => {
  const qt = new QuadTree({ x: 0, y: 0, w: 100, h: 100 }, 1, 4)
  qt.insert({ id: 1, x: 500, y: 500, w: 10, h: 10 })
  qt.insert({ id: 2, x: 10, y: 10, w: 10, h: 10 })
  qt.insert({ id: 3, x: 40, y: 40, w: 10, h: 10 })
  expect(qt.queryPoint(505, 505)?.id).toBe(1)
})
```

- [ ] **Step 2: Chạy test, thấy FAIL**

Run: `npx vitest run src/lib/quadtree.test.ts src/lib/lod.test.ts`
Expected: FAIL — module không tồn tại.

- [ ] **Step 3: Viết triển khai**

`src/lib/lod.ts`:
```ts
export type Lod = 'dot' | 'box' | 'full'

export function lodForScale(scale: number): Lod {
  if (scale < 0.3) return 'dot'
  if (scale < 0.8) return 'box'
  return 'full'
}
```
`src/lib/quadtree.ts`:
```ts
export interface Rect { x: number; y: number; w: number; h: number }
export interface Item extends Rect { id: number }

const hit = (a: Rect, x: number, y: number, w: number, h: number) =>
  a.x < x + w && a.x + a.w > x && a.y < y + h && a.y + a.h > y

export class QuadTree {
  private items: Item[] = []
  private children: QuadTree[] | null = null

  constructor(
    private bounds: Rect,
    private maxItems = 16,
    private maxDepth = 8,
    private depth = 0,
  ) {}

  insert(it: Item): void {
    // Ô nằm ngoài bounds ban đầu được giữ tại nút gốc để vẫn tìm thấy.
    if (this.depth === 0 && !hit(this.bounds, it.x, it.y, it.w, it.h)) { this.items.push(it); return }
    if (this.children) {
      const i = this.childIndex(it)
      if (i >= 0) this.children[i].insert(it)
      else this.items.push(it)
      return
    }
    this.items.push(it)
    if (this.items.length > this.maxItems && this.depth < this.maxDepth) this.split()
  }

  queryRect(x: number, y: number, w: number, h: number, out: Item[] = []): Item[] {
    // Ô nằm ngoài bounds nằm ở nút gốc nên nút gốc luôn được duyệt items của nó.
    if (this.depth > 0 && !hit(this.bounds, x, y, w, h)) return out
    for (const it of this.items) if (hit(it, x, y, w, h)) out.push(it)
    if (this.children) for (const c of this.children) c.queryRect(x, y, w, h, out)
    return out
  }

  queryPoint(x: number, y: number): Item | null {
    const found = this.queryRect(x, y, 0.001, 0.001)
    return found.length ? found[found.length - 1] : null
  }

  private split(): void {
    const { x, y, w, h } = this.bounds
    const hw = w / 2, hh = h / 2, d = this.depth + 1
    this.children = [
      new QuadTree({ x, y, w: hw, h: hh }, this.maxItems, this.maxDepth, d),
      new QuadTree({ x: x + hw, y, w: hw, h: hh }, this.maxItems, this.maxDepth, d),
      new QuadTree({ x, y: y + hh, w: hw, h: hh }, this.maxItems, this.maxDepth, d),
      new QuadTree({ x: x + hw, y: y + hh, w: hw, h: hh }, this.maxItems, this.maxDepth, d),
    ]
    const old = this.items
    this.items = []
    for (const it of old) this.insert(it)
  }

  private childIndex(it: Item): number {
    const midX = this.bounds.x + this.bounds.w / 2
    const midY = this.bounds.y + this.bounds.h / 2
    const left = it.x + it.w <= midX, right = it.x >= midX
    const top = it.y + it.h <= midY, bottom = it.y >= midY
    if (top && left) return 0
    if (top && right) return 1
    if (bottom && left) return 2
    if (bottom && right) return 3
    return -1
  }
}
```

- [ ] **Step 4: Chạy test, thấy PASS**

Run: `npx vitest run src/lib`
Expected: toàn bộ PASS.

- [ ] **Step 5: Commit**

```bash
git add src/lib && git commit -m "feat(E1): quadtree and LOD helpers"
```

---

### Task 4: Worker bố cục, client, và cache IndexedDB

**Files:**
- Create: `src/workers/layout.worker.ts`, `src/features/tree/layoutClient.ts`, `src/lib/layoutCache.ts`
- Test: `src/lib/layoutCache.test.ts`
- Modify: `package.json` (devDependency `fake-indexeddb`)

**Interfaces:**
- Consumes: `computeLayout`, `LayoutResult` (Task 2).
- Produces: `runLayout(graph: GraphData, collapsed?: number[]): Promise<LayoutResult>`; `getCachedLayout(key: string): Promise<LayoutResult | undefined>`; `setCachedLayout(key: string, r: LayoutResult): Promise<void>`; `layoutCacheKey(clanId: number, etag: string, side: string, collapsed: number[]): string`.

- [ ] **Step 1: Cài thư viện test**

Run: `npm i -D fake-indexeddb`

- [ ] **Step 2: Viết test (failing)** `src/lib/layoutCache.test.ts`

```ts
import 'fake-indexeddb/auto'
import { getCachedLayout, setCachedLayout, layoutCacheKey } from './layoutCache'

const r = { nodes: [{ id: 1, x: 0, y: 0, w: 120, h: 60, generation: 1 }], edges: [], width: 120, height: 60 }

test('ghi rồi đọc lại đúng kết quả; khoá khác ETag thì không thấy', async () => {
  const k1 = layoutCacheKey(1, 'etag-a', 'both', [])
  const k2 = layoutCacheKey(1, 'etag-b', 'both', [])
  await setCachedLayout(k1, r)
  expect(await getCachedLayout(k1)).toEqual(r)
  expect(await getCachedLayout(k2)).toBeUndefined()
})

test('khoá phụ thuộc vào tập thu gọn (không phụ thuộc thứ tự)', () => {
  expect(layoutCacheKey(1, 'e', 'both', [3, 2])).toBe(layoutCacheKey(1, 'e', 'both', [2, 3]))
  expect(layoutCacheKey(1, 'e', 'both', [2])).not.toBe(layoutCacheKey(1, 'e', 'both', []))
})
```

- [ ] **Step 3: Chạy test, thấy FAIL**

Run: `npx vitest run src/lib/layoutCache.test.ts`
Expected: FAIL — thiếu module.

- [ ] **Step 4: Viết `src/lib/layoutCache.ts`**

```ts
import { get, set } from 'idb-keyval'
import type { LayoutResult } from './layout'

export function layoutCacheKey(clanId: number, etag: string, side: string, collapsed: number[]): string {
  return `layout:${clanId}:${etag}:${side}:${[...collapsed].sort((a, b) => a - b).join(',')}`
}

export async function getCachedLayout(key: string): Promise<LayoutResult | undefined> {
  try { return await get<LayoutResult>(key) } catch { return undefined }
}

export async function setCachedLayout(key: string, r: LayoutResult): Promise<void> {
  try { await set(key, r) } catch { /* cache không bắt buộc */ }
}
```

- [ ] **Step 5: Viết worker và client**

`src/workers/layout.worker.ts`:
```ts
import { computeLayout } from '../lib/layout'
import type { GraphData } from '../lib/types'

self.onmessage = (e: MessageEvent<{ id: number; graph: GraphData; collapsed: number[] }>) => {
  const { id, graph, collapsed } = e.data
  const result = computeLayout(graph, { collapsed: new Set(collapsed) })
  ;(self as unknown as Worker).postMessage({ id, result })
}
```
`src/features/tree/layoutClient.ts`:
```ts
import type { LayoutResult } from '../../lib/layout'
import type { GraphData } from '../../lib/types'

let worker: Worker | null = null
let seq = 0
const pending = new Map<number, (r: LayoutResult) => void>()

function getWorker(): Worker {
  if (!worker) {
    worker = new Worker(new URL('../../workers/layout.worker.ts', import.meta.url), { type: 'module' })
    worker.onmessage = (e: MessageEvent<{ id: number; result: LayoutResult }>) => {
      pending.get(e.data.id)?.(e.data.result)
      pending.delete(e.data.id)
    }
  }
  return worker
}

export function runLayout(graph: GraphData, collapsed: number[] = []): Promise<LayoutResult> {
  return new Promise((resolve) => {
    const id = ++seq
    pending.set(id, resolve)
    getWorker().postMessage({ id, graph, collapsed })
  })
}
```

- [ ] **Step 6: Chạy test + build**

Run: `npx vitest run && npm run build`
Expected: test PASS; build thành công (kiểm tra kiểu worker).

- [ ] **Step 7: Commit**

```bash
git add . && git commit -m "feat(E1): layout worker, client and IndexedDB cache"
```

---

### Task 5: TreeRenderer (PixiJS)

**Files:**
- Create: `src/features/tree/TreeRenderer.ts`

**Interfaces:**
- Consumes: `GraphData`, `LayoutResult`, `QuadTree`, `lodForScale`.
- Produces:
```ts
export interface RendererHandlers {
  onHover?: (personId: number | null, screen: { x: number; y: number }) => void
  onClick?: (personId: number, screen: { x: number; y: number }) => void
}
export class TreeRenderer {
  constructor(canvas: HTMLCanvasElement, handlers?: RendererHandlers)
  setData(graph: GraphData, layout: LayoutResult): void
  focusOn(personId: number, scale?: number): void
  fps(): number
  benchmark(durationMs: number): Promise<{ avgFps: number; minFps: number; frames: number }>
  destroy(): void
}
```
Không thể unit-test (cần WebGL); kiểm tra thủ công ở Task 6.

- [ ] **Step 1: Viết `src/features/tree/TreeRenderer.ts`**

```ts
import { Application, Container, Graphics, Text } from 'pixi.js'
import { Viewport } from 'pixi-viewport'
import type { GraphData, GraphPerson } from '../../lib/types'
import type { LayoutNode, LayoutResult } from '../../lib/layout'
import { QuadTree } from '../../lib/quadtree'
import { lodForScale, type Lod } from '../../lib/lod'

export interface RendererHandlers {
  onHover?: (personId: number | null, screen: { x: number; y: number }) => void
  onClick?: (personId: number, screen: { x: number; y: number }) => void
}

const COLOR = { M: 0x4a90d9, F: 0xe57fa7, U: 0x9aa0a6 } as const

class NodeView {
  container = new Container()
  bg = new Graphics()
  label = new Text('', { fontSize: 13, fill: 0xffffff, wordWrap: true, wordWrapWidth: 104, lineHeight: 16 })
  constructor() {
    this.label.position.set(8, 8)
    this.container.addChild(this.bg, this.label)
  }
  render(node: LayoutNode, p: GraphPerson, lod: Lod) {
    this.container.position.set(node.x, node.y)
    this.bg.clear().beginFill(COLOR[p.gender]).drawRoundedRect(0, 0, node.w, node.h, 8).endFill()
    const years = `${p.birthYear ?? '?'}${p.alive ? '' : ` – ${p.deathYear ?? '?'}`}`
    const prefix = lod === 'full' && p.orderLabel ? `${p.orderLabel} · ` : ''
    this.label.text = `${prefix}${p.name}\n${years}`
  }
}

export class TreeRenderer {
  private app: Application
  private viewport: Viewport
  private edges = new Graphics()
  private dots = new Graphics()
  private views = new Container()
  private pool: NodeView[] = []
  private active = new Map<number, NodeView>()
  private index: QuadTree | null = null
  private persons = new Map<number, GraphPerson>()
  private nodes = new Map<number, LayoutNode>()
  private lod: Lod = 'dot'
  private dirty = true
  private hoverId: number | null = null

  constructor(private canvas: HTMLCanvasElement, private handlers: RendererHandlers = {}) {
    this.app = new Application({
      view: canvas, resizeTo: canvas.parentElement ?? window, backgroundColor: 0xf5f6f8,
      antialias: true, resolution: window.devicePixelRatio || 1, autoDensity: true,
    })
    this.viewport = new Viewport({
      screenWidth: this.app.screen.width, screenHeight: this.app.screen.height,
      worldWidth: 1000, worldHeight: 1000, events: this.app.renderer.events,
    })
    this.viewport.drag().pinch().wheel().decelerate().clampZoom({ minScale: 0.02, maxScale: 2 })
    this.viewport.addChild(this.edges, this.dots, this.views)
    this.app.stage.addChild(this.viewport)

    this.viewport.on('moved', () => { this.dirty = true })
    this.viewport.on('zoomed', () => { this.dirty = true })
    this.app.renderer.on('resize', (w: number, h: number) => { this.viewport.resize(w, h); this.dirty = true })
    this.app.ticker.add(() => { if (this.dirty) { this.dirty = false; this.refresh() } })

    this.viewport.on('pointermove', (e) => {
      const w = this.viewport.toWorld(e.global.x, e.global.y)
      const id = this.index?.queryPoint(w.x, w.y)?.id ?? null
      if (id !== this.hoverId) {
        this.hoverId = id
        this.canvas.style.cursor = id === null ? 'grab' : 'pointer'
        this.handlers.onHover?.(id, { x: e.global.x, y: e.global.y })
      }
    })
    this.viewport.on('clicked', (e) => {
      const id = this.index?.queryPoint(e.world.x, e.world.y)?.id
      if (id !== undefined) this.handlers.onClick?.(id, { x: e.screen.x, y: e.screen.y })
    })
  }

  setData(graph: GraphData, layout: LayoutResult): void {
    this.persons = new Map(graph.persons.map((p) => [p.id, p]))
    this.nodes = new Map(layout.nodes.map((n) => [n.id, n]))
    this.releaseAll()

    this.index = new QuadTree({ x: 0, y: 0, w: Math.max(layout.width, 1), h: Math.max(layout.height, 1) })
    for (const n of layout.nodes) this.index.insert({ id: n.id, x: n.x, y: n.y, w: n.w, h: n.h })

    this.edges.clear().lineStyle({ width: 1, color: 0xaab0b8, native: true })
    for (const e of layout.edges) {
      this.edges.moveTo(e.x1, e.y1)
      if (e.kind === 'union') this.edges.lineTo(e.x2, e.y2)
      else {
        const midY = (e.y1 + e.y2) / 2
        this.edges.lineTo(e.x1, midY).lineTo(e.x2, midY).lineTo(e.x2, e.y2)
      }
    }

    this.dots.clear()
    for (const n of layout.nodes) {
      const p = this.persons.get(n.id)!
      this.dots.beginFill(COLOR[p.gender]).drawCircle(n.x + n.w / 2, n.y + n.h / 2, 22).endFill()
    }

    this.viewport.resize(this.app.screen.width, this.app.screen.height, layout.width, layout.height)
    this.viewport.fit(true, layout.width, layout.height)
    this.viewport.moveCenter(layout.width / 2, layout.height / 2)
    this.dirty = true
  }

  focusOn(personId: number, scale = 1): void {
    const n = this.nodes.get(personId)
    if (!n) return
    this.viewport.setZoom(scale, true)
    this.viewport.moveCenter(n.x + n.w / 2, n.y + n.h / 2)
    this.dirty = true
  }

  fps(): number { return this.app.ticker.FPS }

  benchmark(durationMs: number): Promise<{ avgFps: number; minFps: number; frames: number }> {
    return new Promise((resolve) => {
      const lw = this.viewport.worldWidth, lh = this.viewport.worldHeight
      this.viewport.setZoom(0.5, true)
      const t0 = performance.now()
      let frames = 0, minFps = Infinity
      const tick = () => {
        const t = (performance.now() - t0) / durationMs
        if (t >= 1) {
          this.app.ticker.remove(tick)
          const secs = (performance.now() - t0) / 1000
          resolve({ avgFps: frames / secs, minFps: Number.isFinite(minFps) ? minFps : 0, frames })
          return
        }
        const sx = 0.5 + 0.5 * Math.sin(t * Math.PI * 4)
        const sy = 0.5 + 0.5 * Math.sin(t * Math.PI * 6)
        this.viewport.moveCenter(sx * lw, sy * lh)
        this.viewport.setZoom(0.2 + 0.7 * (0.5 + 0.5 * Math.sin(t * Math.PI * 8)), true)
        this.dirty = true
        frames++
        if (this.app.ticker.deltaMS > 0) minFps = Math.min(minFps, 1000 / this.app.ticker.deltaMS)
      }
      this.app.ticker.add(tick)
    })
  }

  destroy(): void {
    this.app.destroy(false, { children: true })
  }

  private refresh(): void {
    const lod = lodForScale(this.viewport.scale.x)
    if (lod !== this.lod) { this.lod = lod; this.releaseAll() }
    this.dots.visible = lod === 'dot'
    this.views.visible = lod !== 'dot'
    if (lod === 'dot' || !this.index) return

    const b = this.viewport.getVisibleBounds()
    const visible = this.index.queryRect(b.x, b.y, b.width, b.height)
    const keep = new Set<number>()
    for (const it of visible) {
      keep.add(it.id)
      if (!this.active.has(it.id)) {
        const v = this.pool.pop() ?? new NodeView()
        v.render(this.nodes.get(it.id)!, this.persons.get(it.id)!, lod)
        this.views.addChild(v.container)
        this.active.set(it.id, v)
      }
    }
    for (const [id, v] of this.active) {
      if (!keep.has(id)) { this.views.removeChild(v.container); this.pool.push(v); this.active.delete(id) }
    }
  }

  private releaseAll(): void {
    for (const v of this.active.values()) { this.views.removeChild(v.container); this.pool.push(v) }
    this.active.clear()
  }
}
```

- [ ] **Step 2: Kiểm tra kiểu**

Run: `npm run build`
Expected: thành công. Nếu lỗi kiểu liên quan `e.global`/`e.world`, sửa kiểu sự kiện cho khớp pixi-viewport 5 (`FederatedPointerEvent`, `ClickEventData`) nhưng không đổi logic.

- [ ] **Step 3: Commit**

```bash
git add . && git commit -m "feat(E1): PixiJS tree renderer with culling and LOD"
```

---

### Task 6: Trang thử `/proto` và đo fps

**Files:**
- Create: `src/features/tree/ProtoPage.tsx`
- Modify: `src/App.tsx`, `src/App.test.tsx`

**Interfaces:**
- Consumes: `generateFakeGraph`, `runLayout`, `TreeRenderer`.

- [ ] **Step 1: Sửa test App để có định tuyến (failing)** `src/App.test.tsx`

```tsx
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from './App'

test('trang chủ hiển thị tên ứng dụng và liên kết sang trang thử', () => {
  render(<MemoryRouter><App /></MemoryRouter>)
  expect(screen.getByText('Gia phả')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Bản thử vẽ' })).toHaveAttribute('href', '/proto')
})
```

- [ ] **Step 2: Chạy test, thấy FAIL**

Run: `npx vitest run src/App.test.tsx`
Expected: FAIL (không có link).

- [ ] **Step 3: Viết `ProtoPage.tsx`**

```tsx
import { useEffect, useRef, useState } from 'react'
import { generateFakeGraph } from '../../lib/fakeData'
import { runLayout } from './layoutClient'
import { TreeRenderer } from './TreeRenderer'

export default function ProtoPage() {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const rendererRef = useRef<TreeRenderer | null>(null)
  const [total, setTotal] = useState(2000)
  const [layoutMs, setLayoutMs] = useState<number | null>(null)
  const [fps, setFps] = useState(0)
  const [hover, setHover] = useState<{ id: number; x: number; y: number } | null>(null)
  const [bench, setBench] = useState<string>('')

  useEffect(() => {
    const r = new TreeRenderer(canvasRef.current!, {
      onHover: (id, s) => setHover(id === null ? null : { id, x: s.x, y: s.y }),
      onClick: (id) => console.info('click', id),
    })
    rendererRef.current = r
    const timer = setInterval(() => setFps(Math.round(r.fps())), 500)
    return () => { clearInterval(timer); r.destroy() }
  }, [])

  useEffect(() => {
    let cancelled = false
    const graph = generateFakeGraph(total, 3)
    const t0 = performance.now()
    runLayout(graph).then((layout) => {
      if (cancelled) return
      setLayoutMs(Math.round(performance.now() - t0))
      rendererRef.current?.setData(graph, layout)
    })
    return () => { cancelled = true }
  }, [total])

  const runBench = async () => {
    setBench('đang đo...')
    const r = await rendererRef.current!.benchmark(6000)
    setBench(`TB ${r.avgFps.toFixed(1)} fps, thấp nhất ${r.minFps.toFixed(1)} fps (${r.frames} khung)`)
  }

  return (
    <div style={{ position: 'fixed', inset: 0 }}>
      <canvas ref={canvasRef} style={{ width: '100%', height: '100%', display: 'block' }} />
      <div style={{ position: 'absolute', top: 8, left: 8, background: '#fffd', padding: 8, borderRadius: 6 }}>
        <select value={total} onChange={(e) => setTotal(Number(e.target.value))}>
          <option value={2000}>2.000 người</option>
          <option value={10000}>10.000 người</option>
        </select>{' '}
        <button onClick={runBench}>Đo fps 6 giây</button>
        <div>Bố cục: {layoutMs ?? '...'} ms · FPS: {fps}</div>
        <div>{bench}</div>
      </div>
      {hover && (
        <div style={{ position: 'absolute', left: hover.x + 12, top: hover.y + 12, background: '#222', color: '#fff', padding: '4px 8px', borderRadius: 4, pointerEvents: 'none' }}>
          Người #{hover.id}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 4: Sửa `src/App.tsx` và `src/main.tsx`**

`src/App.tsx`:
```tsx
import { Link, Route, Routes } from 'react-router-dom'
import ProtoPage from './features/tree/ProtoPage'

export default function App() {
  return (
    <Routes>
      <Route path="/proto" element={<ProtoPage />} />
      <Route path="*" element={<div><h1>Gia phả</h1><Link to="/proto">Bản thử vẽ</Link></div>} />
    </Routes>
  )
}
```
`src/main.tsx` (thay toàn bộ):
```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import viVN from 'antd/locale/vi_VN'
import App from './App'

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } } })

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider locale={viVN}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </ConfigProvider>
  </React.StrictMode>,
)
```

- [ ] **Step 5: Chạy test và build**

Run: `npx vitest run && npm run build`
Expected: PASS, build thành công.

- [ ] **Step 6: Commit**

```bash
git add . && git commit -m "feat(E1): /proto page with fps benchmark"
```

---

### Task 7: Đo M1 và ra quyết định

**Files:**
- Create: `docs/reports/M1-render-prototype.md`

- [ ] **Step 1: Chạy trang thử**

Run: `npm run dev`, mở `http://localhost:5173/proto` bằng Chrome trên laptop phổ thông (ghi lại cấu hình máy).

- [ ] **Step 2: Đo và ghi số liệu**

Chọn 10.000 người → ghi "Bố cục: ... ms". Bấm "Đo fps 6 giây" 3 lần, ghi TB và thấp nhất. Rê chuột vào 10 nút bất kỳ ở độ phóng gần, xác nhận tooltip hiện đúng và không hiện ở vùng trống.

- [ ] **Step 3: Viết `docs/reports/M1-render-prototype.md`** với bảng:

```markdown
# Báo cáo M1 — bản thử vẽ
Máy: <CPU/GPU/RAM>, Chrome <phiên bản>

| Phép đo | Kết quả | Ngưỡng | Đạt? |
| --- | --- | --- | --- |
| Bố cục 10.000 nút (ms) | | < 1000 | |
| FPS trung bình kéo-phóng (3 lần) | | ≥ 55 | |
| Hover trúng đúng nút (10/10) | | 10/10 | |

Kết luận: ĐẠT / KHÔNG ĐẠT. Nếu KHÔNG ĐẠT: thử tối ưu tối đa 1 ngày (giảm `antialias`, tăng ngưỡng LOD dot lên 0.4); nếu vẫn không đạt, dừng và báo chủ dự án để chuyển sang AntV G6.
```

- [ ] **Step 4: Commit**

```bash
git add docs && git commit -m "docs(E1): M1 measurement report"
```

---

## Self-review

- Spec mục 6 (bố cục) → Task 2; LOD/culling/hit-test/worker/IndexedDB → Task 3–5; mốc M1 → Task 7.
- Chưa vẽ ảnh thật, thu gọn bằng UI, thẻ hover thật: thuộc E5.
