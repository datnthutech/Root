import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, Button, Card, Form, Input } from 'antd'
import { authApi } from '../../api/auth'
import { errorText } from '../../auth/errorText'

export default function RegisterPage() {
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)

  const onFinish = async (v: { username: string; password: string; displayName: string }) => {
    setBusy(true); setError(null)
    try { await authApi.register(v.username, v.password, v.displayName); setDone(true) }
    catch (e) { setError(errorText(e)) }
    finally { setBusy(false) }
  }

  if (done) {
    return (
      <Card style={{ maxWidth: 380, margin: '12vh auto', width: 'calc(100% - 32px)' }}>
        <Alert type="success" showIcon message="Đã gửi đăng ký. Vui lòng chờ quản trị viên duyệt." />
        <div style={{ marginTop: 12 }}><Link to="/login">Về trang đăng nhập</Link></div>
      </Card>
    )
  }
  return (
    <Card title="Đăng ký tài khoản" style={{ maxWidth: 380, margin: '12vh auto', width: 'calc(100% - 32px)' }}>
      {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
      <Form layout="vertical" onFinish={onFinish}>
        <Form.Item label="Họ tên" name="displayName" rules={[{ required: true, message: 'Nhập họ tên' }]}><Input /></Form.Item>
        <Form.Item label="Tên đăng nhập" name="username"
          rules={[{ required: true, min: 3, max: 50, pattern: /^[a-zA-Z0-9._-]+$/, message: '3–50 ký tự: chữ, số, . _ -' }]}>
          <Input autoComplete="username" />
        </Form.Item>
        <Form.Item label="Mật khẩu" name="password" rules={[{ required: true, min: 8, max: 72, message: 'Mật khẩu 8–72 ký tự' }]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={busy}>Đăng ký</Button>
      </Form>
      <div style={{ marginTop: 12, textAlign: 'center' }}><Link to="/login">Đã có tài khoản? Đăng nhập</Link></div>
    </Card>
  )
}
