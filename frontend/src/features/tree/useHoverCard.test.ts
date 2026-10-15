import { act, renderHook } from '@testing-library/react'
import { useHoverCard } from './useHoverCard'

beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())

test('chỉ hiện thẻ sau 300 ms', () => {
  const { result } = renderHook(() => useHoverCard(300, true))
  act(() => result.current.onHover(5, { x: 10, y: 20 }))
  act(() => { vi.advanceTimersByTime(299) })
  expect(result.current.card).toBeNull()
  act(() => { vi.advanceTimersByTime(2) })
  expect(result.current.card).toEqual({ id: 5, x: 10, y: 20 })
})
test('rời nút trước 300 ms thì không hiện', () => {
  const { result } = renderHook(() => useHoverCard(300, true))
  act(() => result.current.onHover(5, { x: 0, y: 0 }))
  act(() => { vi.advanceTimersByTime(200) })
  act(() => result.current.onHover(null, { x: 0, y: 0 }))
  act(() => { vi.advanceTimersByTime(500) })
  expect(result.current.card).toBeNull()
})
test('chuyển sang nút khác thì tính lại 300 ms và ẩn thẻ cũ', () => {
  const { result } = renderHook(() => useHoverCard(300, true))
  act(() => result.current.onHover(1, { x: 0, y: 0 }))
  act(() => { vi.advanceTimersByTime(400) })
  expect(result.current.card?.id).toBe(1)
  act(() => result.current.onHover(2, { x: 5, y: 5 }))
  expect(result.current.card).toBeNull()
  act(() => { vi.advanceTimersByTime(301) })
  expect(result.current.card?.id).toBe(2)
})
test('enabled=false (thiết bị cảm ứng) thì không bao giờ hiện', () => {
  const { result } = renderHook(() => useHoverCard(300, false))
  act(() => result.current.onHover(1, { x: 0, y: 0 }))
  act(() => { vi.advanceTimersByTime(1000) })
  expect(result.current.card).toBeNull()
})
test('hide() ẩn thẻ đang hiện', () => {
  const { result } = renderHook(() => useHoverCard(300, true))
  act(() => result.current.onHover(1, { x: 0, y: 0 }))
  act(() => { vi.advanceTimersByTime(400) })
  act(() => result.current.hide())
  expect(result.current.card).toBeNull()
})
