package cn.ms08.apiconvert.dto.admin;

import java.math.BigDecimal;

/**
 * 渠道表单中的单个模型映射项，用于一次保存多个上游模型。
 *
 * @param publicName 兼容旧前端字段；有值时按手动别名处理
 * @param providerModel 上游供应商真实模型 ID
 * @param modelAlias 用户手动设置的模型别名，别名必须全局唯一
 * @param inputQuotaPerMillion 每 100 万普通输入 token 消耗的额度
 * @param outputQuotaPerMillion 每 100 万输出 token 消耗的额度
 * @param cacheReadQuotaPerMillion 每 100 万缓存读取输入 token 消耗的额度
 * @param vision 是否支持图片/视觉输入
 * @param toolsSupport 是否支持工具/函数调用
 * @param jsonModeSupport 是否支持 JSON 输出模式
 * @param contextLength 最大上下文窗口（token 数）
 * @param allowedEndpointTypes 逗号分隔的 EndpointType 名称；为空表示不限制
 * @param allowedCapabilities 逗号分隔的能力类型名称，限定该模型可用的上游能力；为空表示使用渠道全部能力
 */
public record ChannelModelForm(
        String publicName,
        String providerModel,
        String modelAlias,
        BigDecimal inputQuotaPerMillion,
        BigDecimal outputQuotaPerMillion,
        BigDecimal cacheReadQuotaPerMillion,
        Boolean vision,
        Boolean toolsSupport,
        Boolean jsonModeSupport,
        Long contextLength,
        String allowedEndpointTypes,
        String allowedCapabilities
) {
    public ChannelModelForm(String publicName, String providerModel) {
        this(publicName, providerModel, null, null, null, null, null, null, null, null, null, null);
    }

    public ChannelModelForm(String publicName, String providerModel, String modelAlias) {
        this(publicName, providerModel, modelAlias, null, null, null, null, null, null, null, null, null);
    }
}
