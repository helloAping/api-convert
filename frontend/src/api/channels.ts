import request from './request'
import type { ApiResponse, ChannelForm, ChannelModelFetchRequest, ChannelQuotaVO, ChannelVO, UpstreamModelVO } from '@/types'

/** 获取管理表格中的所有自定义上游渠道。 */
export const getChannels = () => request.get<ApiResponse<ChannelVO[]>>('/api/admin/channels')

/** 创建渠道聚合记录，包括供应商、端点、可选凭证和可选模型映射。 */
export const createChannel = (data: ChannelForm) => request.post<ApiResponse<ChannelVO>>('/api/admin/channels', data)

/** 更新渠道配置；apiKey 为空表示保留现有凭证。 */
export const updateChannel = (id: number, data: ChannelForm) => request.put<ApiResponse<ChannelVO>>(`/admin/channels/${id}`, data)

/** 删除渠道聚合记录，后端会同步删除依赖的端点、凭证和模型记录。 */
export const deleteChannel = (id: number) => request.delete<ApiResponse<null>>(`/admin/channels/${id}`)

/** 实时获取渠道在上游供应商侧的额度，不保存结果。 */
export const fetchChannelQuota = (id: number) => request.get<ApiResponse<ChannelQuotaVO>>(`/admin/channels/${id}/quota`)

/** 在渠道表单保存前请求供应商特定的模型发现。 */
export const fetchChannelModels = (data: ChannelModelFetchRequest) =>
  request.post<ApiResponse<UpstreamModelVO[]>>('/admin/channels/models', data)
