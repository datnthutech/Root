import { http } from './http'

export interface AuditChange { field: string; label: string; from: string; to: string }
export interface AuditEntry {
  id: number; userId: number | null; username: string | null; displayName: string | null; entityType: string; entityId: number; action: string
  createdAt: string; summary: string; changes: AuditChange[]; revertible: boolean
}
export const AUDIT_PAGE = 30

export const auditApi = {
  list: (entityId: number, beforeId?: number, limit: number = AUDIT_PAGE) =>
    http<AuditEntry[]>(`/audit?entityId=${entityId}&limit=${limit}${beforeId ? `&beforeId=${beforeId}` : ''}`),
  revert: (id: number) => http<void>(`/audit/${id}/revert`, { method: 'POST' }),
}
