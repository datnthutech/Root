import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntApp } from 'antd'
import ChangePasswordModal from './ChangePasswordModal'
import { authApi } from '../api/auth'

vi.mock('../api/auth', () => ({ authApi: { changePassword: vi.fn() } }))

const wrap = (onClose = vi.fn(), onChanged = vi.fn()) => {
  render(<AntApp><ChangePasswordModal open onClose={onClose} onChanged={onChanged} /></AntApp>)
  return { onClose, onChanged }
}
const fill = async (cur: string, next: string, again: string) => {
  // fireEvent.change thay vì gõ từng phím: modal antd render nặng, gõ từng phím dễ quá 5 giây
  fireEvent.change(screen.getByLabelText('Mật khẩu hiện tại'), { target: { value: cur } })
  fireEvent.change(screen.getByLabelText('Mật khẩu mới'), { target: { value: next } })
  fireEvent.change(screen.getByLabelText('Nhập lại mật khẩu mới'), { target: { value: again } })
  await userEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))
}

beforeEach(() => { vi.mocked(authApi.changePassword).mockReset() })

test('đổi mật khẩu thành công thì gọi API và đóng modal', async () => {
  vi.mocked(authApi.changePassword).mockResolvedValue(undefined)
  const { onClose, onChanged } = wrap()
  await fill('password123', 'newpassword456', 'newpassword456')
  await waitFor(() => expect(authApi.changePassword).toHaveBeenCalledWith('password123', 'newpassword456'))
  await waitFor(() => expect(onClose).toHaveBeenCalled())
  await waitFor(() => expect(onChanged).toHaveBeenCalled())
  expect(await screen.findByText('Đã đổi mật khẩu, vui lòng đăng nhập lại')).toBeInTheDocument()
})

test('nhập lại không khớp hoặc quá ngắn thì không gọi API', async () => {
  wrap()
  await fill('password123', 'newpassword456', 'khac-hoan-toan')
  expect(await screen.findByText('Mật khẩu nhập lại không khớp')).toBeInTheDocument()
  expect(authApi.changePassword).not.toHaveBeenCalled()
})

test('mật khẩu mới dưới 8 ký tự bị chặn', async () => {
  wrap()
  await fill('password123', 'short', 'short')
  expect(await screen.findByText('Tối thiểu 8 ký tự')).toBeInTheDocument()
  expect(authApi.changePassword).not.toHaveBeenCalled()
})

test('lỗi từ server hiện qua errorText và không đóng modal', async () => {
  vi.mocked(authApi.changePassword).mockRejectedValue({ code: 'VALIDATION_FAILED', message: 'Mật khẩu hiện tại không đúng', status: 400, details: null })
  const { onClose, onChanged } = wrap()
  await fill('sai-sai-sai', 'newpassword456', 'newpassword456')
  expect(await screen.findByText('Mật khẩu hiện tại không đúng')).toBeInTheDocument()
  expect(onClose).not.toHaveBeenCalled()
  expect(onChanged).not.toHaveBeenCalled()
})
