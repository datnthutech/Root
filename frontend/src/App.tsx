import { Link, Route, Routes } from 'react-router-dom'
import ProtoPage from './features/tree/ProtoPage'

export default function App() {
  return (
    <Routes>
      <Route path="/proto" element={<ProtoPage />} />
      <Route path="*" element={<div><h1>Gia phả</h1><Link to="/proto">Bản thử vẽ</Link></div>} />
    </Routes>
  )
}
