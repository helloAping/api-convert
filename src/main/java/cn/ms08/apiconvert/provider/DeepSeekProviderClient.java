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

@Component
public class DeepSeekProviderClient extends BaseAiProviderClient {

    public DeepSeekProviderClient(RestClient.Builder restClientBuilder,
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
    public ProviderType type() { return ProviderType.DEEPSEEK; }

    // Chat Completions 的 reasoning_content="" 兜底、Anthropic Messages 的 thinking 块补全
    // 之前在 beforeChatRequest / beforeAnthropicRequest 里实现，已迁移到
    // {@link cn.ms08.apiconvert.adapter.endpoint.DeepSeekHook}，按 ProviderType 维度集中维护。
    // 这里不再覆写 BaseAiProviderClient 的钩子，避免重复逻辑。

    // ==================== models / quota ====================

    @Override
    public List<ProviderModel> models(ProviderModelFetchRequest request) {
        try {
            String body = restClientBuilder.clone().baseUrl(request.baseUrl()).build()
                    .get().uri(request.modelsPath())
                    .header("Authorization", "Bearer " + request.apiKey())
                    .retrieve().body(String.class);
            return parseDeepSeekModelList(body);
        } catch (RestClientResponseException e) {
            throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                    "Provider models request failed: status=" + e.getStatusCode().value());
        } catch (RestClientException | IllegalArgumentException e) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "Provider models request failed: " + e.getMessage());
        }
    }

    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        try {
            String body = restClientBuilder.clone().baseUrl(request.baseUrl()).build()
                    .get().uri("/user/balance")
                    .header("Authorization", "Bearer " + request.apiKey())
                    .retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode balanceInfos = root.path("balance_infos");
            if (balanceInfos.isArray() && !balanceInfos.isEmpty()) {
                JsonNode info = balanceInfos.get(0);
                String currency = info.path("currency").asText("");
                return new ProviderQuota(true, "余额 " + info.path("total_balance").asText() + " " + currency,
                        null, null, null, currency, "");
            }
            return new ProviderQuota(true, "已获取额度响应", null, null, null, "", "");
        } catch (Exception e) {
            return new ProviderQuota(false, "DeepSeek 额度获取失败: " + e.getMessage(), null, null, null, "", "");
        }
    }

    private List<ProviderModel> parseDeepSeekModelList(String body) {
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            if (!data.isArray()) throw new IllegalArgumentException("missing data array");
            List<ProviderModel> models = new ArrayList<>();
            for (JsonNode item : data) {
                String id = item.path("id").asText(null);
                if (id != null && !id.isBlank())
                    models.add(new ProviderModel(id, item.path("owned_by").asText("")));
            }
            return models;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse model list", e);
        }
    }
}

