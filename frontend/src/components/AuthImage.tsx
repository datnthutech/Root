import { useEffect, useState } from 'react'
import { Avatar } from 'antd'
import { UserOutlined } from '@ant-design/icons'
import { rawFetch } from '../api/http'

export function AuthImage({ src, size, alt }: { src: string | null | undefined; size: number; alt?: string }) {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    if (!src) { setUrl(null); return }
    let revoked: string | null = null
    let cancelled = false
    rawFetch(src).then(async (r) => {
      if (!r.ok || cancelled) return
      const blob = await r.blob()
      if (cancelled) return
      revoked = URL.createObjectURL(blob)
      setUrl(revoked)
    }).catch(() => { /* hiện avatar mặc định */ })
    return () => { cancelled = true; if (revoked) URL.revokeObjectURL(revoked) }
  }, [src])
  return <Avatar size={size} src={url ?? undefined} icon={!url && <UserOutlined />} alt={alt} />
}
