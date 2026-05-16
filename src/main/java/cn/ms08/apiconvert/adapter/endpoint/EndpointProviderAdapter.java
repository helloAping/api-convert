package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;

/**
 * 端点-供应商接口适配器，负责在路由层自动适配端点协议与供应商协议之间的差异。
 * <p>
 * 当客户端请求的端点协议与上游供应商 API 协议不一致时（例如 Responses API 端点路由到
 * Chat Completions 上游），由该适配器在路由层完成请求预处理和响应后处理，使端点处理
 * 器无需感知下游适配细节。
 * </p>
 * <p>
 * 所有实现类通过 {@link EndpointProviderAdapterRegistry} 注册，由
 * {@link cn.ms08.apiconvert.service.ChatGatewayService} 在路由阶段自动选取。
 * </p>
 */
public interface EndpointProviderAdapter {

    /**
     * 适配器处理的端点类型。
     */
    EndpointType sourceEndpoint();

    /**
     * 适配器处理的目标供应商类型。
     */
    ProviderType targetProvider();

    /**
     * 适配请求：将统一请求调整为目标供应商兼容的格式。
     * <p>
     * 当端点请求适配器（如 {@code OpenAiResponsesRequestAdapter.toUnified()}）
     * 完成基础格式转换后，部分供应商特定的请求结构差异需要在此补充处理。
     * </p>
     *
     * @param request 已由端点请求适配器转换的统一请求
     * @return 适配后的统一请求
     */
    default UnifiedChatRequest adaptRequest(UnifiedChatRequest request) {
        return request;
    }

    /**
     * 适配响应：将供应商返回的统一响应调整为端点协议兼容的格式。
     * <p>
     * 适配后的响应会将端点协议对应的可序列化 VO 对象存入 {@code rawResponse} 字段，
     * 端点处理器直接写入 {@code response.getOutputStream()}。
     * </p>
     *
     * @param response   供应商客户端返回的统一响应
     * @param publicModel 对外暴露的模型名
     * @return 适配后的统一响应，rawResponse 为端点协议 VO
     */
    UnifiedChatResponse adaptResponse(UnifiedChatResponse response, String publicModel);
}
