export class PhotoStore {
  private cache = new Map<string, string>()
  private pending = new Set<string>()
  private failed = new Set<string>()
  private queue: string[] = []
  private running = 0
  private readonly concurrency: number
  private readonly max: number

  constructor(
    private fetchBlob: (url: string) => Promise<Blob | null>,
    private onLoaded: (url: string) => void,
    opts: { concurrency?: number; max?: number } = {},
  ) {
    this.concurrency = opts.concurrency ?? 6
    this.max = opts.max ?? 400
  }

  get(url: string): string | null {
    const hit = this.cache.get(url)
    if (hit) { this.cache.delete(url); this.cache.set(url, hit); return hit }
    if (!this.pending.has(url) && !this.failed.has(url)) {
      this.pending.add(url)
      this.queue.push(url)
      this.pump()
    }
    return null
  }

  destroy(): void {
    for (const v of this.cache.values()) URL.revokeObjectURL(v)
    this.cache.clear()
    this.queue = []
  }

  private pump(): void {
    while (this.running < this.concurrency && this.queue.length) {
      const url = this.queue.shift()!
      this.running++
      this.fetchBlob(url)
        .then((blob) => {
          if (blob) { this.cache.set(url, URL.createObjectURL(blob)); this.evict(); this.onLoaded(url) }
          else this.failed.add(url)
        })
        .catch(() => { this.failed.add(url) })
        .finally(() => { this.running--; this.pending.delete(url); this.pump() })
    }
  }

  private evict(): void {
    while (this.cache.size > this.max) {
      const [k, v] = this.cache.entries().next().value as [string, string]
      this.cache.delete(k)
      URL.revokeObjectURL(v)
    }
  }
}
