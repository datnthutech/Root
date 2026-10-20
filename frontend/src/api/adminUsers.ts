import { http } from './http'
import type { Role } from './auth'

export type UserStatus = 'PENDING' | 'ACTIVE' | 'LOCKED'
export interface AdminUser { id: number; username: string; displayName: string | null; role: Role; status: UserStatus; linkedPersonId: number | null; createdAt: string }
export interface AdminUserPatch { displayName?: string; role?: Role; status?: UserStatus; linkedPersonId?: number }

export const adminUsersApi = {
  list: (status?: UserStatus) => http<AdminUser[]>(`/admin/users${status ? `?status=${status}` : ''}`),
  update: (id: number, patch: AdminUserPatch) => http<AdminUser>(`/admin/users/${id}`, { method: 'PUT', body: JSON.stringify(patch) }),
  resetUserPassword: (id: number, newPassword: string) =>
    http<void>(`/admin/users/${id}/password`, { method: 'PUT', body: JSON.stringify({ newPassword }) }),
  create: (b: { username: string; password: string; displayName: string; role: Role; linkedPersonId?: number }) =>
    http<AdminUser>('/admin/users', { method: 'POST', body: JSON.stringify(b) }),
}
