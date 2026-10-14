import 'fake-indexeddb/auto'
import { loadClanGraph } from './tree'
import { setAccessToken } from './http'
import type { GraphData } from '../lib/types'

const graph: GraphData = { clanId: 1, rootPersonId: 1, siblingCountMode: 'PER_UNION', persons: [], unions: [], links: [] }

afterEach(() => { vi.unstubAllGlobals(); setAccessToken(null) })

test('lần 2 gửi If-None-Match và dùng bản lưu khi nhận 304', async () => {
  const seen: (string | null)[] = []
  const fetchMock = vi.fn(async (_url: string, init: RequestInit) => {
    const h = new Headers(init.headers)
    seen.push(h.get('If-None-Match'))
    return seen.length === 1
      ? new Response(JSON.stringify(graph), { status: 200, headers: { ETag: '"abc"' } })
      : new Response(null, { status: 304 })
  })
  vi.stubGlobal('fetch', fetchMock)
  setAccessToken('tok')

  const first = await loadClanGraph(1, 'both')
  expect(first.etag).toBe('"abc"')
  const second = await loadClanGraph(1, 'both')
  expect(seen).toEqual([null, '"abc"'])
  expect(second.graph).toEqual(graph)
  expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Authorization')).toBe('Bearer tok')
})

test('nhận 304 nhưng không có bản lưu thì tải lại không điều kiện', async () => {
  let n = 0
  vi.stubGlobal('fetch', vi.fn(async () => (++n === 1 ? new Response(null, { status: 304 }) : new Response(JSON.stringify(graph), { status: 200, headers: { ETag: '"x"' } }))))
  const r = await loadClanGraph(77, 'paternal')
  expect(r.graph).toEqual(graph)
  expect(n).toBe(2)
})

test('lỗi HTTP ném ApiFailure', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response('{"code":"NOT_FOUND","message":"x","details":null}', { status: 404 })))
  await expect(loadClanGraph(404, 'both')).rejects.toMatchObject({ code: 'NOT_FOUND', status: 404 })
})
