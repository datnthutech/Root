import { Card, Space, Typography } from 'antd'
import { AuthImage } from '../../components/AuthImage'
import { lifespan } from '../../lib/format'
import type { GraphPerson } from '../../lib/types'

export function HoverCard({ person, x, y }: { person: GraphPerson; x: number; y: number }) {
  return (
    <Card size="small" style={{ position: 'absolute', left: x + 14, top: y + 14, width: 230, pointerEvents: 'none', zIndex: 20, boxShadow: '0 2px 10px #0003' }}>
      <Space align="start">
        <AuthImage src={person.thumb ? person.thumb.replace('size=96', 'size=256') : null} size={56} alt={person.name} />
        <div>
          <Typography.Text strong>{person.name}</Typography.Text>
          <div>{lifespan(person)}</div>
          {person.orderLabel && <div>{person.orderLabel}</div>}
        </div>
      </Space>
    </Card>
  )
}
