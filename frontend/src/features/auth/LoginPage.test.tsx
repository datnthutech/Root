import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthContext } from '../../auth/AuthContext'
import LoginPage from './LoginPage'

function setup(login: (u: string, p: string) => Promise<void>) {
  render(
    <AuthContext.Provider value={{ user: null, loading: false, login, logout: async () => {} }}>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<div>Trang chủ</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

test('đăng nhập thành công chuyển về trang chủ', async () => {
  const login = vi.fn().mockResolvedValue(undefined)
  setup(login)
  await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'an')
  await userEvent.type(screen.getByLabelText('Mật khẩu'), 'password123')
  await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))
  await waitFor(() => expect(screen.getByText('Trang chủ')).toBeInTheDocument())
  expect(login).toHaveBeenCalledWith('an', 'password123')
})

test('sai mật khẩu hiện thông báo tiếng Việt', async () => {
  setup(vi.fn().mockRejectedValue({ code: 'BAD_CREDENTIALS', status: 401 }))
  await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'an')
  await userEvent.type(screen.getByLabelText('Mật khẩu'), 'sai-sai-sai')
  await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))
  expect(await screen.findByText('Sai tên đăng nhập hoặc mật khẩu')).toBeInTheDocument()
})

test('tài khoản chờ duyệt hiện đúng thông báo', async () => {
  setup(vi.fn().mockRejectedValue({ code: 'ACCOUNT_PENDING', status: 403 }))
  await userEvent.type(screen.getByLabelText('Tên đăng nhập'), 'an')
  await userEvent.type(screen.getByLabelText('Mật khẩu'), 'password123')
  await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))
  expect(await screen.findByText('Tài khoản đang chờ quản trị viên duyệt')).toBeInTheDocument()
})
