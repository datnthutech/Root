import { render, screen } from '@testing-library/react'
import { renderBio } from './bio'

test('đậm, nghiêng, danh sách, xuống dòng', () => {
  const { container } = render(<div>{renderBio('Ông **Tổ** sinh *năm 1900*\nQuê Bến Tre\n\n- Con A\n- Con B')}</div>)
  expect(container.querySelector('strong')?.textContent).toBe('Tổ')
  expect(container.querySelector('em')?.textContent).toBe('năm 1900')
  expect(container.querySelectorAll('li')).toHaveLength(2)
  expect(container.querySelectorAll('br')).toHaveLength(1)
})
test('HTML trong văn bản hiện nguyên chữ, không được thực thi', () => {
  const { container } = render(<div>{renderBio('<script>alert(1)</script> <b>x</b>')}</div>)
  expect(container.querySelector('script')).toBeNull()
  expect(container.querySelector('b')).toBeNull()
  expect(screen.getByText(/<script>alert\(1\)<\/script>/)).toBeInTheDocument()
})
test('văn bản rỗng không render gì', () => {
  const { container } = render(<div>{renderBio('')}</div>)
  expect(container.firstChild?.childNodes.length).toBe(0)
})
