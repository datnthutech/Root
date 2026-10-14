import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthContext } from './AuthContext'
import { RequireAuth, hasRole } from './RequireAuth'
import type { CurrentUser } from '../api/auth'

const user = (role: CurrentUser['role']): CurrentUser => ({ id: 1, username: 'a', displayName: null, role, linkedPersonId: null })
const ctx = (u: CurrentUser | null, loading = false) => ({ user: u, loading, login: async () => {}, logout: async () => {} })

function renderAt(value: ReturnType<typeof ctx>, minRole?: CurrentUser['role']) {
  render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={['/secret']}>
        <Routes>
          <Route path="/login" element={<div>Trang đăng nhập</div>} />
          <Route path="/secret" element={<RequireAuth role={minRole}><div>Nội dung kín</div></RequireAuth>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

test('chưa đăng nhập chuyển sang /login', () => {
  renderAt(ctx(null))
  expect(screen.getByText('Trang đăng nhập')).toBeInTheDocument()
})
test('đang tải phiên thì chưa chuyển trang', () => {
  renderAt(ctx(null, true))
  expect(screen.queryByText('Trang đăng nhập')).not.toBeInTheDocument()
})
test('đủ quyền thì thấy nội dung', () => {
  renderAt(ctx(user('ADMIN')), 'EDITOR')
  expect(screen.getByText('Nội dung kín')).toBeInTheDocument()
})
test('thiếu quyền thì thấy thông báo không đủ quyền', () => {
  renderAt(ctx(user('VIEWER')), 'ADMIN')
  expect(screen.getByText('Bạn không có quyền truy cập trang này')).toBeInTheDocument()
})
test('hasRole theo thứ bậc', () => {
  expect(hasRole(user('ADMIN'), 'VIEWER')).toBe(true)
  expect(hasRole(user('VIEWER'), 'EDITOR')).toBe(false)
  expect(hasRole(null, 'VIEWER')).toBe(false)
})
