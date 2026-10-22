import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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
  const ui = (over: Partial<React.ComponentProps<typeof PersonFormDrawer>> = {}) => (
    <QueryClientProvider client={qc}><AntApp><PersonFormDrawer open mode="create" onClose={onClose} onSaved={onSaved} {...props} {...over} /></AntApp></QueryClientProvider>
  )
  const { rerender } = render(ui())
  return { onSaved, onClose, qc, reopen: (over: Partial<React.ComponentProps<typeof PersonFormDrawer>>) => rerender(ui(over)) }
}

async function pickPrecision(index: number, label: string) {
  await userEvent.click(screen.getAllByRole('combobox')[index])
  const dd = document.querySelector<HTMLElement>('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')!
  await userEvent.click(within(dd).getByText(label))
}
async function typeDate(index: number, text: string) {
  // fireEvent.change thay vì gõ từng phím: mỗi phím làm form render lại và validate lại, quá chậm trong jsdom
  fireEvent.change(pickers()[index], { target: { value: text } })
  fireEvent.blur(pickers()[index]) // Enter sẽ submit form, nên commit bằng blur
}
const pickers = () => Array.from(document.querySelectorAll<HTMLInputElement>('.ant-picker input'))

beforeEach(() => {
  vi.mocked(editApi.createPerson).mockReset()
  vi.mocked(editApi.updatePerson).mockReset()
  vi.spyOn(personApi, 'biography').mockReset().mockResolvedValue({ personId: 5, biography: 'Tiểu sử cũ' })
  vi.spyOn(personApi, 'profile').mockReset()
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
  expect(await screen.findByText('Dữ liệu đã được người khác sửa')).toBeInTheDocument()
  expect(onSaved).not.toHaveBeenCalled()
  expect(screen.getByLabelText('Họ và tên')).toBeInTheDocument()
})

test('tải lại sau xung đột cập nhật version mới', async () => {
  vi.mocked(editApi.updatePerson).mockRejectedValueOnce({ code: 'VERSION_CONFLICT', status: 409, message: '', details: null })
  vi.mocked(personApi.profile).mockResolvedValue(profile({ version: 7, fullName: 'Tên Mới Nhất' }))
  setup({ mode: 'edit', person: profile() })
  await userEvent.click(await screen.findByRole('button', { name: 'Lưu' }, { timeout: 5000 }))
  await userEvent.click(await screen.findByRole('button', { name: 'Tải lại dữ liệu mới nhất' }, { timeout: 5000 }))
  expect(await screen.findByDisplayValue('Tên Mới Nhất', {}, { timeout: 5000 })).toBeInTheDocument()
  expect(screen.queryByText('Dữ liệu đã được người khác sửa')).not.toBeInTheDocument()
  expect(personApi.biography).toHaveBeenCalledTimes(2)
  vi.mocked(editApi.updatePerson).mockResolvedValue(profile({ version: 8 }))
  await userEvent.click(await screen.findByRole('button', { name: 'Lưu' }, { timeout: 5000 }))
  await waitFor(() => expect(editApi.updatePerson).toHaveBeenLastCalledWith(5, expect.objectContaining({ version: 7 })), { timeout: 5000 })
})

test('sửa gửi kèm biography đã tải', async () => {
  vi.mocked(editApi.updatePerson).mockResolvedValue(profile({ version: 4 }))
  const { onSaved } = setup({ mode: 'edit', person: profile() })
  await userEvent.click(await screen.findByRole('button', { name: 'Lưu' }))
  await waitFor(() => expect(onSaved).toHaveBeenCalled())
  expect(editApi.updatePerson).toHaveBeenCalledWith(5, expect.objectContaining({ biography: 'Tiểu sử cũ' }))
})

test('tải biography lỗi thì không hiện form, thử lại được', async () => {
  vi.mocked(personApi.biography).mockRejectedValueOnce({ code: 'UNKNOWN', status: 500, message: '', details: null })
  setup({ mode: 'edit', person: profile() })
  const retry = await screen.findByRole('button', { name: 'Thử lại' })
  expect(screen.queryByRole('button', { name: 'Lưu' })).not.toBeInTheDocument()
  expect(screen.queryByLabelText('Họ và tên')).not.toBeInTheDocument()
  await userEvent.click(retry)
  expect(await screen.findByRole('button', { name: 'Lưu' })).toBeInTheDocument()
  expect(editApi.updatePerson).not.toHaveBeenCalled()
})

test('độ chính xác Tháng gửi birthDate ngày 01', async () => {
  vi.mocked(editApi.createPerson).mockResolvedValue(profile())
  const { onSaved } = setup()
  await userEvent.type(screen.getByLabelText('Họ và tên'), 'Ai Đó')
  await pickPrecision(0, 'Tháng')
  await typeDate(0, '04/1953')
  await userEvent.click(screen.getByRole('button', { name: 'Lưu' }))
  await waitFor(() => expect(onSaved).toHaveBeenCalled())
  expect(editApi.createPerson).toHaveBeenCalledWith(expect.objectContaining({ birthDate: '1953-04-01', birthPrecision: 'MONTH' }))
}, 20000)

test('chuyển còn sống sang đã mất gửi ngày mất, chuyển lại thì null', async () => {
  vi.mocked(editApi.createPerson).mockResolvedValue(profile())
  const { onSaved } = setup()
  await userEvent.type(screen.getByLabelText('Họ và tên'), 'Ai Đó')
  expect(screen.queryByText('Ngày mất (dương lịch)')).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('switch'))
  expect(screen.getByText('Ngày mất (dương lịch)')).toBeInTheDocument()
  await pickPrecision(1, 'Năm')
  await typeDate(1, '1990')
  await userEvent.click(screen.getByRole('button', { name: 'Lưu' }))
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1))
  expect(editApi.createPerson).toHaveBeenLastCalledWith(expect.objectContaining({ alive: false, deathDate: '1990-01-01', deathPrecision: 'YEAR' }))
  await userEvent.click(screen.getByRole('switch'))
  await userEvent.click(screen.getByRole('button', { name: 'Lưu' }))
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(2))
  expect(editApi.createPerson).toHaveBeenLastCalledWith(expect.objectContaining({ alive: true, deathDate: null, deathPrecision: 'UNKNOWN' }))
}, 30000)

async function fillDates(birth: string, deathPrec: string, death: string) {
  await userEvent.type(screen.getByLabelText('Họ và tên'), 'Ai Đó')
  await pickPrecision(0, 'Ngày')
  await typeDate(0, `${birth}`)
  await userEvent.click(screen.getByRole('switch'))
  await pickPrecision(1, deathPrec)
  await typeDate(1, `${death}`)
  await userEvent.click(screen.getByRole('button', { name: 'Lưu' }))
}

test('ngày mất trước ngày sinh bị chặn', async () => {
  setup()
  await fillDates('15/06/1950', 'Ngày', '01/01/1940')
  expect(await screen.findByText('Ngày mất không được trước ngày sinh')).toBeInTheDocument()
  expect(editApi.createPerson).not.toHaveBeenCalled()
}, 30000)

test('mất năm 1950 so với sinh 15/06/1950 được chấp nhận', async () => {
  vi.mocked(editApi.createPerson).mockResolvedValue(profile())
  const { onSaved } = setup()
  await fillDates('15/06/1950', 'Năm', '1950')
  await waitFor(() => expect(onSaved).toHaveBeenCalled())
}, 30000)

test('ngày mất bằng ngày sinh được chấp nhận', async () => {
  vi.mocked(editApi.createPerson).mockResolvedValue(profile())
  const { onSaved } = setup()
  await fillDates('15/06/1950', 'Ngày', '15/06/1950')
  await waitFor(() => expect(onSaved).toHaveBeenCalled())
}, 30000)

test('lỗi validate ngày mất được xoá khi đổi ngày sinh', async () => {
  setup()
  await fillDates('15/06/1950', 'Ngày', '01/01/1940')
  expect(await screen.findByText('Ngày mất không được trước ngày sinh')).toBeInTheDocument()
  fireEvent.change(pickers()[0], { target: { value: '01/01/1930' } })
  fireEvent.blur(pickers()[0])
  await waitFor(() => expect(screen.queryByText('Ngày mất không được trước ngày sinh')).not.toBeInTheDocument())
}, 30000)

test('reload lỗi biography giữ trạng thái xung đột và hiện Thử lại', async () => {
  vi.mocked(editApi.updatePerson).mockRejectedValueOnce({ code: 'VERSION_CONFLICT', status: 409, message: '', details: null })
  vi.mocked(personApi.profile).mockResolvedValue(profile({ version: 7, fullName: 'Tên Mới Nhất' }))
  setup({ mode: 'edit', person: profile() })
  await userEvent.click(await screen.findByRole('button', { name: 'Lưu' }))
  vi.mocked(personApi.biography).mockRejectedValue({ code: 'UNKNOWN', status: 500, message: '', details: null })
  await userEvent.click(await screen.findByRole('button', { name: 'Tải lại dữ liệu mới nhất' }))
  expect(await screen.findByRole('button', { name: 'Thử lại' })).toBeInTheDocument()
  expect(screen.getByText('Dữ liệu đã được người khác sửa')).toBeInTheDocument()
  expect(screen.queryByDisplayValue('Tên Mới Nhất')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Lưu' })).not.toBeInTheDocument()
}, 20000)

test('refetch nền không làm mất nội dung đang gõ', async () => {
  const { qc } = setup({ mode: 'edit', person: profile() })
  const name = await screen.findByLabelText('Họ và tên')
  await userEvent.clear(name)
  await userEvent.type(name, 'Đang gõ dở')
  await act(async () => { await qc.refetchQueries({ queryKey: ['person-bio'] }) })
  expect(screen.getByLabelText('Họ và tên')).toHaveValue('Đang gõ dở')
})

test('mở lại với person mới thì bỏ trạng thái cũ', async () => {
  vi.mocked(editApi.updatePerson).mockRejectedValueOnce({ code: 'VERSION_CONFLICT', status: 409, message: '', details: null })
  vi.mocked(personApi.profile).mockResolvedValue(profile({ version: 7 }))
  const { reopen, onSaved } = setup({ mode: 'edit', person: profile() })
  await userEvent.click(await screen.findByRole('button', { name: 'Lưu' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Tải lại dữ liệu mới nhất' }))
  await screen.findByRole('button', { name: 'Lưu' })
  reopen({ open: false, mode: 'edit', person: profile() })
  vi.mocked(editApi.updatePerson).mockResolvedValue(profile({ version: 12 }))
  reopen({ open: true, mode: 'edit', person: profile({ version: 11 }) })
  await userEvent.click(await screen.findByRole('button', { name: 'Lưu' }))
  await waitFor(() => expect(onSaved).toHaveBeenCalled())
  expect(editApi.updatePerson).toHaveBeenLastCalledWith(5, expect.objectContaining({ version: 11 }))
  expect(screen.queryByText('Dữ liệu đã được người khác sửa')).not.toBeInTheDocument()
}, 20000)

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
