import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntApp } from 'antd'
import { AuthContext } from '../../auth/AuthContext'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import PersonDialog from './PersonDialog'
import { personApi } from '../../api/person'
import { editApi } from '../../api/edit'
import { auditApi } from '../../api/audit'

vi.mock('../../api/edit', async (orig) => ({ ...(await orig<typeof import('../../api/edit')>()), editApi: { deletePerson: vi.fn() } }))
vi.mock('../../api/audit', async (orig) => ({ ...(await orig<typeof import('../../api/audit')>()), auditApi: { list: vi.fn(), revert: vi.fn() } }))
vi.mock('../../api/person', async (orig) => ({ ...(await orig<typeof import('../../api/person')>()), personApi: { profile: vi.fn(), family: vi.fn(), biography: vi.fn() } }))

const ref = (id: number, name: string, orderLabel: string | null = null) =>
  ({ id, name, gender: 'M' as const, birthYear: 1950, deathYear: null, alive: true, orderLabel, linkId: null })

beforeEach(() => {
  vi.mocked(auditApi.list).mockReset()
  vi.mocked(auditApi.list).mockResolvedValue([{ id: 2, userId: 1, username: 'e', displayName: 'Biên Tập', entityType: 'PERSON', entityId: 5, action: 'UPDATE', createdAt: '2026-10-14T01:00:00Z', summary: 'Sửa hồ sơ «An»', changes: [], revertible: true }])
  vi.mocked(personApi.profile).mockResolvedValue({
    id: 5, fullName: 'Nguyễn Văn An', gender: 'M', birthDate: '1925-03-04', birthPrecision: 'DAY', birthYear: 1925, alive: false,
    deathDate: '1990-01-01', deathPrecision: 'YEAR', deathYear: 1990, deathAnniversaryLunar: '12/3 âm lịch', originPlace: 'Bến Tre',
    generation: 2, orderLabel: 'anh Hai', orderNeedsConfirm: true, version: 0, createdAt: '2026-01-01T00:00:00Z', canEdit: false,
  })
  vi.mocked(personApi.family).mockResolvedValue({
    personId: 5, parents: [ref(1, 'Cha An')], spouses: [{ unionId: 1, unionOrder: 1, status: 'MARRIED', partner: ref(6, 'Vợ An'), children: [ref(7, 'Con An', 'anh Hai')] }],
    otherChildren: [], fullSiblings: [ref(8, 'Em An', 'chị Ba')], halfSiblingsSameFather: [ref(9, 'Em khác mẹ')], halfSiblingsSameMother: [],
  })
  vi.mocked(personApi.biography).mockResolvedValue({ personId: 5, biography: 'Ông **An** làm ruộng\n- Con 1' })
})

function setup(onNavigate = vi.fn(), onClose = vi.fn(), role: 'VIEWER' | 'EDITOR' | 'ADMIN' = 'VIEWER') {
  render(
    <AuthContext.Provider value={{ user: { id: 1, username: 'v', displayName: null, role, linkedPersonId: null }, loading: false, login: async () => {}, logout: async () => {} }}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AntApp><PersonDialog personId={5} onClose={onClose} onNavigate={onNavigate} onTreeChanged={() => {}} /></AntApp>
      </QueryClientProvider>
    </AuthContext.Provider>,
  )
  return { onNavigate, onClose }
}

test('tab Hồ sơ hiện ngày theo độ chính xác, đời và thứ', async () => {
  setup()
  expect(await screen.findByText('Nguyễn Văn An')).toBeInTheDocument()
  expect(screen.getByText('04/03/1925')).toBeInTheDocument()
  expect(screen.getByText('1990')).toBeInTheDocument()
  expect(screen.getByText('12/3 âm lịch')).toBeInTheDocument()
  expect(screen.getByText(/anh Hai/)).toBeInTheDocument()
  expect(screen.getByText(/cần xác nhận/)).toBeInTheDocument()
})

test('tab Gia đình liệt kê và bấm tên thì điều hướng', async () => {
  const { onNavigate } = setup()
  await screen.findByText('Nguyễn Văn An')
  await userEvent.click(screen.getByRole('tab', { name: 'Gia đình' }))
  expect(await screen.findByText(/Vợ An/)).toBeInTheDocument()
  expect(screen.getByText(/chị Ba/)).toBeInTheDocument()
  expect(screen.getByText(/Em khác mẹ/)).toBeInTheDocument()
  await userEvent.click(screen.getByText(/Con An/))
  expect(onNavigate).toHaveBeenCalledWith(7)
})

test('tab Sơ yếu lý lịch render định dạng cơ bản', async () => {
  setup()
  await screen.findByText('Nguyễn Văn An')
  await userEvent.click(screen.getByRole('tab', { name: 'Sơ yếu lý lịch' }))
  await waitFor(() => expect(document.querySelector('strong')?.textContent).toBe('An'))
  expect(document.querySelectorAll('li')).toHaveLength(1)
})

test('VIEWER người còn sống: chỉ có năm sinh', async () => {
  vi.mocked(personApi.profile).mockResolvedValue({
    id: 5, fullName: 'Người Sống', gender: 'F', birthYear: 1953, alive: true, version: 0, createdAt: '2026-01-01T00:00:00Z', canEdit: false,
  })
  setup()
  expect(await screen.findByText('Người Sống')).toBeInTheDocument()
  expect(screen.getByText('1953 (chỉ hiện năm)')).toBeInTheDocument()
})

test('VIEWER không thấy nút sửa, xoá, đổi ảnh, thứ tự hay thêm quan hệ', async () => {
  setup()
  await screen.findByText('Nguyễn Văn An')
  expect(screen.queryByRole('button', { name: 'Sửa hồ sơ' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Đổi ảnh/ })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Xoá' })).not.toBeInTheDocument()
  expect(screen.queryByText(/Đặt thứ tự thủ công/)).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('tab', { name: 'Gia đình' }))
  await screen.findByText(/Vợ An/)
  expect(screen.queryByRole('button', { name: 'Thêm con' })).not.toBeInTheDocument()
})

test('EDITOR không sở hữu (canEdit=false) chỉ có thứ tự và quan hệ, không có sửa/ảnh/xoá', async () => {
  setup(vi.fn(), vi.fn(), 'EDITOR')
  await screen.findByText('Nguyễn Văn An')
  expect(screen.queryByRole('button', { name: 'Sửa hồ sơ' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Xoá' })).not.toBeInTheDocument()
  expect(screen.getByText(/Đặt thứ tự thủ công/)).toBeInTheDocument()
})

test('ADMIN xoá người: gọi deletePerson, đóng popup và báo cây làm mới', async () => {
  vi.mocked(personApi.profile).mockResolvedValue({
    id: 5, fullName: 'Nguyễn Văn An', gender: 'M', alive: true, version: 0, createdAt: '2026-01-01T00:00:00Z', canEdit: true,
  })
  vi.mocked(editApi.deletePerson).mockResolvedValue(undefined)
  const { onClose } = setup(vi.fn(), vi.fn(), 'ADMIN')
  expect(await screen.findByRole('button', { name: 'Sửa hồ sơ' })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Xoá' }))
  await waitFor(() => expect(screen.getAllByRole('button', { name: 'Xoá' })).toHaveLength(2))
  await userEvent.click(screen.getAllByRole('button', { name: 'Xoá' })[1])
  await waitFor(() => expect(editApi.deletePerson).toHaveBeenCalledWith(5))
  await waitFor(() => expect(onClose).toHaveBeenCalled())
}, 20000)

test('VIEWER không có tab Lịch sử sửa và không gọi API audit', async () => {
  setup()
  await screen.findByText('Nguyễn Văn An')
  expect(screen.queryByRole('tab', { name: 'Lịch sử sửa' })).not.toBeInTheDocument()
  expect(auditApi.list).not.toHaveBeenCalled()
})

test('EDITOR thấy tab Lịch sử sửa nhưng không có nút Khôi phục', async () => {
  setup(vi.fn(), vi.fn(), 'EDITOR')
  await screen.findByText('Nguyễn Văn An')
  await userEvent.click(screen.getByRole('tab', { name: 'Lịch sử sửa' }))
  expect(await screen.findByText('Sửa hồ sơ «An»')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Khôi phục' })).not.toBeInTheDocument()
})

test('ADMIN thấy tab Lịch sử sửa và nút Khôi phục', async () => {
  setup(vi.fn(), vi.fn(), 'ADMIN')
  await screen.findByText('Nguyễn Văn An')
  await userEvent.click(screen.getByRole('tab', { name: 'Lịch sử sửa' }))
  expect(await screen.findByRole('button', { name: 'Khôi phục' })).toBeInTheDocument()
})

test('đánh dấu data-person-ready=1 khi hồ sơ đã tải', async () => {
  setup()
  await screen.findByText('Nguyễn Văn An')
  await waitFor(() => expect(document.querySelector('[data-person-ready="1"]')).not.toBeNull())
})
