import { Fragment, type ReactNode } from 'react'

function inline(text: string, keyBase: string): ReactNode[] {
  return text.split(/(\*\*[^*]+\*\*|\*[^*]+\*)/g).filter(Boolean).map((part, i) => {
    const key = `${keyBase}-${i}`
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) return <strong key={key}>{part.slice(2, -2)}</strong>
    if (part.startsWith('*') && part.endsWith('*') && part.length > 2) return <em key={key}>{part.slice(1, -1)}</em>
    return <Fragment key={key}>{part}</Fragment>
  })
}

export function renderBio(text: string): ReactNode {
  if (!text.trim()) return null
  const blocks: ReactNode[] = []
  let para: string[] = []
  let list: string[] = []
  const flushPara = () => {
    if (!para.length) return
    const k = `p${blocks.length}`
    blocks.push(
      <p key={k}>{para.map((line, i) => <Fragment key={i}>{i > 0 && <br />}{inline(line, `${k}-${i}`)}</Fragment>)}</p>,
    )
    para = []
  }
  const flushList = () => {
    if (!list.length) return
    const k = `l${blocks.length}`
    blocks.push(<ul key={k}>{list.map((item, i) => <li key={i}>{inline(item, `${k}-${i}`)}</li>)}</ul>)
    list = []
  }
  for (const line of text.split('\n')) {
    if (line.startsWith('- ')) { flushPara(); list.push(line.slice(2)) }
    else if (!line.trim()) { flushPara(); flushList() }
    else { flushList(); para.push(line) }
  }
  flushPara(); flushList()
  return <>{blocks}</>
}
