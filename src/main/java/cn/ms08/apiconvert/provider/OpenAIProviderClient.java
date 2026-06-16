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
import cn.ms08.apiconvert.logging.LogSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class OpenAIProviderClient extends BaseAiProviderClient {

    public OpenAIProviderClient(RestClient.Builder restClientBuilder,
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
    public ProviderType type() { return ProviderType.OPENAI; }

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
                    "Provider models request failed: status=" + e.getStatusCode().value());
        } catch (RestClientException | IllegalArgumentException e) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "Provider models request failed: " + e.getMessage());
        }
    }

    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        RestClientResponseException last = null;
        for (String path : List.of("/user/balance", "/dashboard/billing/credit_grants")) {
            try {
                String body = restClientBuilder.clone().baseUrl(request.baseUrl()).build()
                        .get().uri(path)
                        .header("Authorization", "Bearer " + request.apiKey())
                        .retrieve().body(String.class);
                return parseQuota(path, body);
            } catch (RestClientResponseException e) { last = e; }
            catch (RestClientException | IllegalArgumentException e) {
                throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                        "Provider quota request failed: " + e.getMessage());
            }
        }
        if (last != null)
            throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                    "Provider quota request failed: status=" + last.getStatusCode().value());
        throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                "Provider quota request failed");
    }

    private List<ProviderModel> parseOpenAiModelList(String body) {
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
            throw new IllegalArgumentException("Failed to parse: " + LogSanitizer.sanitizeBody(body), e);
        }
    }

    private ProviderQuota parseQuota(String path, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("balance_infos") && root.path("balance_infos").isArray())
                return parseBalanceInfos(root, body);
            BigDecimal totalGranted = decimal(root, "total_granted");
            BigDecimal totalUsed = decimal(root, "total_used");
            BigDecimal totalAvailable = decimal(root, "total_available");
            if (totalGranted != null || totalUsed != null || totalAvailable != null) {
                return new ProviderQuota(true, "可用额度 " + displayAmount(totalAvailable, "USD")
                        + "，已用" + displayAmount(totalUsed, "USD"),
                        totalAvailable, totalUsed, totalAvailable, "USD", LogSanitizer.sanitizeBody(body));
            }
            return new ProviderQuota(true, "已获取额度响应，但未识别到标准额度字段",
                    null, null, null, "", LogSanitizer.sanitizeBody(body));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse quota: " + LogSanitizer.sanitizeBody(body), e);
        }
    }

    private ProviderQuota parseBalanceInfos(JsonNode root, String body) {
        JsonNode first = root.path("balance_infos").get(0);
        if (first == null || first.isMissingNode())
            return new ProviderQuota(true, "上游返回余额列表为空", null, null, null, "", LogSanitizer.sanitizeBody(body));
        String currency = first.path("currency").asText("");
        BigDecimal balance = decimal(first, "total_balance");
        BigDecimal granted = decimal(first, "granted_balance");
        BigDecimal toppedUp = decimal(first, "topped_up_balance");
        String availableText = root.has("is_available") ? (root.path("is_available").asBoolean() ? "可用" : "不可用") : "未知";
        return new ProviderQuota(true, "账户" + availableText + "，余额" + displayAmount(balance, currency)
                + "，赠与额 " + displayAmount(granted, currency)
                + "，充值余额" + displayAmount(toppedUp, currency),
                balance, null, balance, currency, LogSanitizer.sanitizeBody(body));
    }

    private BigDecimal decimal(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull() || node.asText().isBlank()) return null;
        try { return new BigDecimal(node.asText()); } catch (NumberFormatException e) { return null; }
    }

    private String displayAmount(BigDecimal amount, String currency) {
        if (amount == null) return "未知";
        return amount.stripTrailingZeros().toPlainString() + (currency == null || currency.isBlank() ? "" : " " + currency);
    }
}
