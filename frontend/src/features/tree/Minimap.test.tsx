import { minimapTransform } from './Minimap'

test('co giãn giữ tỉ lệ và căn giữa', () => {
  const t = minimapTransform({ width: 2000, height: 500 }, { w: 200, h: 100 })
  expect(t.scale).toBeCloseTo(0.1)
  expect(t.ox).toBeCloseTo(0)
  expect(t.oy).toBeCloseTo(25)
})
test('layout rỗng không chia cho 0', () => {
  const t = minimapTransform({ width: 0, height: 0 }, { w: 200, h: 100 })
  expect(Number.isFinite(t.scale)).toBe(true)
})
