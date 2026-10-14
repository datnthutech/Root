import { lodForScale } from './lod'

test('chọn mức chi tiết theo độ phóng', () => {
  expect(lodForScale(0.1)).toBe('dot')
  expect(lodForScale(0.29)).toBe('dot')
  expect(lodForScale(0.3)).toBe('box')
  expect(lodForScale(0.79)).toBe('box')
  expect(lodForScale(0.8)).toBe('full')
  expect(lodForScale(2)).toBe('full')
})
