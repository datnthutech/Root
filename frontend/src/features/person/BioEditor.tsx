import { useRef } from 'react'
import { Button, Input, Space } from 'antd'

/** Soạn Markdown tối giản: **đậm**, *nghiêng*, "- " danh sách. Controlled theo `value`/`onChange` để dùng được trong Form.Item. */
export default function BioEditor({ value = '', onChange }: { value?: string; onChange?: (v: string) => void }) {
  const ref = useRef<HTMLTextAreaElement>(null)
  const wrap = (left: string, right = left) => {
    const el = ref.current
    if (!el) return
    const { selectionStart: s, selectionEnd: e } = el
    onChange?.(`${value.slice(0, s)}${left}${value.slice(s, e) || 'văn bản'}${right}${value.slice(e)}`)
  }
  const bullet = () => onChange?.(`${value}${value && !value.endsWith('\n') ? '\n' : ''}- `)
  return (
    <div>
      <Space style={{ marginBottom: 4 }}>
        <Button size="small" onClick={() => wrap('**')}><b>B</b></Button>
        <Button size="small" onClick={() => wrap('*')}><i>I</i></Button>
        <Button size="small" onClick={bullet}>• Danh sách</Button>
      </Space>
      <Input.TextArea ref={ref as never} value={value} onChange={(e) => onChange?.(e.target.value)} rows={8} maxLength={20000} showCount />
    </div>
  )
}
