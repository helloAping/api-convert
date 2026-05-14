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
 */
public record ChannelModelForm(
        String publicName,
        String providerModel,
        String modelAlias,
        BigDecimal inputQuotaPerMillion,
        BigDecimal outputQuotaPerMillion,
        BigDecimal cacheReadQuotaPerMillion
) {
    /**
     * 兼容仍然只传 publicName/providerModel 的旧调用方。
     */
    public ChannelModelForm(String publicName, String providerModel) {
        this(publicName, providerModel, null, null, null, null);
    }

    /**
     * 兼容已支持别名、但尚未支持额度单价的调用方。
     */
    public ChannelModelForm(String publicName, String providerModel, String modelAlias) {
        this(publicName, providerModel, modelAlias, null, null, null);
    }
}
