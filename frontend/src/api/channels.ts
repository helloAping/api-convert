import request from './request'
import type { ApiResponse, ChannelAuthStartVO, ChannelAuthStatusVO, ChannelForm, ChannelModelFetchRequest, ChannelQuotaVO, ChannelVO, UpstreamModelVO } from '@/types'

/** 获取管理表格中的所有自定义上游渠道。 */
export const getChannels = () => request.get<ApiResponse<ChannelVO[]>>('/api/admin/channels')

/** 创建渠道聚合记录，包括供应商、端点、可选凭证和可选模型映射。 */
export const createChannel = (data: ChannelForm) => request.post<ApiResponse<ChannelVO>>('/api/admin/channels', data)

/** 更新渠道配置；apiKey 为空表示保留现有凭证。 */
export const updateChannel = (id: number, data: ChannelForm) => request.put<ApiResponse<ChannelVO>>(`/api/admin/channels/${id}`, data)

/** 删除渠道聚合记录，后端会同步删除依赖的端点、凭证和模型记录。 */
export const deleteChannel = (id: number) => request.delete<ApiResponse<null>>(`/api/admin/channels/${id}`)

/** 实时获取渠道在上游供应商侧的额度，不保存结果。 */
export const fetchChannelQuota = (id: number) => request.get<ApiResponse<ChannelQuotaVO>>(`/api/admin/channels/${id}/quota`)

/** 在渠道表单保存前请求供应商特定的模型发现。 */
export const fetchChannelModels = (data: ChannelModelFetchRequest) =>
  request.post<ApiResponse<UpstreamModelVO[]>>('/api/admin/channels/models', data)

/** 上传 GPT_AUTH / CLAUDE_AUTH 渠道使用的 auth.json。 */
export const uploadChannelAuth = (id: number, file: File) => {
  const data = new FormData()
  data.append('file', file)
  return request.post<ApiResponse<ChannelAuthStatusVO>>(`/api/admin/channels/${id}/auth/upload`, data)
}

/** 生成 OAuth 登录授权链接。 */
export const startChannelAuth = (id: number) =>
  request.post<ApiResponse<ChannelAuthStartVO>>(`/api/admin/channels/${id}/auth/start`)

/** 提交浏览器授权后跳转到 localhost 的完整回调 URL。 */
export const submitChannelAuthCallbackUrl = (id: number, callbackUrl: string) =>
  request.post<ApiResponse<ChannelAuthStatusVO>>(`/api/admin/channels/${id}/auth/callback-url`, { callbackUrl })

/** 查询渠道授权文件绑定状态。 */
export const getChannelAuthStatus = (id: number) =>
  request.get<ApiResponse<ChannelAuthStatusVO>>(`/api/admin/channels/${id}/auth/status`)

/** 删除渠道授权文件引用。 */
export const deleteChannelAuth = (id: number) =>
  request.delete<ApiResponse<ChannelAuthStatusVO>>(`/api/admin/channels/${id}/auth`)
