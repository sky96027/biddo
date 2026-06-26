import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { MemberProfile } from '../types'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  member: MemberProfile | null
  setTokens: (accessToken: string, refreshToken: string) => void
  setMember: (member: MemberProfile) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      member: null,
      setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
      setMember: (member) => set({ member }),
      logout: () => set({ accessToken: null, refreshToken: null, member: null }),
    }),
    { name: 'biddo-auth' }
  )
)