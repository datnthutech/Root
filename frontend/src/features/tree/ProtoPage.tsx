import { useEffect, useRef, useState } from 'react'
import { generateFakeGraph } from '../../lib/fakeData'
import { runLayout } from './layoutClient'
import { TreeRenderer } from './TreeRenderer'

export default function ProtoPage() {
  const hostRef = useRef<HTMLDivElement>(null)
  const rendererRef = useRef<TreeRenderer | null>(null)
  const [total, setTotal] = useState(2000)
  const [layoutMs, setLayoutMs] = useState<number | null>(null)
  const [fps, setFps] = useState(0)
  const [hover, setHover] = useState<{ id: number; x: number; y: number } | null>(null)
  const [bench, setBench] = useState<string>('')

  useEffect(() => {
    // Canvas tạo mới mỗi lần mount: Pixi destroy làm mất WebGL context, StrictMode sẽ mount lại trên cùng canvas.
    const canvas = document.createElement('canvas')
    canvas.style.cssText = 'width:100%;height:100%;display:block'
    hostRef.current!.appendChild(canvas)
    const r = new TreeRenderer(canvas, {
      onHover: (id, s) => setHover(id === null ? null : { id, x: s.x, y: s.y }),
      onClick: (id) => console.info('click', id),
    })
    rendererRef.current = r
    const timer = setInterval(() => setFps(Math.round(r.fps())), 500)
    return () => { clearInterval(timer); r.destroy(); canvas.remove(); rendererRef.current = null }
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
      <div ref={hostRef} style={{ position: 'absolute', inset: 0 }} />
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
