import { useCallback, useEffect, useRef, useState } from 'react'

export function useHoverCard(delayMs = 300, enabled = true) {
  const [card, setCard] = useState<{ id: number; x: number; y: number } | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout>>()

  const clear = () => { if (timer.current) clearTimeout(timer.current) }
  const onHover = useCallback((id: number | null, pos: { x: number; y: number }) => {
    clear()
    setCard(null)
    if (id === null || !enabled) return
    timer.current = setTimeout(() => setCard({ id, x: pos.x, y: pos.y }), delayMs)
  }, [delayMs, enabled])
  const hide = useCallback(() => { clear(); setCard(null) }, [])
  useEffect(() => clear, [])
  return { card, onHover, hide }
}
