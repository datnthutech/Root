import { render, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AuthContext } from '../../auth/AuthContext'
import TreePage from './TreePage'

const focusOn = vi.fn()
vi.mock('./TreeCanvas', async () => {
  const React = await import('react')
  return { TreeCanvas: React.forwardRef((_p: unknown, ref: React.Ref<unknown>) => { React.useImperativeHandle(ref, () => ({ focusOn, fit() {}, moveCenterTo() {} })); return <div data-testid="canvas" /> }) }
})
vi.mock('./TreeToolbar', () => ({ TreeToolbar: () => null }))
const graph = { persons: [{ id: 5 }] }
const mk = (keepCamera: boolean) => ({ graph, layout: { nodes: [{ id: 5 }], edges: [], width: 1, height: 1 }, loading: false, error: null, collapsed: new Set(), toggle: () => {}, keepCamera })
let state = mk(false)
vi.mock('./useTreeData', () => ({ useTreeData: () => state }))
vi.mock('../../api/tree', async (orig) => ({ ...(await orig<typeof import('../../api/tree')>()), listClans: vi.fn(async () => []) }))

const ui = () => (
  <AuthContext.Provider value={{ user: { id: 1, username: 'a', displayName: null, role: 'VIEWER', linkedPersonId: null }, loading: false, login: async () => {}, logout: async () => {} }}>
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={['/tree?mode=focus&person=5&up=3&down=3']}><TreePage /></MemoryRouter>
    </QueryClientProvider>
  </AuthContext.Provider>
)

test('bố cục mới từ nguồn mới (keepCamera=false) đưa camera tới người trung tâm; đổi bố cục do +/- (keepCamera=true) thì không', async () => {
  focusOn.mockClear(); state = mk(false)
  const { rerender } = render(ui())
  await waitFor(() => expect(focusOn).toHaveBeenCalledWith(5, 1))
  focusOn.mockClear()
  state = mk(true) // layout mới sau khi bấm +/-
  rerender(ui())
  await new Promise((r) => setTimeout(r, 50))
  expect(focusOn).not.toHaveBeenCalled()
})
