import { Route, Routes } from 'react-router-dom'
import AppLayout from './components/AppLayout'
import { RequireAuth } from './auth/RequireAuth'
import LoginPage from './features/auth/LoginPage'
import RegisterPage from './features/auth/RegisterPage'
import AdminUsersPage from './features/admin/AdminUsersPage'
import ProtoPage from './features/tree/ProtoPage'

function Home() { return <h1 style={{ padding: 16 }}>Gia phả</h1> }

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/proto" element={<ProtoPage />} />
      <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
        <Route path="/" element={<Home />} />
        <Route path="/admin/users" element={<RequireAuth role="ADMIN"><AdminUsersPage /></RequireAuth>} />
      </Route>
    </Routes>
  )
}
