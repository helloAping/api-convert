package cn.ms08.apiconvert.dto.admin;

import java.util.List;

/**
 * 管理端创建或更新上游转发渠道的表单。
 *
 * @param code 稳定的渠道编码，持久化时也作为 provider_code 使用
 * @param name 管理后台展示名称
 * @param type 供应商类型（ProviderType），例如 OPENAI_COMPATIBLE、ANTHROPIC、OPENAI_RESPONSES、GEMINI
 * @param baseUrl 上游 Base URL
 * @param chatPath 供应商特定的对话或消息请求路径
 * @param modelsPath 供应商特定的模型列表路径
 * @param apiKey 供应商凭证；更新时为空表示保留现有密钥
 * @param priority 路由权重，加权模式下数值越高分配流量越多
 * @param status 凭证状态，例如 ACTIVE 或 DISABLED
 * @param publicModel 可选的网关对外模型别名
 * @param providerModel 可选的上游模型 ID，与 publicModel 配对保存
 * @param modelPrefix 可选模型前缀，保存时会拼到对外模型名前方
 * @param models 批量保存的模型映射列表，优先于兼容用的 publicModel/providerModel 字段
 * @param enabled 渠道和端点是否可用于路由
 */
public record ChannelForm(
    String code,
    String name,
    String type,
    String baseUrl,
    String chatPath,
    String videoPath,
    String imagePath,
    String modelsPath,
    String apiKey,
    String authMode,
    Integer priority,
    String status,
    String publicModel,
    String providerModel,
    String modelPrefix,
    List<ChannelModelForm> models,
    Boolean enabled
) {}
