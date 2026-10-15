import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Modal, Spin, Tabs } from 'antd'
import { personApi } from '../../api/person'
import { editApi } from '../../api/edit'
import { errorText } from '../../auth/errorText'
import { useAuth } from '../../auth/AuthContext'
import { hasRole } from '../../auth/RequireAuth'
import AuditTab from './AuditTab'
import BioTab from './BioTab'
import FamilyTab from './FamilyTab'
import PersonFormDrawer from './PersonFormDrawer'
import ProfileTab from './ProfileTab'

export default function PersonDialog({ personId, onClose, onNavigate, onTreeChanged }: {
  personId: number | null; onClose(): void; onNavigate(id: number): void; onTreeChanged(): void
}) {
  const { user } = useAuth()
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [editing, setEditing] = useState(false)
  const enabled = personId !== null
  const profile = useQuery({ queryKey: ['person', personId], queryFn: () => personApi.profile(personId!), enabled })
  const family = useQuery({ queryKey: ['person-family', personId], queryFn: () => personApi.family(personId!), enabled })
  const bio = useQuery({ queryKey: ['person-bio', personId], queryFn: () => personApi.biography(personId!), enabled })

  const onChanged = () => {
    void qc.invalidateQueries({ queryKey: ['person'] })
    void qc.invalidateQueries({ queryKey: ['person-family'] })
    void qc.invalidateQueries({ queryKey: ['person-bio'] })
    onTreeChanged()
  }
  const close = () => { setEditing(false); onClose() }
  const onDelete = async () => {
    try {
      await editApi.deletePerson(personId!)
      message.success('Đã xoá')
      close()
      onTreeChanged()
    } catch (e) {
      message.error(errorText((e as { code?: string }).code))
    }
  }

  const wrap = (q: { isLoading: boolean; error: unknown; data?: unknown }, node: () => React.ReactNode) =>
    !enabled ? null : q.isLoading || (!q.error && q.data === undefined) ? <Spin /> : q.error ? <Alert type="error" message={errorText((q.error as { code?: string }).code)} /> : node()

  return (
    <>
      <Modal open={enabled} onCancel={close} footer={null} destroyOnClose width="min(720px, calc(100vw - 16px))" style={{ top: 8 }}
        title="Thông tin">
        <Tabs items={[
          { key: 'profile', label: 'Hồ sơ', children: wrap(profile, () => (
            <ProfileTab p={profile.data!} canOrder={hasRole(user, 'EDITOR')} isAdmin={hasRole(user, 'ADMIN')}
              onEdit={() => setEditing(true)} onDelete={onDelete} onChanged={onChanged} />)) },
          { key: 'family', label: 'Gia đình', children: wrap(family, () => (profile.data ? (
            <FamilyTab f={family.data!} personId={personId!} personName={profile.data.fullName} personGender={profile.data.gender}
              canManage={hasRole(user, 'EDITOR')} onNavigate={onNavigate} onChanged={onChanged} />) : <Spin />)) },
          { key: 'bio', label: 'Sơ yếu lý lịch', children: wrap(bio, () => <BioTab text={bio.data!.biography} />) },
          ...(hasRole(user, 'EDITOR') ? [{
            key: 'history', label: 'Lịch sử sửa',
            children: <AuditTab personId={personId!} canRevert={hasRole(user, 'ADMIN')} onReverted={onChanged} />,
          }] : []),
        ]} />
      </Modal>
      {/* giữ person cuối cùng khi đóng để animation đóng không bị trống */}
      {profile.data && (
        <PersonFormDrawer open={editing} mode="edit" person={profile.data} onClose={() => setEditing(false)}
          onSaved={() => { setEditing(false); onChanged() }} />
      )}
    </>
  )
}
