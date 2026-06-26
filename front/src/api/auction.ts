import client from './client'
import type { ApiResponse, CursorResponse, AuctionSummary, AuctionDetail, AuctionCreateRequest, Category } from '../types'

export interface SearchParams {
  keyword?: string
  categoryId?: number
  minPrice?: number
  maxPrice?: number
  endWithin?: '1h' | '24h' | '3d'
  sort?: 'BID_COUNT' | 'END_TIME' | 'PRICE'
  cursor?: string
  size?: number
}

export const searchAuctions = async (params?: SearchParams) => {
  const res = await client.get<ApiResponse<CursorResponse<AuctionSummary>>>('/api/v1/search/auctions', { params })
  return res.data.data
}

export const getPopularAuctions = async (size = 10) => {
  const res = await client.get<ApiResponse<AuctionSummary[]>>('/api/v1/auctions/popular', { params: { size } })
  return res.data.data
}

export const getAuction = async (auctionId: number) => {
  const res = await client.get<ApiResponse<AuctionDetail>>(`/api/v1/auctions/${auctionId}`)
  return res.data.data
}

export const getSimilarAuctions = async (auctionId: number) => {
  const res = await client.get<ApiResponse<AuctionSummary[]>>(`/api/v1/auctions/${auctionId}/similar`, { params: { size: 6 } })
  return res.data.data
}

export const createAuction = async (data: AuctionCreateRequest) => {
  const res = await client.post<ApiResponse<{ auctionId: number }>>('/api/v1/auctions', data)
  return res.data.data
}

export const getCategories = async () => {
  const res = await client.get<ApiResponse<Category[]>>('/api/v1/categories')
  return res.data.data
}