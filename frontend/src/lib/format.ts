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
