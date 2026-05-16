import request from './request'
import type { ApiResponse, ApiKeyVO, ApiKeyCreationVO, ApiKeyForm, ApiKeyQuotaAddRequest, ApiKeyUpdateForm } from '@/types'

export const getApiKeys = () => request.get<ApiResponse<ApiKeyVO[]>>('/api/admin/api-keys')
export const createApiKey = (data: ApiKeyForm) => request.post<ApiResponse<ApiKeyCreationVO>>('/api/admin/api-keys', data)
export const updateApiKey = (id: number, data: ApiKeyUpdateForm) => request.put<ApiResponse<ApiKeyVO>>(`/api/admin/api-keys/${id}`, data)
export const addApiKeyQuota = (id: number, data: ApiKeyQuotaAddRequest) => request.post<ApiResponse<ApiKeyVO>>(`/api/admin/api-keys/${id}/quota`, data)
export const deleteApiKey = (id: number) => request.delete<ApiResponse<null>>(`/api/admin/api-keys/${id}`)
