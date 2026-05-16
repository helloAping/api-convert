import request from './request'
import type { ApiResponse, AdminLoginVO } from '@/types'

export function login(username: string, password: string) {
  return request.post<ApiResponse<AdminLoginVO>>('/api/admin/login', { username, password })
}

export function logout() {
  return request.post<ApiResponse<null>>('/api/admin/logout')
}

export function getMe() {
  return request.get<ApiResponse<{ username: string }>>('/api/admin/me')
}
