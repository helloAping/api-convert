package cn.ms08.apiconvert.vo.admin;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端模型视图，列表接口会按对外模型名聚合多个渠道的重复模型。
 *
 * @param id 聚合展示时使用第一条模型记录 ID
 * @param publicName 网关对外暴露的模型名
 * @param providerCode 单条记录的供应商编码，聚合列表中为首个渠道
 * @param providerModel 单条记录的上游模型名，聚合列表中为首个上游模型
 * @param capabilitiesJson 模型能力配置 JSON
 * @param enabled 聚合后只要存在启用记录即视为启用
 * @param channelCount 保存该模型的渠道数量
 * @param providerCodes 保存该模型的渠道编码列表
 * @param providerModels 该模型对应的上游模型名列表
 * @param inputQuotaPerMillion 每 100 万普通输入 token 消耗的额度
 * @param outputQuotaPerMillion 每 100 万输出 token 消耗的额度
 * @param cacheReadQuotaPerMillion 每 100 万缓存读取输入 token 消耗的额度
 */
public record ModelVO(
    Long id,
    String publicName,
    String providerCode,
    String providerModel,
    String capabilitiesJson,
    Boolean enabled,
    Long channelCount,
    List<String> providerCodes,
    List<String> providerModels,
    BigDecimal inputQuotaPerMillion,
    BigDecimal outputQuotaPerMillion,
    BigDecimal cacheReadQuotaPerMillion
) {}
