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
