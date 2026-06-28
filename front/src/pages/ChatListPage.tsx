import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getChatRooms } from '../api/chat'
import type { ChatRoom } from '../types'

const formatTime = (iso: string | null) => {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  if (isToday) return d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
  return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
}

const ChatListPage = () => {
  const navigate = useNavigate()
  const [rooms, setRooms] = useState<ChatRoom[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getChatRooms()
      .then(setRooms)
      .catch((e) => setError(e.response?.data?.error?.message ?? '채팅 목록을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <p className="text-center py-16 text-gray-400">불러오는 중...</p>
  if (error) return <p className="text-center py-16 text-red-500">{error}</p>

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-800 mb-6">채팅</h1>

      {rooms.length === 0 ? (
        <p className="text-center py-16 text-gray-400">진행 중인 채팅이 없습니다.</p>
      ) : (
        <ul className="divide-y divide-gray-100 border border-gray-200 rounded-lg overflow-hidden">
          {rooms.map((room) => (
            <li key={room.roomId}>
              <button
                onClick={() => navigate(`/chat/${room.roomId}`)}
                className="w-full flex items-center gap-4 px-4 py-4 hover:bg-gray-50 transition-colors text-left"
              >
                <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 font-bold text-sm flex-shrink-0">
                  {room.opponentNickname.slice(0, 1)}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between">
                    <span className="font-medium text-gray-800 truncate">{room.opponentNickname}</span>
                    <span className="text-xs text-gray-400 flex-shrink-0 ml-2">{formatTime(room.lastMessageAt)}</span>
                  </div>
                  <p className="text-xs text-gray-500 truncate mt-0.5">{room.auctionTitle}</p>
                  <p className="text-sm text-gray-600 truncate mt-0.5">
                    {room.lastMessage ?? '메시지가 없습니다.'}
                  </p>
                </div>
                {room.unreadCount > 0 && (
                  <span className="flex-shrink-0 bg-blue-500 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center">
                    {room.unreadCount > 99 ? '99+' : room.unreadCount}
                  </span>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default ChatListPage