import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from './App'

test('trang chủ hiển thị tên ứng dụng và liên kết sang trang thử', () => {
  render(<MemoryRouter><App /></MemoryRouter>)
  expect(screen.getByText('Gia phả')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Bản thử vẽ' })).toHaveAttribute('href', '/proto')
})
