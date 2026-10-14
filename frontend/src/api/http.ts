export type ApiErrorBody = { code: string; message: string; details: unknown }
export type ApiFailure = ApiErrorBody & { status: number }

let accessToken: string | null = null
export const setAccessToken = (t: string | null) => { accessToken = t }
export const getAccessToken = () => accessToken

export async function http<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  const res = await fetch(`/api/v1${path}`, { ...init, headers, credentials: 'include' })
  if (!res.ok) {
    let body: ApiErrorBody = { code: 'UNKNOWN', message: res.statusText, details: null }
    try { body = await res.json() } catch { /* giữ mặc định */ }
    throw { ...body, status: res.status } satisfies ApiFailure
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
