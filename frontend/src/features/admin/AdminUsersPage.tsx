import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Form, Input, InputNumber, Modal, Select, Space, Table, Tag } from 'antd'
import { adminUsersApi, type AdminUser, type UserStatus } from '../../api/adminUsers'
import type { Role } from '../../api/auth'
import { errorText } from '../../auth/errorText'

interface CreateValues { username: string; displayName?: string; password: string; confirm: string; role: Role; linkedPersonId?: number }
/** Các ô trong form tạo tài khoản, để gắn lỗi từ server (details) đúng ô. */
const CREATE_FIELDS = ['username', 'password', 'displayName', 'role', 'linkedPersonId']

const STATUS_COLOR: Record<UserStatus, string> = { PENDING: 'gold', ACTIVE: 'green', LOCKED: 'red' }
const STATUS_TEXT: Record<UserStatus, string> = { PENDING: 'Chờ duyệt', ACTIVE: 'Hoạt động', LOCKED: 'Đã khoá' }

export default function AdminUsersPage() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const [filter, setFilter] = useState<UserStatus | undefined>('PENDING')
  const { data = [], isLoading } = useQuery({ queryKey: ['admin-users', filter], queryFn: () => adminUsersApi.list(filter) })
  const [resetFor, setResetFor] = useState<AdminUser | null>(null)
  const [resetForm] = Form.useForm<{ newPassword: string }>()
  const resetMut = useMutation({
    mutationFn: (a: { id: number; newPassword: string }) => adminUsersApi.resetUserPassword(a.id, a.newPassword),
    onSuccess: () => { message.success('Đã đặt lại mật khẩu'); setResetFor(null) },
    onError: (e) => message.error(errorText(e)),
  })
  const [createOpen, setCreateOpen] = useState(false)
  const [createForm] = Form.useForm<CreateValues>()
  const createMut = useMutation({
    mutationFn: (v: CreateValues) => adminUsersApi.create({
      username: v.username.trim(), password: v.password, displayName: v.displayName?.trim() || undefined,
      role: v.role, linkedPersonId: v.linkedPersonId ?? undefined,
    }),
    onSuccess: () => {
      message.success('Đã tạo tài khoản')
      setCreateOpen(false)
      setFilter(undefined) // tài khoản mới ACTIVE, không nằm trong "Chờ duyệt"
      qc.invalidateQueries({ queryKey: ['admin-users'] })
    },
    onError: (e) => {
      const details = (e as { details?: unknown } | null)?.details
      if (details && typeof details === 'object' && Object.keys(details).length) {
        createForm.setFields(Object.entries(details)
          .filter(([name]) => CREATE_FIELDS.includes(name))
          .map(([name, msg]) => ({ name: name as keyof CreateValues, errors: [String(msg)] })))
      }
      message.error(errorText(e))
    },
  })
  const mut = useMutation({
    mutationFn: (a: { id: number; patch: Parameters<typeof adminUsersApi.update>[1] }) => adminUsersApi.update(a.id, a.patch),
    onSuccess: () => { message.success('Đã cập nhật'); qc.invalidateQueries({ queryKey: ['admin-users'] }) },
    onError: (e) => message.error(errorText(e)),
  })

  return (
    <div style={{ padding: 16 }}>
      <Space style={{ marginBottom: 12 }}>
        <span>Lọc theo trạng thái:</span>
        <Select<UserStatus | 'ALL'> style={{ width: 160 }} value={filter ?? 'ALL'}
          onChange={(v) => setFilter(v === 'ALL' ? undefined : v)}
          options={[{ value: 'ALL', label: 'Tất cả' }, ...(['PENDING', 'ACTIVE', 'LOCKED'] as UserStatus[]).map((s) => ({ value: s, label: STATUS_TEXT[s] }))]} />
        <Button type="primary" onClick={() => { createForm.resetFields(); setCreateOpen(true) }}>Thêm tài khoản</Button>
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
              <Button size="small" onClick={() => { resetForm.resetFields(); setResetFor(row) }}>Đặt lại mật khẩu</Button>
            </Space>) },
        ]} />
      <Modal title={`Đặt lại mật khẩu: ${resetFor?.username ?? ''}`} open={resetFor !== null} okText="Đặt lại" cancelText="Huỷ"
        confirmLoading={resetMut.isPending} onCancel={() => setResetFor(null)} onOk={() => resetForm.submit()} destroyOnHidden forceRender>
        <Form form={resetForm} layout="vertical" onFinish={(v) => resetFor && resetMut.mutate({ id: resetFor.id, newPassword: v.newPassword })}>
          <Form.Item name="newPassword" label="Mật khẩu mới" rules={[{ required: true, message: 'Nhập mật khẩu mới' }, { min: 8, message: 'Tối thiểu 8 ký tự' }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
      <Modal title="Thêm tài khoản" open={createOpen} okText="Tạo tài khoản" cancelText="Huỷ"
        confirmLoading={createMut.isPending} onCancel={() => setCreateOpen(false)} onOk={() => createForm.submit()} destroyOnHidden forceRender>
        <Form form={createForm} layout="vertical" initialValues={{ role: 'VIEWER' }} onFinish={(v) => createMut.mutate(v)}>
          <Form.Item name="username" label="Tên đăng nhập" rules={[{ required: true, message: 'Nhập tên đăng nhập' },
            { min: 3, max: 50, message: 'Từ 3 đến 50 ký tự' }, { pattern: /^[a-zA-Z0-9._-]+$/, message: 'Chỉ dùng chữ không dấu, số và . _ -' }]}>
            <Input autoComplete="off" />
          </Form.Item>
          <Form.Item name="displayName" label="Họ tên" rules={[{ max: 120, message: 'Tối đa 120 ký tự' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Nhập mật khẩu' }, { min: 8, message: 'Tối thiểu 8 ký tự' },
            { validator: (_, value) => (!value || new TextEncoder().encode(value).length <= 72 ? Promise.resolve() : Promise.reject(new Error('Mật khẩu quá dài (tối đa 72 byte, ký tự có dấu chiếm nhiều byte)'))) }]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="confirm" label="Nhập lại mật khẩu" dependencies={['password']}
            rules={[{ required: true, message: 'Nhập lại mật khẩu' },
              ({ getFieldValue }) => ({ validator: (_, value) => (!value || getFieldValue('password') === value ? Promise.resolve() : Promise.reject(new Error('Mật khẩu nhập lại không khớp'))) })]}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="role" label="Vai trò">
            <Select<Role> options={[{ value: 'VIEWER', label: 'Cấp thấp' }, { value: 'EDITOR', label: 'Cấp trung' }, { value: 'ADMIN', label: 'Cấp cao' }]} />
          </Form.Item>
          <Form.Item name="linkedPersonId" label="Gắn với người (ID)">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
