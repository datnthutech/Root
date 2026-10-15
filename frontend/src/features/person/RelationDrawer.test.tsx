import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntApp } from 'antd'
import RelationDrawer from './RelationDrawer'
import { editApi } from '../../api/edit'

vi.mock('../../api/edit', async (orig) => ({ ...(await orig<typeof import('../../api/edit')>()), editApi: {
  createPerson: vi.fn(), addChild: vi.fn(), addParentChild: vi.fn(), createUnion: vi.fn() } }))
vi.mock('../tree/PersonSearch', () => ({ PersonSearch: ({ onPick }: { onPick(id: number): void }) => <button onClick={() => onPick(77)}>chọn-77</button> }))

const person = { id: 5, name: 'An', gender: 'M' as const }
const spouses = [{ unionId: 3, unionOrder: 1, status: 'MARRIED', partner: { id: 6, name: 'Vợ An', gender: 'F' as const, birthYear: null, deathYear: null, alive: true, orderLabel: null, linkId: null }, children: [] }]

function setup(mode: 'parent' | 'child' | 'spouse') {
  const onDone = vi.fn()
  render(<AntApp><RelationDrawer open mode={mode} person={person} spouses={spouses} onClose={() => {}} onDone={onDone} /></AntApp>)
  return onDone
}

test('thêm con là người đã có, chọn hôn phối → gọi addChild với unionId', async () => {
  vi.mocked(editApi.addChild).mockResolvedValue({ links: [], warnings: [] })
  const onDone = setup('child')
  await userEvent.click(screen.getByRole('button', { name: 'chọn-77' }))
  await userEvent.click(screen.getByRole('button', { name: 'Thêm con' }))
  await waitFor(() => expect(editApi.addChild).toHaveBeenCalled())
  expect(vi.mocked(editApi.addChild).mock.calls[0][0]).toMatchObject({ parentId: 5, childId: 77, unionId: 3 })
  expect(onDone).toHaveBeenCalled()
})

test('thêm cha/mẹ là người đã có → addParentChild với parentId là người được chọn', async () => {
  vi.mocked(editApi.addParentChild).mockResolvedValue({ link: {} as never, warnings: [] })
  setup('parent')
  await userEvent.click(screen.getByRole('button', { name: 'chọn-77' }))
  await userEvent.click(screen.getByRole('button', { name: 'Thêm cha/mẹ' }))
  await waitFor(() => expect(editApi.addParentChild).toHaveBeenCalledWith(expect.objectContaining({ parentId: 77, childId: 5 })))
})

test('thêm vợ/chồng tạo hôn phối giữa hai người', async () => {
  vi.mocked(editApi.createUnion).mockResolvedValue({ id: 1, partnerAId: 5, partnerBId: 77, order: 2, status: 'MARRIED' })
  setup('spouse')
  await userEvent.click(screen.getByRole('button', { name: 'chọn-77' }))
  await userEvent.click(screen.getByRole('button', { name: 'Thêm vợ/chồng' }))
  await waitFor(() => expect(editApi.createUnion).toHaveBeenCalledWith(expect.objectContaining({ partnerAId: 5, partnerBId: 77 })))
})

test('cảnh báo ngày tháng từ BE được hiển thị nhưng vẫn hoàn tất', async () => {
  vi.mocked(editApi.addParentChild).mockResolvedValue({ link: {} as never, warnings: [{ code: 'CHILD_BEFORE_PARENT', message: 'Con sinh trước cha/mẹ' }] })
  const onDone = setup('parent')
  await userEvent.click(screen.getByRole('button', { name: 'chọn-77' }))
  await userEvent.click(screen.getByRole('button', { name: 'Thêm cha/mẹ' }))
  expect(await screen.findByText('Con sinh trước cha/mẹ')).toBeInTheDocument()
  await waitFor(() => expect(onDone).toHaveBeenCalled())
})

test('lỗi vòng lặp hiện thông báo và không gọi onDone', async () => {
  vi.mocked(editApi.addParentChild).mockRejectedValue({ code: 'RELATION_CYCLE', status: 409 })
  const onDone = setup('parent')
  await userEvent.click(screen.getByRole('button', { name: 'chọn-77' }))
  await userEvent.click(screen.getByRole('button', { name: 'Thêm cha/mẹ' }))
  expect(await screen.findByText('Quan hệ này tạo vòng lặp trong gia phả')).toBeInTheDocument()
  expect(onDone).not.toHaveBeenCalled()
})
