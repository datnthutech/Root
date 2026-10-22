import { useEffect, useRef } from 'react'
import type { LayoutResult } from '../../lib/layout'
import type { ViewInfo } from './TreeRenderer'

export function minimapTransform(layout: { width: number; height: number }, box: { w: number; h: number }) {
  const lw = Math.max(layout.width, 1), lh = Math.max(layout.height, 1)
  const scale = Math.min(box.w / lw, box.h / lh)
  return { scale, ox: (box.w - lw * scale) / 2, oy: (box.h - lh * scale) / 2 }
}

export function Minimap({ layout, view, onNavigate, width = 180, height = 120 }: {
  layout: LayoutResult | null; view: ViewInfo | null; onNavigate(x: number, y: number): void; width?: number; height?: number
}) {
  const ref = useRef<HTMLCanvasElement>(null)
  const dragging = useRef(false)

  useEffect(() => {
    const c = ref.current
    if (!c) return
    const ctx = c.getContext('2d')
    if (!ctx) return
    ctx.clearRect(0, 0, width, height)
    if (!layout) return
    const t = minimapTransform(layout, { w: width, h: height })
    ctx.fillStyle = '#7a8794'
    for (const n of layout.nodes) ctx.fillRect(t.ox + n.x * t.scale, t.oy + n.y * t.scale, Math.max(1.5, n.w * t.scale), Math.max(1.5, n.h * t.scale))
    if (view) {
      ctx.strokeStyle = '#e53935'
      ctx.lineWidth = 1.5
      ctx.strokeRect(t.ox + view.x * t.scale, t.oy + view.y * t.scale, view.w * t.scale, view.h * t.scale)
    }
  }, [layout, view, width, height])

  const navigate = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!layout) return
    const rect = e.currentTarget.getBoundingClientRect()
    const t = minimapTransform(layout, { w: width, h: height })
    onNavigate((e.clientX - rect.left - t.ox) / t.scale, (e.clientY - rect.top - t.oy) / t.scale)
  }

  return (
    <canvas ref={ref} width={width} height={height} aria-label="Bản đồ thu nhỏ"
      style={{ background: '#fffd', border: '1px solid #ccc', borderRadius: 6, touchAction: 'none', width, height }}
      onPointerDown={(e) => { dragging.current = true; e.currentTarget.setPointerCapture(e.pointerId); navigate(e) }}
      onPointerMove={(e) => { if (dragging.current) navigate(e) }}
      onPointerUp={() => { dragging.current = false }} />
  )
}
