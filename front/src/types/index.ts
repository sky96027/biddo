export interface ApiResponse<T> {
  success: boolean
  data: T
  error: { code: string; message: string } | null
}

export interface CursorResponse<T> {
  content: T[]
  nextCursor: string | null
  hasNext: boolean
}

// Auth
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface MemberProfile {
  memberId: number
  email: string
  nickname: string
  profileImageUrl: string | null
  trustScore: number
}

// Auction
export type AuctionStatus = 'PENDING' | 'ACTIVE' | 'ENDED' | 'CANCELLED' | 'SOLD'
export type ItemCondition = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'FAIR' | 'POOR'

export interface AuctionSummary {
  auctionId: number
  title: string
  currentPrice: number
  bidCount: number
  thumbnailUrl: string | null
  endTime: string
  status: AuctionStatus
  sellerNickname: string
  categoryName: string
}

export interface AuctionSellerInfo {
  memberId: number
  nickname: string
  profileImageUrl: string | null
  trustScore: number
}

export interface AuctionCategoryInfo {
  categoryId: number
  name: string
}

export interface AuctionDetail {
  auctionId: number
  title: string
  description: string
  condition: ItemCondition
  currentPrice: number
  startingPrice: number
  buyNowPrice: number | null
  bidCount: number
  viewCount: number
  status: AuctionStatus
  seller: AuctionSellerInfo
  category: AuctionCategoryInfo
  winnerId: number | null
  startTime: string
  endTime: string
  imageUrls: string[]
}

export interface AuctionCreateRequest {
  title: string
  description: string
  categoryId: number
  condition: ItemCondition
  startingPrice: number
  buyNowPrice?: number
  startTime?: string
  endTime: string
  imageUrls: string[]
}

// Bid
export type BidType = 'MANUAL' | 'AUTO' | 'BUY_NOW'

export interface BidHistoryItem {
  bidId: number
  bidderNickname: string
  bidAmount: number
  bidType: BidType
  createdAt: string
}

export interface BidResponse {
  bidId: number
  auctionId: number
  bidAmount: number
  bidType: BidType
  isWinning: boolean
  createdAt: string
}

export interface AutoBidResponse {
  autoBidId: number
  auctionId: number
  maxAmount: number
  isActive: boolean
}

// Notification
export type NotificationType = 'BID' | 'OUTBID' | 'AUCTION_END' | 'WON' | 'PRICE_ALERT' | 'KEYWORD_MATCH'

export interface NotificationItem {
  notificationId: number
  type: NotificationType
  message: string
  isRead: boolean
  auctionId: number | null
  createdAt: string
}

// Category
export interface Category {
  id: number
  name: string
  depth: number
  sortOrder: number
  children?: Category[]
}

// Chat
export type MessageType = 'TEXT' | 'IMAGE' | 'SYSTEM'
export type ChatRoomStatus = 'ACTIVE' | 'CLOSED'

export interface ChatRoom {
  roomId: number
  auctionId: number
  auctionTitle: string
  opponentNickname: string
  lastMessage: string | null
  lastMessageAt: string | null
  unreadCount: number
  status: ChatRoomStatus
}

export interface ChatMessage {
  messageId: number
  senderId: number
  senderNickname: string
  content: string
  messageType: MessageType
  imageUrl: string | null
  createdAt: string
}

// WebSocket
export type WsMessageType = 'NEW_BID' | 'AUCTION_ENDED' | 'AUCTION_SOLD' | 'COUNTDOWN_EXTENDED'

export interface WsMessage {
  type: WsMessageType
  bidder?: string
  amount?: number
  bidType?: BidType
  timestamp?: string
  currentPrice?: number
  bidCount?: number
  winnerId?: number
  finalPrice?: number
  newEndTime?: string
  reason?: string
}