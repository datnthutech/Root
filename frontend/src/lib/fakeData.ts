import type { GraphData, GraphLink, GraphPerson, GraphUnion } from './types'

function mulberry32(seed: number) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export function generateFakeGraph(total: number, seed = 1): GraphData {
  const rnd = mulberry32(seed)
  const persons: GraphPerson[] = []
  const unions: GraphUnion[] = []
  const links: GraphLink[] = []
  let nextId = 1
  let nextUnion = 1

  const make = (gender: 'M' | 'F', generation: number, birthYear: number, membership: 'BLOOD' | 'IN_LAW'): GraphPerson => {
    const p: GraphPerson = {
      id: nextId++, name: `Người ${nextId - 1}`, gender, birthYear,
      deathYear: birthYear < 1950 ? birthYear + 60 + Math.floor(rnd() * 25) : null,
      alive: birthYear >= 1950, thumb: null, birthOrder: null, orderLabel: null, generation, membership,
    }
    persons.push(p)
    return p
  }

  const root = make('M', 1, 1800, 'BLOOD')
  const queue: GraphPerson[] = [root]
  while (queue.length && persons.length < total) {
    const p = queue.shift()!
    if (persons.length >= total) break
    const spouse = make(p.gender === 'M' ? 'F' : 'M', p.generation, (p.birthYear ?? 1800) + Math.floor(rnd() * 6), 'IN_LAW')
    const union: GraphUnion = { id: nextUnion++, partnerAId: p.id, partnerBId: spouse.id, order: 1, status: 'MARRIED' }
    unions.push(union)
    const kids = 1 + Math.floor(rnd() * 4)
    for (let i = 0; i < kids && persons.length < total; i++) {
      const child = make(rnd() < 0.5 ? 'M' : 'F', p.generation + 1, (p.birthYear ?? 1800) + 22 + i * 2, 'BLOOD')
      child.birthOrder = i + 2
      const [father, mother] = p.gender === 'M' ? [p, spouse] : [spouse, p]
      links.push({ parentId: father.id, childId: child.id, unionId: union.id, kind: 'BIOLOGICAL' })
      links.push({ parentId: mother.id, childId: child.id, unionId: union.id, kind: 'BIOLOGICAL' })
      queue.push(child)
    }
  }
  return { clanId: 1, rootPersonId: root.id, siblingCountMode: 'PER_UNION', persons, unions, links }
}
