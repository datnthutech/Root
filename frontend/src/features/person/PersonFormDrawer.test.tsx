import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import PersonFormDrawer from './PersonFormDrawer'
import { editApi } from '../../api/edit'
import { personApi, type PersonProfile } from '../../api/person'

vi.mock('../../api/edit', async (orig) => ({ ...(await orig<typeof import('../../api/edit')>()), editApi: { createPerson: vi.fn(), updatePerson: vi.fn() } }))

const profile = (over: Partial<PersonProfile> = {}): PersonProfile => ({
  id: 5, fullName: 'Nguyễn Cũ', gender: 'M', alive: true, version: 3, createdAt: '2026-01-01T00:00:00Z', canEdit: true, ...over,
})

function setup(props: Partial<React.ComponentProps<typeof PersonFormDrawer>> = {}) {
  const onSaved = vi.fn(), onClose = vi.fn()
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><AntApp><PersonFormDrawer open mode="create" onClose={onClose} onSaved={onSaved} {...props} /></AntApp></QueryClientProvider>)
  return { onSaved, onClose }
}

beforeEach(() => {
  vi.mocked(editApi.createPerson).mockReset()
  vi.mocked(editApi.updatePerson).mockReset()
  vi.spyOn(personApi, 'biography').mockResolvedValue({ personId: 5, biography: null })
})

test('tạo người mới gửi đúng dữ liệu và gọi onSaved', async () => {
  vi.mocked(editApi.createPerson).mockResolvedValue(profile({ id: 9, fullName: 'Trần Mới' }))
  const { onSaved } = setup()
  await userEvent.type(screen.getByLabelText('Họ và tên'), 'Trần Mới')
  await userEvent.click(screen.getByRole('radio', { name: 'Nữ' }))
  await userEvent.click(screen.getByRole('button', { name: 'Lưu' }))
  await waitFor(() => expect(onSaved).toHaveBeenCalled())
  expect(editApi.createPerson).toHaveBeenCalledWith(expect.objectContaining({ fullName: 'Trần Mới', gender: 'F', alive: true }))
  expect(vi.mocked(editApi.createPerson).mock.calls[0][0]).not.toHaveProperty('version')
})

test('thiếu họ tên thì không gọi API', async () => {
  setup()
  await userEvent.click(screen.getByRole('button', { name: 'Lưu' }))
  expect(await screen.findByText('Nhập họ và tên')).toBeInTheDocument()
  expect(editApi.createPerson).not.toHaveBeenCalled()
})

test('sửa gửi kèm version hiện tại', async () => {
  vi.mocked(editApi.updatePerson).mockResolvedValue(profile({ version: 4 }))
  const { onSaved } = setup({ mode: 'edit', person: profile() })
  const name = await screen.findByLabelText('Họ và tên')
  await userEvent.clear(name)
  await userEvent.type(name, 'Nguyễn Mới')
  await userEvent.click(screen.getByRole('button', { name: 'Lưu' }))
  await waitFor(() => expect(onSaved).toHaveBeenCalled())
  expect(editApi.updatePerson).toHaveBeenCalledWith(5, expect.objectContaining({ fullName: 'Nguyễn Mới', version: 3 }))
})

test('xung đột phiên bản hiện thông báo và không đóng form', async () => {
  vi.mocked(editApi.updatePerson).mockRejectedValue({ code: 'VERSION_CONFLICT', status: 409, message: '', details: null })
  const { onSaved } = setup({ mode: 'edit', person: profile() })
  await userEvent.click(await screen.findByRole('button', { name: 'Lưu' }))
  expect(await screen.findByText(/người khác sửa/)).toBeInTheDocument()
  expect(onSaved).not.toHaveBeenCalled()
})

test('bấm Lưu liên tiếp chỉ gửi một lần', async () => {
  let resolve!: (v: PersonProfile) => void
  vi.mocked(editApi.createPerson).mockReturnValue(new Promise((r) => { resolve = r }))
  setup()
  await userEvent.type(screen.getByLabelText('Họ và tên'), 'Một Người')
  const save = screen.getByRole('button', { name: 'Lưu' })
  await userEvent.click(save)
  await userEvent.click(save)
  expect(editApi.createPerson).toHaveBeenCalledTimes(1)
  resolve(profile())
})
