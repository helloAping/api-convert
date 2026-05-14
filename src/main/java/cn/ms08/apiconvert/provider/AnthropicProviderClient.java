package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.AnthropicResponseAdapter;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuota;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.logging.LogSanitizer;
import cn.ms08.apiconvert.vo.AnthropicMessageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic 原生 API 供应商客户端，负责消息转发和模型发现。
 */
@Component
public class AnthropicProviderClient implements AiProviderClient {

    /**
     * Anthropic 接口要求携带的 API 版本。
     */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /**
     * 已配置出站日志的共享 RestClient 构建器。
     */
    private final RestClient.Builder restClientBuilder;
    /**
     * 解析 Anthropic 模型列表 JSON 响应。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * 将统一请求转换为 Anthropic 上游请求。
     */
    private final AnthropicRequestAdapter requestAdapter;
    /**
     * 将 Anthropic 上游响应转换为统一响应。
     */
    private final AnthropicResponseAdapter responseAdapter;

    /**
     * 注入 HTTP 客户端和协议适配器；出站日志会对凭证脱敏。
     */
    public AnthropicProviderClient(RestClient.Builder restClientBuilder, AnthropicRequestAdapter requestAdapter,
                                   AnthropicResponseAdapter responseAdapter) {
        this.restClientBuilder = restClientBuilder;
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
    }

    /**
     * 标识当前实现为 Anthropic 供应商客户端。
     */
    @Override
    public ProviderType type() {
        return ProviderType.ANTHROPIC;
    }

    /**
     * 向已配置的 Anthropic 消息路径发送非流式请求，认证头不会写入响应。
     */
    @Override
    public UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request) {
        try {
            AnthropicMessageResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.chatPath())
                    .header("x-api-key", route.apiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .body(requestAdapter.toProviderRequest(request, route.providerModel()))
                    .retrieve()
                    .body(AnthropicMessageResponse.class);
            if (response == null) {
                throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "Anthropic provider returned empty response");
            }
            return responseAdapter.toUnified(response);
        } catch (ProviderException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, upstreamError("Anthropic request failed", exception));
        } catch (RestClientException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "Anthropic request failed: " + exception.getMessage());
        }
    }

    /**
     * 使用 x-api-key 和 anthropic-version 请求头获取 Anthropic 模型列表。
     */
    @Override
    public List<ProviderModel> models(ProviderModelFetchRequest request) {
        try {
            String body = restClientBuilder.clone()
                    .baseUrl(request.baseUrl())
                    .build()
                    .get()
                    .uri(request.modelsPath())
                    .header("x-api-key", request.apiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .retrieve()
                    .body(String.class);
            return parseAnthropicModelList(body);
        } catch (RestClientResponseException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, upstreamError("Anthropic models request failed", exception));
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "Anthropic models request failed: " + exception.getMessage());
        }
    }

    /**
     * Anthropic 普通 API Key 没有统一余额接口，管理端实时展示为不支持，避免伪造或存储额度。
     */
    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        return new ProviderQuota(false, "Anthropic 当前没有通用额度查询接口，请在供应商控制台查看。", null, null, null, "", "");
    }

    /**
     * 兼容常见 Anthropic 风格模型外层结构，也支持兼容网关返回的纯字符串数组。
     */
    private List<ProviderModel> parseAnthropicModelList(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                data = root.path("models");
            }
            if (!data.isArray()) {
                throw new IllegalArgumentException("Anthropic model list response missing data/models array");
            }
            List<ProviderModel> models = new ArrayList<>();
            for (JsonNode item : data) {
                String id = item.isTextual() ? item.asText() : item.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    String ownedBy = item.isObject() ? item.path("owned_by").asText("anthropic") : "anthropic";
                    models.add(new ProviderModel(id, ownedBy));
                }
            }
            return models;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse Anthropic model list response: " + LogSanitizer.sanitizeBody(body), exception);
        }
    }

    /**
     * 保留上游状态码和响应体供前端排查，同时对敏感信息脱敏。
     */
    private String upstreamError(String prefix, RestClientResponseException exception) {
        String body = LogSanitizer.sanitizeBody(exception.getResponseBodyAsString());
        if (body.isBlank()) {
            return prefix + ": status=" + exception.getStatusCode().value();
        }
        return prefix + ": status=" + exception.getStatusCode().value() + ", body=" + body;
    }
}
