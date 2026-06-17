package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;

/**
 * 跨协议路由适配器，负责在路由层把客户端请求的端点协议适配到上游端点协议。
 * <p>
 * 适配器以 (sourceEndpoint, targetEndpoint) 维度声明，与具体供应商类型解耦：
 * 任何支持 targetEndpoint 协议的供应商（OPENAI / DEEPSEEK / MIMO_TOKEN_PLAN / CUSTOM ...）
 * 都可以复用同一份适配器。
 * </p>
 * <p>
 * 典型场景：
 * <ul>
 *   <li>客户端 /v1/chat/completions 路由到 Anthropic 上游 → ChatCompletionsToAnthropicAdapter</li>
 *   <li>客户端 /v1/messages 路由到 OpenAI Chat 上游 → AnthropicToOpenAiCompatibleAdapter</li>
 *   <li>客户端 /v1/responses 路由到 Anthropic 上游 → ResponsesToAnthropicAdapter</li>
 * </ul>
 * </p>
 * <p>
 * 由 {@link EndpointProviderAdapterRegistry} 按 (sourceEndpoint, targetEndpoint) 索引，
 * {@link cn.ms08.apiconvert.service.ChatGatewayService} 在路由阶段自动选取。
 * </p>
 */
public interface EndpointProviderAdapter {

    /**
     * 适配器处理的源端点类型，即客户端请求的端点协议。
     */
    EndpointType sourceEndpoint();

    /**
     * 适配器处理的目标端点类型，即上游实际响应的端点协议。
     * <p>
     * 与供应商类型解耦：只要上游使用该端点协议（无论 OPENAI/DEEPSEEK/MIMO_TOKEN_PLAN ...），
     * 都通过此方法声明的 targetEndpoint 匹配同一份适配器。
     * </p>
     */
    EndpointType targetEndpoint();

    /**
     * 兼容旧版本：按供应商类型查找适配器（仅用于过渡期，建议新代码改用 {@link #targetEndpoint()}）。
     * <p>
     * 默认实现返回 null。已迁移的适配器应仅重写 {@link #targetEndpoint()}。
     * </p>
     */
    default ProviderType targetProvider() {
        return null;
    }

    /**
     * 适配请求：将统一请求调整为目标端点协议兼容的格式。
     * <p>
     * 当端点请求适配器完成基础格式转换后，部分端点协议特定的请求结构差异需要在此补充处理。
     * </p>
     *
     * @param request 已由端点请求适配器转换的统一请求
     * @return 适配后的统一请求
     */
    default UnifiedChatRequest adaptRequest(UnifiedChatRequest request) {
        return request;
    }

    /**
     * 适配响应：将供应商返回的统一响应调整为源端点协议兼容的格式。
     * <p>
     * 适配后的响应会将源端点协议对应的可序列化 VO 对象存入 {@code rawResponse} 字段。
     * 端点处理器直接写入 {@code response.getOutputStream()}。
     * </p>
     *
     * @param response   供应商客户端返回的统一响应
     * @param publicModel 对外暴露的模型名
     * @return 适配后的统一响应，rawResponse 为源端点协议 VO
     */
    UnifiedChatResponse adaptResponse(UnifiedChatResponse response, String publicModel);
}
