package cn.ms08.apiconvert.dto.admin;

/**
 * 管理端在保存渠道模型映射前预览上游模型的请求。
 *
 * @param type 供应商协议类型，用于选择对应的 AiProviderClient
 * @param channelId 编辑已有渠道时的渠道 ID，用于 API Key 留空时回退读取已保存凭证
 * @param baseUrl 上游 Base URL
 * @param modelsPath 供应商特定的模型列表路径
 * @param apiKey 供应商凭证，仅用于本次上游请求，不会被该接口持久化
 */
public record ChannelModelFetchRequest(
    String type,
    Long channelId,
    String baseUrl,
    String modelsPath,
    String apiKey
) {}
