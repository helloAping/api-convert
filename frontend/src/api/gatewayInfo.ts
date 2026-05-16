import request from './request'
import type { ApiResponse, GatewayInfoVO } from '@/types'

/** 加载控制台展示的后端 Base URL 和公开网关端点清单。 */
export const getGatewayInfo = () => request.get<ApiResponse<GatewayInfoVO>>('/api/admin/gateway-info')
