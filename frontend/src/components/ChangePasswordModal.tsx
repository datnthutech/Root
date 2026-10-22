import { useState } from 'react'
import { App, Form, Input, Modal } from 'antd'
import { authApi } from '../api/auth'
import { errorText } from '../auth/errorText'

interface Values { currentPassword: string; newPassword: string; confirm: string }

export default function ChangePasswordModal({ open, onClose, onChanged }: { open: boolean; onClose: () => void; onChanged: () => void | Promise<void> }) {
  const { message } = App.useApp()
  const [form] = Form.useForm<Values>()
  const [saving, setSaving] = useState(false)

  const submit = async (v: Values) => {
    setSaving(true)
    try {
      await authApi.changePassword(v.currentPassword, v.newPassword)
      message.success('Đã đổi mật khẩu, vui lòng đăng nhập lại')
      form.resetFields()
      onClose()
      await onChanged()
    } catch (err) {
      message.error(errorText(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title="Đổi mật khẩu" open={open} okText="Đổi mật khẩu" cancelText="Huỷ" confirmLoading={saving}
      onCancel={() => { form.resetFields(); onClose() }} onOk={() => form.submit()} forceRender>
      <Form form={form} layout="vertical" onFinish={submit}>
        <Form.Item name="currentPassword" label="Mật khẩu hiện tại" rules={[{ required: true, message: 'Nhập mật khẩu hiện tại' }]}>
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Form.Item name="newPassword" label="Mật khẩu mới" rules={[{ required: true, message: 'Nhập mật khẩu mới' }, { min: 8, message: 'Tối thiểu 8 ký tự' }]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item name="confirm" label="Nhập lại mật khẩu mới" dependencies={['newPassword']}
          rules={[{ required: true, message: 'Nhập lại mật khẩu mới' },
            ({ getFieldValue }) => ({ validator: (_, value) => (!value || getFieldValue('newPassword') === value ? Promise.resolve() : Promise.reject(new Error('Mật khẩu nhập lại không khớp'))) })]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
