import { formatDate, formatDateTime, lifespan, truncate } from './format'

test('formatDate theo độ chính xác', () => {
  expect(formatDate('1953-04-05', 'DAY')).toBe('05/04/1953')
  expect(formatDate('1953-04-05', 'MONTH')).toBe('04/1953')
  expect(formatDate('1953-04-05', 'YEAR')).toBe('1953')
  expect(formatDate('1953-04-05', 'UNKNOWN')).toBe('Không rõ')
  expect(formatDate(null, 'DAY')).toBe('Không rõ')
  expect(formatDate(undefined, undefined)).toBe('Không rõ')
})
test('lifespan', () => {
  expect(lifespan({ birthYear: 1900, deathYear: 1970, alive: false })).toBe('1900 – 1970')
  expect(lifespan({ birthYear: 1950, deathYear: null, alive: true })).toBe('1950')
  expect(lifespan({ birthYear: null, deathYear: null, alive: false })).toBe('? – ?')
  expect(lifespan({ birthYear: null, deathYear: null, alive: true })).toBe('?')
})
test('truncate', () => {
  expect(truncate('abcdef', 4)).toBe('abc…')
  expect(truncate('abc', 4)).toBe('abc')
})

test('formatDateTime dạng dd/MM/yyyy HH:mm', () => {
  const d = new Date(2026, 9, 14, 8, 5)
  expect(formatDateTime(d.toISOString())).toBe('14/10/2026 08:05')
})
test('formatDateTime với chuỗi sai trả về chuỗi gốc', () => {
  expect(formatDateTime('not-a-date')).toBe('not-a-date')
})
