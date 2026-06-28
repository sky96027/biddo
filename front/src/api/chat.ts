import client from './client'
import type { ApiResponse, CursorResponse, ChatRoom, ChatMessage } from '../types'

export const getChatRooms = async (): Promise<ChatRoom[]> => {
  const res = await client.get<ApiResponse<ChatRoom[]>>('/api/v1/chat/rooms')
  return res.data.data
}

export const getChatMessages = async (
  roomId: number,
  cursor?: number
): Promise<CursorResponse<ChatMessage>> => {
  const res = await client.get<ApiResponse<CursorResponse<ChatMessage>>>(
    `/api/v1/chat/rooms/${roomId}/messages`,
    { params: cursor ? { cursor } : undefined }
  )
  return res.data.data
}

export const sendChatMessage = async (roomId: number, content: string): Promise<ChatMessage> => {
  const res = await client.post<ApiResponse<ChatMessage>>(
    `/api/v1/chat/rooms/${roomId}/messages`,
    { content }
  )
  return res.data.data
}