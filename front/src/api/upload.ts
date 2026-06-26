import axios from 'axios'
import client from './client'
import type { ApiResponse } from '../types'

interface PresignedUrlResponse {
  presignedUrl: string
  fileUrl: string
  expiresIn: number
}

export const uploadImage = async (file: File, purpose = 'AUCTION'): Promise<string> => {
  const { presignedUrl, fileUrl } = await getPresignedUrl(file.name, file.type, purpose)
  await axios.put(presignedUrl, file, { headers: { 'Content-Type': file.type } })
  return fileUrl
}

const getPresignedUrl = async (fileName: string, contentType: string, purpose: string) => {
  const res = await client.post<ApiResponse<PresignedUrlResponse>>('/api/v1/upload/presigned-url', {
    fileName,
    contentType,
    purpose,
  })
  return res.data.data
}