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
