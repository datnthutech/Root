import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AuthContext } from './auth/AuthContext'
import App from './App'

test('chưa đăng nhập vào / thì thấy trang đăng nhập', () => {
  render(
    <AuthContext.Provider value={{ user: null, loading: false, login: async () => {}, logout: async () => {} }}>
      <MemoryRouter initialEntries={['/']}><App /></MemoryRouter>
    </AuthContext.Provider>,
  )
  expect(screen.getByText('Đăng nhập gia phả')).toBeInTheDocument()
})
