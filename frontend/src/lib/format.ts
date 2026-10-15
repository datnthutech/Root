export function formatDate(date: string | null | undefined, precision: string | null | undefined): string {
  if (!date || !precision || precision === 'UNKNOWN') return 'Không rõ'
  const [y, m, d] = date.split('-')
  if (precision === 'YEAR') return y
  if (precision === 'MONTH') return `${m}/${y}`
  return `${d}/${m}/${y}`
}

export function lifespan(p: { birthYear?: number | null; deathYear?: number | null; alive: boolean }): string {
  const by = p.birthYear ?? '?'
  return p.alive ? `${by}` : `${by} – ${p.deathYear ?? '?'}`
}

export function truncate(s: string, max: number): string {
  return s.length <= max ? s : `${s.slice(0, max - 1)}…`
}

export function formatDateTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`
}
