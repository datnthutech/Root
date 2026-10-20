import { useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { Button, Layout, Space } from 'antd'
import { useAuth } from '../auth/AuthContext'
import { hasRole } from '../auth/RequireAuth'
import ChangePasswordModal from './ChangePasswordModal'

export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [pwOpen, setPwOpen] = useState(false)
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 16px' }}>
        <Space size="large">
          <Link to="/" style={{ color: '#fff', fontWeight: 600 }}>Gia phả</Link>
          {hasRole(user, 'ADMIN') && <Link to="/admin/users" style={{ color: '#fff' }}>Quản lý tài khoản</Link>}
          {hasRole(user, 'ADMIN') && <Link to="/admin/clans" style={{ color: '#fff' }}>Quản lý dòng họ</Link>}
          {hasRole(user, 'ADMIN') && <Link to="/admin/import" style={{ color: '#fff' }}>Nhập Excel</Link>}
        </Space>
        <Space>
          <span style={{ color: '#fff' }}>{user?.displayName ?? user?.username}</span>
          <Button size="small" onClick={() => setPwOpen(true)}>Đổi mật khẩu</Button>
          <Button size="small" onClick={async () => { await logout(); navigate('/login') }}>Đăng xuất</Button>
        </Space>
      </Layout.Header>
      <Layout.Content><Outlet /></Layout.Content>
      <ChangePasswordModal open={pwOpen} onClose={() => setPwOpen(false)} />
    </Layout>
  )
}
