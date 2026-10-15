import dayjs from 'dayjs'
import { fromIso, toIso } from './dateField'

test('toIso theo độ chính xác', () => {
  const d = dayjs('1953-04-05')
  expect(toIso(d, 'DAY')).toBe('1953-04-05')
  expect(toIso(d, 'MONTH')).toBe('1953-04-01')
  expect(toIso(d, 'YEAR')).toBe('1953-01-01')
  expect(toIso(d, 'UNKNOWN')).toBeNull()
  expect(toIso(null, 'DAY')).toBeNull()
})
test('fromIso', () => {
  expect(fromIso('1953-04-05')?.format('YYYY-MM-DD')).toBe('1953-04-05')
  expect(fromIso(null)).toBeNull()
  expect(fromIso(undefined)).toBeNull()
})
