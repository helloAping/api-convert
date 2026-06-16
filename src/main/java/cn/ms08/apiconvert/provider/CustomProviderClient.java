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
 * 自定义供应商：用户自行填写 baseUrl / apiKey，同时声明
 * CHAT_COMPLETIONS + ANTHROPIC_MESSAGES 两个能力，适用于自建标准 OpenAI / Anthropic 兼容网关。
 */
@Component
public class CustomProviderClient extends BaseAiProviderClient {

    public CustomProviderClient(RestClient.Builder restClientBuilder,
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
    public ProviderType type() { return ProviderType.CUSTOM; }

    @Override
    public List<ProviderModel> models(ProviderModelFetchRequest request) {
        try {
            String body = restClientBuilder.clone().baseUrl(request.baseUrl()).build()
                    .get().uri(request.modelsPath())
                    .header("Authorization", "Bearer " + request.apiKey())
                    .retrieve().body(String.class);
            return parseOpenAiModelList(body);
        } catch (RestClientResponseException e) {
            throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                    "Custom provider models request failed: status=" + e.getStatusCode().value());
        } catch (RestClientException | IllegalArgumentException e) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "Custom provider models request failed: " + e.getMessage());
        }
    }

    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        return new ProviderQuota(false, "自定义供应商暂不支持通用余额查询，请在上游服务查看。",
                null, null, null, "", "");
    }

    private List<ProviderModel> parseOpenAiModelList(String body) {
        try {
            JsonNode data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("data");
            if (!data.isArray()) throw new IllegalArgumentException("missing data array");
            List<ProviderModel> models = new ArrayList<>();
            for (JsonNode item : data) {
                String id = item.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    models.add(new ProviderModel(id, item.path("owned_by").asText("custom")));
                }
            }
            return models;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse model list", e);
        }
    }
}
