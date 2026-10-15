import { Descriptions, Space } from 'antd'
import { AuthImage } from '../../components/AuthImage'
import { formatDate } from '../../lib/format'
import type { PersonProfile } from '../../api/person'

const GENDER = { M: 'Nam', F: 'Nữ', U: 'Chưa rõ' } as const

export function birthText(p: PersonProfile): string {
  if (p.birthDate) return formatDate(p.birthDate, p.birthPrecision)
  return p.birthYear ? `${p.birthYear} (chỉ hiện năm)` : 'Không rõ'
}

export default function ProfileTab({ p }: { p: PersonProfile }) {
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Space align="center"><AuthImage src={p.photoUrl} size={96} alt={p.fullName} /><h3 style={{ margin: 0 }}>{p.fullName}</h3></Space>
      <Descriptions column={1} size="small" bordered>
        {p.nickname && <Descriptions.Item label="Tên thường gọi">{p.nickname}</Descriptions.Item>}
        <Descriptions.Item label="Giới tính">{GENDER[p.gender]}</Descriptions.Item>
        <Descriptions.Item label="Ngày sinh">{birthText(p)}</Descriptions.Item>
        {p.birthLunar && <Descriptions.Item label="Ngày sinh (âm lịch)">{p.birthLunar}</Descriptions.Item>}
        <Descriptions.Item label="Tình trạng">{p.alive ? 'Còn sống' : 'Đã mất'}</Descriptions.Item>
        {!p.alive && <Descriptions.Item label="Ngày mất">{formatDate(p.deathDate, p.deathPrecision)}</Descriptions.Item>}
        {p.deathAnniversaryLunar && <Descriptions.Item label="Ngày giỗ (âm lịch)">{p.deathAnniversaryLunar}</Descriptions.Item>}
        {p.originPlace && <Descriptions.Item label="Nguyên quán">{p.originPlace}</Descriptions.Item>}
        {p.homePlace && <Descriptions.Item label="Quê quán">{p.homePlace}</Descriptions.Item>}
        {p.burialPlace && <Descriptions.Item label="Nơi an táng">{p.burialPlace}</Descriptions.Item>}
        {p.generation && <Descriptions.Item label="Đời thứ">{p.generation}</Descriptions.Item>}
        {p.orderLabel && <Descriptions.Item label="Thứ trong nhà">{p.orderLabel}{p.orderNeedsConfirm ? ' (cần xác nhận thứ tự)' : ''}</Descriptions.Item>}
      </Descriptions>
    </Space>
  )
}
