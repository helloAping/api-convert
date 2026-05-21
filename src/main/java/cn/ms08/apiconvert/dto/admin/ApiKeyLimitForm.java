package cn.ms08.apiconvert.dto.admin;

import java.math.BigDecimal;

/**
 * 网关密钥限制项表单，用于配置额度、请求数等可扩展滑动窗口限制。
 *
 * @param limitType 限制类型，当前支持 QUOTA、REQUEST
 * @param windowValue 滑动窗口长度数值
 * @param windowUnit 滑动窗口单位，支持 MINUTE、HOUR、DAY
 * @param limitValue 限制阈值；额度限制表示额度，请求数限制表示次数
 * @param configJson 预留扩展配置 JSON，不能包含密钥明文
 */
public record ApiKeyLimitForm(
        String limitType,
        Integer windowValue,
        String windowUnit,
        BigDecimal limitValue,
        String configJson
) {
}
