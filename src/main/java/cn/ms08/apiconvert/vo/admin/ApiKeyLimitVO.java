package cn.ms08.apiconvert.vo.admin;

import java.math.BigDecimal;

/**
 * 管理端网关密钥限制项视图，不包含任何密钥或 token。
 *
 * @param id 限制项 ID
 * @param limitType 限制类型，当前支持 QUOTA、REQUEST
 * @param windowValue 滑动窗口长度数值
 * @param windowUnit 滑动窗口单位，支持 MINUTE、HOUR、DAY
 * @param limitValue 限制阈值；额度限制表示额度，请求数限制表示次数
 * @param configJson 预留扩展配置 JSON
 */
public record ApiKeyLimitVO(
        Long id,
        String limitType,
        Integer windowValue,
        String windowUnit,
        BigDecimal limitValue,
        String configJson
) {
}
