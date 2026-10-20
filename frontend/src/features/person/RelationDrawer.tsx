import { useState } from 'react'
import { Alert, App, Button, Drawer, Radio, Select, Space, Tabs, Typography } from 'antd'
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
  const [tab, setTab] = useState('existing')
  const [createdId, setCreatedId] = useState<number | null>(null)
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

  const run = async (otherId: () => Promise<number>, isNew = false) => {
    if (busy) return
    setBusy(true)
    let id: number | null = null
    try {
      id = await otherId()
      await attach(id)
      message.success('Đã cập nhật quan hệ')
      onDone()
    } catch (e) {
      if (isNew && id !== null) {
        // người mới đã được tạo nhưng chưa gắn được: chuyển sang tab "Người đã có" để thử lại, không tạo trùng
        setPicked(id); setCreatedId(id); setTab('existing')
        message.error(`Đã tạo người mới (#${id}) nhưng chưa gắn được quan hệ: ${errorText(e)}. Bấm "${TITLE[mode]}" để thử lại.`)
      } else {
        message.error(errorText(e))
      }
    } finally {
      setBusy(false)
    }
  }

  const defaultGender = mode === 'spouse' ? (person.gender === 'M' ? 'F' : person.gender === 'F' ? 'M' : 'U') : 'U'

  return (
    <Drawer open={open} onClose={onClose} title={`${TITLE[mode]} — ${person.name}`} width="min(520px, 100vw)" destroyOnHidden>
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
      <Tabs activeKey={tab} onChange={setTab} items={[
        {
          key: 'existing', label: 'Người đã có',
          children: (
            <Space direction="vertical" style={{ width: '100%' }}>
              {createdId !== null && (
                <Alert type="warning" showIcon message={`Người mới (#${createdId}) đã được tạo nhưng chưa gắn quan hệ. Có thể thử lại bên dưới.`} />
              )}
              <PersonSearch onPick={(id) => { setPicked(id); setCreatedId(null) }} />
              {picked !== null && <Typography.Text>Đã chọn người có mã #{picked}</Typography.Text>}
              <Button type="primary" disabled={picked === null} loading={busy} onClick={() => run(async () => picked!)}>{TITLE[mode]}</Button>
            </Space>
          ),
        },
        {
          key: 'new', label: 'Tạo người mới',
          children: (
            <PersonForm busy={busy} withBiography={false} defaultGender={defaultGender} submitText={`Tạo và ${TITLE[mode].toLowerCase()}`}
              onSubmit={async (v) => { await run(async () => (await editApi.createPerson(v)).id, true) }} />
          ),
        },
      ]} />
    </Drawer>
  )
}
