import { Application, Container, Graphics, Point, Sprite, Text, Texture } from 'pixi.js'
import { Viewport } from 'pixi-viewport'
import type { GraphData, GraphPerson } from '../../lib/types'
import type { LayoutNode, LayoutResult } from '../../lib/layout'
import { QuadTree } from '../../lib/quadtree'
import { lodForScale, type Lod } from '../../lib/lod'
import { lifespan, truncate } from '../../lib/format'
import type { PhotoStore } from '../../lib/photoStore'

export interface ViewInfo { x: number; y: number; w: number; h: number; scale: number }
export interface RendererHandlers {
  onHover?: (personId: number | null, screen: { x: number; y: number }) => void
  onClick?: (personId: number, screen: { x: number; y: number }) => void
  onToggle?: (personId: number) => void
  onViewChange?: (v: ViewInfo) => void
}

const COLOR = { M: 0x4a90d9, F: 0xe57fa7, U: 0x9aa0a6 } as const
const TOGGLE_PAD = 14 // phần nút +/- thò ra dưới ô

class NodeView {
  container = new Container()
  bg = new Graphics()
  avatarMask = new Graphics()
  avatar = new Sprite()
  name = new Text('', { fontSize: 12, fill: '#ffffff', wordWrap: true, breakWords: true, lineHeight: 14 })
  sub = new Text('', { fontSize: 11, fill: '#ffffff' })
  toggle = new Graphics()
  toggleLabel = new Text('', { fontSize: 13, fill: '#333333', fontWeight: 'bold' })

  constructor() {
    this.avatar.mask = this.avatarMask
    this.container.addChild(this.bg, this.avatar, this.avatarMask, this.name, this.sub, this.toggle, this.toggleLabel)
  }

  render(n: LayoutNode, p: GraphPerson, lod: Lod, selected: boolean, photo: Texture | null) {
    this.container.position.set(n.x, n.y)
    this.bg.clear()
    if (selected) this.bg.lineStyle(3, 0xffc107)
    this.bg.beginFill(COLOR[p.gender]).drawRoundedRect(0, 0, n.w, n.h, 8).endFill()

    const years = lifespan(p)
    if (lod === 'full') {
      this.bg.lineStyle(0).beginFill(0xffffff, 0.35).drawCircle(24, 22, 18).endFill()
      this.avatarMask.clear().beginFill(0x000000).drawCircle(24, 22, 18).endFill()
      this.avatar.visible = !!photo
      if (photo) { this.avatar.texture = photo; this.avatar.position.set(6, 4); this.avatar.width = 36; this.avatar.height = 36 }
      this.name.style.wordWrapWidth = 68
      this.name.text = truncate(p.name, 22)
      this.name.position.set(46, 4)
      this.sub.text = p.orderLabel ? `${years} · ${p.orderLabel}` : years
      this.sub.position.set(6, 43)
    } else {
      this.avatar.visible = false
      this.avatarMask.clear()
      this.name.style.wordWrapWidth = 104
      this.name.text = truncate(p.name, 28)
      this.name.position.set(8, 6)
      this.sub.text = years
      this.sub.position.set(8, 40)
    }

    this.toggle.clear()
    this.toggleLabel.visible = n.hasKids
    if (n.hasKids) {
      this.toggle.lineStyle(1, 0x888888).beginFill(0xffffff).drawCircle(n.w / 2, n.h + 2, 8).endFill()
      this.toggleLabel.text = n.collapsed ? '+' : '−'
      this.toggleLabel.anchor.set(0.5)
      this.toggleLabel.position.set(n.w / 2, n.h + 2)
    }
  }
}

export class TreeRenderer {
  private app: Application
  private viewport: Viewport
  private edges = new Graphics()
  private halo = new Graphics()
  private dots = new Graphics()
  private views = new Container()
  private pool: NodeView[] = []
  private active = new Map<number, NodeView>()
  private index: QuadTree | null = null
  private persons = new Map<number, GraphPerson>()
  private nodes = new Map<number, LayoutNode>()
  private textures = new Map<string, Texture>()
  private photos: PhotoStore | null = null
  private lod: Lod = 'dot'
  private dirty = true
  private hoverId: number | null = null
  private selectedId: number | null = null

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
    this.viewport.addChild(this.edges, this.halo, this.dots, this.views)
    this.app.stage.addChild(this.viewport)

    const mark = () => { this.dirty = true }
    this.viewport.on('moved', mark)
    this.viewport.on('zoomed', mark)
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
    this.viewport.on('pointerout', () => {
      if (this.hoverId !== null) { this.hoverId = null; this.handlers.onHover?.(null, { x: 0, y: 0 }) }
    })
    this.viewport.on('clicked', (e) => {
      const hit = this.index?.queryPoint(e.world.x, e.world.y)
      if (!hit) return
      const n = this.nodes.get(hit.id)!
      const onToggle = this.lod !== 'dot' && n.hasKids && e.world.y > n.y + n.h - 4 && Math.abs(e.world.x - (n.x + n.w / 2)) <= 12
      if (onToggle) this.handlers.onToggle?.(hit.id)
      else this.handlers.onClick?.(hit.id, { x: e.screen.x, y: e.screen.y })
    })
  }

  setPhotoStore(store: PhotoStore | null): void { this.photos = store }

  invalidate(): void {
    for (const [id, v] of this.active) this.renderView(v, id)
    this.dirty = true
  }

  setData(graph: GraphData, layout: LayoutResult, opts: { keepCamera?: boolean } = {}): void {
    const center = this.viewport.center.clone()
    const scale = this.viewport.scale.x
    this.persons = new Map(graph.persons.map((p) => [p.id, p]))
    this.nodes = new Map(layout.nodes.map((n) => [n.id, n]))
    this.releaseAll()

    this.index = new QuadTree({ x: 0, y: 0, w: Math.max(layout.width, 1), h: Math.max(layout.height + TOGGLE_PAD, 1) })
    for (const n of layout.nodes) this.index.insert({ id: n.id, x: n.x, y: n.y, w: n.w, h: n.h + (n.hasKids ? TOGGLE_PAD : 0) })

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
      const p = this.persons.get(n.id)
      if (p) this.dots.beginFill(COLOR[p.gender]).drawCircle(n.x + n.w / 2, n.y + n.h / 2, 22).endFill()
    }
    this.drawHalo()

    this.viewport.resize(this.app.screen.width, this.app.screen.height, layout.width, layout.height)
    if (opts.keepCamera) { this.viewport.setZoom(scale, true); this.viewport.moveCenter(center.x, center.y) }
    else this.fit()
    this.dirty = true
  }

  setSelected(personId: number | null): void {
    this.selectedId = personId
    this.drawHalo()
    this.dirty = true
    for (const [id, v] of this.active) this.renderView(v, id)
  }

  focusOn(personId: number, scale = 1): void {
    const n = this.nodes.get(personId)
    if (!n) return
    this.viewport.animate({ time: 350, position: new Point(n.x + n.w / 2, n.y + n.h / 2), scale, ease: 'easeInOutSine' })
    this.dirty = true
  }

  moveCenterTo(x: number, y: number): void { this.viewport.moveCenter(x, y); this.dirty = true }

  fit(): void {
    const w = this.viewport.worldWidth, h = this.viewport.worldHeight
    this.viewport.fit(true, w, h)
    this.viewport.moveCenter(w / 2, h / 2)
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
          resolve({ avgFps: frames / ((performance.now() - t0) / 1000), minFps: Number.isFinite(minFps) ? minFps : 0, frames })
          return
        }
        this.viewport.moveCenter((0.5 + 0.5 * Math.sin(t * Math.PI * 4)) * lw, (0.5 + 0.5 * Math.sin(t * Math.PI * 6)) * lh)
        this.viewport.setZoom(0.2 + 0.7 * (0.5 + 0.5 * Math.sin(t * Math.PI * 8)), true)
        this.dirty = true
        frames++
        if (this.app.ticker.deltaMS > 0) minFps = Math.min(minFps, 1000 / this.app.ticker.deltaMS)
      }
      this.app.ticker.add(tick)
    })
  }

  destroy(): void {
    this.photos = null
    this.app.destroy(false, { children: true })
  }

  private drawHalo(): void {
    this.halo.clear()
    const n = this.selectedId === null ? null : this.nodes.get(this.selectedId)
    if (n) this.halo.lineStyle(6, 0xffc107).drawCircle(n.x + n.w / 2, n.y + n.h / 2, 34)
  }

  private texture(url: string | null | undefined): Texture | null {
    if (!url || !this.photos) return null
    const objectUrl = this.photos.get(url)
    if (!objectUrl) return null
    let t = this.textures.get(objectUrl)
    if (!t) { t = Texture.from(objectUrl); this.textures.set(objectUrl, t) }
    return t
  }

  private renderView(v: NodeView, id: number): void {
    const n = this.nodes.get(id), p = this.persons.get(id)
    if (!n || !p) return
    v.render(n, p, this.lod, id === this.selectedId, this.lod === 'full' ? this.texture(p.thumb ? `${p.thumb}` : null) : null)
  }

  private refresh(): void {
    const scale = this.viewport.scale.x
    const lod = lodForScale(scale)
    if (lod !== this.lod) { this.lod = lod; this.releaseAll() }
    this.dots.visible = lod === 'dot'
    this.views.visible = lod !== 'dot'
    const b = this.viewport.getVisibleBounds()
    this.handlers.onViewChange?.({ x: b.x, y: b.y, w: b.width, h: b.height, scale })
    if (lod === 'dot' || !this.index) return

    const visible = this.index.queryRect(b.x, b.y, b.width, b.height)
    const keep = new Set<number>()
    for (const it of visible) {
      keep.add(it.id)
      if (!this.active.has(it.id)) {
        const v = this.pool.pop() ?? new NodeView()
        this.renderView(v, it.id)
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
