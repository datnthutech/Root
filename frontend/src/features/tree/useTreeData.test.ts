import { renderHook, waitFor } from '@testing-library/react'
import { useTreeData, type TreeParams } from './useTreeData'
import * as tree from '../../api/tree'
import * as client from './layoutClient'

vi.mock('../../api/tree', () => ({ loadClanGraph: vi.fn(), loadFocusGraph: vi.fn() }))
vi.mock('./layoutClient', () => ({ runLayout: vi.fn() }))
vi.mock('../../lib/layoutCache', () => ({
  getCachedLayout: vi.fn(async () => undefined),
  setCachedLayout: vi.fn(async () => {}),
  layoutCacheKey: (...a: unknown[]) => JSON.stringify(a),
}))

const params = (clanId: number): TreeParams => ({ mode: 'clan', clanId, side: 'all' as never, personId: null, up: 2, down: 2 })
const g = (clanId: number) => ({ clanId, rootPersonId: 1, persons: [], unions: [], links: [] }) as never
const lay = (tag: string) => ({ tag }) as never

beforeEach(() => vi.clearAllMocks())

test('layout lỗi: hiện error và tắt loading', async () => {
  vi.mocked(tree.loadClanGraph).mockResolvedValue({ graph: g(1), etag: 'e' } as never)
  vi.mocked(client.runLayout).mockRejectedValue(new Error('boom'))
  const { result } = renderHook(() => useTreeData(params(1)))
  await waitFor(() => expect(result.current.error?.code).toBe('LAYOUT_FAILED'))
  expect(result.current.loading).toBe(false)
})

test('graph và layout luôn là cặp nhất quán', async () => {
  const g1 = g(1), g2 = g(2)
  vi.mocked(tree.loadClanGraph).mockResolvedValueOnce({ graph: g1, etag: 'a' } as never).mockResolvedValueOnce({ graph: g2, etag: 'b' } as never)
  let release!: () => void
  vi.mocked(client.runLayout).mockResolvedValueOnce(lay('L1')).mockImplementationOnce(() => new Promise((res) => { release = () => res(lay('L2')) }))
  const seen: [unknown, unknown][] = []
  const { result, rerender } = renderHook((p: TreeParams) => { const r = useTreeData(p); seen.push([r.graph, r.layout]); return r }, { initialProps: params(1) })
  await waitFor(() => expect(result.current.layout).toEqual(lay('L1')))
  rerender(params(2))
  await waitFor(() => expect(client.runLayout).toHaveBeenCalledTimes(2))
  release()
  await waitFor(() => expect(result.current.layout).toEqual(lay('L2')))
  for (const [gr, l] of seen) {
    if (gr === g2) expect(l).toEqual(lay('L2'))
    if (l && (l as never as { tag: string }).tag === 'L1') expect(gr).toBe(g1)
  }
})
