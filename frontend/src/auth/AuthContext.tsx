import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { authApi, type CurrentUser, type TokenResponse } from '../api/auth'
import { setAccessToken, setUnauthorizedHandler } from '../api/http'

export interface AuthState {
  user: CurrentUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthState>({ user: null, loading: true, login: async () => {}, logout: async () => {} })
export const useAuth = () => useContext(AuthContext)

/** `onSessionReset` chạy mỗi khi người đăng nhập đổi (kể cả đăng xuất) để xoá dữ liệu cache của phiên cũ. */
export function AuthProvider({ children, onSessionReset }: { children: ReactNode; onSessionReset?: () => void }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)

  const prevUserId = useRef<number | null | undefined>(undefined)
  const resetRef = useRef(onSessionReset)
  resetRef.current = onSessionReset
  useEffect(() => {
    if (loading) return   // phiên ban đầu chưa xác định: chưa tính là đổi người dùng
    const id = user?.id ?? null
    if (prevUserId.current !== undefined && prevUserId.current !== id) resetRef.current?.()
    prevUserId.current = id
  }, [user?.id, loading])

  const accept = useCallback((r: TokenResponse) => { setAccessToken(r.accessToken); setUser(r.user) }, [])
  const clear = useCallback(() => { setAccessToken(null); setUser(null) }, [])

  useEffect(() => {
    setUnauthorizedHandler(async () => {
      try { accept(await authApi.refresh()); return true } catch { clear(); return false }
    })
    authApi.refresh().then(accept).catch(clear).finally(() => setLoading(false))
    return () => setUnauthorizedHandler(null)
  }, [accept, clear])

  const value = useMemo<AuthState>(() => ({
    user, loading,
    login: async (u, p) => accept(await authApi.login(u, p)),
    logout: async () => { try { await authApi.logout() } finally { clear() } },
  }), [user, loading, accept, clear])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
