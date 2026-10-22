import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { Result, Spin } from 'antd'
import type { CurrentUser, Role } from '../api/auth'
import { useAuth } from './AuthContext'

const RANK: Record<Role, number> = { VIEWER: 1, EDITOR: 2, ADMIN: 3 }
export const hasRole = (user: CurrentUser | null, min: Role) => !!user && RANK[user.role] >= RANK[min]

export function RequireAuth({ role = 'VIEWER', children }: { role?: Role; children: ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <Spin style={{ display: 'block', margin: '20vh auto' }} />
  if (!user) return <Navigate to="/login" replace />
  if (!hasRole(user, role)) return <Result status="403" title="403" subTitle="Bạn không có quyền truy cập trang này" />
  return <>{children}</>
}
