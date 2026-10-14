export type Lod = 'dot' | 'box' | 'full'

export function lodForScale(scale: number): Lod {
  if (scale < 0.3) return 'dot'
  if (scale < 0.8) return 'box'
  return 'full'
}
