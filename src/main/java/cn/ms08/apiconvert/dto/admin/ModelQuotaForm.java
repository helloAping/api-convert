package cn.ms08.apiconvert.dto.admin;

import java.math.BigDecimal;

/**
 * 管理端维护模型额度单价的请求体，三个字段均按每 100 万 token 消耗多少额度配置。
 *
 * @param inputQuotaPerMillion 普通输入 token 单价
 * @param outputQuotaPerMillion 输出 token 单价
 * @param cacheReadQuotaPerMillion 缓存读取输入 token 单价；为空时按普通输入计费
 */
public record ModelQuotaForm(
        BigDecimal inputQuotaPerMillion,
        BigDecimal outputQuotaPerMillion,
        BigDecimal cacheReadQuotaPerMillion
) {
}
