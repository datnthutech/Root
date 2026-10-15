import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Alert, App, Button, Space, Table, Typography, Upload } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { importApi, type ImportError, type ImportReport } from '../../api/importApi'
import { errorText } from '../../auth/errorText'

const columns = [
  { title: 'Sheet', dataIndex: 'sheet', width: 110 },
  { title: 'Dòng', dataIndex: 'row', width: 70 },
  { title: 'Cột', dataIndex: 'column', width: 140 },
  { title: 'Thông báo', dataIndex: 'message' },
]

export default function ImportPage() {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [file, setFileState] = useState<File | null>(null)
  const fileRef = useRef<File | null>(null)
  const [dryRes, setDryRes] = useState<{ file: File; report: ImportReport } | null>(null)
  const [done, setDone] = useState<ImportReport | null>(null)
  const [busy, setBusy] = useState(false)
  const busyRef = useRef(false)

  const setFile = (f: File | null) => { fileRef.current = f; setFileState(f); setDryRes(null); setDone(null) }
  const dry = dryRes && dryRes.file === file ? dryRes.report : null

  const run = async (dryRun: boolean) => {
    const f = file
    if (!f || busyRef.current) return
    if (!dryRun && !(dryRes && dryRes.file === f && dryRes.report.errors.length === 0)) return
    busyRef.current = true
    setBusy(true)
    try {
      const r = await importApi.upload(f, dryRun)
      if (fileRef.current !== f) return
      if (dryRun) { setDryRes({ file: f, report: r }); setDone(null) }
      else if (r.errors.length) setDryRes({ file: f, report: { ...r, dryRun: true } })
      else {
        setDone(r)
        message.success('Đã nhập xong')
        for (const k of ['clans', 'person', 'person-family', 'person-bio', 'tree', 'tree-graph', 'audit'])
          void qc.invalidateQueries({ queryKey: [k] })
      }
    } catch (e) {
      message.error(errorText(e))
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }

  const canImport = !!file && !!dry && dry.errors.length === 0 && !done
  return (
    <div style={{ padding: 16, maxWidth: 960, margin: '0 auto' }}>
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <Button onClick={async () => { try { await importApi.downloadTemplate() } catch (e) { message.error(errorText(e)) } }}>Tải mẫu Excel</Button>
        <Upload.Dragger accept=".xlsx" maxCount={1} disabled={busy} showUploadList={!!file} beforeUpload={(f) => { setFile(f as File); return false }}
          onRemove={() => { setFile(null) }}>
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p>Kéo thả hoặc bấm để chọn tệp .xlsx</p>
        </Upload.Dragger>
        <Space>
          <Button disabled={!file} loading={busy} onClick={() => run(true)}>Kiểm tra thử</Button>
          <Button type="primary" disabled={!canImport} loading={busy} onClick={() => run(false)}>Nhập thật</Button>
        </Space>
        {dry && (
          <>
            <Alert showIcon type={dry.errors.length ? 'error' : 'success'}
              message={dry.errors.length ? `Có ${dry.errors.length} lỗi — chưa thể nhập` : `Sẵn sàng nhập: ${dry.persons} người, ${dry.unions} hôn phối, ${dry.links} quan hệ`} />
            {dry.errors.length > 0 && <Table<ImportError> size="small" rowKey={(e) => `${e.sheet}-${e.row}-${e.column}-${e.code}`} columns={columns} dataSource={dry.errors} pagination={{ pageSize: 20 }} scroll={{ x: true }} />}
            {dry.warnings.length > 0 && (
              <>
                <Typography.Text type="warning">Cảnh báo ({dry.warnings.length}) — vẫn nhập được:</Typography.Text>
                <Table<ImportError> size="small" rowKey={(e) => `${e.sheet}-${e.row}-${e.code}`} columns={columns} dataSource={dry.warnings} pagination={{ pageSize: 10 }} scroll={{ x: true }} />
              </>
            )}
          </>
        )}
        {done && (
          <Alert showIcon type="success" message={`Đã nhập ${done.persons} người, ${done.unions} hôn phối, ${done.links} quan hệ`}
            action={done.clanId ? <Link to={`/tree?mode=clan&clan=${done.clanId}`}>Xem cây</Link> : undefined} />
        )}
      </Space>
    </div>
  )
}
