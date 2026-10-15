import { rawFetch, toFailure } from './http'

export interface ImportError { sheet: string; row: number; column: string; code: string; message: string }
export interface ImportReport {
  dryRun: boolean; persons: number; unions: number; links: number; errors: ImportError[]; warnings: ImportError[]; clanId: number | null
}

export const importApi = {
  async downloadTemplate(): Promise<void> {
    const res = await rawFetch('/import/template')
    if (!res.ok) throw await toFailure(res)
    const url = URL.createObjectURL(await res.blob())
    const a = document.createElement('a')
    a.href = url
    a.download = 'mau-nhap-gia-pha.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  },
  async upload(file: File, dryRun: boolean): Promise<ImportReport> {
    const fd = new FormData()
    fd.append('file', file)
    const res = await rawFetch(`/import/excel?dryRun=${dryRun}`, { method: 'POST', body: fd })
    if (res.status === 422) {
      const body = (await res.clone().json().catch(() => null)) as { details?: ImportReport } | null
      if (body?.details && Array.isArray(body.details.errors)) return body.details
      throw await toFailure(res)
    }
    if (!res.ok) throw await toFailure(res)
    return (await res.json()) as ImportReport
  },
}
