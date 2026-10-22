import { http } from './http'

export type Role = 'ADMIN' | 'EDITOR' | 'VIEWER'
export interface CurrentUser { id: number; username: string; displayName: string | null; role: Role; linkedPersonId: number | null }
export interface TokenResponse { accessToken: string; expiresInSeconds: number; user: CurrentUser }

let inflight: Promise<TokenResponse> | null = null

export const authApi = {
  login: (username: string, password: string) =>
    http<TokenResponse>('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  refresh: (): Promise<TokenResponse> => {
    if (!inflight) {
      inflight = http<TokenResponse>('/auth/refresh', { method: 'POST' }).finally(() => { inflight = null })
    }
    return inflight
  },
  register: (username: string, password: string, displayName: string) =>
    http<{ id: number; status: string }>('/auth/register', { method: 'POST', body: JSON.stringify({ username, password, displayName }) }),
  changePassword: (currentPassword: string, newPassword: string) =>
    http<void>('/me/password', { method: 'PUT', body: JSON.stringify({ currentPassword, newPassword }) }),
  logout: () => http<void>('/auth/logout', { method: 'POST' }),
}
