import { Application, Container, Graphics, Text } from 'pixi.js'
import { Viewport } from 'pixi-viewport'
import type { GraphData, GraphPerson } from '../../lib/types'
import type { LayoutNode, LayoutResult } from '../../lib/layout'
import { QuadTree } from '../../lib/quadtree'
import { lodForScale, type Lod } from '../../lib/lod'

export interface RendererHandlers {
  onHover?: (personId: number | null, screen: { x: number; y: number }) => void
  onClick?: (personId: number, screen: { x: number; y: number }) => void
}

const COLOR = { M: 0x4a90d9, F: 0xe57fa7, U: 0x9aa0a6 } as const

class NodeView {
  container = new Container()
  bg = new Graphics()
  label = new Text('', { fontSize: 13, fill: 0xffffff, wordWrap: true, wordWrapWidth: 104, lineHeight: 16 })
  constructor() {
    this.label.position.set(8, 8)
    this.container.addChild(this.bg, this.label)
  }
  render(node: LayoutNode, p: GraphPerson, lod: Lod) {
    this.container.position.set(node.x, node.y)
    this.bg.clear().beginFill(COLOR[p.gender]).drawRoundedRect(0, 0, node.w, node.h, 8).endFill()
    const years = `${p.birthYear ?? '?'}${p.alive ? '' : ` – ${p.deathYear ?? '?'}`}`
    const prefix = lod === 'full' && p.orderLabel ? `${p.orderLabel} · ` : ''
    this.label.text = `${prefix}${p.name}\n${years}`
  }
}

export class TreeRenderer {
  private app: Application
  private viewport: Viewport
  private edges = new Graphics()
  private dots = new Graphics()
  private views = new Container()
  private pool: NodeView[] = []
  private active = new Map<number, NodeView>()
  private index: QuadTree | null = null
  private persons = new Map<number, GraphPerson>()
  private nodes = new Map<number, LayoutNode>()
  private lod: Lod = 'dot'
  private dirty = true
  private hoverId: number | null = null

  constructor(private canvas: HTMLCanvasElement, private handlers: RendererHandlers = {}) {
    this.app = new Application({
      view: canvas, resizeTo: canvas.parentElement ?? window, backgroundColor: 0xf5f6f8,
      antialias: true, resolution: window.devicePixelRatio || 1, autoDensity: true,
    })
    this.viewport = new Viewport({
      screenWidth: this.app.screen.width, screenHeight: this.app.screen.height,
      worldWidth: 1000, worldHeight: 1000, events: this.app.renderer.events,
    })
    this.viewport.drag().pinch().wheel().decelerate().clampZoom({ minScale: 0.02, maxScale: 2 })
    this.viewport.addChild(this.edges, this.dots, this.views)
    this.app.stage.addChild(this.viewport)

    this.viewport.on('moved', () => { this.dirty = true })
    this.viewport.on('zoomed', () => { this.dirty = true })
    this.app.renderer.on('resize', (w: number, h: number) => { this.viewport.resize(w, h); this.dirty = true })
    this.app.ticker.add(() => { if (this.dirty) { this.dirty = false; this.refresh() } })

    this.viewport.on('pointermove', (e) => {
      const w = this.viewport.toWorld(e.global.x, e.global.y)
      const id = this.index?.queryPoint(w.x, w.y)?.id ?? null
      if (id !== this.hoverId) {
        this.hoverId = id
        this.canvas.style.cursor = id === null ? 'grab' : 'pointer'
        this.handlers.onHover?.(id, { x: e.global.x, y: e.global.y })
      }
    })
    this.viewport.on('clicked', (e) => {
      const id = this.index?.queryPoint(e.world.x, e.world.y)?.id
      if (id !== undefined) this.handlers.onClick?.(id, { x: e.screen.x, y: e.screen.y })
    })
  }

  setData(graph: GraphData, layout: LayoutResult): void {
    this.persons = new Map(graph.persons.map((p) => [p.id, p]))
    this.nodes = new Map(layout.nodes.map((n) => [n.id, n]))
    this.releaseAll()

    this.index = new QuadTree({ x: 0, y: 0, w: Math.max(layout.width, 1), h: Math.max(layout.height, 1) })
    for (const n of layout.nodes) this.index.insert({ id: n.id, x: n.x, y: n.y, w: n.w, h: n.h })

    this.edges.clear().lineStyle({ width: 1, color: 0xaab0b8, native: true })
    for (const e of layout.edges) {
      this.edges.moveTo(e.x1, e.y1)
      if (e.kind === 'union') this.edges.lineTo(e.x2, e.y2)
      else {
        const midY = (e.y1 + e.y2) / 2
        this.edges.lineTo(e.x1, midY).lineTo(e.x2, midY).lineTo(e.x2, e.y2)
      }
    }

    this.dots.clear()
    for (const n of layout.nodes) {
      const p = this.persons.get(n.id)!
      this.dots.beginFill(COLOR[p.gender]).drawCircle(n.x + n.w / 2, n.y + n.h / 2, 22).endFill()
    }

    this.viewport.resize(this.app.screen.width, this.app.screen.height, layout.width, layout.height)
    this.viewport.fit(true, layout.width, layout.height)
    this.viewport.moveCenter(layout.width / 2, layout.height / 2)
    this.dirty = true
  }

  focusOn(personId: number, scale = 1): void {
    const n = this.nodes.get(personId)
    if (!n) return
    this.viewport.setZoom(scale, true)
    this.viewport.moveCenter(n.x + n.w / 2, n.y + n.h / 2)
    this.dirty = true
  }

  fps(): number { return this.app.ticker.FPS }

  benchmark(durationMs: number): Promise<{ avgFps: number; minFps: number; frames: number }> {
    return new Promise((resolve) => {
      const lw = this.viewport.worldWidth, lh = this.viewport.worldHeight
      this.viewport.setZoom(0.5, true)
      const t0 = performance.now()
      let frames = 0, minFps = Infinity
      const tick = () => {
        const t = (performance.now() - t0) / durationMs
        if (t >= 1) {
          this.app.ticker.remove(tick)
          const secs = (performance.now() - t0) / 1000
          resolve({ avgFps: frames / secs, minFps: Number.isFinite(minFps) ? minFps : 0, frames })
          return
        }
        const sx = 0.5 + 0.5 * Math.sin(t * Math.PI * 4)
        const sy = 0.5 + 0.5 * Math.sin(t * Math.PI * 6)
        this.viewport.moveCenter(sx * lw, sy * lh)
        this.viewport.setZoom(0.2 + 0.7 * (0.5 + 0.5 * Math.sin(t * Math.PI * 8)), true)
        this.dirty = true
        frames++
        if (this.app.ticker.deltaMS > 0) minFps = Math.min(minFps, 1000 / this.app.ticker.deltaMS)
      }
      this.app.ticker.add(tick)
    })
  }

  destroy(): void {
    this.app.destroy(false, { children: true })
  }

  private refresh(): void {
    const lod = lodForScale(this.viewport.scale.x)
    if (lod !== this.lod) { this.lod = lod; this.releaseAll() }
    this.dots.visible = lod === 'dot'
    this.views.visible = lod !== 'dot'
    if (lod === 'dot' || !this.index) return

    const b = this.viewport.getVisibleBounds()
    const visible = this.index.queryRect(b.x, b.y, b.width, b.height)
    const keep = new Set<number>()
    for (const it of visible) {
      keep.add(it.id)
      if (!this.active.has(it.id)) {
        const v = this.pool.pop() ?? new NodeView()
        v.render(this.nodes.get(it.id)!, this.persons.get(it.id)!, lod)
        this.views.addChild(v.container)
        this.active.set(it.id, v)
      }
    }
    for (const [id, v] of this.active) {
      if (!keep.has(id)) { this.views.removeChild(v.container); this.pool.push(v); this.active.delete(id) }
    }
  }

  private releaseAll(): void {
    for (const v of this.active.values()) { this.views.removeChild(v.container); this.pool.push(v) }
    this.active.clear()
  }
}
