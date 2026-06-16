package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // ==================== Hooks ====================

    @Override
    protected OpenAiChatCompletionRequest beforeChatRequest(ModelRoute route, OpenAiChatCompletionRequest request) {
        if (request.getMessages() != null) {
            for (var msg : request.getMessages()) {
                if ("assistant".equals(msg.getRole()) && msg.getReasoningContent() == null) {
                    msg.setReasoningContent("");
                }
            }
        }
        return request;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AnthropicMessageRequest beforeAnthropicRequest(ModelRoute route, AnthropicMessageRequest request) {
        if (request.getMessages() != null) {
            for (var msg : request.getMessages()) {
                Object content = msg.getContent();
                if (content instanceof List<?> blocks) {
                    List<Object> mutableBlocks = new ArrayList<>(blocks);
                    boolean changed = false;
                    for (int i = 0; i < mutableBlocks.size(); i++) {
                        Object block = mutableBlocks.get(i);
                        if (block instanceof Map<?, ?> m && "thinking".equals(String.valueOf(m.get("type")))) {
                            Map<String, Object> mutable = new LinkedHashMap<>((Map<String, Object>) m);
                            if (!mutable.containsKey("thinking") || String.valueOf(mutable.get("thinking")).isBlank()) {
                                Object text = mutable.get("text");
                                mutable.put("thinking", text == null ? "" : String.valueOf(text));
                                mutableBlocks.set(i, mutable);
                                changed = true;
                            }
                        }
                    }
                    if (changed) {
                        msg.setContent(mutableBlocks);
                    }
                }
            }
        }
        return request;
    }

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
