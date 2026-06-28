import { useState, useEffect, useRef, FormEvent } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getChatMessages, sendChatMessage } from '../api/chat'
import { useAuthStore } from '../store/authStore'
import type { ChatMessage } from '../types'

const formatTime = (iso: string) =>
  new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })

const ChatRoomPage = () => {
  const { roomId } = useParams<{ roomId: string }>()
  const navigate = useNavigate()
  const member = useAuthStore((s) => s.member)

  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const id = Number(roomId)

  useEffect(() => {
    getChatMessages(id)
      .then((res) => setMessages([...res.content].reverse()))
      .catch((e) => setError(e.response?.data?.error?.message ?? '메시지를 불러오지 못했습니다.'))
  }, [id])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  useEffect(() => {
    const stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        stompClient.subscribe(`/topic/chat/${id}`, (frame) => {
          try {
            const msg = JSON.parse(frame.body) as ChatMessage
            setMessages((prev) => {
              if (prev.some((m) => m.messageId === msg.messageId)) return prev
              return [...prev, msg]
            })
          } catch {
            // ignore malformed frames
          }
        })
      },
    })
    stompClient.activate()
    return () => { stompClient.deactivate() }
  }, [id])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const content = input.trim()
    if (!content) return
    setInput('')
    setSending(true)
    setError(null)
    try {
      await sendChatMessage(id, content)
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { error?: { message?: string } } } })?.response?.data?.error?.message
      setError(msg ?? '전송에 실패했습니다.')
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto flex flex-col h-[calc(100vh-64px)]">
      <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-200 bg-white">
        <button onClick={() => navigate('/chat')} className="text-gray-400 hover:text-gray-600 text-xl leading-none">‹</button>
        <h2 className="font-semibold text-gray-800">채팅</h2>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-3 bg-gray-50">
        {error && <p className="text-sm text-red-500 text-center">{error}</p>}
        {messages.map((msg) => {
          const isMine = msg.senderId === member?.memberId
          const isSystem = msg.messageType === 'SYSTEM'

          if (isSystem) {
            return (
              <div key={msg.messageId} className="flex justify-center">
                <span className="text-xs text-gray-400 bg-gray-200 rounded-full px-3 py-1">{msg.content}</span>
              </div>
            )
          }

          return (
            <div key={msg.messageId} className={`flex ${isMine ? 'justify-end' : 'justify-start'}`}>
              {!isMine && (
                <div className="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center text-blue-600 text-xs font-bold mr-2 flex-shrink-0 self-end">
                  {msg.senderNickname.slice(0, 1)}
                </div>
              )}
              <div className={`flex flex-col ${isMine ? 'items-end' : 'items-start'} max-w-[70%]`}>
                {!isMine && <span className="text-xs text-gray-500 mb-1">{msg.senderNickname}</span>}
                <div className={`px-3 py-2 rounded-2xl text-sm break-words ${
                  isMine
                    ? 'bg-blue-500 text-white rounded-br-sm'
                    : 'bg-white text-gray-800 border border-gray-200 rounded-bl-sm'
                }`}>
                  {msg.content}
                </div>
                <span className="text-xs text-gray-400 mt-1">{formatTime(msg.createdAt)}</span>
              </div>
            </div>
          )
        })}
        <div ref={bottomRef} />
      </div>

      <form onSubmit={handleSubmit} className="flex gap-2 px-4 py-3 border-t border-gray-200 bg-white">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="메시지를 입력하세요"
          disabled={sending}
          className="flex-1 border border-gray-300 rounded-full px-4 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
        <button
          type="submit"
          disabled={sending || !input.trim()}
          className="bg-blue-500 text-white rounded-full px-4 py-2 text-sm font-medium hover:bg-blue-600 disabled:opacity-50"
        >
          전송
        </button>
      </form>
    </div>
  )
}

export default ChatRoomPage