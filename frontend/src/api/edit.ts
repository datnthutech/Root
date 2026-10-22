import { http, rawFetch, toFailure } from './http'
import type { PersonProfile } from './person'
import type { Precision } from '../lib/dateField'

export interface PersonInput {
  fullName: string; nickname?: string; gender: 'M' | 'F' | 'U'
  birthDate?: string | null; birthPrecision?: Precision; birthLunar?: string
  deathDate?: string | null; deathPrecision?: Precision; deathAnniversaryLunar?: string
  alive: boolean; originPlace?: string; homePlace?: string; burialPlace?: string; biography?: string; version?: number
}
export interface Warning { code: string; message: string }
export interface LinkBody { parentId: number; childId: number; kind?: 'BIOLOGICAL' | 'ADOPTED' | 'STEP'; unionId?: number }
export interface LinkDto { id: number; parentId: number; childId: number; kind: string; unionId: number | null; countsInOrder: boolean }
export interface UnionDto { id: number; partnerAId: number; partnerBId: number; order: number; status: string; startDate?: string; endDate?: string }
export interface UnionBody { order?: number; status?: 'MARRIED' | 'DIVORCED' | 'WIDOWED'; startDate?: string; endDate?: string }

const json = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) })

export const editApi = {
  createPerson: (i: PersonInput) => http<PersonProfile>('/persons', json('POST', i)),
  updatePerson: (id: number, i: PersonInput) => http<PersonProfile>(`/persons/${id}`, json('PUT', i)),
  deletePerson: (id: number) => http<void>(`/persons/${id}`, { method: 'DELETE' }),
  setBirthOrder: (id: number, order: number | null) =>
    http<{ personId: number; birthOrderOverride: number | null }>(`/persons/${id}/birth-order`, json('PUT', { order })),
  uploadPhoto: async (id: number, file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    const res = await rawFetch(`/persons/${id}/photo`, { method: 'PUT', body: fd })
    if (!res.ok) throw await toFailure(res)
    return (await res.json()) as { personId: number; photoKey: string; thumb: string }
  },
  addParentChild: (b: LinkBody) => http<{ link: LinkDto; warnings: Warning[] }>('/relations/parent-child', json('POST', b)),
  addChild: (b: LinkBody) => http<{ links: LinkDto[]; warnings: Warning[] }>('/relations/children', json('POST', b)),
  removeParentChild: (id: number) => http<void>(`/relations/parent-child/${id}`, { method: 'DELETE' }),
  createUnion: (b: UnionBody & { partnerAId: number; partnerBId: number }) => http<UnionDto>('/relations/unions', json('POST', b)),
  updateUnion: (id: number, b: UnionBody) => http<UnionDto>(`/relations/unions/${id}`, json('PUT', b)),
  deleteUnion: (id: number) => http<void>(`/relations/unions/${id}`, { method: 'DELETE' }),
}
