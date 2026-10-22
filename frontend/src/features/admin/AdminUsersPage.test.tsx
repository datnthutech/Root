import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import AdminUsersPage from './AdminUsersPage'
import { adminUsersApi } from '../../api/adminUsers'

vi.mock('../../api/adminUsers', () => ({ adminUsersApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), resetUserPassword: vi.fn() } }))

const wrap = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><AntApp><AdminUsersPage /></AntApp></QueryClientProvider>,
)
const fill = (username: string, password: string, again: string) => {
  // fireEvent.change thay vì gõ từng phím: modal antd render nặng, gõ từng phím dễ quá 5 giây
  fireEvent.change(screen.getByLabelText('Tên đăng nhập'), { target: { value: username } })
  fireEvent.change(screen.getByLabelText('Họ tên'), { target: { value: 'Nguyễn Văn A' } })
  fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: password } })
  fireEvent.change(screen.getByLabelText('Nhập lại mật khẩu'), { target: { value: again } })
}
const openCreate = async () => userEvent.click(await screen.findByRole('button', { name: 'Thêm tài khoản' }))

beforeEach(() => {
  vi.mocked(adminUsersApi.list).mockResolvedValue([])
  vi.mocked(adminUsersApi.create).mockReset()
})

test('tạo tài khoản mới gọi API với giá trị đã nhập', async () => {
  vi.mocked(adminUsersApi.create).mockResolvedValue({ id: 3, username: 'nguyenvana', displayName: 'Nguyễn Văn A', role: 'VIEWER', status: 'ACTIVE', linkedPersonId: null, createdAt: '2026-10-20T00:00:00Z' })
  wrap()
  await openCreate()
  fill('nguyenvana', 'password123', 'password123')
  await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
  await waitFor(() => expect(adminUsersApi.create).toHaveBeenCalledWith(expect.objectContaining({ username: 'nguyenvana', password: 'password123', displayName: 'Nguyễn Văn A', role: 'VIEWER', linkedPersonId: undefined })))
  expect(await screen.findByText('Đã tạo tài khoản')).toBeInTheDocument()
  await waitFor(() => expect(adminUsersApi.list).toHaveBeenLastCalledWith(undefined))
  expect(screen.getByText('Tất cả')).toBeInTheDocument()
})

test('nhập lại mật khẩu không khớp thì không gọi API', async () => {
  wrap()
  await openCreate()
  fill('nguyenvana', 'password123', 'khac-hoan-toan')
  await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
  expect(await screen.findByText('Mật khẩu nhập lại không khớp')).toBeInTheDocument()
  expect(adminUsersApi.create).not.toHaveBeenCalled()
})

test('tên đăng nhập có dấu tiếng Việt hoặc dấu cách thì bị chặn', async () => {
  wrap()
  await openCreate()
  fill('nguyễn a', 'password123', 'password123')
  await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
  expect(await screen.findByText('Chỉ dùng chữ không dấu, số và . _ -')).toBeInTheDocument()
  expect(adminUsersApi.create).not.toHaveBeenCalled()
})

test('trùng tên đăng nhập thì báo lỗi và modal vẫn mở', async () => {
  vi.mocked(adminUsersApi.create).mockRejectedValue({ code: 'USERNAME_TAKEN', message: 'Tên đăng nhập đã tồn tại', status: 409, details: null })
  wrap()
  await openCreate()
  fill('nguyenvana', 'password123', 'password123')
  await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
  expect(await screen.findByText('Tên đăng nhập đã tồn tại')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Tạo tài khoản' })).toBeInTheDocument()
})

test('lỗi validation từ server hiện ngay dưới ô tương ứng', async () => {
  vi.mocked(adminUsersApi.create).mockRejectedValue({ code: 'VALIDATION_FAILED', message: 'Dữ liệu không hợp lệ', status: 400, details: { username: 'Phải từ 3 đến 50 ký tự' } })
  wrap()
  await openCreate()
  fill('nguyenvana', 'password123', 'password123')
  await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
  await screen.findByText('Phải từ 3 đến 50 ký tự')
  const field = screen.getByLabelText('Tên đăng nhập').closest<HTMLElement>('.ant-form-item')!
  expect(within(field).getByText('Phải từ 3 đến 50 ký tự')).toBeInTheDocument()
})
