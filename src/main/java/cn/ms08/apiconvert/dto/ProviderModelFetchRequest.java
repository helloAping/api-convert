package cn.ms08.apiconvert.dto;

/**
 * 获取上游模型列表时传递给供应商客户端的内部请求。
 *
 * @param baseUrl 管理员配置的上游 Base URL
 * @param modelsPath 供应商特定的模型列表路径
 * @param apiKey 供应商 API Key；调用方和实现方都不能明文记录到日志
 */
public record ProviderModelFetchRequest(
        String baseUrl,
        String modelsPath,
        String apiKey
) {
}
