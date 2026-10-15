import { useState } from 'react'
import { App, Button, InputNumber, Space } from 'antd'
import { editApi } from '../../api/edit'
import { errorText } from '../../auth/errorText'

export default function BirthOrderControl({ personId, current, onChanged }: { personId: number; current?: number; onChanged(): void }) {
  const { message } = App.useApp()
  const [value, setValue] = useState<number | null>(current ?? null)
  const [busy, setBusy] = useState(false)
  const save = async (order: number | null) => {
    if (busy) return
    setBusy(true)
    try { await editApi.setBirthOrder(personId, order); message.success('Đã đặt thứ tự'); onChanged() }
    catch (e) { message.error(errorText((e as { code?: string }).code)) }
    finally { setBusy(false) }
  }
  return (
    <Space wrap>
      <span>Đặt thứ tự thủ công (thứ):</span>
      <InputNumber min={2} max={99} value={value} onChange={setValue} />
      <Button loading={busy} onClick={() => save(value)} disabled={value === null}>Đặt</Button>
      <Button loading={busy} onClick={() => { setValue(null); void save(null) }}>Về tự động</Button>
    </Space>
  )
}
