import { useState, useEffect, useRef } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../../store/authStore'
import { logout } from '../../api/auth'
import { getNotifications, markAsRead } from '../../api/notification'
import { useSSE } from '../../hooks/useSSE'
import type { NotificationItem } from '../../types'

const Header = () => {
  const { member, accessToken, refreshToken, logout: clearAuth } = useAuthStore()
  const navigate = useNavigate()
  const [showNotif, setShowNotif] = useState(false)
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const autoCloseTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (!accessToken) return
    getNotifications()
      .then((res) => {
        setNotifications(res.content)
        setUnreadCount(res.content.filter((n) => !n.isRead).length)
      })
      .catch(() => {})
  }, [accessToken])

  useSSE(
    accessToken ? `/api/v1/notifications/subscribe?token=${accessToken}` : '',
    (data) => {
      const notif = data as NotificationItem
      setNotifications((prev) => [notif, ...prev])
      setUnreadCount((c) => c + 1)
      setShowNotif(true)
      if (autoCloseTimer.current) clearTimeout(autoCloseTimer.current)
      autoCloseTimer.current = setTimeout(() => setShowNotif(false), 4000)
    },
    !!accessToken
  )

  const handleLogout = async () => {
    if (refreshToken) await logout(refreshToken).catch(() => {})
    clearAuth()
    navigate('/login')
  }

  const handleMarkAsRead = async (notif: NotificationItem) => {
    if (!notif.isRead) {
      await markAsRead(notif.notificationId).catch(() => {})
      setNotifications((prev) =>
        prev.map((n) => (n.notificationId === notif.notificationId ? { ...n, isRead: true } : n))
      )
      setUnreadCount((c) => Math.max(0, c - 1))
    }
    if (notif.auctionId) navigate(`/auctions/${notif.auctionId}`)
    setShowNotif(false)
  }

  return (
    <header className="bg-white border-b border-gray-200 sticky top-0 z-50">
      <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between">
        <Link to="/" className="text-xl font-bold text-blue-600">Biddo</Link>

        <div className="flex items-center gap-3">
          {accessToken ? (
            <>
              <Link to="/auctions/create" className="text-sm px-3 py-1.5 bg-blue-600 text-white rounded hover:bg-blue-700">
                경매 등록
              </Link>

              <div className="relative">
                <button
                  onClick={() => setShowNotif(!showNotif)}
                  className="relative p-1.5 text-gray-600 hover:text-gray-900"
                >
                  🔔
                  {unreadCount > 0 && (
                    <span className="absolute -top-1 -right-1 bg-red-500 text-white text-xs rounded-full w-4 h-4 flex items-center justify-center">
                      {unreadCount > 9 ? '9+' : unreadCount}
                    </span>
                  )}
                </button>

                {showNotif && (
                  <div className="absolute right-0 mt-2 w-80 bg-white border border-gray-200 rounded-lg shadow-lg max-h-96 overflow-y-auto">
                    <div className="p-3 border-b text-sm font-medium text-gray-700">알림</div>
                    {notifications.length === 0 ? (
                      <p className="p-4 text-sm text-gray-500 text-center">알림이 없습니다.</p>
                    ) : (
                      notifications.map((n) => (
                        <button
                          key={n.notificationId}
                          onClick={() => handleMarkAsRead(n)}
                          className={`w-full text-left p-3 text-sm border-b hover:bg-gray-50 ${!n.isRead ? 'bg-blue-50' : ''}`}
                        >
                          <p className="text-gray-800">{n.message}</p>
                          <p className="text-gray-400 text-xs mt-1">{new Date(n.createdAt).toLocaleString('ko-KR')}</p>
                        </button>
                      ))
                    )}
                  </div>
                )}
              </div>

              <span className="text-sm text-gray-700">{member?.nickname}</span>
              <button onClick={handleLogout} className="text-sm text-gray-500 hover:text-gray-700">로그아웃</button>
            </>
          ) : (
            <>
              <Link to="/login" className="text-sm text-gray-600 hover:text-gray-900">로그인</Link>
              <Link to="/register" className="text-sm px-3 py-1.5 bg-blue-600 text-white rounded hover:bg-blue-700">회원가입</Link>
            </>
          )}
        </div>
      </div>
    </header>
  )
}

export default Header