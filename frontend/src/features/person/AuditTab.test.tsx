import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import AuditTab from './AuditTab'
import { auditApi, type AuditEntry } from '../../api/audit'

vi.mock('../../api/audit', async (orig) => ({ ...(await orig<typeof import('../../api/audit')>()), auditApi: { list: vi.fn(), revert: vi.fn() } }))

const entry = (id: number, over: Partial<AuditEntry> = {}): AuditEntry => ({
  id, userId: 1, username: 'editor1', displayName: 'Biên Tập', entityType: 'PERSON', entityId: 5, action: 'UPDATE',
  createdAt: '2026-10-14T01:00:00Z', summary: `Sửa hồ sơ «Tên ${id}»`, changes: [], revertible: true, ...over,
})

function setup(canRevert: boolean, onReverted = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AntApp><AuditTab personId={5} canRevert={canRevert} onReverted={onReverted} /></AntApp>
    </QueryClientProvider>,
  )
  return onReverted
}

test('hiện người sửa, mô tả và bảng thay đổi', async () => {
  vi.mocked(auditApi.list).mockResolvedValue([entry(2, { changes: [{ field: 'fullName', label: 'Họ tên', from: 'A', to: 'B' }] })])
  setup(false)
  expect(await screen.findByText('Sửa hồ sơ «Tên 2»')).toBeInTheDocument()
  expect(screen.getByText(/Biên Tập/)).toBeInTheDocument()
  expect(screen.getByText('Họ tên')).toBeInTheDocument()
  expect(screen.getByText('A')).toBeInTheDocument()
  expect(screen.getByText('B')).toBeInTheDocument()
})

test('không phải ADMIN thì không có nút Khôi phục', async () => {
  vi.mocked(auditApi.list).mockResolvedValue([entry(2)])
  setup(false)
  await screen.findByText('Sửa hồ sơ «Tên 2»')
  expect(screen.queryByRole('button', { name: 'Khôi phục' })).not.toBeInTheDocument()
})

test('ADMIN chỉ thấy nút Khôi phục ở mục revertible', async () => {
  vi.mocked(auditApi.list).mockResolvedValue([entry(3, { revertible: false, action: 'SET_PHOTO', summary: 'Đổi ảnh «X»' }), entry(2)])
  setup(true)
  await screen.findByText('Đổi ảnh «X»')
  expect(screen.getAllByRole('button', { name: 'Khôi phục' })).toHaveLength(1)
})

test('khôi phục gọi API, báo cho cha và làm mới', async () => {
  vi.mocked(auditApi.list).mockResolvedValue([entry(2)])
  vi.mocked(auditApi.revert).mockResolvedValue(undefined)
  const onReverted = setup(true)
  await userEvent.click(await screen.findByRole('button', { name: 'Khôi phục' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Khôi phục bản này' }))
  await waitFor(() => expect(auditApi.revert).toHaveBeenCalledWith(2))
  expect(onReverted).toHaveBeenCalled()
})

test('lỗi khôi phục hiện thông báo tiếng Việt', async () => {
  vi.mocked(auditApi.list).mockResolvedValue([entry(2)])
  vi.mocked(auditApi.revert).mockRejectedValue({ code: 'RELATION_CYCLE', status: 409 })
  const onReverted = setup(true)
  await userEvent.click(await screen.findByRole('button', { name: 'Khôi phục' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Khôi phục bản này' }))
  expect(await screen.findByText('Quan hệ này tạo vòng lặp trong gia phả')).toBeInTheDocument()
  expect(onReverted).not.toHaveBeenCalled()
})

test('đủ một trang thì có nút Tải thêm và gọi với beforeId', async () => {
  const page = Array.from({ length: 30 }, (_, i) => entry(100 - i))
  vi.mocked(auditApi.list).mockResolvedValueOnce(page).mockResolvedValueOnce([entry(10)])
  setup(false)
  await screen.findByText('Sửa hồ sơ «Tên 100»')
  await userEvent.click(screen.getByRole('button', { name: 'Tải thêm' }))
  await waitFor(() => expect(auditApi.list).toHaveBeenLastCalledWith(5, 71, 30))
})

test('chưa có lịch sử thì hiện trạng thái trống', async () => {
  vi.mocked(auditApi.list).mockResolvedValue([])
  setup(false)
  expect(await screen.findByText('Chưa có lịch sử sửa')).toBeInTheDocument()
})
