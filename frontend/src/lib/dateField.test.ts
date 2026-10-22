import dayjs from 'dayjs'
import { deathBeforeBirth, fromIso, toIso } from './dateField'

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

test('deathBeforeBirth so ở độ chính xác thô hơn', () => {
  expect(deathBeforeBirth('1950-06-15', 'DAY', '1940-01-01', 'DAY')).toBe(true)
  expect(deathBeforeBirth('1950-06-15', 'DAY', '1950-01-01', 'YEAR')).toBe(false)
  expect(deathBeforeBirth('1950-06-15', 'DAY', '1949-01-01', 'YEAR')).toBe(true)
  expect(deathBeforeBirth('1950-06-15', 'DAY', '1950-06-15', 'DAY')).toBe(false)
  expect(deathBeforeBirth('1950-06-15', 'DAY', '1950-05-20', 'MONTH')).toBe(true)
  expect(deathBeforeBirth('1950-06-15', 'DAY', '1950-06-01', 'MONTH')).toBe(false)
  expect(deathBeforeBirth(null, 'UNKNOWN', '1940-01-01', 'DAY')).toBe(false)
})
