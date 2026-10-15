import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthProvider, useAuth } from './AuthContext'
import { authApi } from '../api/auth'

vi.mock('../api/auth', () => ({ authApi: { refresh: vi.fn(), login: vi.fn(), logout: vi.fn() } }))

const tok = (id: number) => ({ accessToken: 't' + id, expiresInSeconds: 900, user: { id, username: 'u' + id, displayName: null, role: 'VIEWER', linkedPersonId: null } })

function Probe() {
  const a = useAuth()
  return <>
    <span>{a.loading ? 'loading' : a.user ? `user${a.user.id}` : 'anon'}</span>
    <button onClick={() => a.logout()}>out</button>
    <button onClick={() => a.login('x', 'y')}>in</button>
  </>
}

test('đăng xuất và đổi người dùng gọi onSessionReset; tải phiên ban đầu thì không', async () => {
  vi.mocked(authApi.refresh).mockResolvedValue(tok(1) as never)
  vi.mocked(authApi.logout).mockResolvedValue(undefined)
  vi.mocked(authApi.login).mockResolvedValue(tok(2) as never)
  const reset = vi.fn()
  render(<AuthProvider onSessionReset={reset}><Probe /></AuthProvider>)
  await screen.findByText('user1')
  expect(reset).not.toHaveBeenCalled()
  await userEvent.click(screen.getByText('out'))
  await screen.findByText('anon')
  await waitFor(() => expect(reset).toHaveBeenCalledTimes(1))
  await userEvent.click(screen.getByText('in'))
  await screen.findByText('user2')
  await waitFor(() => expect(reset).toHaveBeenCalledTimes(2))
})
