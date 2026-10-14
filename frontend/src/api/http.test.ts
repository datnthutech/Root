import { http, setAccessToken, setUnauthorizedHandler } from './http'

afterEach(() => { vi.unstubAllGlobals(); setUnauthorizedHandler(null); setAccessToken(null) })

test('gặp 401 thì refresh một lần rồi gọi lại', async () => {
  let dataCalls = 0
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url === '/api/v1/data') {
      dataCalls++
      return dataCalls === 1
        ? new Response('{"code":"UNAUTHENTICATED","message":"x","details":null}', { status: 401 })
        : new Response('{"ok":true}', { status: 200 })
    }
    return new Response('{}', { status: 200 })
  }))
  setUnauthorizedHandler(async () => { setAccessToken('new'); return true })
  expect(await http('/data')).toEqual({ ok: true })
  expect(dataCalls).toBe(2)
})

test('refresh thất bại thì ném lỗi 401 gốc, không lặp vô hạn', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response('{"code":"UNAUTHENTICATED","message":"x","details":null}', { status: 401 })))
  setUnauthorizedHandler(async () => false)
  await expect(http('/data')).rejects.toMatchObject({ status: 401, code: 'UNAUTHENTICATED' })
})

test('hai lời gọi refresh đồng thời chỉ gửi một request (single-flight)', async () => {
  const { authApi } = await import('./auth')
  let calls = 0
  vi.stubGlobal('fetch', vi.fn(async () => {
    calls++
    await new Promise((r) => setTimeout(r, 10))
    return new Response('{"accessToken":"t","expiresInSeconds":900,"user":{"id":1,"username":"a","displayName":null,"role":"VIEWER","linkedPersonId":null}}', { status: 200 })
  }))
  const [a, b] = await Promise.all([authApi.refresh(), authApi.refresh()])
  expect(calls).toBe(1)
  expect(a).toEqual(b)
})

test('đường dẫn /auth/ không kích hoạt refresh', async () => {
  const handler = vi.fn(async () => true)
  setUnauthorizedHandler(handler)
  vi.stubGlobal('fetch', vi.fn(async () => new Response('{"code":"BAD_CREDENTIALS","message":"x","details":null}', { status: 401 })))
  await expect(http('/auth/login', { method: 'POST', body: '{}' })).rejects.toMatchObject({ code: 'BAD_CREDENTIALS' })
  expect(handler).not.toHaveBeenCalled()
})
