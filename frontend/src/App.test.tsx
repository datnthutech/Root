import { render, screen } from '@testing-library/react'
import App from './App'

test('hiển thị tên ứng dụng', () => {
  render(<App />)
  expect(screen.getByText('Gia phả')).toBeInTheDocument()
})
