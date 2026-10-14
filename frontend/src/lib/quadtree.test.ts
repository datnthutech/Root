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
