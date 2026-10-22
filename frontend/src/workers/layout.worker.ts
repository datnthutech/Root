import { computeLayout } from '../lib/layout'
import type { GraphData } from '../lib/types'

self.onmessage = (e: MessageEvent<{ id: number; graph: GraphData; collapsed: number[] }>) => {
  const { id, graph, collapsed } = e.data
  const result = computeLayout(graph, { collapsed: new Set(collapsed) })
  ;(self as unknown as Worker).postMessage({ id, result })
}
