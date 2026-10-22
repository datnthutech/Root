import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntApp } from 'antd'
import FamilyTab from './FamilyTab'
import { editApi } from '../../api/edit'
import type { FamilyResponse } from '../../api/person'

vi.mock('../../api/edit', async (orig) => ({ ...(await orig<typeof import('../../api/edit')>()), editApi: { removeParentChild: vi.fn(), deleteUnion: vi.fn() } }))
vi.mock('./RelationDrawer', () => ({ default: ({ open, mode }: { open: boolean; mode: string }) => (open ? <div>Drawer {mode}</div> : null) }))

const ref = (id: number, name: string, linkId: number | null) => ({ id, name, gender: 'M' as const, birthYear: 1950, deathYear: null, alive: true, orderLabel: null, linkId })
const fam: FamilyResponse = {
  personId: 5, parents: [ref(1, 'Cha An', 11)],
  spouses: [{ unionId: 3, unionOrder: 1, status: 'MARRIED', partner: ref(6, 'Vợ An', null), children: [ref(7, 'Con An', 12)] }],
  otherChildren: [], fullSiblings: [], halfSiblingsSameFather: [], halfSiblingsSameMother: [],
}

const setup = (canManage: boolean, onChanged = vi.fn()) => {
  render(<AntApp><FamilyTab f={fam} personId={5} personName="An" personGender="M" canManage={canManage} onNavigate={() => {}} onChanged={onChanged} /></AntApp>)
  return onChanged
}

test('VIEWER không thấy nút thêm hay gỡ', () => {
  setup(false)
  expect(screen.queryByRole('button', { name: /Thêm/ })).not.toBeInTheDocument()
  expect(screen.queryByLabelText('Gỡ quan hệ')).not.toBeInTheDocument()
  expect(screen.queryByLabelText('Gỡ hôn phối')).not.toBeInTheDocument()
})

test('EDITOR thấy 3 nút thêm và mở drawer đúng chế độ', async () => {
  setup(true)
  await userEvent.click(screen.getByRole('button', { name: 'Thêm con' }))
  expect(screen.getByText('Drawer child')).toBeInTheDocument()
})

test('gỡ con gọi removeParentChild với linkId và làm mới', async () => {
  vi.mocked(editApi.removeParentChild).mockResolvedValue(undefined)
  const onChanged = setup(true)
  const buttons = screen.getAllByLabelText('Gỡ quan hệ')
  await userEvent.click(buttons[1]) // [0]=cha, [1]=con (theo thứ tự hiển thị)
  await userEvent.click(await screen.findByRole('button', { name: 'Gỡ' }))
  await waitFor(() => expect(editApi.removeParentChild).toHaveBeenCalledWith(12))
  expect(onChanged).toHaveBeenCalled()
})

test('gỡ hôn phối gọi deleteUnion', async () => {
  vi.mocked(editApi.deleteUnion).mockResolvedValue(undefined)
  setup(true)
  await userEvent.click(screen.getByLabelText('Gỡ hôn phối'))
  await userEvent.click(await screen.findByRole('button', { name: 'Gỡ' }))
  await waitFor(() => expect(editApi.deleteUnion).toHaveBeenCalledWith(3))
})
