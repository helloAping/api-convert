package cn.ms08.apiconvert.vo.admin;

import java.math.BigDecimal;

/**
 * 管理端渠道详情中的模型映射视图。
 *
 * @param id 模型映射记录 ID
 * @param publicName 网关对外暴露的模型名
 * @param providerModel 上游供应商真实模型 ID
 * @param modelAlias 用户手动设置的模型别名，为空表示使用默认对外模型名
 * @param vision 是否支持图片/视觉输入
 * @param toolsSupport 是否支持工具/函数调用
 * @param jsonModeSupport 是否支持 JSON 输出模式
 * @param contextLength 最大上下文窗口（token 数）
 * @param enabled 该模型映射是否启用
 * @param inputQuotaPerMillion 每 100 万普通输入 token 消耗的额度
 * @param outputQuotaPerMillion 每 100 万输出 token 消耗的额度
 * @param cacheReadQuotaPerMillion 每 100 万缓存读取输入 token 消耗的额度
 */
public record ChannelModelMappingVO(
        Long id,
        String publicName,
        String providerModel,
        String modelAlias,
        Boolean vision,
        Boolean toolsSupport,
        Boolean jsonModeSupport,
        Long contextLength,
        Boolean enabled,
        BigDecimal inputQuotaPerMillion,
        BigDecimal outputQuotaPerMillion,
        BigDecimal cacheReadQuotaPerMillion
) {
}
