import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './components/AppLayout'
import { RequireAuth } from './auth/RequireAuth'
import LoginPage from './features/auth/LoginPage'
import RegisterPage from './features/auth/RegisterPage'
import AdminUsersPage from './features/admin/AdminUsersPage'
import AdminClansPage from './features/admin/AdminClansPage'
import ImportPage from './features/import/ImportPage'
import ProtoPage from './features/tree/ProtoPage'
import TreePage from './features/tree/TreePage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/proto" element={<ProtoPage />} />
      <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
        <Route path="/" element={<Navigate to="/tree" replace />} />
        <Route path="/tree" element={<TreePage />} />
        <Route path="/admin/users" element={<RequireAuth role="ADMIN"><AdminUsersPage /></RequireAuth>} />
        <Route path="/admin/clans" element={<RequireAuth role="ADMIN"><AdminClansPage /></RequireAuth>} />
        <Route path="/admin/import" element={<RequireAuth role="ADMIN"><ImportPage /></RequireAuth>} />
      </Route>
    </Routes>
  )
}
