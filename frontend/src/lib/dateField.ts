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
