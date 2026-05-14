import request from './request'
import type { ApiResponse, PageResult, RequestLogVO, RequestLogSearchParam } from '@/types'

export const searchRequestLogs = (params: RequestLogSearchParam) =>
  request.get<ApiResponse<PageResult<RequestLogVO>>>('/admin/request-logs', { params })
export const getRequestLog = (id: number) => request.get<ApiResponse<RequestLogVO>>(`/admin/request-logs/${id}`)
