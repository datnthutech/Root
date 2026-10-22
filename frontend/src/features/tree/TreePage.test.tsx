import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { AuthContext } from '../../auth/AuthContext'
import TreePage from './TreePage'
import * as treeApi from '../../api/tree'

vi.mock('./TreeCanvas', () => ({ TreeCanvas: () => <div data-testid="canvas" /> }))
vi.mock('./useTreeData', () => ({ useTreeData: () => ({ graph: null, layout: null, loading: false, error: null, collapsed: new Set(), toggle: () => {}, keepCamera: false, refresh: () => {} }) }))
vi.mock('../../api/tree', async (orig) => ({ ...(await orig<typeof import('../../api/tree')>()), listClans: vi.fn(), searchPersons: vi.fn(async () => []) }))

const Where = () => <div data-testid="where">{useLocation().search}</div>
const user = (linkedPersonId: number | null) => ({ id: 1, username: 'a', displayName: null, role: 'VIEWER' as const, linkedPersonId })

function setup(u: ReturnType<typeof user>, url = '/tree') {
  render(
    <AuthContext.Provider value={{ user: u, loading: false, login: async () => {}, logout: async () => {} }}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter initialEntries={[url]}><TreePage /><Where /></MemoryRouter>
      </QueryClientProvider>
    </AuthContext.Provider>,
  )
}

test('người dùng có linkedPersonId mở sẵn chế độ tập trung quanh chính mình', async () => {
  vi.mocked(treeApi.listClans).mockResolvedValue([])
  setup(user(42))
  await waitFor(() => expect(screen.getByTestId('where').textContent).toContain('mode=focus'))
  expect(screen.getByTestId('where').textContent).toContain('person=42')
})

test('không có linkedPersonId thì vào dòng họ đầu tiên', async () => {
  vi.mocked(treeApi.listClans).mockResolvedValue([{ id: 7, name: 'Họ A', founderPersonId: 1, memberCount: 10 }, { id: 8, name: 'Họ B', founderPersonId: 2, memberCount: 5 }])
  setup(user(null))
  await waitFor(() => expect(screen.getByTestId('where').textContent).toContain('clan=7'))
})

test('chưa có dòng họ nào thì hiện thông báo trống', async () => {
  vi.mocked(treeApi.listClans).mockResolvedValue([])
  setup(user(null))
  expect(await screen.findByText(/Chưa có dòng họ nào/)).toBeInTheDocument()
})

test('URL hợp lệ được giữ nguyên', async () => {
  vi.mocked(treeApi.listClans).mockResolvedValue([{ id: 7, name: 'Họ A', founderPersonId: 1, memberCount: 10 }])
  setup(user(42), '/tree?mode=clan&clan=7&side=maternal')
  expect(await screen.findByTestId('canvas')).toBeInTheDocument()
  expect(screen.getByTestId('where').textContent).toContain('side=maternal')
})
