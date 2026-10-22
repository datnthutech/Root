import dayjs, { type Dayjs } from 'dayjs'

export type Precision = 'DAY' | 'MONTH' | 'YEAR' | 'UNKNOWN'

export function toIso(d: Dayjs | null | undefined, precision: Precision): string | null {
  if (!d || precision === 'UNKNOWN') return null
  if (precision === 'YEAR') return `${d.format('YYYY')}-01-01`
  if (precision === 'MONTH') return `${d.format('YYYY-MM')}-01`
  return d.format('YYYY-MM-DD')
}

export function fromIso(s: string | null | undefined): Dayjs | null {
  return s ? dayjs(s) : null
}

const LEN: Record<Precision, number> = { YEAR: 4, MONTH: 7, DAY: 10, UNKNOWN: 0 }

/** True khi ngày mất rõ ràng trước ngày sinh; so ở độ chính xác thô hơn của hai ngày. */
export function deathBeforeBirth(birth: string | null, bp: Precision, death: string | null, dp: Precision): boolean {
  if (!birth || !death) return false
  const n = Math.min(LEN[bp], LEN[dp])
  if (n === 0) return false
  return death.slice(0, n) < birth.slice(0, n)
}
