import { useCallback, useEffect, useRef, useState } from 'react'
import type { GraphData } from '../../lib/types'
import type { LayoutResult } from '../../lib/layout'
import { defaultCollapsed, DEFAULT_COLLAPSE_DEPTH } from '../../lib/defaultCollapsed'
import { getCachedLayout, layoutCacheKey, setCachedLayout } from '../../lib/layoutCache'
import { loadClanGraph, loadFocusGraph, type Side } from '../../api/tree'
import type { ApiFailure } from '../../api/http'
import { runLayout } from './layoutClient'

export interface TreeParams { mode: 'clan' | 'focus'; clanId: number | null; side: Side; personId: number | null; up: number; down: number }

export function useTreeData(p: TreeParams) {
  const [base, setBase] = useState<{ graph: GraphData; etag: string } | null>(null)
  const [collapsed, setCollapsed] = useState<Set<number>>(new Set())
  const [layout, setLayout] = useState<LayoutResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<ApiFailure | null>(null)
  const [keepCamera, setKeepCamera] = useState(false)
  const lastGraph = useRef<GraphData | null>(null)

  const sourceKey = `${p.mode}:${p.clanId}:${p.side}:${p.personId}:${p.up}:${p.down}`
  useEffect(() => {
    if ((p.mode === 'clan' && p.clanId === null) || (p.mode === 'focus' && p.personId === null)) return
    let cancelled = false
    setLoading(true); setError(null)
    const load = p.mode === 'clan' ? loadClanGraph(p.clanId!, p.side) : loadFocusGraph(p.personId!, p.up, p.down)
    load.then((r) => {
      if (cancelled) return
      setCollapsed(p.mode === 'clan' ? defaultCollapsed(r.graph, DEFAULT_COLLAPSE_DEPTH) : new Set())
      setKeepCamera(false)
      setBase(r)
    }).catch((e) => { if (!cancelled) { setError(e as ApiFailure); setLoading(false) } })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceKey])

  useEffect(() => {
    if (!base) return
    let cancelled = false
    const ids = [...collapsed]
    const mark = p.mode === 'clan' ? p.side : `focus:${p.personId}:${p.up}:${p.down}`
    const key = layoutCacheKey(base.graph.clanId, base.etag, mark, ids)
    ;(async () => {
      let result = base.etag ? await getCachedLayout(key) : undefined
      if (!result) {
        result = await runLayout(base.graph, ids)
        if (base.etag) void setCachedLayout(key, result)
      }
      if (cancelled) return
      setKeepCamera(lastGraph.current === base.graph)
      lastGraph.current = base.graph
      setLayout(result)
      setLoading(false)
    })()
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [base, collapsed])

  const toggle = useCallback((id: number) => {
    setCollapsed((prev) => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n })
  }, [])

  return { graph: base?.graph ?? null, layout, loading, error, collapsed, toggle, keepCamera }
}
