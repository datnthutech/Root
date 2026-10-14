import 'fake-indexeddb/auto'
import { getCachedLayout, setCachedLayout, layoutCacheKey } from './layoutCache'

const r = { nodes: [{ id: 1, x: 0, y: 0, w: 120, h: 60, generation: 1 }], edges: [], width: 120, height: 60 }

test('ghi rồi đọc lại đúng kết quả; khoá khác ETag thì không thấy', async () => {
  const k1 = layoutCacheKey(1, 'etag-a', 'both', [])
  const k2 = layoutCacheKey(1, 'etag-b', 'both', [])
  await setCachedLayout(k1, r)
  expect(await getCachedLayout(k1)).toEqual(r)
  expect(await getCachedLayout(k2)).toBeUndefined()
})

test('khoá phụ thuộc vào tập thu gọn (không phụ thuộc thứ tự)', () => {
  expect(layoutCacheKey(1, 'e', 'both', [3, 2])).toBe(layoutCacheKey(1, 'e', 'both', [2, 3]))
  expect(layoutCacheKey(1, 'e', 'both', [2])).not.toBe(layoutCacheKey(1, 'e', 'both', []))
})
