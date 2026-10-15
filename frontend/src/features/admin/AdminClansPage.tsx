import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Form, Input, Modal, Radio, Space, Switch, Table, Typography } from 'antd'
import { adminClansApi, type ClanBody, type ClanView } from '../../api/adminClans'
import { errorText } from '../../auth/errorText'
import { PersonSearch } from '../tree/PersonSearch'

export default function AdminClansPage() {
  const qc = useQueryClient()
  const { message } = App.useApp()
  const { data = [], isLoading } = useQuery({ queryKey: ['admin-clans'], queryFn: adminClansApi.list })
  const [editing, setEditing] = useState<ClanView | 'new' | null>(null)
  const [founder, setFounder] = useState<number | null>(null)
  const [form] = Form.useForm()

  const save = useMutation({
    mutationFn: (b: ClanBody) => (editing === 'new' ? adminClansApi.create(b) : adminClansApi.update((editing as ClanView).id, b)),
    onSuccess: () => { message.success('Đã lưu dòng họ'); setEditing(null); qc.invalidateQueries({ queryKey: ['admin-clans'] }); qc.invalidateQueries({ queryKey: ['clans'] }) },
    onError: (e) => message.error(errorText(e)),
  })

  const open = (c: ClanView | 'new') => {
    setEditing(c)
    setFounder(c === 'new' ? null : c.founderPersonId)
    form.setFieldsValue(c === 'new' ? { name: '', description: '', siblingCountMode: 'PER_UNION', useUtLabel: true }
      : { name: c.name, description: c.description ?? '', siblingCountMode: c.siblingCountMode, useUtLabel: c.useUtLabel })
  }

  return (
    <div style={{ padding: 16 }}>
      <Button type="primary" onClick={() => open('new')} style={{ marginBottom: 12 }}>Thêm dòng họ</Button>
      <Table<ClanView> rowKey="id" loading={isLoading} dataSource={data} size="small" scroll={{ x: true }}
        columns={[
          { title: 'Tên dòng họ', dataIndex: 'name' },
          { title: 'Thủy tổ (ID)', dataIndex: 'founderPersonId' },
          { title: 'Số thành viên', dataIndex: 'memberCount' },
          { title: 'Cách đếm thứ', dataIndex: 'siblingCountMode', render: (m) => (m === 'PER_FATHER' ? 'Đếm liền theo cha' : 'Theo từng hôn phối') },
          { title: '', render: (_, c) => <Button size="small" onClick={() => open(c)}>Sửa</Button> },
        ]} />
      <Modal open={editing !== null} title={editing === 'new' ? 'Thêm dòng họ' : 'Sửa dòng họ'} onCancel={() => setEditing(null)} footer={null} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={(v) => save.mutate({ ...v, founderPersonId: founder })}>
          <Form.Item label="Tên dòng họ" name="name" rules={[{ required: true, whitespace: true, message: 'Nhập tên dòng họ' }]}><Input /></Form.Item>
          <Form.Item label="Thủy tổ">
            <Space direction="vertical">
              <PersonSearch onPick={setFounder} />
              <Typography.Text>{founder ? `Đã chọn người #${founder}` : 'Chưa chọn thủy tổ'}</Typography.Text>
            </Space>
          </Form.Item>
          <Form.Item label="Cách đếm thứ anh em" name="siblingCountMode">
            <Radio.Group options={[{ value: 'PER_UNION', label: 'Theo từng hôn phối' }, { value: 'PER_FATHER', label: 'Đếm liền theo cha' }]} />
          </Form.Item>
          <Form.Item label="Gọi con cuối là Út" name="useUtLabel" valuePropName="checked"><Switch /></Form.Item>
          <Form.Item label="Mô tả" name="description"><Input.TextArea rows={3} /></Form.Item>
          <Button type="primary" htmlType="submit" loading={save.isPending} block>Lưu</Button>
        </Form>
      </Modal>
    </div>
  )
}
