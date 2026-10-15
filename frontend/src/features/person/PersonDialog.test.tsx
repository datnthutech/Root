import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import PersonDialog from './PersonDialog'
import { personApi } from '../../api/person'

vi.mock('../../api/person', async (orig) => ({ ...(await orig<typeof import('../../api/person')>()), personApi: { profile: vi.fn(), family: vi.fn(), biography: vi.fn() } }))

const ref = (id: number, name: string, orderLabel: string | null = null) =>
  ({ id, name, gender: 'M' as const, birthYear: 1950, deathYear: null, alive: true, orderLabel })

beforeEach(() => {
  vi.mocked(personApi.profile).mockResolvedValue({
    id: 5, fullName: 'Nguyễn Văn An', gender: 'M', birthDate: '1925-03-04', birthPrecision: 'DAY', birthYear: 1925, alive: false,
    deathDate: '1990-01-01', deathPrecision: 'YEAR', deathYear: 1990, deathAnniversaryLunar: '12/3 âm lịch', originPlace: 'Bến Tre',
    generation: 2, orderLabel: 'anh Hai', orderNeedsConfirm: true, version: 0, createdAt: '2026-01-01T00:00:00Z',
  })
  vi.mocked(personApi.family).mockResolvedValue({
    personId: 5, parents: [ref(1, 'Cha An')], spouses: [{ unionId: 1, unionOrder: 1, status: 'MARRIED', partner: ref(6, 'Vợ An'), children: [ref(7, 'Con An', 'anh Hai')] }],
    otherChildren: [], fullSiblings: [ref(8, 'Em An', 'chị Ba')], halfSiblingsSameFather: [ref(9, 'Em khác mẹ')], halfSiblingsSameMother: [],
  })
  vi.mocked(personApi.biography).mockResolvedValue({ personId: 5, biography: 'Ông **An** làm ruộng\n- Con 1' })
})

function setup(onNavigate = vi.fn(), onClose = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <PersonDialog personId={5} onClose={onClose} onNavigate={onNavigate} />
    </QueryClientProvider>,
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
    id: 5, fullName: 'Người Sống', gender: 'F', birthYear: 1953, alive: true, version: 0, createdAt: '2026-01-01T00:00:00Z',
  })
  setup()
  expect(await screen.findByText('Người Sống')).toBeInTheDocument()
  expect(screen.getByText('1953 (chỉ hiện năm)')).toBeInTheDocument()
})
