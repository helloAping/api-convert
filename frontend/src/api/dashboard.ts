import request from './request'
import type { ApiResponse, DashboardStatsVO } from '@/types'

export interface DashboardStatsParam {
  days?: number
  hours?: number
  topN?: number
}

/** 加载控制台仪表盘聚合统计。 */
export const getDashboardStats = (params: DashboardStatsParam) =>
  request.get<ApiResponse<DashboardStatsVO>>('/api/admin/dashboard/stats', { params })
