import { useEffect, useState } from 'react'
import { AutoComplete, Input } from 'antd'
import { searchPersons, type SearchHit } from '../../api/tree'
import { lifespan } from '../../lib/format'

export function PersonSearch({ onPick }: { onPick: (id: number) => void }) {
  const [q, setQ] = useState('')
  const [hits, setHits] = useState<SearchHit[]>([])

  useEffect(() => {
    if (!q.trim()) { setHits([]); return }
    let cancelled = false
    const t = setTimeout(() => {
      searchPersons(q.trim()).then((r) => { if (!cancelled) setHits(r) }).catch(() => { if (!cancelled) setHits([]) })
    }, 250)
    return () => { cancelled = true; clearTimeout(t) }
  }, [q])

  return (
    <AutoComplete style={{ width: 220 }} value={q} onChange={setQ}
      options={hits.map((h) => ({ value: String(h.id), label: `${h.name} (${lifespan(h)})${h.generation ? ` · đời ${h.generation}` : ''}` }))}
      onSelect={(v) => { onPick(Number(v)); setQ('') }}>
      <Input.Search placeholder="Tìm theo tên..." allowClear />
    </AutoComplete>
  )
}
