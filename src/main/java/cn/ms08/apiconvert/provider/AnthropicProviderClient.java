package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuota;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * 官方 Anthropic Messages 协议供应商。
 * 默认 baseUrl https://api.anthropic.com，使用 x-api-key 鉴权（继承自 BaseAiProviderClient），
 * 仅声明 ANTHROPIC_MESSAGES 端点能力。
 */
@Component
public class AnthropicProviderClient extends BaseAiProviderClient {

    public AnthropicProviderClient(RestClient.Builder restClientBuilder,
                                   OpenAiRequestAdapter openAiRequestAdapter,
                                   OpenAiResponseAdapter openAiResponseAdapter,
                                   AnthropicRequestAdapter anthropicRequestAdapter,
                                   AnthropicResponseAdapter anthropicResponseAdapter,
                                   OpenAiResponsesRequestAdapter responsesRequestAdapter,
                                   OpenAiResponsesResponseAdapter responsesResponseAdapter) {
        super(restClientBuilder, openAiRequestAdapter, openAiResponseAdapter,
                anthropicRequestAdapter, anthropicResponseAdapter,
                responsesRequestAdapter, responsesResponseAdapter);
    }

    @Override
    public ProviderType type() { return ProviderType.ANTHROPIC; }

    @Override
    public boolean supportsStreaming(cn.ms08.apiconvert.endpoint.EndpointType endpointType) {
        return endpointType == cn.ms08.apiconvert.endpoint.EndpointType.ANTHROPIC_MESSAGES;
    }

    @Override
    public List<ProviderModel> models(ProviderModelFetchRequest request) {
        try {
            String body = restClientBuilder.clone().baseUrl(request.baseUrl()).build()
                    .get().uri(request.modelsPath())
                    .header("x-api-key", request.apiKey())
                    .header("anthropic-version", "2023-06-01")
                    .retrieve().body(String.class);
            return parseAnthropicModelList(body);
        } catch (RestClientResponseException e) {
            throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                    "Anthropic models request failed: status=" + e.getStatusCode().value());
        } catch (RestClientException | IllegalArgumentException e) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "Anthropic models request failed: " + e.getMessage());
        }
    }

    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        return new ProviderQuota(false, "Anthropic 官方供应商暂不支持通用余额查询，请在 Anthropic Console 查看。",
                null, null, null, "", "");
    }

    /**
     * Anthropic /v1/models 返回格式：{"data":[{"id":"claude-3-5-sonnet-...","type":"model","display_name":"..."}]}
     * 解析逻辑与 OpenAI 兼容格式一致。
     */
    private List<ProviderModel> parseAnthropicModelList(String body) {
        try {
            JsonNode data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("data");
            if (!data.isArray()) throw new IllegalArgumentException("missing data array");
            List<ProviderModel> models = new ArrayList<>();
            for (JsonNode item : data) {
                String id = item.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    String ownedBy = item.path("type").asText("anthropic");
                    models.add(new ProviderModel(id, ownedBy));
                }
            }
            return models;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Anthropic model list", e);
        }
    }
}
