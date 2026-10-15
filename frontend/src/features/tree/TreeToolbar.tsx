import { Button, InputNumber, Segmented, Select, Space } from 'antd'
import type { ClanSummary, Side } from '../../api/tree'
import type { TreeParams } from './useTreeData'
import { PersonSearch } from './PersonSearch'

export function TreeToolbar({ params, clans, myPersonId, onChange, onPick, onFit }: {
  params: TreeParams
  clans: ClanSummary[]
  myPersonId: number | null
  onChange(next: TreeParams): void
  onPick(personId: number): void
  onFit(): void
}) {
  const firstClan = clans[0]?.id ?? null
  return (
    <Space wrap size={[8, 8]} style={{ padding: 8, background: '#fffe', borderRadius: 8 }}>
      <Segmented value={params.mode} options={[{ value: 'clan', label: 'Toàn cảnh' }, { value: 'focus', label: 'Tập trung' }]}
        onChange={(v) => {
          if (v === 'clan' && firstClan !== null) onChange({ ...params, mode: 'clan', clanId: params.clanId ?? firstClan })
          if (v === 'focus' && (params.personId ?? myPersonId) !== null) onChange({ ...params, mode: 'focus', personId: params.personId ?? myPersonId })
        }} />
      {params.mode === 'clan' && (
        <>
          <Select style={{ minWidth: 140 }} value={params.clanId ?? undefined} placeholder="Chọn dòng họ"
            options={clans.map((c) => ({ value: c.id, label: c.name }))} onChange={(clanId) => onChange({ ...params, clanId })} />
          <Segmented<Side> value={params.side} options={[{ value: 'both', label: 'Cả hai' }, { value: 'paternal', label: 'Bên nội' }, { value: 'maternal', label: 'Bên ngoại' }]}
            onChange={(side) => onChange({ ...params, side })} />
        </>
      )}
      {params.mode === 'focus' && (
        <>
          <span>Tổ tiên <InputNumber size="small" min={0} max={10} value={params.up} onChange={(v) => onChange({ ...params, up: v ?? 3 })} /></span>
          <span>Con cháu <InputNumber size="small" min={0} max={10} value={params.down} onChange={(v) => onChange({ ...params, down: v ?? 3 })} /></span>
        </>
      )}
      <PersonSearch onPick={onPick} />
      {myPersonId !== null && <Button onClick={() => onChange({ ...params, mode: 'focus', personId: myPersonId, clanId: null })}>Vị trí của tôi</Button>}
      <Button onClick={onFit}>Vừa khung</Button>
    </Space>
  )
}
