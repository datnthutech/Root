import { http } from './http'

export interface ClanView { id: number; name: string; founderPersonId: number | null; description: string | null; siblingCountMode: 'PER_UNION' | 'PER_FATHER'; useUtLabel: boolean; memberCount: number }
export interface ClanBody { name: string; founderPersonId?: number | null; description?: string; siblingCountMode?: 'PER_UNION' | 'PER_FATHER'; useUtLabel?: boolean }

export const adminClansApi = {
  list: () => http<ClanView[]>('/admin/clans'),
  create: (b: ClanBody) => http<ClanView>('/admin/clans', { method: 'POST', body: JSON.stringify(b) }),
  update: (id: number, b: ClanBody) => http<ClanView>(`/admin/clans/${id}`, { method: 'PUT', body: JSON.stringify(b) }),
}
