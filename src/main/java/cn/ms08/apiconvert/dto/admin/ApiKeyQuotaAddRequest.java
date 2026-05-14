package cn.ms08.apiconvert.dto.admin;

import java.math.BigDecimal;

/**
 * 给网关密钥追加额度的请求；只增加余额，不改动滑动窗口配置。
 *
 * @param amount 本次追加的额度，必须大于 0
 */
public record ApiKeyQuotaAddRequest(
        BigDecimal amount
) {
}
