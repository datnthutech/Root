import { http } from './http'

export interface PersonProfile {
  id: number; fullName: string; nickname?: string; gender: 'M' | 'F' | 'U'
  birthDate?: string; birthPrecision?: string; birthYear?: number; birthLunar?: string
  deathDate?: string; deathPrecision?: string; deathYear?: number; deathAnniversaryLunar?: string
  alive: boolean; originPlace?: string; homePlace?: string; burialPlace?: string
  generation?: number; birthOrder?: number; orderLabel?: string; orderNeedsConfirm?: boolean
  photoUrl?: string; version: number; createdBy?: number; createdAt: string
}
export interface PersonRef { id: number; name: string; gender: 'M' | 'F' | 'U'; birthYear: number | null; deathYear: number | null; alive: boolean; orderLabel: string | null }
export interface SpouseFamily { unionId: number; unionOrder: number; status: string; startDate?: string; endDate?: string; partner: PersonRef; children: PersonRef[] }
export interface FamilyResponse {
  personId: number; parents: PersonRef[]; spouses: SpouseFamily[]; otherChildren: PersonRef[]
  fullSiblings: PersonRef[]; halfSiblingsSameFather: PersonRef[]; halfSiblingsSameMother: PersonRef[]
}

export const personApi = {
  profile: (id: number) => http<PersonProfile>(`/persons/${id}`),
  family: (id: number) => http<FamilyResponse>(`/persons/${id}/family`),
  biography: (id: number) => http<{ personId: number; biography: string | null }>(`/persons/${id}/biography`),
}
