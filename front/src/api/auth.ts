import client from './client'
import type { ApiResponse, LoginResponse, MemberProfile } from '../types'

export const login = async (email: string, password: string) => {
  const res = await client.post<ApiResponse<LoginResponse>>('/api/v1/auth/login', { email, password })
  return res.data.data
}

export const signup = async (email: string, password: string, nickname: string) => {
  const res = await client.post<ApiResponse<MemberProfile>>('/api/v1/auth/signup', { email, password, nickname })
  return res.data.data
}

export const logout = async (refreshToken: string) => {
  await client.post('/api/v1/auth/logout', { refreshToken })
}

export const getMyProfile = async () => {
  const res = await client.get<ApiResponse<MemberProfile>>('/api/v1/members/me')
  return res.data.data
}