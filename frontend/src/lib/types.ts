export type Gender = 'M' | 'F' | 'U'

export interface GraphPerson {
  id: number
  name: string
  gender: Gender
  birthYear: number | null
  deathYear: number | null
  alive: boolean
  thumb: string | null
  birthOrder: number | null
  orderLabel: string | null
  generation: number
  membership: 'BLOOD' | 'IN_LAW'
}

export interface GraphUnion {
  id: number
  partnerAId: number
  partnerBId: number
  order: number
  status: 'MARRIED' | 'DIVORCED' | 'WIDOWED'
}

export interface GraphLink {
  parentId: number
  childId: number
  unionId: number | null
  kind: 'BIOLOGICAL' | 'ADOPTED' | 'STEP'
}

export interface GraphData {
  clanId: number
  rootPersonId: number
  siblingCountMode: 'PER_UNION' | 'PER_FATHER'
  persons: GraphPerson[]
  unions: GraphUnion[]
  links: GraphLink[]
}
