import { importApi } from './importApi'

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const report = { dryRun: true, persons: 1, unions: 0, links: 0, errors: [], warnings: [], clanId: null }
afterEach(() => vi.unstubAllGlobals())

test('upload dùng /import/excel?dryRun= và multipart', async () => {
  const f = vi.fn(async () => json(200, report))
  vi.stubGlobal('fetch', f)
  await importApi.upload(new File(['x'], 'a.xlsx'), true)
  await importApi.upload(new File(['x'], 'a.xlsx'), false)
  const calls = f.mock.calls as unknown as [string, RequestInit][]
  expect(calls[0][0]).toBe('/api/v1/import/excel?dryRun=true')
  expect(calls[1][0]).toBe('/api/v1/import/excel?dryRun=false')
  expect(calls[0][1].method).toBe('POST')
  expect(calls[0][1].body).toBeInstanceOf(FormData)
})

test('422 có details trả về báo cáo', async () => {
  const r = { ...report, dryRun: false, errors: [{ sheet: 'a', row: 1, column: 'b', code: 'c', message: 'm' }] }
  vi.stubGlobal('fetch', vi.fn(async () => json(422, { code: 'IMPORT_INVALID', message: 'x', details: r })))
  expect(await importApi.upload(new File(['x'], 'a.xlsx'), false)).toEqual(r)
})

test('422 không có details ném lỗi thường', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => json(422, { code: 'IMPORT_INVALID', message: 'x', details: null })))
  await expect(importApi.upload(new File(['x'], 'a.xlsx'), false)).rejects.toMatchObject({ code: 'IMPORT_INVALID', status: 422 })
})

test('lỗi khác ném ApiFailure', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => json(413, { code: 'PAYLOAD_TOO_LARGE', message: 'x', details: null })))
  await expect(importApi.upload(new File(['x'], 'a.xlsx'), true)).rejects.toMatchObject({ status: 413 })
})

test('downloadTemplate gọi /import/template', async () => {
  const f = vi.fn(async () => new Response('x', { status: 200 }))
  vi.stubGlobal('fetch', f)
  URL.createObjectURL = vi.fn(() => 'blob:x')
  URL.revokeObjectURL = vi.fn()
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  await importApi.downloadTemplate()
  expect((f.mock.calls as unknown as [string][])[0][0]).toBe('/api/v1/import/template')
})
