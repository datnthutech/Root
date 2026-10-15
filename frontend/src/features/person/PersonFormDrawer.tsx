import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { App, Drawer } from 'antd'
import { editApi, type PersonInput } from '../../api/edit'
import { personApi, type PersonProfile } from '../../api/person'
import { errorText } from '../../auth/errorText'
import PersonForm from './PersonForm'

export default function PersonFormDrawer({ open, mode, person, onClose, onSaved }: {
  open: boolean; mode: 'create' | 'edit'; person?: PersonProfile; onClose(): void; onSaved(p: PersonProfile): void
}) {
  const { message } = App.useApp()
  const [busy, setBusy] = useState(false)
  const bio = useQuery({ queryKey: ['person-bio', person?.id], queryFn: () => personApi.biography(person!.id), enabled: open && mode === 'edit' && !!person })

  const submit = async (values: PersonInput) => {
    if (busy) return
    setBusy(true)
    try {
      const saved = mode === 'create' ? await editApi.createPerson(values) : await editApi.updatePerson(person!.id, values)
      message.success('Đã lưu')
      onSaved(saved)
    } catch (e) {
      message.error(errorText((e as { code?: string }).code))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Drawer open={open} onClose={onClose} width="min(520px, 100vw)" destroyOnClose title={mode === 'create' ? 'Thêm người mới' : 'Sửa hồ sơ'}>
      {(mode === 'create' || !bio.isLoading) && (
        <PersonForm initial={person} initialBiography={bio.data?.biography ?? ''} busy={busy} onSubmit={submit} />
      )}
    </Drawer>
  )
}
