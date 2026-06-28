import { useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import axios from 'axios'
import Header from './components/layout/Header'
import PrivateRoute from './components/common/PrivateRoute'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import AuctionListPage from './pages/AuctionListPage'
import AuctionCreatePage from './pages/AuctionCreatePage'
import AuctionDetailPage from './pages/AuctionDetailPage'
import ChatListPage from './pages/ChatListPage'
import ChatRoomPage from './pages/ChatRoomPage'

const ServerDownPage = () => (
  <div className="min-h-screen flex flex-col items-center justify-center bg-gray-50 px-4">
    <div className="text-center max-w-md">
      <div className="text-6xl mb-6">🔧</div>
      <h1 className="text-2xl font-bold text-gray-800 mb-3">서버 점검 중입니다</h1>
      <p className="text-gray-500 mb-2">포트폴리오 시연 목적으로 운영되는 서비스로,</p>
      <p className="text-gray-500 mb-1">매일 <span className="font-medium text-gray-700">09:00 ~ 19:00</span> 운영됩니다.</p>
      <p className="text-gray-500 mb-6">즉시 확인을 원하실 경우 이력서의 연락처로 문의해 주세요.</p>
      <p className="text-sm text-gray-400">자동으로 재연결을 시도하고 있습니다...</p>
    </div>
  </div>
)

const App = () => {
  const [serverDown, setServerDown] = useState(false)
  const [checked, setChecked] = useState(false)

  useEffect(() => {
    const check = async () => {
      try {
        await axios.get('/api/v1/categories', { timeout: 5000 })
        setServerDown(false)
      } catch (e: unknown) {
        if (axios.isAxiosError(e)) {
          const status = e.response?.status
          if (!status || status === 502 || status === 503 || status === 504) setServerDown(true)
        }
      } finally {
        setChecked(true)
      }
    }

    void check()
    const timer = setInterval(() => void check(), 5000)
    return () => clearInterval(timer)
  }, [])

  if (!checked) return null

  if (serverDown) return <ServerDownPage />

  return (
    <BrowserRouter>
      <Header />
      <main>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/" element={<AuctionListPage />} />
          <Route path="/auctions/:id" element={<AuctionDetailPage />} />
          <Route
            path="/auctions/create"
            element={<PrivateRoute><AuctionCreatePage /></PrivateRoute>}
          />
          <Route
            path="/chat"
            element={<PrivateRoute><ChatListPage /></PrivateRoute>}
          />
          <Route
            path="/chat/:roomId"
            element={<PrivateRoute><ChatRoomPage /></PrivateRoute>}
          />
        </Routes>
      </main>
    </BrowserRouter>
  )
}

export default App