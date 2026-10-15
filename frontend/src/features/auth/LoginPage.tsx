import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert, Button, Card, Form, Input } from 'antd'
import { useAuth } from '../../auth/AuthContext'
import { errorText } from '../../auth/errorText'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const onFinish = async (v: { username: string; password: string }) => {
    setBusy(true); setError(null)
    try { await login(v.username, v.password); navigate('/', { replace: true }) }
    catch (e) { setError(errorText(e)) }
    finally { setBusy(false) }
  }

  return (
    <Card title="Đăng nhập gia phả" style={{ maxWidth: 380, margin: '12vh auto', width: 'calc(100% - 32px)' }}>
      {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
      <Form layout="vertical" onFinish={onFinish}>
        <Form.Item label="Tên đăng nhập" name="username" rules={[{ required: true, message: 'Nhập tên đăng nhập' }]}>
          <Input autoComplete="username" />
        </Form.Item>
        <Form.Item label="Mật khẩu" name="password" rules={[{ required: true, message: 'Nhập mật khẩu' }]}>
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={busy}>Đăng nhập</Button>
      </Form>
      <div style={{ marginTop: 12, textAlign: 'center' }}>Chưa có tài khoản? <Link to="/register">Đăng ký</Link></div>
    </Card>
  )
}
