import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Drawer, Space } from 'antd'
import { editApi, type PersonInput } from '../../api/edit'
import { personApi, type PersonProfile } from '../../api/person'
import { errorText } from '../../auth/errorText'
import PersonForm from './PersonForm'

type Props = { open: boolean; mode: 'create' | 'edit'; person?: PersonProfile; onClose(): void; onSaved(p: PersonProfile): void }

/** Drawer giữ ổn định (để còn animation đóng); chỉ phần nội dung được khoá lại mỗi lần mở / đổi người để reset state. */
export default function PersonFormDrawer(props: Props) {
  const { open, mode, person, onClose } = props
  const [openCount, setOpenCount] = useState(0)
  const [prevOpen, setPrevOpen] = useState(open)
  if (open !== prevOpen) {
    setPrevOpen(open)
    if (open) setOpenCount((c) => c + 1)
  }
  return (
    <Drawer open={open} onClose={onClose} width="min(520px, 100vw)" destroyOnClose title={mode === 'create' ? 'Thêm người mới' : 'Sửa hồ sơ'}>
      <DrawerContent key={`${person?.id ?? 'new'}-${openCount}`} {...props} />
    </Drawer>
  )
}

function DrawerContent({ open, mode, person, onSaved }: Props) {
  const { message } = App.useApp()
  const qc = useQueryClient()
  const [busy, setBusy] = useState(false)
  const [fresh, setFresh] = useState<PersonProfile | null>(null)
  const [conflict, setConflict] = useState(false)
  const [reloading, setReloading] = useState(false)
  const [formKey, setFormKey] = useState(0)
  const current = fresh ?? person
  const bioKey = ['person-bio', person?.id]
  const bio = useQuery({
    queryKey: bioKey, queryFn: () => personApi.biography(person!.id),
    enabled: open && mode === 'edit' && !!person, staleTime: 0, gcTime: 0, refetchOnMount: 'always', refetchOnWindowFocus: false, refetchOnReconnect: false,
  })

  const submit = async (values: PersonInput) => {
    if (busy) return
    setBusy(true)
    try {
      const saved = mode === 'create' ? await editApi.createPerson(values) : await editApi.updatePerson(person!.id, values)
      message.success('Đã lưu')
      qc.removeQueries({ queryKey: bioKey })
      onSaved(saved)
    } catch (e) {
      const code = (e as { code?: string }).code
      if (code === 'VERSION_CONFLICT') setConflict(true)
      message.error(errorText(code))
    } finally {
      setBusy(false)
    }
  }

  const reload = async () => {
    setReloading(true)
    try {
      const [p, bioRes] = await Promise.all([personApi.profile(person!.id), bio.refetch()])
      if (bioRes.isError) return
      setFresh(p)
      setConflict(false)
      setFormKey((k) => k + 1)
    } catch (e) {
      message.error(errorText((e as { code?: string }).code))
    } finally {
      setReloading(false)
    }
  }

  const formReady = mode === 'create' || (bio.data !== undefined && !bio.isError && !reloading)

  return (
    <>
      {mode === 'edit' && bio.isError && (
        <Alert type="error" showIcon message="Không tải được sơ yếu lý lịch"
          action={<Button size="small" onClick={() => bio.refetch()}>Thử lại</Button>} />
      )}
      {conflict && (
        <Alert type="warning" showIcon style={{ marginBottom: 12 }} message="Dữ liệu đã được người khác sửa"
          action={<Space><Button size="small" loading={reloading} onClick={reload}>Tải lại dữ liệu mới nhất</Button></Space>} />
      )}
      {formReady && (
        <PersonForm key={formKey} initial={current} initialBiography={bio.data?.biography ?? ''} busy={busy} onSubmit={submit} />
      )}
    </>
  )
}
