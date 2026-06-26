import client from './client'
import type { ApiResponse, BidResponse, AutoBidResponse, CursorResponse, BidHistoryItem } from '../types'

export const getAuctionBids = async (auctionId: number, cursor?: string) => {
  const res = await client.get<ApiResponse<CursorResponse<BidHistoryItem>>>(
    `/api/v1/auctions/${auctionId}/bids`,
    { params: { cursor, size: 20 } }
  )
  return res.data.data
}

export const placeBid = async (auctionId: number, bidAmount: number) => {
  const res = await client.post<ApiResponse<BidResponse>>(`/api/v1/auctions/${auctionId}/bids`, { bidAmount })
  return res.data.data
}

export const buyNow = async (auctionId: number) => {
  const res = await client.post<ApiResponse<BidResponse>>(`/api/v1/auctions/${auctionId}/buy-now`)
  return res.data.data
}

export const setAutoBid = async (auctionId: number, maxAmount: number) => {
  const res = await client.post<ApiResponse<AutoBidResponse>>(`/api/v1/auctions/${auctionId}/auto-bids`, { maxAmount })
  return res.data.data
}

export const cancelAutoBid = async (auctionId: number) => {
  await client.delete(`/api/v1/auctions/${auctionId}/auto-bids`)
}