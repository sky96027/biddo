import client from './client'
import type { ApiResponse, CursorResponse, NotificationItem } from '../types'

export const getNotifications = async (cursor?: string) => {
  const res = await client.get<ApiResponse<CursorResponse<NotificationItem>>>('/api/v1/notifications', {
    params: { isRead: false, cursor, size: 20 },
  })
  return res.data.data
}

export const markAsRead = async (notificationId: number) => {
  await client.patch(`/api/v1/notifications/${notificationId}/read`)
}