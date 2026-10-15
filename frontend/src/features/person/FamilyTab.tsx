import { Empty, List, Typography } from 'antd'
import { lifespan } from '../../lib/format'
import type { FamilyResponse, PersonRef } from '../../api/person'

function Refs({ title, items, onNavigate }: { title: string; items: PersonRef[]; onNavigate(id: number): void }) {
  if (!items.length) return null
  return (
    <List size="small" header={<Typography.Text strong>{title}</Typography.Text>} dataSource={items}
      renderItem={(r) => (
        <List.Item>
          <a onClick={() => onNavigate(r.id)}>{r.orderLabel ? `${r.orderLabel} · ` : ''}{r.name}</a>&nbsp;({lifespan(r)})
        </List.Item>
      )} />
  )
}

export default function FamilyTab({ f, onNavigate }: { f: FamilyResponse; onNavigate(id: number): void }) {
  const empty = !f.parents.length && !f.spouses.length && !f.otherChildren.length && !f.fullSiblings.length
    && !f.halfSiblingsSameFather.length && !f.halfSiblingsSameMother.length
  if (empty) return <Empty description="Chưa có thông tin gia đình" />
  return (
    <div>
      <Refs title="Cha mẹ" items={f.parents} onNavigate={onNavigate} />
      {f.spouses.map((s) => (
        <div key={s.unionId}>
          <Refs title={`Vợ/chồng (${s.unionOrder})`} items={[s.partner]} onNavigate={onNavigate} />
          <Refs title="Con" items={s.children} onNavigate={onNavigate} />
        </div>
      ))}
      <Refs title="Con (chưa rõ hôn phối)" items={f.otherChildren} onNavigate={onNavigate} />
      <Refs title="Anh chị em ruột" items={f.fullSiblings} onNavigate={onNavigate} />
      <Refs title="Anh chị em cùng cha khác mẹ" items={f.halfSiblingsSameFather} onNavigate={onNavigate} />
      <Refs title="Anh chị em cùng mẹ khác cha" items={f.halfSiblingsSameMother} onNavigate={onNavigate} />
    </div>
  )
}
