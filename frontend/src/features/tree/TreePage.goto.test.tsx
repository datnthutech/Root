import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { AuthContext } from '../../auth/AuthContext'
import TreePage from './TreePage'

const focusOn = vi.fn()
vi.mock('./TreeCanvas', async () => {
  const React = await import('react')
  return { TreeCanvas: React.forwardRef((_p: unknown, ref: React.Ref<unknown>) => { React.useImperativeHandle(ref, () => ({ focusOn, fit() {}, moveCenterTo() {} })); return <div data-testid="canvas" /> }) }
})
vi.mock('./TreeToolbar', () => ({ TreeToolbar: ({ onPick }: { onPick(id: number): void }) => (<><button onClick={() => onPick(5)}>pick5</button><button onClick={() => onPick(6)}>pick6</button></>) }))
const graph = { persons: [{ id: 5 }, { id: 6 }] }
const layout = { nodes: [{ id: 5 }], edges: [], width: 1, height: 1 }
vi.mock('./useTreeData', () => ({ useTreeData: () => ({ graph, layout, loading: false, error: null, collapsed: new Set(), toggle: () => {}, keepCamera: false, refresh: () => {} }) }))
vi.mock('../../api/tree', async (orig) => ({ ...(await orig<typeof import('../../api/tree')>()), listClans: vi.fn(async () => []) }))

const Where = () => <div data-testid="where">{useLocation().search}</div>

function setup() {
  render(
    <AuthContext.Provider value={{ user: { id: 1, username: 'a', displayName: null, role: 'VIEWER', linkedPersonId: null }, loading: false, login: async () => {}, logout: async () => {} }}>
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={['/tree?mode=clan&clan=7&side=both']}><TreePage /><Where /></MemoryRouter>
      </QueryClientProvider>
    </AuthContext.Provider>,
  )
}

test('người đang hiện trong bố cục: bay camera tới, giữ nguyên URL', () => {
  setup(); fireEvent.click(screen.getByText('pick5'))
  expect(focusOn).toHaveBeenCalledWith(5, 1)
  expect(screen.getByTestId('where').textContent).toContain('mode=clan')
})

test('người nằm dưới nhánh thu gọn (có trong graph, không có trong layout): chuyển sang chế độ tập trung', async () => {
  focusOn.mockClear(); setup(); fireEvent.click(screen.getByText('pick6'))
  await waitFor(() => expect(screen.getByTestId('where').textContent).toContain('mode=focus'))
  expect(screen.getByTestId('where').textContent).toContain('person=6')
  expect(focusOn).not.toHaveBeenCalledWith(6, 1)
})
