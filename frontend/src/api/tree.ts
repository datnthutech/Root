import { get, set } from 'idb-keyval'
import { http, rawFetch, toFailure } from './http'
import type { GraphData } from '../lib/types'

export type Side = 'both' | 'paternal' | 'maternal'
export interface LoadedGraph { graph: GraphData; etag: string }
export interface ClanSummary { id: number; name: string; founderPersonId: number | null; memberCount: number }
export interface SearchHit { id: number; name: string; gender: 'M' | 'F' | 'U'; birthYear: number | null; deathYear: number | null; alive: boolean; generation: number | null }

async function safeGet<T>(key: string): Promise<T | undefined> { try { return await get<T>(key) } catch { return undefined } }
async function safeSet(key: string, v: unknown) { try { await set(key, v) } catch { /* cache không bắt buộc */ } }

async function fetchGraph(path: string, cacheKey: string | null): Promise<LoadedGraph> {
  const cached = cacheKey ? await safeGet<LoadedGraph>(cacheKey) : undefined
  const headers = new Headers()
  if (cached?.etag) headers.set('If-None-Match', cached.etag)
  let res = await rawFetch(path, { headers, cache: 'no-store' })
  if (res.status === 304) {
    if (cached) return cached
    res = await rawFetch(path, { cache: 'no-store' })   // mất bản lưu: tải lại không điều kiện
  }
  if (!res.ok) throw await toFailure(res)
  const graph = (await res.json()) as GraphData
  const etag = res.headers.get('ETag') ?? ''
  if (cacheKey && etag) await safeSet(cacheKey, { graph, etag })
  return { graph, etag }
}

export const loadClanGraph = (clanId: number, side: Side) =>
  fetchGraph(`/clans/${clanId}/graph?side=${side}`, `graph:${clanId}:${side}`)
export const loadFocusGraph = (personId: number, up: number, down: number) =>
  fetchGraph(`/persons/${personId}/focus?up=${up}&down=${down}`, null)
export const listClans = () => http<ClanSummary[]>('/clans')
export const searchPersons = (q: string) => http<SearchHit[]>(`/persons/search?q=${encodeURIComponent(q)}&limit=15`)
