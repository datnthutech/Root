import type { Side } from '../../api/tree'
import type { TreeParams } from './useTreeData'

const clamp = (n: number) => Math.max(0, Math.min(10, n))
const num = (s: string | null): number | null => { if (s === null || s === '') return null; const n = Number(s); return Number.isInteger(n) ? n : null }

export function parseParams(sp: URLSearchParams): TreeParams | null {
  const clan = num(sp.get('clan')), person = num(sp.get('person'))
  if (sp.has('clan') && clan === null) return null
  if (sp.has('person') && person === null) return null
  const mode = sp.get('mode') === 'focus' ? 'focus' : sp.get('mode') === 'clan' ? 'clan' : person !== null && clan === null ? 'focus' : 'clan'
  if (mode === 'clan' && clan === null) return null
  if (mode === 'focus' && person === null) return null
  const sideRaw = sp.get('side')
  const side: Side = sideRaw === 'paternal' || sideRaw === 'maternal' ? sideRaw : 'both'
  const upN = num(sp.get('up')), downN = num(sp.get('down'))
  return {
    mode, clanId: mode === 'clan' ? clan : null, side, personId: mode === 'focus' ? person : null,
    up: upN === null ? 3 : clamp(upN), down: downN === null ? 3 : clamp(downN),
  }
}

export function toSearch(p: TreeParams): Record<string, string> {
  return p.mode === 'clan'
    ? { mode: 'clan', clan: String(p.clanId), side: p.side }
    : { mode: 'focus', person: String(p.personId), up: String(p.up), down: String(p.down) }
}
