export interface Rect { x: number; y: number; w: number; h: number }
export interface Item extends Rect { id: number }

const hit = (a: Rect, x: number, y: number, w: number, h: number) =>
  a.x < x + w && a.x + a.w > x && a.y < y + h && a.y + a.h > y

export class QuadTree {
  private items: Item[] = []
  private children: QuadTree[] | null = null

  constructor(
    private bounds: Rect,
    private maxItems = 16,
    private maxDepth = 8,
    private depth = 0,
  ) {}

  insert(it: Item): void {
    // Ô nằm ngoài bounds ban đầu được giữ tại nút gốc để vẫn tìm thấy.
    if (this.depth === 0 && !hit(this.bounds, it.x, it.y, it.w, it.h)) { this.items.push(it); return }
    if (this.children) {
      const i = this.childIndex(it)
      if (i >= 0) this.children[i].insert(it)
      else this.items.push(it)
      return
    }
    this.items.push(it)
    if (this.items.length > this.maxItems && this.depth < this.maxDepth) this.split()
  }

  queryRect(x: number, y: number, w: number, h: number, out: Item[] = []): Item[] {
    // Ô nằm ngoài bounds nằm ở nút gốc nên nút gốc luôn được duyệt items của nó.
    if (this.depth > 0 && !hit(this.bounds, x, y, w, h)) return out
    for (const it of this.items) if (hit(it, x, y, w, h)) out.push(it)
    if (this.children) for (const c of this.children) c.queryRect(x, y, w, h, out)
    return out
  }

  queryPoint(x: number, y: number): Item | null {
    const found = this.queryRect(x, y, 0.001, 0.001)
    return found.length ? found[found.length - 1] : null
  }

  private split(): void {
    const { x, y, w, h } = this.bounds
    const hw = w / 2, hh = h / 2, d = this.depth + 1
    this.children = [
      new QuadTree({ x, y, w: hw, h: hh }, this.maxItems, this.maxDepth, d),
      new QuadTree({ x: x + hw, y, w: hw, h: hh }, this.maxItems, this.maxDepth, d),
      new QuadTree({ x, y: y + hh, w: hw, h: hh }, this.maxItems, this.maxDepth, d),
      new QuadTree({ x: x + hw, y: y + hh, w: hw, h: hh }, this.maxItems, this.maxDepth, d),
    ]
    const old = this.items
    this.items = []
    for (const it of old) this.insert(it)
  }

  private childIndex(it: Item): number {
    const midX = this.bounds.x + this.bounds.w / 2
    const midY = this.bounds.y + this.bounds.h / 2
    const left = it.x + it.w <= midX, right = it.x >= midX
    const top = it.y + it.h <= midY, bottom = it.y >= midY
    if (top && left) return 0
    if (top && right) return 1
    if (bottom && left) return 2
    if (bottom && right) return 3
    return -1
  }
}
