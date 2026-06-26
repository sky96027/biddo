import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Header from './components/layout/Header'
import PrivateRoute from './components/common/PrivateRoute'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import AuctionListPage from './pages/AuctionListPage'
import AuctionCreatePage from './pages/AuctionCreatePage'
import AuctionDetailPage from './pages/AuctionDetailPage'

const App = () => (
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
      </Routes>
    </main>
  </BrowserRouter>
)

export default App