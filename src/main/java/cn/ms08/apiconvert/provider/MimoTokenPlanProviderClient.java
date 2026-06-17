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
 * Xiaomi MiMo Token Plan 供应商：固定订阅费、按套餐限量调用，
 * 同时支持 OpenAI 兼容协议（/v1/chat/completions）和 Anthropic 兼容协议（/anthropic/v1/messages）。
 * 参考 https://mimo.mi.com/docs/zh-CN/quick-start/summary/first-api-call
 * <p>
 * 默认 baseUrl https://token-plan-cn.xiaomimimo.com，按量付费场景使用 https://api.xiaomimimo.com。
 * Chat 调用使用 Authorization: Bearer，Anthropic 调用使用 x-api-key 鉴权。
 * </p>
 */
@Component
public class MimoTokenPlanProviderClient extends BaseAiProviderClient {

    public MimoTokenPlanProviderClient(RestClient.Builder restClientBuilder,
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
    public ProviderType type() { return ProviderType.MIMO_TOKEN_PLAN; }

    @Override
    public List<ProviderModel> models(ProviderModelFetchRequest request) {
        try {
            String body = restClientBuilder.clone().baseUrl(request.baseUrl()).build()
                    .get().uri(request.modelsPath())
                    .header("Authorization", "Bearer " + request.apiKey())
                    .retrieve().body(String.class);
            return parseMimoModelList(body);
        } catch (RestClientResponseException e) {
            throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                    "MiMo Token Plan models request failed: status=" + e.getStatusCode().value());
        } catch (RestClientException | IllegalArgumentException e) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "MiMo Token Plan models request failed: " + e.getMessage());
        }
    }

    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        return new ProviderQuota(false, "MiMo Token Plan 按套餐扣费，余额请在 Xiaomi MiMo 控制台查看。",
                null, null, null, "", "");
    }

    /**
     * MiMo OpenAI 兼容 /v1/models 返回 {"object":"list","data":[{"id":"mimo-v2.5-pro","object":"model",...}]}
     */
    private List<ProviderModel> parseMimoModelList(String body) {
        try {
            JsonNode data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("data");
            if (!data.isArray()) throw new IllegalArgumentException("missing data array");
            List<ProviderModel> models = new ArrayList<>();
            for (JsonNode item : data) {
                String id = item.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    models.add(new ProviderModel(id, item.path("owned_by").asText("xiaomi")));
                }
            }
            return models;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse MiMo model list", e);
        }
    }
}
