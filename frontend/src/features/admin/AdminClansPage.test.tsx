import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntApp } from 'antd'
import AdminClansPage from './AdminClansPage'
import { adminClansApi } from '../../api/adminClans'

vi.mock('../../api/adminClans', () => ({ adminClansApi: { list: vi.fn(), create: vi.fn(), update: vi.fn() } }))
vi.mock('../tree/PersonSearch', () => ({ PersonSearch: ({ onPick }: { onPick(id: number): void }) => <button type="button" onClick={() => onPick(42)}>chọn-thuỷ-tổ</button> }))

const wrap = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><AntApp><AdminClansPage /></AntApp></QueryClientProvider>,
)

test('liệt kê dòng họ', async () => {
  vi.mocked(adminClansApi.list).mockResolvedValue([{ id: 1, name: 'Họ Nguyễn', founderPersonId: 5, description: null, siblingCountMode: 'PER_UNION', useUtLabel: true, memberCount: 120 }])
  wrap()
  expect(await screen.findByText('Họ Nguyễn')).toBeInTheDocument()
  expect(screen.getByText('120')).toBeInTheDocument()
})

test('tạo dòng họ mới với thủy tổ chọn từ tìm kiếm', async () => {
  vi.mocked(adminClansApi.list).mockResolvedValue([])
  vi.mocked(adminClansApi.create).mockResolvedValue({ id: 2, name: 'Họ Trần', founderPersonId: 42, description: null, siblingCountMode: 'PER_UNION', useUtLabel: true, memberCount: 1 })
  wrap()
  await userEvent.click(await screen.findByRole('button', { name: 'Thêm dòng họ' }))
  await userEvent.type(screen.getByLabelText('Tên dòng họ'), 'Họ Trần')
  await userEvent.click(screen.getByRole('button', { name: 'chọn-thuỷ-tổ' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Lưu' }))
  await waitFor(() => expect(adminClansApi.create).toHaveBeenCalledWith(expect.objectContaining({ name: 'Họ Trần', founderPersonId: 42 })))
})

test('sửa dòng họ gọi update với id', async () => {
  vi.mocked(adminClansApi.list).mockResolvedValue([{ id: 1, name: 'Họ Nguyễn', founderPersonId: 5, description: null, siblingCountMode: 'PER_UNION', useUtLabel: true, memberCount: 120 }])
  vi.mocked(adminClansApi.update).mockResolvedValue({ id: 1, name: 'Họ Nguyễn', founderPersonId: 5, description: null, siblingCountMode: 'PER_UNION', useUtLabel: true, memberCount: 120 })
  wrap()
  await userEvent.click(await screen.findByRole('button', { name: 'Sửa' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Lưu' }))
  await waitFor(() => expect(adminClansApi.update).toHaveBeenCalledWith(1, expect.objectContaining({ name: 'Họ Nguyễn', founderPersonId: 5 })))
})
