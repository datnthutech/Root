import { useEffect, useRef, useState } from 'react'
import { generateFakeGraph } from '../../lib/fakeData'
import { runLayout } from './layoutClient'
import { TreeRenderer } from './TreeRenderer'

export default function ProtoPage() {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const rendererRef = useRef<TreeRenderer | null>(null)
  const [total, setTotal] = useState(2000)
  const [layoutMs, setLayoutMs] = useState<number | null>(null)
  const [fps, setFps] = useState(0)
  const [hover, setHover] = useState<{ id: number; x: number; y: number } | null>(null)
  const [bench, setBench] = useState<string>('')

  useEffect(() => {
    const r = new TreeRenderer(canvasRef.current!, {
      onHover: (id, s) => setHover(id === null ? null : { id, x: s.x, y: s.y }),
      onClick: (id) => console.info('click', id),
    })
    rendererRef.current = r
    const timer = setInterval(() => setFps(Math.round(r.fps())), 500)
    return () => { clearInterval(timer); r.destroy() }
  }, [])

  useEffect(() => {
    let cancelled = false
    const graph = generateFakeGraph(total, 3)
    const t0 = performance.now()
    runLayout(graph).then((layout) => {
      if (cancelled) return
      setLayoutMs(Math.round(performance.now() - t0))
      rendererRef.current?.setData(graph, layout)
    })
    return () => { cancelled = true }
  }, [total])

  const runBench = async () => {
    setBench('đang đo...')
    const r = await rendererRef.current!.benchmark(6000)
    setBench(`TB ${r.avgFps.toFixed(1)} fps, thấp nhất ${r.minFps.toFixed(1)} fps (${r.frames} khung)`)
  }

  return (
    <div style={{ position: 'fixed', inset: 0 }}>
      <canvas ref={canvasRef} style={{ width: '100%', height: '100%', display: 'block' }} />
      <div style={{ position: 'absolute', top: 8, left: 8, background: '#fffd', padding: 8, borderRadius: 6 }}>
        <select value={total} onChange={(e) => setTotal(Number(e.target.value))}>
          <option value={2000}>2.000 người</option>
          <option value={10000}>10.000 người</option>
        </select>{' '}
        <button onClick={runBench}>Đo fps 6 giây</button>
        <div>Bố cục: {layoutMs ?? '...'} ms · FPS: {fps}</div>
        <div>{bench}</div>
      </div>
      {hover && (
        <div style={{ position: 'absolute', left: hover.x + 12, top: hover.y + 12, background: '#222', color: '#fff', padding: '4px 8px', borderRadius: 4, pointerEvents: 'none' }}>
          Người #{hover.id}
        </div>
      )}
    </div>
  )
}
