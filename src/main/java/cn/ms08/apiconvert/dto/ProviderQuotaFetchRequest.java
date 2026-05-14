package cn.ms08.apiconvert.dto;

/**
 * 获取上游渠道额度时传递给供应商客户端的内部请求。
 *
 * @param baseUrl 管理员保存的上游 Base URL
 * @param apiKey 上游供应商密钥；日志和响应中必须脱敏
 */
public record ProviderQuotaFetchRequest(
        String baseUrl,
        String apiKey
) {
}
