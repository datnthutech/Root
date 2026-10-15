import { useState } from 'react'
import { App, Button, Upload } from 'antd'
import { UploadOutlined } from '@ant-design/icons'
import { editApi } from '../../api/edit'
import { errorText } from '../../auth/errorText'

export default function PhotoUpload({ personId, onChanged }: { personId: number; onChanged(): void }) {
  const { message } = App.useApp()
  const [busy, setBusy] = useState(false)
  return (
    <Upload accept="image/jpeg,image/png" showUploadList={false} disabled={busy}
      beforeUpload={(file) => {
        if (file.size > 10 * 1024 * 1024) { message.error('Ảnh tối đa 10 MB'); return Upload.LIST_IGNORE }
        setBusy(true)
        editApi.uploadPhoto(personId, file as File)
          .then(() => { message.success('Đã đổi ảnh'); onChanged() })
          .catch((e) => message.error(errorText(e)))
          .finally(() => setBusy(false))
        return false
      }}>
      <Button icon={<UploadOutlined />} loading={busy}>Đổi ảnh</Button>
    </Upload>
  )
}
