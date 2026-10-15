import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Empty, List, Popconfirm, Space, Spin, Table, Typography } from 'antd'
import { AUDIT_PAGE, auditApi, type AuditEntry } from '../../api/audit'
import { errorText } from '../../auth/errorText'
import { formatDateTime } from '../../lib/format'

export default function AuditTab({ personId, canRevert, onReverted }: { personId: number; canRevert: boolean; onReverted(): void }) {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const q = useInfiniteQuery({
    queryKey: ['audit', personId],
    queryFn: ({ pageParam }) => auditApi.list(personId, pageParam, AUDIT_PAGE),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last: AuditEntry[]) => (last.length === AUDIT_PAGE ? last[last.length - 1].id : undefined),
  })

  const revert = async (id: number) => {
    try {
      await auditApi.revert(id)
      message.success('Đã khôi phục')
      await qc.invalidateQueries({ queryKey: ['audit', personId] })
      onReverted()
    } catch (e) {
      message.error(errorText((e as { code?: string }).code))
    }
  }

  if (q.isLoading) return <Spin />
  const items = q.data?.pages.flat() ?? []
  if (!items.length) return <Empty description="Chưa có lịch sử sửa" />
  return (
    <div>
      <List dataSource={items} itemLayout="vertical" renderItem={(e) => (
        <List.Item key={e.id} extra={canRevert && e.revertible ? (
          <Popconfirm title="Khôi phục về trạng thái trước lần thay đổi này?" okText="Khôi phục bản này" cancelText="Huỷ" onConfirm={() => revert(e.id)}>
            <Button size="small">Khôi phục</Button>
          </Popconfirm>) : null}>
          <Space direction="vertical" size={2} style={{ width: '100%' }}>
            <Typography.Text type="secondary">{formatDateTime(e.createdAt)} · {e.displayName ?? e.username ?? 'Hệ thống'}</Typography.Text>
            <Typography.Text strong>{e.summary}</Typography.Text>
            {e.changes.length > 0 && (
              <Table size="small" pagination={false} rowKey={(c) => `${c.field}-${e.changes.indexOf(c)}`} dataSource={e.changes} scroll={{ x: true }}
                columns={[{ title: 'Trường', dataIndex: 'label' }, { title: 'Trước', dataIndex: 'from' }, { title: 'Sau', dataIndex: 'to' }]} />
            )}
          </Space>
        </List.Item>
      )} />
      {q.hasNextPage && <Button block loading={q.isFetchingNextPage} onClick={() => q.fetchNextPage()}>Tải thêm</Button>}
    </div>
  )
}
