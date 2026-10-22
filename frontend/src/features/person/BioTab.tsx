import { Empty } from 'antd'
import { renderBio } from '../../lib/bio'

export default function BioTab({ text }: { text: string | null }) {
  if (!text || !text.trim()) return <Empty description="Chưa có sơ yếu lý lịch" />
  return <div>{renderBio(text)}</div>
}
