import { anchorShift } from './anchorShift'

test('độ dời bằng hiệu tâm của nút neo', () => {
  expect(anchorShift({ x: 100, y: 10, w: 40, h: 20 }, { x: 0, y: 10, w: 40, h: 20 })).toEqual({ dx: -100, dy: 0 })
})
test('thiếu nút neo thì không dời', () => {
  expect(anchorShift(undefined, { x: 0, y: 0, w: 1, h: 1 })).toEqual({ dx: 0, dy: 0 })
})
