import { parseParams, toSearch } from './treeParams'

const sp = (s: string) => new URLSearchParams(s)

test('chế độ dòng họ', () => {
  expect(parseParams(sp('mode=clan&clan=2&side=paternal'))).toEqual({ mode: 'clan', clanId: 2, side: 'paternal', personId: null, up: 3, down: 3 })
})
test('chế độ tập trung và kẹp up/down', () => {
  expect(parseParams(sp('mode=focus&person=9&up=99&down=-4'))).toEqual({ mode: 'focus', clanId: null, side: 'both', personId: 9, up: 10, down: 0 })
})
test('suy ra mode từ tham số có mặt; side sai về both', () => {
  expect(parseParams(sp('person=5'))?.mode).toBe('focus')
  expect(parseParams(sp('clan=1&side=zzz'))?.side).toBe('both')
})
test('thiếu cả clan lẫn person thì null; id không phải số thì null', () => {
  expect(parseParams(sp(''))).toBeNull()
  expect(parseParams(sp('clan=abc'))).toBeNull()
})
test('toSearch quay vòng được', () => {
  const p = { mode: 'focus' as const, clanId: null, side: 'both' as const, personId: 7, up: 2, down: 4 }
  expect(parseParams(new URLSearchParams(toSearch(p)))).toEqual(p)
})
