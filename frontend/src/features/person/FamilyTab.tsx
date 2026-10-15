import { useState } from 'react'
import { App, Button, Empty, List, Popconfirm, Space, Typography } from 'antd'
import { CloseOutlined } from '@ant-design/icons'
import { editApi } from '../../api/edit'
import type { FamilyResponse, PersonRef } from '../../api/person'
import { errorText } from '../../auth/errorText'
import { lifespan } from '../../lib/format'
import RelationDrawer from './RelationDrawer'

type Mode = 'parent' | 'child' | 'spouse'

function Refs({ title, items, onNavigate, onRemove }: {
  title: string; items: PersonRef[]; onNavigate(id: number): void; onRemove?: (r: PersonRef) => React.ReactNode
}) {
  if (!items.length) return null
  return (
    <List size="small" header={<Typography.Text strong>{title}</Typography.Text>} dataSource={items}
      renderItem={(r) => (
        <List.Item extra={onRemove?.(r)}>
          <a onClick={() => onNavigate(r.id)}>{r.orderLabel ? `${r.orderLabel} · ` : ''}{r.name}</a>&nbsp;({lifespan(r)})
        </List.Item>
      )} />
  )
}

export default function FamilyTab({ f, personId, personName, personGender, canManage, onNavigate, onChanged }: {
  f: FamilyResponse; personId: number; personName: string; personGender: 'M' | 'F' | 'U'; canManage: boolean
  onNavigate(id: number): void; onChanged(): void
}) {
  const { message } = App.useApp()
  // drawer giữ ổn định để còn animation đóng; key tăng mỗi lần mở để reset state bên trong
  const [drawer, setDrawer] = useState<{ mode: Mode; open: boolean; n: number }>({ mode: 'child', open: false, n: 0 })
  const openDrawer = (mode: Mode) => setDrawer((d) => ({ mode, open: true, n: d.n + 1 }))
  const closeDrawer = () => setDrawer((d) => ({ ...d, open: false }))

  const act = async (fn: () => Promise<unknown>) => {
    try { await fn(); onChanged() } catch (e) { message.error(errorText(e)) }
  }
  const removeLink = (r: PersonRef) => !canManage || r.linkId === null ? null : (
    <Popconfirm title="Gỡ quan hệ này?" okText="Gỡ" cancelText="Huỷ" onConfirm={() => act(() => editApi.removeParentChild(r.linkId!))}>
      <Button type="text" size="small" danger aria-label="Gỡ quan hệ" icon={<CloseOutlined />} />
    </Popconfirm>
  )

  const empty = !f.parents.length && !f.spouses.length && !f.otherChildren.length && !f.fullSiblings.length
    && !f.halfSiblingsSameFather.length && !f.halfSiblingsSameMother.length

  return (
    <div>
      {canManage && (
        <Space wrap style={{ marginBottom: 8 }}>
          <Button onClick={() => openDrawer('parent')}>Thêm cha/mẹ</Button>
          <Button onClick={() => openDrawer('spouse')}>Thêm vợ/chồng</Button>
          <Button onClick={() => openDrawer('child')}>Thêm con</Button>
        </Space>
      )}
      {empty && <Empty description="Chưa có thông tin gia đình" />}
      <Refs title="Cha mẹ" items={f.parents} onNavigate={onNavigate} onRemove={removeLink} />
      {f.spouses.map((s) => (
        <div key={s.unionId}>
          <Refs title={`Vợ/chồng (${s.unionOrder})`} items={[s.partner]} onNavigate={onNavigate}
            onRemove={() => canManage ? (
              <Popconfirm title="Gỡ hôn phối này?" okText="Gỡ" cancelText="Huỷ" onConfirm={() => act(() => editApi.deleteUnion(s.unionId))}>
                <Button type="text" size="small" danger aria-label="Gỡ hôn phối" icon={<CloseOutlined />} />
              </Popconfirm>) : null} />
          <Refs title="Con" items={s.children} onNavigate={onNavigate} onRemove={removeLink} />
        </div>
      ))}
      <Refs title="Con (chưa rõ hôn phối)" items={f.otherChildren} onNavigate={onNavigate} onRemove={removeLink} />
      <Refs title="Anh chị em ruột" items={f.fullSiblings} onNavigate={onNavigate} />
      <Refs title="Anh chị em cùng cha khác mẹ" items={f.halfSiblingsSameFather} onNavigate={onNavigate} />
      <Refs title="Anh chị em cùng mẹ khác cha" items={f.halfSiblingsSameMother} onNavigate={onNavigate} />
      {canManage && drawer.n > 0 && (
        <RelationDrawer key={drawer.n} open={drawer.open} mode={drawer.mode} person={{ id: personId, name: personName, gender: personGender }}
          spouses={f.spouses} onClose={closeDrawer} onDone={() => { closeDrawer(); onChanged() }} />
      )}
    </div>
  )
}
