import { useState } from 'react'
import { App, Button, Drawer, Radio, Select, Space, Tabs, Typography } from 'antd'
import { editApi, type LinkBody } from '../../api/edit'
import type { SpouseFamily } from '../../api/person'
import { errorText } from '../../auth/errorText'
import { PersonSearch } from '../tree/PersonSearch'
import PersonForm from './PersonForm'

type Mode = 'parent' | 'child' | 'spouse'
const TITLE: Record<Mode, string> = { parent: 'Thêm cha/mẹ', child: 'Thêm con', spouse: 'Thêm vợ/chồng' }

export default function RelationDrawer({ open, mode, person, spouses, onClose, onDone }: {
  open: boolean; mode: Mode; person: { id: number; name: string; gender: 'M' | 'F' | 'U' }
  spouses: SpouseFamily[]; onClose(): void; onDone(): void
}) {
  const { message } = App.useApp()
  const [busy, setBusy] = useState(false)
  const [picked, setPicked] = useState<number | null>(null)
  const [kind, setKind] = useState<'BIOLOGICAL' | 'ADOPTED' | 'STEP'>('BIOLOGICAL')
  const [unionId, setUnionId] = useState<number | undefined>(spouses[0]?.unionId)

  const attach = async (otherId: number) => {
    if (mode === 'child') {
      const r = await editApi.addChild({ parentId: person.id, childId: otherId, kind, unionId })
      r.warnings.forEach((w) => message.warning(w.message))
    } else if (mode === 'parent') {
      const body: LinkBody = { parentId: otherId, childId: person.id, kind }
      const r = await editApi.addParentChild(body)
      r.warnings.forEach((w) => message.warning(w.message))
    } else {
      await editApi.createUnion({ partnerAId: person.id, partnerBId: otherId })
    }
  }

  const run = async (otherId: () => Promise<number>) => {
    if (busy) return
    setBusy(true)
    try {
      await attach(await otherId())
      message.success('Đã cập nhật quan hệ')
      onDone()
    } catch (e) {
      message.error(errorText((e as { code?: string }).code))
    } finally {
      setBusy(false)
    }
  }

  const defaultGender = mode === 'spouse' ? (person.gender === 'M' ? 'F' : person.gender === 'F' ? 'M' : 'U') : 'U'

  return (
    <Drawer open={open} onClose={onClose} title={`${TITLE[mode]} — ${person.name}`} width="min(520px, 100vw)" destroyOnClose>
      {mode !== 'spouse' && (
        <Space direction="vertical" style={{ width: '100%', marginBottom: 12 }}>
          <Radio.Group value={kind} onChange={(e) => setKind(e.target.value)}
            options={[{ value: 'BIOLOGICAL', label: 'Ruột' }, { value: 'ADOPTED', label: 'Con nuôi' }, { value: 'STEP', label: 'Con riêng' }]} />
          {mode === 'child' && spouses.length > 0 && (
            <Select style={{ width: '100%' }} value={unionId} allowClear placeholder="Với vợ/chồng (tuỳ chọn)" onChange={setUnionId}
              options={spouses.map((s) => ({ value: s.unionId, label: `Với ${s.partner.name}` }))} />
          )}
        </Space>
      )}
      <Tabs items={[
        {
          key: 'existing', label: 'Người đã có',
          children: (
            <Space direction="vertical" style={{ width: '100%' }}>
              <PersonSearch onPick={(id) => setPicked(id)} />
              {picked !== null && <Typography.Text>Đã chọn người có mã #{picked}</Typography.Text>}
              <Button type="primary" disabled={picked === null} loading={busy} onClick={() => run(async () => picked!)}>{TITLE[mode]}</Button>
            </Space>
          ),
        },
        {
          key: 'new', label: 'Tạo người mới',
          children: (
            <PersonForm busy={busy} withBiography={false} defaultGender={defaultGender} submitText={`Tạo và ${TITLE[mode].toLowerCase()}`}
              onSubmit={async (v) => { await run(async () => (await editApi.createPerson(v)).id) }} />
          ),
        },
      ]} />
    </Drawer>
  )
}
