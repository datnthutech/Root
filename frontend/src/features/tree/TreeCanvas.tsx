import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'
import type { GraphData } from '../../lib/types'
import type { LayoutResult } from '../../lib/layout'
import { PhotoStore } from '../../lib/photoStore'
import { rawFetch } from '../../api/http'
import { TreeRenderer, type ViewInfo } from './TreeRenderer'

export interface TreeCanvasHandle {
  focusOn(id: number, scale?: number): void
  fit(): void
  moveCenterTo(x: number, y: number): void
}
interface Props {
  graph: GraphData | null
  layout: LayoutResult | null
  keepCamera: boolean
  selectedId: number | null
  onHover(id: number | null, pos: { x: number; y: number }): void
  onClick(id: number): void
  onToggle(id: number): void
  onViewChange(v: ViewInfo): void
}

export const TreeCanvas = forwardRef<TreeCanvasHandle, Props>(function TreeCanvas(props, ref) {
  const hostRef = useRef<HTMLDivElement>(null)
  const rendererRef = useRef<TreeRenderer | null>(null)
  const propsRef = useRef(props)
  propsRef.current = props

  useEffect(() => {
    // Fresh <canvas> per effect run: Pixi destroy() loses the WebGL context of its canvas (StrictMode remount).
    const canvas = document.createElement('canvas')
    canvas.style.width = '100%'
    canvas.style.height = '100%'
    canvas.style.display = 'block'
    canvas.style.touchAction = 'none'
    hostRef.current!.appendChild(canvas)
    const r = new TreeRenderer(canvas, {
      onHover: (id, pos) => propsRef.current.onHover(id, pos),
      onClick: (id) => propsRef.current.onClick(id),
      onToggle: (id) => propsRef.current.onToggle(id),
      onViewChange: (v) => propsRef.current.onViewChange(v),
    })
    const photos = new PhotoStore(async (url) => {
      const res = await rawFetch(url)
      return res.ok ? res.blob() : null
    }, () => r.invalidate())
    r.setPhotoStore(photos)
    rendererRef.current = r
    const p = propsRef.current
    if (p.graph && p.layout) r.setData(p.graph, p.layout, { keepCamera: p.keepCamera })
    r.setSelected(p.selectedId)
    return () => { photos.destroy(); r.destroy(); rendererRef.current = null; canvas.remove() }
  }, [])

  useEffect(() => {
    if (props.graph && props.layout) rendererRef.current?.setData(props.graph, props.layout, { keepCamera: props.keepCamera })
  }, [props.graph, props.layout, props.keepCamera])

  useEffect(() => { rendererRef.current?.setSelected(props.selectedId) }, [props.selectedId, props.layout])

  useImperativeHandle(ref, () => ({
    focusOn: (id, scale) => rendererRef.current?.focusOn(id, scale),
    fit: () => rendererRef.current?.fit(),
    moveCenterTo: (x, y) => rendererRef.current?.moveCenterTo(x, y),
  }), [])

  return <div ref={hostRef} style={{ width: '100%', height: '100%', position: 'relative' }} />
})
