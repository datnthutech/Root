import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, InputNumber, Select, Space, Table, Tag } from 'antd'
import { adminUsersApi, type AdminUser, type UserStatus } from '../../api/adminUsers'
import type { Role } from '../../api/auth'
import { errorText } from '../../auth/errorText'

const STATUS_COLOR: Record<UserStatus, string> = { PENDING: 'gold', ACTIVE: 'green', LOCKED: 'red' }
const STATUS_TEXT: Record<UserStatus, string> = { PENDING: 'Chờ duyệt', ACTIVE: 'Hoạt động', LOCKED: 'Đã khoá' }

export default function AdminUsersPage() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [filter, setFilter] = useState<UserStatus | undefined>('PENDING')
  const { data = [], isLoading } = useQuery({ queryKey: ['admin-users', filter], queryFn: () => adminUsersApi.list(filter) })
  const mut = useMutation({
    mutationFn: (a: { id: number; patch: Parameters<typeof adminUsersApi.update>[1] }) => adminUsersApi.update(a.id, a.patch),
    onSuccess: () => { message.success('Đã cập nhật'); qc.invalidateQueries({ queryKey: ['admin-users'] }) },
    onError: (e) => message.error(errorText((e as { code?: string }).code)),
  })

  return (
    <div style={{ padding: 16 }}>
      <Space style={{ marginBottom: 12 }}>
        <span>Lọc theo trạng thái:</span>
        <Select<UserStatus | 'ALL'> style={{ width: 160 }} value={filter ?? 'ALL'}
          onChange={(v) => setFilter(v === 'ALL' ? undefined : v)}
          options={[{ value: 'ALL', label: 'Tất cả' }, ...(['PENDING', 'ACTIVE', 'LOCKED'] as UserStatus[]).map((s) => ({ value: s, label: STATUS_TEXT[s] }))]} />
      </Space>
      <Table<AdminUser> rowKey="id" loading={isLoading} dataSource={data} size="small" scroll={{ x: true }} pagination={{ pageSize: 20 }}
        columns={[
          { title: 'Tên đăng nhập', dataIndex: 'username' },
          { title: 'Họ tên', dataIndex: 'displayName' },
          { title: 'Trạng thái', dataIndex: 'status', render: (s: UserStatus) => <Tag color={STATUS_COLOR[s]}>{STATUS_TEXT[s]}</Tag> },
          { title: 'Vai trò', dataIndex: 'role', render: (r: Role, row) => (
            <Select<Role> size="small" value={r} style={{ width: 110 }} onChange={(role) => mut.mutate({ id: row.id, patch: { role } })}
              options={[{ value: 'VIEWER', label: 'Cấp thấp' }, { value: 'EDITOR', label: 'Cấp trung' }, { value: 'ADMIN', label: 'Cấp cao' }]} />) },
          { title: 'Gắn với người (ID)', dataIndex: 'linkedPersonId', render: (v: number | null, row) => (
            <InputNumber size="small" min={1} defaultValue={v ?? undefined} style={{ width: 100 }}
              onBlur={(e) => { const n = Number(e.target.value); if (n && n !== v) mut.mutate({ id: row.id, patch: { linkedPersonId: n } }) }} />) },
          { title: 'Thao tác', render: (_, row) => (
            <Space>
              {row.status !== 'ACTIVE' && <Button size="small" type="primary" onClick={() => mut.mutate({ id: row.id, patch: { status: 'ACTIVE' } })}>{row.status === 'PENDING' ? 'Duyệt' : 'Mở khoá'}</Button>}
              {row.status === 'ACTIVE' && <Button size="small" danger onClick={() => mut.mutate({ id: row.id, patch: { status: 'LOCKED' } })}>Khoá</Button>}
            </Space>) },
        ]} />
    </div>
  )
}
