import { defaultCollapsed, DEFAULT_COLLAPSE_DEPTH } from './defaultCollapsed'
import { generateFakeGraph } from './fakeData'
import type { GraphData, GraphPerson } from './types'

const P = (id: number): GraphPerson => ({ id, name: `P${id}`, gender: 'M', birthYear: null, deathYear: null, alive: false, thumb: null,
  birthOrder: null, orderLabel: null, generation: 1, membership: 'BLOOD' })
const chain = (n: number): GraphData => ({
  clanId: 1, rootPersonId: 1, siblingCountMode: 'PER_UNION',
  persons: Array.from({ length: n }, (_, i) => P(i + 1)), unions: [],
  links: Array.from({ length: n - 1 }, (_, i) => ({ parentId: i + 1, childId: i + 2, unionId: null, kind: 'BIOLOGICAL' as const })),
})

test('thu gọn người ở đời thứ N nếu có con', () => {
  expect(DEFAULT_COLLAPSE_DEPTH).toBe(4)
  expect([...defaultCollapsed(chain(6), 4)]).toEqual([4])
})
test('cây nông hơn N đời thì không thu gọn gì', () => {
  expect(defaultCollapsed(chain(4), 4).size).toBe(0)
})
test('không thu gọn người ở đời N nếu họ là lá', () => {
  expect(defaultCollapsed(chain(4), 4).has(4)).toBe(false)
})
test('dữ liệu lớn không lỗi và chỉ thu gọn người có con', () => {
  const g = generateFakeGraph(2000, 5)
  const c = defaultCollapsed(g, 4)
  const parents = new Set(g.links.map((l) => l.parentId))
  for (const id of c) expect(parents.has(id)).toBe(true)
})
