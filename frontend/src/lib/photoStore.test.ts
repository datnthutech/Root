import { PhotoStore } from './photoStore'

let created = 0
beforeEach(() => {
  created = 0
  vi.stubGlobal('URL', { createObjectURL: vi.fn(() => `blob:${++created}`), revokeObjectURL: vi.fn() })
})
afterEach(() => vi.unstubAllGlobals())

const tick = () => new Promise((r) => setTimeout(r, 0))

test('trả null lúc đầu, tải xong gọi onLoaded và trả object URL', async () => {
  const onLoaded = vi.fn()
  const s = new PhotoStore(async () => new Blob(['x']), onLoaded)
  expect(s.get('/a')).toBeNull()
  await tick(); await tick()
  expect(onLoaded).toHaveBeenCalledWith('/a')
  expect(s.get('/a')).toBe('blob:1')
})
test('không tải trùng khi đang chờ', async () => {
  const fetchBlob = vi.fn(async () => new Blob(['x']))
  const s = new PhotoStore(fetchBlob, () => {})
  s.get('/a'); s.get('/a'); s.get('/a')
  await tick()
  expect(fetchBlob).toHaveBeenCalledTimes(1)
})
test('giới hạn số lượt tải đồng thời', async () => {
  let running = 0, peak = 0
  const fetchBlob = async () => { running++; peak = Math.max(peak, running); await tick(); running--; return new Blob(['x']) }
  const s = new PhotoStore(fetchBlob, () => {}, { concurrency: 2 })
  for (let i = 0; i < 6; i++) s.get(`/p${i}`)
  for (let i = 0; i < 10; i++) await tick()
  expect(peak).toBe(2)
  for (let i = 0; i < 6; i++) expect(s.get(`/p${i}`)).not.toBeNull()
})
test('ảnh lỗi/404 không bị tải lại mỗi lần get', async () => {
  const fetchBlob = vi.fn(async () => null)
  const s = new PhotoStore(fetchBlob, () => {})
  s.get('/missing'); await tick(); await tick()
  s.get('/missing'); s.get('/missing'); await tick()
  expect(fetchBlob).toHaveBeenCalledTimes(1)
})
test('vượt max thì bỏ ảnh cũ nhất và thu hồi object URL', async () => {
  const s = new PhotoStore(async () => new Blob(['x']), () => {}, { max: 2 })
  for (const u of ['/1', '/2', '/3']) { s.get(u); await tick(); await tick() }
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:1')
})

const load = async (s: PhotoStore, urls: string[]) => { for (const u of urls) { s.get(u); await tick(); await tick() } }

test('vượt max chỉ thu hồi ảnh cũ nhất', async () => {
  const s = new PhotoStore(async () => new Blob(['x']), () => {}, { max: 2 })
  await load(s, ['/1', '/2', '/3'])
  expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1)
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:1')
})
test('get làm mới độ gần đây (LRU)', async () => {
  const s = new PhotoStore(async () => new Blob(['x']), () => {}, { max: 2 })
  await load(s, ['/1', '/2'])
  expect(s.get('/1')).toBe('blob:1')
  await load(s, ['/3'])
  expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1)
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:2')
})
test('destroy thu hồi mọi object URL', async () => {
  const s = new PhotoStore(async () => new Blob(['x']), () => {})
  await load(s, ['/1', '/2', '/3'])
  s.destroy()
  expect(URL.revokeObjectURL).toHaveBeenCalledTimes(3)
  for (const n of [1, 2, 3]) expect(URL.revokeObjectURL).toHaveBeenCalledWith(`blob:${n}`)
  expect(s.get('/1')).toBeNull()
})
test('tải dở dang xong sau destroy thì không tạo object URL, không onLoaded', async () => {
  let resolve!: (b: Blob) => void
  const onLoaded = vi.fn()
  const s = new PhotoStore(() => new Promise<Blob>((r) => { resolve = r }), onLoaded)
  s.get('/a')
  await tick()
  s.destroy()
  resolve(new Blob(['x']))
  await tick(); await tick()
  expect(URL.createObjectURL).not.toHaveBeenCalled()
  expect(onLoaded).not.toHaveBeenCalled()
  expect(s.get('/a')).toBeNull()
})
test('get sau destroy không xếp hàng tải', async () => {
  const fetchBlob = vi.fn(async () => new Blob(['x']))
  const s = new PhotoStore(fetchBlob, () => {})
  s.destroy()
  expect(s.get('/a')).toBeNull()
  await tick()
  expect(fetchBlob).not.toHaveBeenCalled()
})
