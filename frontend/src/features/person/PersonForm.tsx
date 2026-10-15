import { useState } from 'react'
import type { Rule } from 'antd/es/form'
import { Button, DatePicker, Form, Input, Radio, Select, Space, Switch } from 'antd'
import type { PersonProfile } from '../../api/person'
import type { PersonInput } from '../../api/edit'
import { deathBeforeBirth, fromIso, toIso, type Precision } from '../../lib/dateField'
import BioEditor from './BioEditor'

const PRECISIONS = [{ value: 'DAY', label: 'Ngày' }, { value: 'MONTH', label: 'Tháng' }, { value: 'YEAR', label: 'Năm' }, { value: 'UNKNOWN', label: 'Không rõ' }]
const pickerOf = (p: Precision) => (p === 'YEAR' ? 'year' : p === 'MONTH' ? 'month' : 'date')

function DateWithPrecision({ nameDate, namePrec, label, initialPrec, rules }: { nameDate: string; namePrec: string; label: string; initialPrec: Precision; rules?: Rule[] }) {
  const form = Form.useFormInstance()
  const prec: Precision = Form.useWatch(namePrec, form) ?? initialPrec
  return (
    <Form.Item label={label}>
      <Space.Compact style={{ width: '100%' }}>
        <Form.Item name={namePrec} noStyle initialValue={initialPrec}><Select style={{ width: 110 }} options={PRECISIONS} /></Form.Item>
        <Form.Item name={nameDate} noStyle rules={rules} dependencies={rules ? ['birthDate', 'birthPrecision', namePrec] : undefined}>
          <DatePicker style={{ width: '100%' }} picker={pickerOf(prec)} disabled={prec === 'UNKNOWN'} format={prec === 'YEAR' ? 'YYYY' : prec === 'MONTH' ? 'MM/YYYY' : 'DD/MM/YYYY'} />
        </Form.Item>
      </Space.Compact>
    </Form.Item>
  )
}

export default function PersonForm({ initial, initialBiography = '', submitText = 'Lưu', busy, withBiography = true, defaultGender = 'M', onSubmit }: {
  initial?: PersonProfile; initialBiography?: string; submitText?: string; busy: boolean; withBiography?: boolean; defaultGender?: 'M' | 'F' | 'U'
  onSubmit(values: PersonInput): Promise<void>
}) {
  const [form] = Form.useForm()
  const [alive, setAlive] = useState(initial?.alive ?? true)

  const deathAfterBirth: Rule = ({ getFieldValue }) => ({
    validator: (_, death) => {
      const bp = (getFieldValue('birthPrecision') ?? 'UNKNOWN') as Precision
      const dp = (getFieldValue('deathPrecision') ?? 'UNKNOWN') as Precision
      return deathBeforeBirth(toIso(getFieldValue('birthDate'), bp), bp, toIso(death, dp), dp) ? Promise.reject(new Error('Ngày mất không được trước ngày sinh')) : Promise.resolve()
    },
  })

  const finish = async (v: Record<string, unknown>) => {
    const bp = (v.birthPrecision ?? 'UNKNOWN') as Precision
    const dp = (v.deathPrecision ?? 'UNKNOWN') as Precision
    const birthIso = toIso(v.birthDate as never, bp)
    await onSubmit({
      fullName: String(v.fullName).trim(),
      nickname: v.nickname as string | undefined,
      gender: v.gender as 'M' | 'F' | 'U',
      birthDate: birthIso, birthPrecision: birthIso ? bp : 'UNKNOWN',
      birthLunar: v.birthLunar as string | undefined,
      alive,
      deathDate: alive ? null : toIso(v.deathDate as never, dp), deathPrecision: alive ? 'UNKNOWN' : dp,
      deathAnniversaryLunar: v.deathAnniversaryLunar as string | undefined,
      originPlace: v.originPlace as string | undefined, homePlace: v.homePlace as string | undefined, burialPlace: v.burialPlace as string | undefined,
      biography: withBiography ? (v.biography as string | undefined) : undefined,
      ...(initial ? { version: initial.version } : {}),
    })
  }

  return (
    <Form form={form} layout="vertical" onFinish={finish} disabled={busy}
      initialValues={{
        fullName: initial?.fullName, nickname: initial?.nickname, gender: initial?.gender ?? defaultGender,
        birthDate: fromIso(initial?.birthDate), birthPrecision: (initial?.birthPrecision as Precision) ?? 'UNKNOWN', birthLunar: initial?.birthLunar,
        deathDate: fromIso(initial?.deathDate), deathPrecision: (initial?.deathPrecision as Precision) ?? 'UNKNOWN',
        deathAnniversaryLunar: initial?.deathAnniversaryLunar, originPlace: initial?.originPlace, homePlace: initial?.homePlace, burialPlace: initial?.burialPlace,
      }}>
      <Form.Item label="Họ và tên" name="fullName" rules={[{ required: true, whitespace: true, message: 'Nhập họ và tên' }]}><Input maxLength={200} /></Form.Item>
      <Form.Item label="Tên thường gọi" name="nickname"><Input maxLength={100} /></Form.Item>
      <Form.Item label="Giới tính" name="gender">
        <Radio.Group options={[{ value: 'M', label: 'Nam' }, { value: 'F', label: 'Nữ' }, { value: 'U', label: 'Chưa rõ' }]} />
      </Form.Item>
      <DateWithPrecision nameDate="birthDate" namePrec="birthPrecision" label="Ngày sinh (dương lịch)" initialPrec={(initial?.birthPrecision as Precision) ?? 'UNKNOWN'} />
      <Form.Item label="Ngày sinh (âm lịch)" name="birthLunar"><Input maxLength={40} placeholder="vd: 15/8 Canh Tý" /></Form.Item>
      <Form.Item label="Còn sống"><Switch checked={alive} onChange={setAlive} /></Form.Item>
      {!alive && (
        <>
          <DateWithPrecision nameDate="deathDate" namePrec="deathPrecision" label="Ngày mất (dương lịch)" rules={[deathAfterBirth]} initialPrec={(initial?.deathPrecision as Precision) ?? 'UNKNOWN'} />
          <Form.Item label="Ngày giỗ (âm lịch)" name="deathAnniversaryLunar"><Input maxLength={40} placeholder="vd: 12/3 âm lịch" /></Form.Item>
        </>
      )}
      <Form.Item label="Nguyên quán" name="originPlace"><Input maxLength={200} /></Form.Item>
      <Form.Item label="Quê quán" name="homePlace"><Input maxLength={200} /></Form.Item>
      <Form.Item label="Nơi an táng" name="burialPlace"><Input maxLength={200} /></Form.Item>
      {withBiography && <Form.Item label="Sơ yếu lý lịch" name="biography" initialValue={initialBiography}><BioEditor /></Form.Item>}
      <Button type="primary" htmlType="submit" loading={busy} block>{submitText}</Button>
    </Form>
  )
}
