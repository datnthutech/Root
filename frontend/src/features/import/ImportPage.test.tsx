import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { App as AntApp } from 'antd'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ImportPage from './ImportPage'
import { importApi, type ImportReport } from '../../api/importApi'

vi.mock('../../api/importApi', async (orig) => ({ ...(await orig<typeof import('../../api/importApi')>()), importApi: { downloadTemplate: vi.fn(), upload: vi.fn() } }))

const clean = (over: Partial<ImportReport> = {}): ImportReport => ({ dryRun: true, persons: 4, unions: 1, links: 3, errors: [], warnings: [], clanId: null, ...over })
const xlsx = (name = 'a.xlsx') => new File(['x'], name, { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })

let qc: QueryClient
function setup() {
  qc = new QueryClient()
  const { container } = render(<QueryClientProvider client={qc}><MemoryRouter><AntApp><ImportPage /></AntApp></MemoryRouter></QueryClientProvider>)
  return container.querySelector('input[type=file]') as HTMLInputElement
}

test('chưa chọn tệp thì không bấm được', () => {
  setup()
  expect(screen.getByRole('button', { name: 'Kiểm tra thử' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Nhập thật' })).toBeDisabled()
})

test('tải mẫu gọi downloadTemplate', async () => {
  setup()
  await userEvent.click(screen.getByRole('button', { name: 'Tải mẫu Excel' }))
  expect(importApi.downloadTemplate).toHaveBeenCalled()
})

test('kiểm tra thử sạch lỗi hiện tóm tắt và bật Nhập thật', async () => {
  vi.mocked(importApi.upload).mockResolvedValue(clean())
  const input = setup()
  await userEvent.upload(input, xlsx())
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra thử' }))
  expect(await screen.findByText(/4 người, 1 hôn phối, 3 quan hệ/)).toBeInTheDocument()
  expect(importApi.upload).toHaveBeenCalledWith(expect.any(File), true)
  expect(screen.getByRole('button', { name: 'Nhập thật' })).toBeEnabled()
})

test('có lỗi thì hiện bảng lỗi theo dòng và không bật Nhập thật', async () => {
  vi.mocked(importApi.upload).mockResolvedValue(clean({ errors: [{ sheet: 'Người', row: 7, column: 'Ngày sinh', code: 'BAD_VALUE', message: 'Ngày không hợp lệ: 31/02/1950' }] }))
  const input = setup()
  await userEvent.upload(input, xlsx())
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra thử' }))
  expect(await screen.findByText('Ngày không hợp lệ: 31/02/1950')).toBeInTheDocument()
  expect(screen.getByText('7')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Nhập thật' })).toBeDisabled()
})

test('nhập thật thành công hiện liên kết xem cây', async () => {
  vi.mocked(importApi.upload).mockResolvedValueOnce(clean()).mockResolvedValueOnce(clean({ dryRun: false, clanId: 9 }))
  const input = setup()
  await userEvent.upload(input, xlsx())
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra thử' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Nhập thật' }))
  await waitFor(() => expect(importApi.upload).toHaveBeenLastCalledWith(expect.any(File), false))
  expect(await screen.findByRole('link', { name: 'Xem cây' })).toHaveAttribute('href', '/tree?mode=clan&clan=9')
})

test('chọn tệp khác thì xoá kết quả cũ và tắt Nhập thật', async () => {
  vi.mocked(importApi.upload).mockResolvedValue(clean())
  const input = setup()
  await userEvent.upload(input, xlsx('a.xlsx'))
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra thử' }))
  await screen.findByText(/4 người/)
  // rc-upload tạo lại <input> sau mỗi lần chọn nên phải lấy lại phần tử
  await userEvent.upload(document.querySelector('input[type=file]') as HTMLInputElement, xlsx('b.xlsx'))
  await waitFor(() => expect(screen.queryByText(/4 người/)).not.toBeInTheDocument())
  expect(screen.getByRole('button', { name: 'Nhập thật' })).toBeDisabled()
})

const fileInput = () => document.querySelector('input[type=file]') as HTMLInputElement

test('đổi tệp khi kiểm tra thử đang chạy: kết quả cũ bị bỏ, Nhập thật vẫn tắt, Dragger khoá khi bận', async () => {
  let resolve!: (r: ImportReport) => void
  vi.mocked(importApi.upload).mockReset().mockReturnValue(new Promise((r) => { resolve = r }))
  setup()
  await userEvent.upload(fileInput(), xlsx('a.xlsx'))
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra thử' }))
  expect(fileInput()).toBeDisabled()
  await act(async () => { resolve(clean()) })
  await waitFor(() => expect(screen.getByRole('button', { name: 'Nhập thật' })).toBeEnabled())
})

test('kết quả kiểm tra thử của tệp cũ không áp dụng cho tệp mới', async () => {
  let resolve!: (r: ImportReport) => void
  vi.mocked(importApi.upload).mockReset().mockReturnValue(new Promise((r) => { resolve = r }))
  setup()
  await userEvent.upload(fileInput(), xlsx('a.xlsx'))
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra thử' }))
  // mô phỏng đổi tệp bằng sự kiện change trực tiếp (Dragger bị khoá khi bận)
  const { fireEvent } = await import('@testing-library/react')
  fireEvent.change(fileInput(), { target: { files: [xlsx('b.xlsx')] } })
  await act(async () => { resolve(clean()) })
  expect(screen.getByRole('button', { name: 'Nhập thật' })).toBeDisabled()
})

test('nút bị khoá khi đang chạy, nhập thật 422 hiện lỗi và gọi upload với tệp đã kiểm tra', async () => {
  const bad = clean({ dryRun: false, errors: [{ sheet: 'Quan hệ', row: 3, column: 'Cha', code: 'X', message: 'Lỗi giữa chừng' }] })
  vi.mocked(importApi.upload).mockReset().mockResolvedValueOnce(clean()).mockResolvedValueOnce(bad)
  setup()
  await userEvent.upload(fileInput(), xlsx())
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra thử' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Nhập thật' }))
  expect(await screen.findByText('Lỗi giữa chừng')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Nhập thật' })).toBeDisabled()
  expect(importApi.upload).toHaveBeenCalledTimes(2)
})

test('đang bận thì nút Kiểm tra thử không gọi thêm lần nữa', async () => {
  vi.mocked(importApi.upload).mockReset().mockReturnValue(new Promise(() => {}))
  setup()
  await userEvent.upload(fileInput(), xlsx())
  const btn = screen.getByRole('button', { name: /Kiểm tra thử/ })
  await userEvent.click(btn)
  await userEvent.click(screen.getByRole('button', { name: /Kiểm tra thử/ }))
  expect(importApi.upload).toHaveBeenCalledTimes(1)
})

test('nhập thật thành công làm mới cache', async () => {
  vi.mocked(importApi.upload).mockReset().mockResolvedValueOnce(clean()).mockResolvedValueOnce(clean({ dryRun: false, clanId: 9 }))
  setup()
  const spy = vi.spyOn(qc, 'invalidateQueries')
  await userEvent.upload(fileInput(), xlsx())
  await userEvent.click(screen.getByRole('button', { name: 'Kiểm tra thử' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Nhập thật' }))
  await screen.findByRole('link', { name: 'Xem cây' })
  const keys = spy.mock.calls.map((c) => (c[0] as { queryKey: string[] }).queryKey[0])
  expect(keys).toEqual(expect.arrayContaining(['clans', 'person', 'person-family', 'person-bio', 'tree-graph']))
})
