package cn.ms08.apiconvert.vo.admin;

import java.util.List;

/**
 * 由供应商、端点、凭证和模型记录组装出的管理端渠道视图。
 *
 * @param id 管理端接口使用的渠道 ID，即供应商表行 ID
 * @param code 稳定的渠道编码，也是 provider_code
 * @param name 管理后台展示名称
 * @param type 供应商类型（ProviderType），例如 OPENAI_COMPATIBLE、ANTHROPIC、OPENAI_RESPONSES、GEMINI
 * @param enabled 渠道是否可用于路由
 * @param baseUrl 上游 Base URL
 * @param chatPath 供应商特定的对话或消息路径
 * @param modelsPath 供应商特定的模型发现路径
 * @param credentialId 当前选中凭证行 ID，没有凭证时为空
 * @param credentialName 当前选中凭证展示名，没有凭证时为空
 * @param apiKey 已脱敏的供应商 API Key，绝不返回原始密钥
 * @param priority 路由权重，加权模式下数值越高分配流量越多
 * @param status 当前选中凭证状态
 * @param modelCount 绑定到该渠道的模型映射数量
 * @param models 绑定到该渠道的模型映射列表
 */
public record ChannelVO(
    Long id,
    String code,
    String name,
    String type,
    Boolean enabled,
    String baseUrl,
    String chatPath,
    String modelsPath,
    Long credentialId,
    String credentialName,
    String apiKey,
    Integer priority,
    String status,
    Long modelCount,
    List<ChannelModelMappingVO> models
) {
    /**
     * 在供应商凭证离开后端边界前进行脱敏。
     */
    public static String maskApiKey(String key) {
        if (key == null || key.isBlank()) return "";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
