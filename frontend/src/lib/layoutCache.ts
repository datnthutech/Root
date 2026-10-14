import { get, set } from 'idb-keyval'
import type { LayoutResult } from './layout'

export function layoutCacheKey(clanId: number, etag: string, side: string, collapsed: number[]): string {
  return `layout:${clanId}:${etag}:${side}:${[...collapsed].sort((a, b) => a - b).join(',')}`
}

export async function getCachedLayout(key: string): Promise<LayoutResult | undefined> {
  try { return await get<LayoutResult>(key) } catch { return undefined }
}

export async function setCachedLayout(key: string, r: LayoutResult): Promise<void> {
  try { await set(key, r) } catch { /* cache không bắt buộc */ }
}
