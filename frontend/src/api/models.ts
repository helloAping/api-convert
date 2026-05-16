import request from './request'
import type { ApiResponse, ModelCapabilitiesForm, ModelEnabledForm, ModelQuotaForm, ModelVO } from '@/types'

/** 加载由后端按对外模型名聚合后的模型列表。 */
export const getModels = () => request.get<ApiResponse<ModelVO[]>>('/admin/models')

/** 更新模型按百万 token 计费的额度单价。 */
export const updateModelQuota = (id: number, data: ModelQuotaForm) =>
  request.put<ApiResponse<ModelVO>>(`/admin/models/${id}/quota`, data)

/** 启用或关闭同一对外模型名下的全部渠道映射。 */
export const updateModelEnabled = (id: number, data: ModelEnabledForm) =>
  request.put<ApiResponse<ModelVO>>(`/admin/models/${id}/enabled`, data)

/** 更新模型能力配置（同步到同名模型的所有渠道映射）。 */
export const updateModelCapabilities = (id: number, data: ModelCapabilitiesForm) =>
  request.put<ApiResponse<ModelVO>>(`/admin/models/${id}/capabilities`, data)
