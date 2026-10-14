import type { LayoutResult } from '../../lib/layout'
import type { GraphData } from '../../lib/types'

let worker: Worker | null = null
let seq = 0
const pending = new Map<number, (r: LayoutResult) => void>()

function getWorker(): Worker {
  if (!worker) {
    worker = new Worker(new URL('../../workers/layout.worker.ts', import.meta.url), { type: 'module' })
    worker.onmessage = (e: MessageEvent<{ id: number; result: LayoutResult }>) => {
      pending.get(e.data.id)?.(e.data.result)
      pending.delete(e.data.id)
    }
  }
  return worker
}

export function runLayout(graph: GraphData, collapsed: number[] = []): Promise<LayoutResult> {
  return new Promise((resolve) => {
    const id = ++seq
    pending.set(id, resolve)
    getWorker().postMessage({ id, graph, collapsed })
  })
}
