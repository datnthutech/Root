import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Alert, Empty, Spin } from 'antd'
import { useAuth } from '../../auth/AuthContext'
import { listClans } from '../../api/tree'
import { errorText } from '../../auth/errorText'
import PersonDialog from '../person/PersonDialog'
import { HoverCard } from './HoverCard'
import { Minimap } from './Minimap'
import { TreeCanvas, type TreeCanvasHandle } from './TreeCanvas'
import { TreeToolbar } from './TreeToolbar'
import { parseParams, toSearch } from './treeParams'
import type { ViewInfo } from './TreeRenderer'
import { useHoverCard } from './useHoverCard'
import { useTreeData, type TreeParams } from './useTreeData'

const canHover = () => typeof window !== 'undefined' && window.matchMedia?.('(hover: hover)').matches === true

export default function TreePage() {
  const { user } = useAuth()
  const [sp, setSp] = useSearchParams()
  const params = useMemo(() => parseParams(sp), [sp])
  const clans = useQuery({ queryKey: ['clans'], queryFn: listClans })
  const canvas = useRef<TreeCanvasHandle>(null)
  const [selected, setSelected] = useState<number | null>(null)
  const [dialogId, setDialogId] = useState<number | null>(null)
  const [view, setView] = useState<ViewInfo | null>(null)
  const hover = useHoverCard(300, canHover())
  const pendingFocus = useRef<number | null>(null)

  const myPerson = user?.linkedPersonId ?? null

  // quyết định màn hình mở đầu khi URL chưa đủ thông tin
  useEffect(() => {
    if (params) return
    if (myPerson !== null) setSp(toSearch({ mode: 'focus', clanId: null, side: 'both', personId: myPerson, up: 3, down: 3 }), { replace: true })
    else if (clans.data?.length) setSp(toSearch({ mode: 'clan', clanId: clans.data[0].id, side: 'both', personId: null, up: 3, down: 3 }), { replace: true })
  }, [params, myPerson, clans.data, setSp])

  const data = useTreeData(params ?? { mode: 'clan', clanId: null, side: 'both', personId: null, up: 3, down: 3 })

  // sau khi bố cục mới của chế độ focus sẵn sàng, đưa camera tới người trung tâm / người được yêu cầu
  useEffect(() => {
    if (!data.layout) return
    if (data.keepCamera) return // đổi bố cục do +/- : giữ camera (renderer neo theo nút vừa bấm)
    const target = pendingFocus.current ?? (params?.mode === 'focus' ? params.personId : null)
    pendingFocus.current = null
    if (target === null || !data.graph?.persons.some((p) => p.id === target)) return
    setSelected(target)
    const t = setTimeout(() => canvas.current?.focusOn(target, 1), 0)
    return () => clearTimeout(t)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data.layout])

  const update = (next: TreeParams) => setSp(toSearch(next))

  const goTo = (id: number) => {
    setDialogId(null)
    // chỉ bay camera khi nút đang hiện trong bố cục (người nằm dưới nhánh thu gọn thì chuyển sang chế độ tập trung)
    if (data.layout?.nodes.some((n) => n.id === id)) { setSelected(id); canvas.current?.focusOn(id, 1); return }
    pendingFocus.current = id
    update({ mode: 'focus', clanId: null, side: 'both', personId: id, up: params?.up ?? 3, down: params?.down ?? 3 })
  }

  if (!params) {
    if (myPerson === null && clans.isSuccess && clans.data.length === 0) {
      return <Empty style={{ marginTop: '20vh' }} description="Chưa có dòng họ nào. Quản trị viên cần tạo dòng họ và chọn thủy tổ." />
    }
    if (clans.isError) return <Alert type="error" message={errorText((clans.error as { code?: string }).code)} style={{ margin: 16 }} />
    return <Spin style={{ display: 'block', margin: '20vh auto' }} />
  }

  const hovered = hover.card ? data.graph?.persons.find((p) => p.id === hover.card!.id) ?? null : null
  return (
    <div style={{ position: 'relative', height: 'calc(100vh - 64px)' }}>
      <TreeCanvas ref={canvas} graph={data.graph} layout={data.layout} keepCamera={data.keepCamera} selectedId={selected}
        onHover={hover.onHover} onViewChange={setView} onToggle={(id) => { hover.hide(); data.toggle(id) }}
        onClick={(id) => { hover.hide(); setSelected(id); setDialogId(id) }} />
      <div style={{ position: 'absolute', top: 8, left: 8, right: 8, pointerEvents: 'none' }}>
        <div style={{ pointerEvents: 'auto', display: 'inline-block', maxWidth: '100%' }}>
          <TreeToolbar params={params} clans={clans.data ?? []} myPersonId={myPerson} onChange={update} onPick={goTo}
            onFit={() => canvas.current?.fit()} />
        </div>
      </div>
      {data.error && <Alert type="error" showIcon style={{ position: 'absolute', top: 64, left: 8 }} message={errorText(data.error.code)} />}
      {data.loading && <Spin style={{ position: 'absolute', top: '50%', left: '50%' }} />}
      <div style={{ position: 'absolute', right: 8, bottom: 8 }}>
        <Minimap layout={data.layout} view={view} onNavigate={(x, y) => canvas.current?.moveCenterTo(x, y)} />
      </div>
      {hovered && hover.card && <HoverCard person={hovered} x={hover.card.x} y={hover.card.y} />}
      <PersonDialog personId={dialogId} onClose={() => setDialogId(null)} onNavigate={goTo} />
    </div>
  )
}
