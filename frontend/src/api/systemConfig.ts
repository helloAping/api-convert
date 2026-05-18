import request from './request'
import type { ApiResponse, RoutingConfigForm, RoutingConfigVO } from '@/types'

/** 读取当前网关路由配置。 */
export const getRoutingConfig = () =>
  request.get<ApiResponse<RoutingConfigVO>>('/api/admin/system-config/routing')

/** 更新网关路由模式、会话粘性和失败避让参数。 */
export const updateRoutingConfig = (data: RoutingConfigForm) =>
  request.put<ApiResponse<RoutingConfigVO>>('/api/admin/system-config/routing', data)
