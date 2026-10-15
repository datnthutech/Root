import { useQuery } from '@tanstack/react-query'
import { Alert, Modal, Spin, Tabs } from 'antd'
import { personApi } from '../../api/person'
import { errorText } from '../../auth/errorText'
import BioTab from './BioTab'
import FamilyTab from './FamilyTab'
import ProfileTab from './ProfileTab'

export default function PersonDialog({ personId, onClose, onNavigate }: {
  personId: number | null; onClose(): void; onNavigate(id: number): void
}) {
  const enabled = personId !== null
  const profile = useQuery({ queryKey: ['person', personId], queryFn: () => personApi.profile(personId!), enabled })
  const family = useQuery({ queryKey: ['person-family', personId], queryFn: () => personApi.family(personId!), enabled })
  const bio = useQuery({ queryKey: ['person-bio', personId], queryFn: () => personApi.biography(personId!), enabled })

  const wrap = (q: { isLoading: boolean; error: unknown; data?: unknown }, node: () => React.ReactNode) =>
    !enabled ? null : q.isLoading || (!q.error && q.data === undefined) ? <Spin /> : q.error ? <Alert type="error" message={errorText((q.error as { code?: string }).code)} /> : node()

  return (
    <Modal open={enabled} onCancel={onClose} footer={null} destroyOnClose width="min(720px, calc(100vw - 16px))" style={{ top: 8 }}
      title="Thông tin">
      <Tabs items={[
        { key: 'profile', label: 'Hồ sơ', children: wrap(profile, () => <ProfileTab p={profile.data!} />) },
        { key: 'family', label: 'Gia đình', children: wrap(family, () => <FamilyTab f={family.data!} onNavigate={onNavigate} />) },
        { key: 'bio', label: 'Sơ yếu lý lịch', children: wrap(bio, () => <BioTab text={bio.data!.biography} />) },
      ]} />
    </Modal>
  )
}
